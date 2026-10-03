package io.github.leoascenci0.stashlink.bench;

import io.github.leoascenci0.stashlink.compat.mc.BenchCompat;
import io.github.leoascenci0.stashlink.source.ItemSource;
import io.github.leoascenci0.stashlink.source.Origin;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * O caderno de "emprestados" de cada jogador (Item 16): o que o mod tirou do armazenamento para a estação aberta
 * (grade da bancada, slot de entrada, cursor) e de onde veio. Enquanto o item está na estação ele é do jogador
 * (nenhum outro o enxerga, então dois jogadores nunca disputam o mesmo item). Se ele <b>não for usado</b>, volta
 * para o container de origem, e nunca para a mochila:
 * <ul>
 *   <li>ao escolher outra receita ({@link #returnFromGrid}) ou outro item no painel ({@link #returnCursor});</li>
 *   <li>ao fechar a estação: o jogo devolve a grade à mochila e, no tick seguinte, {@link #tick} devolve de lá.</li>
 * </ul>
 * O que foi gasto (craftado) simplesmente some do caderno: a conta é refeita contra o que ainda está na estação.
 * Guarda só tipos, quantidades e posições, nunca objetos de container nem de mundo.
 */
public final class BenchLedger {
    private static final class Entry {
        final AbstractContainerMenu menu;
        final Map<Item, Integer> borrowed = new LinkedHashMap<>();
        Origin origin;

        Entry(AbstractContainerMenu menu) {
            this.menu = menu;
        }
    }

    /** Por identidade do jogador: relogar cria outro objeto e o antigo é coletado sozinho. */
    private static final Map<ServerPlayer, Entry> LEDGER = new WeakHashMap<>();

    private BenchLedger() {
    }

    /** O mod acabou de pôr {@code got} na estação aberta, vindo de {@code origin}. */
    public static void record(ServerPlayer player, Map<Item, Integer> got, Origin origin) {
        if (got.isEmpty()) {
            return;
        }
        Entry entry = LEDGER.get(player);
        if (entry != null && entry.menu != player.containerMenu) {
            settleFromInventory(player, entry);
            entry = null;
        }
        if (entry == null) {
            entry = new Entry(player.containerMenu);
            LEDGER.put(player, entry);
        }
        for (Map.Entry<Item, Integer> e : got.entrySet()) {
            entry.borrowed.merge(e.getKey(), e.getValue(), Integer::sum);
        }
        entry.origin = entry.origin == null ? origin : entry.origin.merge(origin);
    }

    /** Todo tick: se a estação foi fechada, devolve o que não foi usado; senão, atualiza a conta. */
    public static void tick(ServerPlayer player) {
        Entry entry = LEDGER.get(player);
        if (entry == null) {
            return;
        }
        if (entry.menu != player.containerMenu) {
            settleFromInventory(player, entry);
            LEDGER.remove(player);
        } else {
            reconcile(player, entry);
            if (entry.borrowed.isEmpty()) {
                LEDGER.remove(player);
            }
        }
    }

    /** Antes de montar outra receita: o que o mod pôs na grade e sobrou volta ao container de origem. */
    public static void returnFromGrid(ServerPlayer player, List<Slot> grid) {
        Entry entry = LEDGER.get(player);
        if (entry == null || entry.menu != player.containerMenu) {
            return;
        }
        reconcile(player, entry);
        ItemSource target = BenchPool.returnTarget(player, entry.origin);
        for (Iterator<Map.Entry<Item, Integer>> it = entry.borrowed.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Item, Integer> e = it.next();
            int left = e.getValue();
            for (Slot slot : grid) {
                if (left <= 0) {
                    break;
                }
                ItemStack stack = slot.getItem();
                if (!stack.is(e.getKey()) || !BenchCompat.usableForCrafting(stack)) {
                    continue;
                }
                int move = Math.min(stack.getCount(), left);
                ItemStack out = stack.copyWithCount(move);
                ItemStack rest = target.give(out);
                int moved = move - rest.getCount();
                if (moved > 0) {
                    stack.shrink(moved);
                    slot.set(stack.isEmpty() ? ItemStack.EMPTY : stack);
                    left -= moved;
                }
            }
            if (left <= 0) {
                it.remove();
            } else {
                e.setValue(left);
            }
        }
    }

    /** Antes de pegar outro item do painel: o que está no cursor e veio do armazenamento volta à origem. */
    public static void returnCursor(ServerPlayer player) {
        Entry entry = LEDGER.get(player);
        AbstractContainerMenu menu = player.containerMenu;
        if (entry == null || entry.menu != menu) {
            return;
        }
        ItemStack carried = menu.getCarried();
        Integer owed = entry.borrowed.get(carried.getItem());
        if (carried.isEmpty() || owed == null) {
            return;
        }
        int move = Math.min(carried.getCount(), owed);
        ItemSource target = BenchPool.returnTarget(player, entry.origin);
        ItemStack rest = target.give(carried.copyWithCount(move));
        int moved = move - rest.getCount();
        if (moved > 0) {
            ItemStack now = carried.copy();
            now.shrink(moved);
            menu.setCarried(now.isEmpty() ? ItemStack.EMPTY : now);
            menu.broadcastChanges();
            int left = owed - moved;
            if (left <= 0) {
                entry.borrowed.remove(carried.getItem());
            } else {
                entry.borrowed.put(carried.getItem(), left);
            }
        }
    }

    /** Refaz a conta: nada além do que ainda está no cursor e nos slots de entrada da estação pode estar "emprestado". */
    private static void reconcile(ServerPlayer player, Entry entry) {
        Inventory inventory = player.getInventory();
        for (Iterator<Map.Entry<Item, Integer>> it = entry.borrowed.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Item, Integer> e = it.next();
            int held = 0;
            ItemStack carried = entry.menu.getCarried();
            if (carried.is(e.getKey())) {
                held += carried.getCount();
            }
            ItemStack probe = new ItemStack(e.getKey());
            for (Slot slot : entry.menu.slots) {
                if (slot.container != inventory && slot.mayPlace(probe) && slot.getItem().is(e.getKey())) {
                    held += slot.getItem().getCount();
                }
            }
            if (held <= 0) {
                it.remove();
            } else if (held < e.getValue()) {
                e.setValue(held);
            }
        }
    }

    /** A estação fechou: o jogo já devolveu a grade à mochila; do que não foi usado, devolve à origem. */
    private static void settleFromInventory(ServerPlayer player, Entry entry) {
        if (entry.origin == null) {
            return;
        }
        ItemSource target = BenchPool.returnTarget(player, entry.origin);
        List<ItemStack> backpack = player.getInventory().getNonEquipmentItems();
        for (Map.Entry<Item, Integer> e : new LinkedHashMap<>(entry.borrowed).entrySet()) {
            int left = e.getValue();
            for (int i = 0; i < backpack.size() && left > 0; i++) {
                ItemStack stack = backpack.get(i);
                if (!stack.is(e.getKey()) || !BenchCompat.usableForCrafting(stack)) {
                    continue;
                }
                int move = Math.min(stack.getCount(), left);
                ItemStack rest = target.give(stack.copyWithCount(move));
                int moved = move - rest.getCount();
                if (moved > 0) {
                    stack.shrink(moved);
                    if (stack.isEmpty()) {
                        backpack.set(i, ItemStack.EMPTY);
                    }
                    left -= moved;
                }
            }
        }
        entry.borrowed.clear();
    }
}
