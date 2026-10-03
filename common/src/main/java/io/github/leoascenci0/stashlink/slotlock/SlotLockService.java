package io.github.leoascenci0.stashlink.slotlock;

import io.github.leoascenci0.stashlink.Constants;
import io.github.leoascenci0.stashlink.lootall.LootAllService;
import io.github.leoascenci0.stashlink.network.LockSlotRequest;
import io.github.leoascenci0.stashlink.platform.Services;
import io.github.leoascenci0.stashlink.quickstack.QuickStackService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Trata o pedido de travar/destravar (Alt + clique). O cliente só aponta o slot; o servidor confere tudo: o
 * jogador está vivo e com <b>aquele</b> menu aberto (o {@code containerMenu} do servidor), o menu ainda é válido
 * (distância), é baú/barril/shulker, o slot é do container (não do jogador), os mods de proteção liberam e
 * nenhum outro jogador está com o container aberto. Mexe só em metadado: nenhum item muda de lugar.
 */
public final class SlotLockService {
    private SlotLockService() {
    }

    /** Roda na thread do servidor. */
    public static void handle(ServerPlayer player, LockSlotRequest request) {
        try {
            process(player, request);
        } catch (RuntimeException e) {
            // Pacote vindo da rede: um erro aqui nunca pode derrubar o servidor.
            Constants.LOG.error("Falha ao travar slot de {}", player.getGameProfile().name(), e);
        }
    }

    private static void process(ServerPlayer player, LockSlotRequest request) {
        AbstractContainerMenu menu = player.containerMenu;
        if (!player.isAlive() || player.isSpectator() || menu == player.inventoryMenu
                || menu.containerId != request.containerId() || !LootAllService.isSupportedMenu(menu)
                || !menu.stillValid(player)
                || request.menuSlot() < 0 || request.menuSlot() >= menu.slots.size()) {
            return;
        }
        Slot slot = menu.slots.get(request.menuSlot());
        if (slot.container == player.getInventory() || !SlotLocks.supports(slot.container, slot.getContainerSlot())) {
            return;
        }
        for (BlockEntity be : SlotLocks.holders(slot.container)) {
            if (!Services.PLATFORM.canPlayerUseBlock(player, be.getBlockPos())) {
                return;
            }
        }
        if (QuickStackService.openedByAnother(player, slot.container)) {
            player.sendOverlayMessage(Component.translatableWithFallback("stashlink.slot_lock.busy",
                    "Another player has this container open"));
            return;
        }
        // Slot com item: trava para ele. Vazio: para o item do cursor (reservar antes de guardar).
        ItemStack candidate = slot.hasItem() ? slot.getItem() : menu.getCarried();
        SlotLocks.Result result = SlotLocks.toggle(slot.container, slot.getContainerSlot(), candidate);
        switch (result) {
            case LOCKED -> player.sendOverlayMessage(Component.translatableWithFallback("stashlink.slot_lock.locked",
                    "Slot reserved for %s", candidate.getHoverName()));
            case UNLOCKED -> player.sendOverlayMessage(Component.translatableWithFallback("stashlink.slot_lock.unlocked",
                    "Slot unlocked"));
            case NOTHING_TO_LOCK -> player.sendOverlayMessage(Component.translatableWithFallback("stashlink.slot_lock.nothing",
                    "Click a slot with an item, or an empty slot while holding one, to reserve it"));
            case UNSUPPORTED -> { }
        }
    }
}
