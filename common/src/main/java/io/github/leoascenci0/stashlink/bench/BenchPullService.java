package io.github.leoascenci0.stashlink.bench;

import io.github.leoascenci0.stashlink.Constants;
import io.github.leoascenci0.stashlink.compat.mc.BenchCompat;
import io.github.leoascenci0.stashlink.compat.mc.McCompat;
import io.github.leoascenci0.stashlink.config.Feature;
import io.github.leoascenci0.stashlink.config.FeatureGate;
import io.github.leoascenci0.stashlink.network.BenchPullRequest;
import io.github.leoascenci0.stashlink.source.ItemSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Painel "Armazenamento" das estações (Item 16): o jogador clica num item da lista e o servidor põe um stack no
 * <b>cursor</b> dele, tirando de um container do raio no mesmo passo. Dali em diante é item de verdade na mão,
 * como se tivesse pego da mochila: colocar no slot da estação, shift-clicar o resultado e fechar a tela (o que
 * sobrar vai para a mochila) são cliques normais do jogo, sem item fantasma.
 */
public final class BenchPullService {
    /** Chave por identidade do objeto do jogador: relogar cria outro objeto, e o antigo é coletado sozinho. */
    private static final Map<ServerPlayer, Long> LAST_REQUEST = new WeakHashMap<>();

    private BenchPullService() {
    }

    /** Roda na thread do servidor. */
    public static void handle(ServerPlayer player, BenchPullRequest request) {
        try {
            process(player, request);
        } catch (RuntimeException e) {
            // Pacote vindo da rede: um erro aqui nunca pode derrubar o servidor.
            Constants.LOG.error("Falha ao puxar item do armazenamento para {}", player.getGameProfile().name(), e);
        }
    }

    private static void process(ServerPlayer player, BenchPullRequest request) {
        AbstractContainerMenu menu = player.containerMenu;
        if (!player.isAlive() || player.isSpectator() || menu == player.inventoryMenu
                || menu.containerId != request.containerId() || !BenchCompat.isStation(menu)
                || !menu.stillValid(player) || request.item().isEmpty()) {
            return;
        }
        if (!FeatureGate.allow(player, Feature.BENCH)) {
            return;
        }
        // No máximo um pedido por tick: o painel não precisa de mais, e isso barra spam de pacote.
        long now = McCompat.gameTime(player);
        Long last = LAST_REQUEST.get(player);
        if (last != null && last == now) {
            return;
        }
        LAST_REQUEST.put(player, now);

        ItemStack model = request.item().copyWithCount(1);
        // Trocou de item no painel: o que está no cursor e veio do armazenamento volta ao baú de origem.
        if (!menu.getCarried().isEmpty() && !ItemStack.isSameItemSameComponents(menu.getCarried(), model)) {
            BenchLedger.returnCursor(player);
        }
        ItemStack carried = menu.getCarried();
        // Cursor ocupado por outra coisa (ou já cheio): não troca nem mistura, só ignora.
        if (!carried.isEmpty() && (!ItemStack.isSameItemSameComponents(carried, model)
                || carried.getCount() >= carried.getMaxStackSize())) {
            return;
        }
        int room = carried.isEmpty() ? model.getMaxStackSize() : carried.getMaxStackSize() - carried.getCount();
        int wanted = request.one() ? Math.min(1, room) : room;

        BenchPool pool = BenchPool.of(player);
        ItemSource source = pool.source();
        List<ItemStack> taken = source.take(model, wanted);
        int total = ItemSource.sum(taken);
        if (total <= 0) {
            BenchSync.markDirty(player);
            return;
        }
        // take() nunca passa de "wanted" e wanted cabe no cursor: a soma sempre cabe.
        menu.setCarried(model.copyWithCount(carried.getCount() + total));
        menu.broadcastChanges();
        BenchLedger.record(player, Map.of(model.getItem(), total), pool.origin());
        BenchSync.markDirty(player);
    }
}
