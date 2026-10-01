package io.github.leoascenci0.stashlink.lootall;

import io.github.leoascenci0.stashlink.compat.mc.McCompat;
import io.github.leoascenci0.stashlink.Constants;
import io.github.leoascenci0.stashlink.config.PlayerPrefsStore;
import io.github.leoascenci0.stashlink.config.StashLinkConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Trata a tecla W no servidor. O cliente só <i>pede</i>; aqui o servidor confere que o jogador está vivo, tem
 * mesmo um container aberto (o {@code containerMenu} <b>do servidor</b>, não o que o cliente diz), que ele ainda
 * é válido (distância, bloco existente) e que o pedido não é spam.
 */
public final class LootAllService {
    /** Chave por identidade do objeto do jogador: relogar cria outro objeto, e o antigo é coletado sozinho. */
    private static final Map<ServerPlayer, Long> LAST_REQUEST = new WeakHashMap<>();

    private LootAllService() {
    }

    /** Roda na thread do servidor. */
    public static void handle(ServerPlayer player) {
        try {
            process(player);
        } catch (RuntimeException e) {
            // Pacote vindo da rede: um erro aqui nunca pode derrubar o servidor.
            Constants.LOG.error("Falha ao puxar itens para {}", player.getGameProfile().name(), e);
        }
    }

    /** Só menus de armazenamento simples: baú, barril, ender chest (ChestMenu) e shulker. */
    public static boolean isSupportedMenu(AbstractContainerMenu menu) {
        return menu instanceof ChestMenu || menu instanceof ShulkerBoxMenu;
    }

    private static void process(ServerPlayer player) {
        AbstractContainerMenu menu = player.containerMenu;
        if (!player.isAlive() || player.isSpectator() || menu == player.inventoryMenu || !isSupportedMenu(menu)
                || !menu.stillValid(player)) {
            return;
        }
        long now = McCompat.gameTime(player);
        Long last = LAST_REQUEST.get(player);
        if (last != null && now >= last && now - last < StashLinkConfig.LOOT_ALL_COOLDOWN_TICKS) {
            return;
        }
        LAST_REQUEST.put(player, now);

        Inventory inventory = player.getInventory();
        // Os slots do menu que não são do jogador são o container (baú duplo = um CompoundContainer só).
        List<Container> containers = new ArrayList<>();
        for (Slot slot : menu.slots) {
            if (slot.container != inventory && !containers.contains(slot.container)) {
                containers.add(slot.container);
            }
        }

        int moved = 0;
        int left = 0;
        for (Container container : containers) {
            LootAllLogic.Result result = LootAllLogic.pull(container, inventory,
                    inventory.getNonEquipmentItems(), slot -> PlayerPrefsStore.isSlotLocked(player, slot));
            moved += result.itemsMoved();
            left += result.itemsLeft();
        }
        if (moved > 0) {
            menu.broadcastChanges();
        }

        if (moved > 0 && left > 0) {
            player.sendOverlayMessage(Component.translatableWithFallback("stashlink.loot_all.partial",
                    "%s items taken, %s did not fit", moved, left));
        } else if (moved > 0) {
            player.sendOverlayMessage(Component.translatableWithFallback("stashlink.loot_all.done",
                    "%s items taken", moved));
        } else if (left > 0) {
            player.sendOverlayMessage(Component.translatableWithFallback("stashlink.loot_all.full",
                    "No room in your inventory"));
        } else {
            player.sendOverlayMessage(Component.translatableWithFallback("stashlink.loot_all.empty",
                    "The container is empty"));
        }
    }
}
