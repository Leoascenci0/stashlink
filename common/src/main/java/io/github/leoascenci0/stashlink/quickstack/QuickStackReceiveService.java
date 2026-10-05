package io.github.leoascenci0.stashlink.quickstack;

import io.github.leoascenci0.stashlink.Constants;
import io.github.leoascenci0.stashlink.config.Feature;
import io.github.leoascenci0.stashlink.config.FeatureGate;
import io.github.leoascenci0.stashlink.lootall.LootAllService;
import io.github.leoascenci0.stashlink.network.ReceivesRequest;
import io.github.leoascenci0.stashlink.platform.Services;
import io.github.leoascenci0.stashlink.slotlock.SlotLocks;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Trata o botão "recebe itens com a tecla N" (Item 17). Mesma cautela da trava de slot: o servidor confere que o
 * jogador está vivo e com <b>aquele</b> menu aberto, que o menu ainda é válido (distância), que é baú/barril/shulker,
 * que os mods de proteção liberam e que nenhum outro jogador está com o container aberto. Só mexe em metadado.
 */
public final class QuickStackReceiveService {
    private QuickStackReceiveService() {
    }

    /** Roda na thread do servidor. */
    public static void handle(ServerPlayer player, ReceivesRequest request) {
        try {
            process(player, request);
        } catch (RuntimeException e) {
            // Pacote vindo da rede: um erro aqui nunca pode derrubar o servidor.
            Constants.LOG.error("Falha ao mudar o botão da N de {}", player.getGameProfile().name(), e);
        }
    }

    /** O container do baú/barril/shulker do menu aberto (o primeiro slot que não é do jogador), ou {@code null}. */
    public static Container storageOf(ServerPlayer player, AbstractContainerMenu menu) {
        for (Slot slot : menu.slots) {
            if (slot.container != player.getInventory() && QuickStackReceive.supports(slot.container)) {
                return slot.container;
            }
        }
        return null;
    }

    private static void process(ServerPlayer player, ReceivesRequest request) {
        AbstractContainerMenu menu = player.containerMenu;
        if (!player.isAlive() || player.isSpectator() || menu == player.inventoryMenu
                || menu.containerId != request.containerId() || !LootAllService.isSupportedMenu(menu)
                || !menu.stillValid(player)) {
            return;
        }
        // O botão é parte da tecla N: com ela trancada ou desligada, o servidor não muda nada (Revisão 1.0).
        if (!FeatureGate.allow(player, Feature.QUICK_STACK)) {
            return;
        }
        Container container = storageOf(player, menu);
        if (container == null) {
            player.sendOverlayMessage(Component.translatableWithFallback("stashlink.receives.unsupported",
                    "This container cannot be configured"));
            return;
        }
        for (BlockEntity be : SlotLocks.holders(container)) {
            if (!Services.PLATFORM.canPlayerUseBlock(player, be.getBlockPos())) {
                return;
            }
        }
        if (QuickStackService.openedByAnother(player, container)) {
            player.sendOverlayMessage(Component.translatableWithFallback("stashlink.slot_lock.busy",
                    "Another player has this container open"));
            return;
        }
        QuickStackReceive.set(container, request.receives());
        player.sendOverlayMessage(request.receives()
                ? Component.translatableWithFallback("stashlink.receives.on", "This container now receives items with N")
                : Component.translatableWithFallback("stashlink.receives.off", "N will no longer put items in this container"));
    }
}
