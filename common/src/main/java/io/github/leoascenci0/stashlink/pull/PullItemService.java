package io.github.leoascenci0.stashlink.pull;

import io.github.leoascenci0.stashlink.compat.mc.McCompat;
import io.github.leoascenci0.stashlink.Constants;
import io.github.leoascenci0.stashlink.network.PullItemRequest;
import io.github.leoascenci0.stashlink.config.PlayerPrefsStore;
import io.github.leoascenci0.stashlink.source.ItemSource;
import io.github.leoascenci0.stashlink.source.PlayerSources;
import io.github.leoascenci0.stashlink.source.StackListSink;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Trata o {@link PullItemRequest} no servidor. O cliente só <i>pede</i>; aqui o servidor revalida tudo (quem
 * pede, quanto pede, com que frequência) e usa as mesmas fontes do reabastecimento, que já checam raio e
 * permissão de cada container.
 */
public final class PullItemService {
    /** Pedidos do mesmo jogador mais próximos que isto são ignorados (o Easy Place pode pedir todo tick). */
    static final int MIN_TICKS_BETWEEN_REQUESTS = 4;

    /** Chave por identidade do objeto do jogador: relogar cria outro objeto, e o antigo é coletado sozinho. */
    private static final Map<ServerPlayer, Long> LAST_REQUEST = new WeakHashMap<>();

    /**
     * O slot da hotbar que o mod escolheu por último para cada jogador (ver {@link PulledSlot}). Só guarda
     * posições e itens, nunca o jogador nem o mundo, então a chave fraca basta para não vazar memória.
     */
    private static final Map<ServerPlayer, PulledSlot> LEDGER = new WeakHashMap<>();

    private PullItemService() {
    }

    /** Roda na thread do servidor. */
    public static void handle(ServerPlayer player, PullItemRequest request) {
        try {
            process(player, request);
        } catch (RuntimeException e) {
            // Pacote vindo da rede: um erro aqui nunca pode derrubar o servidor.
            Constants.LOG.error("Falha ao atender pedido de item de {}", player.getGameProfile().name(), e);
        }
    }

    private static void process(ServerPlayer player, PullItemRequest request) {
        if (request.item() == Items.AIR || request.count() < 1) {
            return;
        }
        // Criativo já tem tudo; espectador não mexe em itens; com outro container aberto o jogador já está
        // mexendo no inventário e mudar slots por baixo dele causaria confusão.
        if (!player.isAlive() || player.isCreative() || player.isSpectator()
                || player.containerMenu != player.inventoryMenu) {
            return;
        }
        long now = McCompat.gameTime(player);
        Long last = LAST_REQUEST.get(player);
        if (last != null && now >= last && now - last < MIN_TICKS_BETWEEN_REQUESTS) {
            return;
        }
        LAST_REQUEST.put(player, now);

        Inventory inventory = player.getInventory();
        ItemStack model = new ItemStack(request.item());
        // Nunca mais que um stack cheio, seja qual for o número que o cliente mandou.
        int wanted = Math.min(request.count(), model.getMaxStackSize());
        List<ItemStack> hotbar = inventory.getNonEquipmentItems().subList(0, Inventory.getSelectionSize());
        List<ItemStack> backpack = inventory.getNonEquipmentItems()
                .subList(Inventory.getSelectionSize(), inventory.getNonEquipmentItems().size());

        PulledSlot mine = LEDGER.get(player);
        // O slot só continua sendo do mod se ainda tem o item que o mod pôs (o jogador não trocou nem esvaziou)
        // e não foi travado depois. Senão é do jogador e ninguém mexe.
        if (mine != null && !mine.stillOwned(hotbar)) {
            LEDGER.remove(player);
            mine = null;
        }
        boolean swappable = mine != null && !PlayerPrefsStore.isSlotLocked(player, mine.slot);

        PlayerSources.Operation operation = PlayerSources.operation(player);
        PullLogic.Owned owned = swappable ? mine.asOwned() : null;
        ItemSource returnTo = swappable
                ? operation.returnTarget(mine.origin, new StackListSink(backpack))
                : null;
        PullLogic.Result result = PullLogic.pull(hotbar, inventory.getSelectedSlot(), model, wanted,
                operation.source(), owned, returnTo);
        PulledSlot next = PulledSlot.next(mine, hotbar, result, model, operation.origin());
        if (next == null) {
            LEDGER.remove(player);
        } else {
            LEDGER.put(player, next);
        }
        int slot = result.slot();
        if (slot == PullLogic.NO_SLOT) {
            return;
        }
        // Seleciona o slot que recebeu o item, como o "pick block" faria. O cliente atualiza o slot
        // selecionado por este pacote e o conteúdo pelo envio normal de inventário no fim do tick.
        if (inventory.getSelectedSlot() != slot) {
            inventory.setSelectedSlot(slot);
            McCompat.sendHeldSlot(player, slot);
        }
    }
}
