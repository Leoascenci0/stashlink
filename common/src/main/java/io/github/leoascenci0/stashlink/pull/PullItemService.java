package io.github.leoascenci0.stashlink.pull;

import io.github.leoascenci0.stashlink.compat.mc.McCompat;
import io.github.leoascenci0.stashlink.Constants;
import io.github.leoascenci0.stashlink.network.PullItemRequest;
import io.github.leoascenci0.stashlink.config.Feature;
import io.github.leoascenci0.stashlink.config.FeatureGate;
import io.github.leoascenci0.stashlink.config.PlayerPrefsStore;
import io.github.leoascenci0.stashlink.source.ItemSource;
import io.github.leoascenci0.stashlink.source.PlayerSources;
import io.github.leoascenci0.stashlink.source.StackListSink;
import net.minecraft.network.chat.Component;
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
        if (!FeatureGate.allow(player, Feature.PULL)) {
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

        // Baú que outro jogador está olhando fica de fora, como na tecla N e no botão do meio.
        PlayerSources.Operation operation = PlayerSources.operationSkippingOpened(player);
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
        select(player, result.slot());
        // O item existe por perto, mas a hotbar está cheia de coisas do jogador: o cliente já cancelou o Litematica,
        // então sem este aviso o Easy Place só pararia de colocar o bloco, sem explicação.
        if (result.slot() == PullLogic.NO_SLOT && operation.source().available(model) > 0) {
            warnHotbarFull(player);
        }
    }

    private static void warnHotbarFull(ServerPlayer player) {
        player.sendOverlayMessage(Component.translatableWithFallback("stashlink.pick_block.hotbar_full",
                "Hotbar full: free a slot to bring this item"));
    }

    /**
     * Seleciona o slot que recebeu o item, como o "pick block" faria. O cliente atualiza o slot selecionado por
     * este pacote e o conteúdo pelo envio normal de inventário no fim do tick.
     */
    private static void select(ServerPlayer player, int slot) {
        Inventory inventory = player.getInventory();
        if (slot != PullLogic.NO_SLOT && inventory.getSelectedSlot() != slot) {
            inventory.setSelectedSlot(slot);
            McCompat.sendHeldSlot(player, slot);
        }
    }

    /**
     * Item 19: o botão do meio do mouse mirando um bloco. O jogo base trata isso no servidor
     * ({@code tryPickItem}) e só sabe pegar o que já está no inventário; o mixin chama aqui antes dele.
     *
     * <p>Só age quando o jogo base não faria nada: o item não está em nenhum slot do inventário (se estiver, o
     * jogo base seleciona ou troca para a hotbar, e nada muda) e o jogador não é criativo (criativo ganha o item
     * do nada). Diferente do Litematica, <b>nunca troca</b> o que o jogador tem: sem slot livre na hotbar só avisa.
     * O raio, as trancas e as permissões vêm das mesmas fontes do reabastecimento.
     *
     * @param wanted o item que o jogo base escolheu para aquele bloco ({@code getCloneItemStack})
     * @return {@code true} se o item foi trazido (o jogo base não deve continuar); {@code false} deixa o jogo
     *         base fazer o que faria sem o StashLink
     */
    public static boolean pickBlock(ServerPlayer player, ItemStack wanted) {
        try {
            return pickBlockChecked(player, wanted);
        } catch (RuntimeException e) {
            // Roda dentro do pacote do jogo: um erro aqui nunca pode derrubar o servidor nem o pick block normal.
            Constants.LOG.error("Falha ao puxar item do pick block de {}", player.getGameProfile().name(), e);
            return false;
        }
    }

    private static boolean pickBlockChecked(ServerPlayer player, ItemStack wanted) {
        if (wanted.isEmpty() || !player.isAlive() || player.isSpectator() || player.isCreative()
                || player.containerMenu != player.inventoryMenu) {
            return false;
        }
        // Quieto de propósito: o botão do meio é apertado o tempo todo, e avisar "trancado" a cada clique irritaria.
        if (!FeatureGate.allowSilently(player, Feature.PULL)) {
            return false;
        }
        Inventory inventory = player.getInventory();
        if (inventory.findSlotMatchingItem(wanted) != -1) {
            return false;
        }
        long now = McCompat.gameTime(player);
        Long last = LAST_REQUEST.get(player);
        if (last != null && now >= last && now - last < MIN_TICKS_BETWEEN_REQUESTS) {
            return false;
        }
        LAST_REQUEST.put(player, now);

        ItemStack model = wanted.copyWithCount(1);
        ItemSource source = PlayerSources.operationSkippingOpened(player).source();
        if (source.available(model) <= 0) {
            return false;   // nada guardado por perto: o jogo base também não faria nada
        }
        List<ItemStack> hotbar = inventory.getNonEquipmentItems().subList(0, Inventory.getSelectionSize());
        if (PullLogic.chooseSlot(hotbar, inventory.getSelectedSlot(), model) == PullLogic.NO_SLOT) {
            warnHotbarFull(player);
            return false;
        }
        // Sem "owned"/"returnTo": a troca no mesmo slot do Item 18 é só do Litematica. Aqui o item vai para um slot
        // livre, e depois dele o jogador manda (o registro do Litematica não é tocado).
        PullLogic.Result result = PullLogic.pull(hotbar, inventory.getSelectedSlot(), model, model.getMaxStackSize(),
                source, null, null);
        if (result.slot() == PullLogic.NO_SLOT) {
            return false;
        }
        select(player, result.slot());
        player.inventoryMenu.broadcastChanges();
        return true;
    }
}
