package io.github.leoascenci0.stashlink.slotlock;

import io.github.leoascenci0.stashlink.Constants;
import io.github.leoascenci0.stashlink.lootall.LootAllService;
import io.github.leoascenci0.stashlink.network.SlotLocksSync;
import io.github.leoascenci0.stashlink.platform.Services;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Mantém o cliente com o mod sabendo quais slots do container aberto estão reservados. A cada tick, para cada
 * jogador com baú/barril/shulker aberto, compara o "retrato" das travas com o último enviado e só manda se mudou
 * (abrir o menu, alguém travar/destravar). Quem não tem o mod no cliente nunca recebe nada (a plataforma só envia
 * a quem registrou o pacote) e enxerga o slot simplesmente vazio.
 */
public final class SlotLockSync {
    private record Sent(AbstractContainerMenu menu, List<SlotLocksSync.Entry> entries) {
    }

    /** Por identidade do jogador: relogar cria outro objeto e o antigo é coletado sozinho. */
    private static final Map<ServerPlayer, Sent> SENT = new WeakHashMap<>();

    private SlotLockSync() {
    }

    public static void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            try {
                tickPlayer(player);
            } catch (RuntimeException e) {
                Constants.LOG.error("Falha ao sincronizar slots travados de {}", player.getGameProfile().name(), e);
            }
        }
    }

    private static void tickPlayer(ServerPlayer player) {
        AbstractContainerMenu menu = player.containerMenu;
        if (menu == player.inventoryMenu || !LootAllService.isSupportedMenu(menu)) {
            SENT.remove(player);
            return;
        }
        List<SlotLocksSync.Entry> now = snapshot(player, menu);
        Sent last = SENT.get(player);
        boolean same = last != null && last.menu() == menu ? last.entries().equals(now) : now.isEmpty();
        if (same) {
            return;
        }
        SENT.put(player, new Sent(menu, now));
        Services.PLATFORM.sendIfSupported(player, new SlotLocksSync(menu.containerId, now));
    }

    /** As travas do container aberto, por índice de slot do menu. Público para os testes. */
    public static List<SlotLocksSync.Entry> snapshot(ServerPlayer player, AbstractContainerMenu menu) {
        List<SlotLocksSync.Entry> out = new ArrayList<>();
        for (Slot slot : menu.slots) {
            if (slot.container == player.getInventory()) {
                continue;
            }
            Item item = SlotLocks.lockedItem(slot.container, slot.getContainerSlot());
            if (item != null) {
                out.add(new SlotLocksSync.Entry(slot.index, new ItemStack(item)));
            }
        }
        return out;
    }
}
