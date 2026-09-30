package io.github.leoascenci0.stashlink.quickstack;

import io.github.leoascenci0.stashlink.Constants;
import io.github.leoascenci0.stashlink.config.StashLinkConfig;
import io.github.leoascenci0.stashlink.source.ContainerInsert;
import io.github.leoascenci0.stashlink.source.ContainerSource;
import io.github.leoascenci0.stashlink.source.NearbyContainers;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Trata a tecla N no servidor. O cliente só <i>pede</i>; aqui o servidor revalida quem pede e com que
 * frequência, e cada container é conferido individualmente: dentro do raio, liberado pelos mods de proteção
 * (claim) e não aberto por outro jogador naquele momento.
 */
public final class QuickStackService {
    /** Chave por identidade do objeto do jogador: relogar cria outro objeto, e o antigo é coletado sozinho. */
    private static final Map<ServerPlayer, Long> LAST_REQUEST = new WeakHashMap<>();

    private QuickStackService() {
    }

    /** Roda na thread do servidor. */
    public static void handle(ServerPlayer player) {
        try {
            process(player);
        } catch (RuntimeException e) {
            // Pacote vindo da rede: um erro aqui nunca pode derrubar o servidor.
            Constants.LOG.error("Falha ao guardar itens de {}", player.getGameProfile().name(), e);
        }
    }

    private static void process(ServerPlayer player) {
        // Espectador não mexe em itens; com outro menu aberto o jogador já está mexendo no inventário.
        if (!player.isAlive() || player.isSpectator() || player.containerMenu != player.inventoryMenu) {
            return;
        }
        long now = player.level().getGameTime();
        Long last = LAST_REQUEST.get(player);
        if (last != null && now >= last && now - last < StashLinkConfig.QUICK_STACK_COOLDOWN_TICKS) {
            return;
        }
        LAST_REQUEST.put(player, now);

        List<ContainerSource.Entry> targets = new ArrayList<>();
        for (ContainerSource.Entry entry : NearbyContainers.findAllStorage(player)) {
            // Além da permissão (claim), ninguém pode estar com este container aberto agora: mexer por baixo
            // de quem está olhando o baú geraria confusão (e é a brecha clássica de duplicação).
            targets.add(new ContainerSource.Entry(entry.container(),
                    () -> !openedByAnother(player, entry.container()) && entry.allowed().getAsBoolean()));
        }

        Inventory inventory = player.getInventory();
        // Só a mochila e a hotbar (36 slots); armadura e mão secundária ficam fora. Hotbar e slots travados
        // nunca são esvaziados.
        QuickStackLogic.Result result = QuickStackLogic.stack(inventory.getNonEquipmentItems(),
                slot -> slot < Inventory.getSelectionSize() || StashLinkConfig.isSlotLocked(slot), targets);

        if (result.itemsMoved() > 0) {
            player.sendOverlayMessage(Component.translatableWithFallback("stashlink.quick_stack.done",
                    "%s items stored in %s containers", result.itemsMoved(), result.containersUsed()));
        } else {
            player.sendOverlayMessage(Component.translatableWithFallback("stashlink.quick_stack.nothing",
                    "Nothing to store nearby"));
        }
    }

    /** {@code true} se algum <b>outro</b> jogador está com uma GUI aberta que mostra este container. */
    static boolean openedByAnother(ServerPlayer player, Container container) {
        for (ServerPlayer other : player.level().getServer().getPlayerList().getPlayers()) {
            if (other == player || other.containerMenu == other.inventoryMenu) {
                continue;
            }
            AbstractContainerMenu menu = other.containerMenu;
            for (Slot slot : menu.slots) {
                if (ContainerInsert.isOrContains(container, slot.container)) {
                    return true;
                }
            }
        }
        return false;
    }
}
