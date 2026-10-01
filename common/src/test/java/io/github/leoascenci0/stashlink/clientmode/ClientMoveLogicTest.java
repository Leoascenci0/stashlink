package io.github.leoascenci0.stashlink.clientmode;

import io.github.leoascenci0.stashlink.MinecraftTestSetup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntPredicate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Item 10.2: as decisões de clique do modo cliente — quais slots shift-clicar, casamento de item, slots travados,
 * hotbar — e a conservação de itens quando os cliques são "normais" (modo com slots travados).
 */
class ClientMoveLogicTest {
    private static final int CONTAINER_SLOTS = 27;

    @BeforeAll
    static void bootstrap() {
        MinecraftTestSetup.init();
    }

    /** Menu tipo baú: 0-26 container, 27-53 mochila (inv 9-35), 54-62 hotbar (inv 0-8). */
    private static List<ItemStack> emptyMenu() {
        List<ItemStack> m = new ArrayList<>();
        for (int i = 0; i < CONTAINER_SLOTS + 36; i++) {
            m.add(ItemStack.EMPTY);
        }
        return m;
    }

    private static int menuOfInv(int inv) {
        return inv >= 9 ? CONTAINER_SLOTS + (inv - 9) : CONTAINER_SLOTS + 27 + inv;
    }

    private static List<MenuEntry> entries(List<ItemStack> menu) {
        List<MenuEntry> out = new ArrayList<>();
        for (int i = 0; i < menu.size(); i++) {
            int inv = -1;
            if (i >= CONTAINER_SLOTS + 27) {
                inv = i - (CONTAINER_SLOTS + 27);
            } else if (i >= CONTAINER_SLOTS) {
                inv = 9 + (i - CONTAINER_SLOTS);
            }
            out.add(new MenuEntry(i, inv, menu.get(i)));
        }
        return out;
    }

    private static ItemStack stack(Item item, int n) {
        return new ItemStack(item, n);
    }

    private static final IntPredicate NONE_LOCKED = i -> false;

    // ------------------------------------------------------------------ guardar (N)

    @Test
    void stackSlotsPicksOnlyBackpackItemsTheContainerAlreadyHas() {
        List<ItemStack> m = emptyMenu();
        m.set(0, stack(Items.COBBLESTONE, 10));
        m.set(menuOfInv(10), stack(Items.COBBLESTONE, 64)); // mochila, container tem
        m.set(menuOfInv(11), stack(Items.DIAMOND, 3));      // container não tem
        List<Integer> slots = ClientMoveLogic.stackSlots(entries(m), NONE_LOCKED, Set.of());
        assertEquals(List.of(menuOfInv(10)), slots);
    }

    @Test
    void stackSlotsNeverTouchesHotbar() {
        List<ItemStack> m = emptyMenu();
        m.set(0, stack(Items.COBBLESTONE, 10));
        m.set(menuOfInv(0), stack(Items.COBBLESTONE, 64));  // hotbar
        m.set(menuOfInv(8), stack(Items.COBBLESTONE, 64));  // hotbar
        assertTrue(ClientMoveLogic.stackSlots(entries(m), NONE_LOCKED, Set.of()).isEmpty());
    }

    @Test
    void stackSlotsSkipsLockedSlots() {
        List<ItemStack> m = emptyMenu();
        m.set(0, stack(Items.COBBLESTONE, 10));
        m.set(menuOfInv(10), stack(Items.COBBLESTONE, 64));
        m.set(menuOfInv(12), stack(Items.COBBLESTONE, 64));
        List<Integer> slots = ClientMoveLogic.stackSlots(entries(m), i -> i == 10, Set.of());
        assertEquals(List.of(menuOfInv(12)), slots);
    }

    @Test
    void stackSlotsRequiresSameComponents() {
        List<ItemStack> m = emptyMenu();
        m.set(0, stack(Items.IRON_PICKAXE, 1));
        ItemStack renamed = stack(Items.IRON_PICKAXE, 1);
        renamed.set(DataComponents.CUSTOM_NAME, Component.literal("Minha picareta"));
        m.set(menuOfInv(10), renamed);              // nome diferente: não casa
        m.set(menuOfInv(11), stack(Items.IRON_PICKAXE, 1)); // igual: casa
        assertEquals(List.of(menuOfInv(11)), ClientMoveLogic.stackSlots(entries(m), NONE_LOCKED, Set.of()));
    }

    @Test
    void stackSlotsSkipsAlreadyTriedSlots() {
        List<ItemStack> m = emptyMenu();
        m.set(0, stack(Items.COBBLESTONE, 10));
        m.set(menuOfInv(10), stack(Items.COBBLESTONE, 64));
        Set<Integer> tried = new HashSet<>(List.of(menuOfInv(10)));
        assertTrue(ClientMoveLogic.stackSlots(entries(m), NONE_LOCKED, tried).isEmpty());
    }

    @Test
    void stackSlotsEmptyWhenContainerIsEmpty() {
        List<ItemStack> m = emptyMenu();
        m.set(menuOfInv(10), stack(Items.COBBLESTONE, 64));
        assertTrue(ClientMoveLogic.stackSlots(entries(m), NONE_LOCKED, Set.of()).isEmpty());
    }

    @Test
    void storableCountIgnoresHotbarAndLocked() {
        List<ItemStack> m = emptyMenu();
        m.set(menuOfInv(10), stack(Items.COBBLESTONE, 64));
        m.set(menuOfInv(11), stack(Items.COBBLESTONE, 5));
        m.set(menuOfInv(3), stack(Items.COBBLESTONE, 64)); // hotbar
        assertEquals(69, ClientMoveLogic.storableCount(entries(m), NONE_LOCKED));
        assertEquals(64, ClientMoveLogic.storableCount(entries(m), i -> i == 11));
    }

    // ------------------------------------------------------------------ puxar (W)

    @Test
    void pullUsesQuickMoveWhenNothingLocked() {
        List<ItemStack> m = emptyMenu();
        m.set(4, stack(Items.DIAMOND, 8));
        List<MenuEntry> e = entries(m);
        List<Click> clicks = ClientMoveLogic.pullClicks(e.get(4), e, NONE_LOCKED, false);
        assertEquals(List.of(Click.quickMove(4)), clicks);
    }

    @Test
    void pullDoesNothingWhenInventoryIsFull() {
        List<ItemStack> m = emptyMenu();
        for (int i = CONTAINER_SLOTS; i < m.size(); i++) {
            m.set(i, stack(Items.DIRT, 64));
        }
        m.set(4, stack(Items.DIAMOND, 8));
        List<MenuEntry> e = entries(m);
        assertTrue(ClientMoveLogic.pullClicks(e.get(4), e, NONE_LOCKED, false).isEmpty());
    }

    @Test
    void pullIgnoresPlayerSlotsAsSource() {
        List<ItemStack> m = emptyMenu();
        m.set(menuOfInv(10), stack(Items.DIAMOND, 8));
        List<MenuEntry> e = entries(m);
        assertTrue(ClientMoveLogic.pullClicks(e.get(menuOfInv(10)), e, NONE_LOCKED, false).isEmpty());
    }

    /** Simulador mínimo do clique normal (esquerdo) do jogo, só para provar que o plano não perde nem cria item. */
    private static ItemStack[] applyPickups(List<ItemStack> menu, List<Click> clicks) {
        ItemStack cursor = ItemStack.EMPTY;
        for (Click c : clicks) {
            assertEquals(Click.Kind.PICKUP, c.kind());
            ItemStack slot = menu.get(c.slot());
            if (cursor.isEmpty()) {
                cursor = slot;
                menu.set(c.slot(), ItemStack.EMPTY);
            } else if (slot.isEmpty()) {
                menu.set(c.slot(), cursor);
                cursor = ItemStack.EMPTY;
            } else if (ItemStack.isSameItemSameComponents(slot, cursor)) {
                int move = Math.min(cursor.getCount(), slot.getMaxStackSize() - slot.getCount());
                slot.grow(move);
                cursor.shrink(move);
                if (cursor.isEmpty()) {
                    cursor = ItemStack.EMPTY;
                }
            } else {
                menu.set(c.slot(), cursor);
                cursor = slot;
            }
        }
        return new ItemStack[]{cursor};
    }

    private static int total(List<ItemStack> menu, Item item) {
        int n = 0;
        for (ItemStack s : menu) {
            if (s.is(item)) {
                n += s.getCount();
            }
        }
        return n;
    }

    @Test
    void pullWithLockedSlotsAvoidsThemMergesFirstAndKeepsBackpackBeforeHotbar() {
        List<ItemStack> m = emptyMenu();
        m.set(2, stack(Items.COBBLESTONE, 40));
        m.set(menuOfInv(10), stack(Items.COBBLESTONE, 50)); // cabem 14 aqui
        m.set(menuOfInv(11), stack(Items.COBBLESTONE, 60)); // travado: não pode receber
        IntPredicate locked = i -> i == 11 || i == 9; // 9 vazio mas travado
        List<MenuEntry> e = entries(m);
        List<Click> clicks = ClientMoveLogic.pullClicks(e.get(2), e, locked, true);

        assertEquals(Click.pickup(2), clicks.get(0));
        assertEquals(Click.pickup(menuOfInv(10)), clicks.get(1), "completa o stack igual antes de usar vazio");
        assertEquals(Click.pickup(menuOfInv(12)), clicks.get(2), "primeiro vazio permitido, na mochila");
        assertFalse(clicks.contains(Click.pickup(menuOfInv(11))));
        assertFalse(clicks.contains(Click.pickup(menuOfInv(9))));

        int before = total(m, Items.COBBLESTONE);
        ItemStack[] cursor = applyPickups(m, clicks);
        assertTrue(cursor[0].isEmpty(), "nada pode sobrar no cursor");
        assertEquals(before, total(m, Items.COBBLESTONE), "nada some nem duplica");
        assertTrue(m.get(2).isEmpty());
        assertEquals(64, m.get(menuOfInv(10)).getCount());
        assertEquals(60, m.get(menuOfInv(11)).getCount(), "slot travado intocado");
        assertTrue(m.get(menuOfInv(9)).isEmpty(), "slot travado vazio continua vazio");
    }

    @Test
    void pullWithLockedSlotsPrefersBackpackOverHotbarForEmptySlots() {
        List<ItemStack> m = emptyMenu();
        m.set(2, stack(Items.DIAMOND, 5));
        List<MenuEntry> e = entries(m);
        List<Click> clicks = ClientMoveLogic.pullClicks(e.get(2), e, i -> i == 99, true);
        assertEquals(Click.pickup(menuOfInv(9)), clicks.get(1), "mochila (inv 9) antes da hotbar (inv 0)");
    }

    @Test
    void pullWithLockedSlotsUsesTheOnlyFreeSlot() {
        List<ItemStack> m = emptyMenu();
        // só 1 slot livre (inv 20); todo o resto cheio de outra coisa ou travado
        for (int i = 0; i < 36; i++) {
            m.set(menuOfInv(i), stack(Items.DIRT, 64));
        }
        m.set(menuOfInv(20), ItemStack.EMPTY);
        m.set(2, stack(Items.OAK_LOG, 64));
        m.set(3, stack(Items.OAK_LOG, 64));
        // Para o clique de 2: cabe 64 em inv 20 -> sem sobra. Faz com que o item seja 64 + room de 64: ok.
        List<MenuEntry> e = entries(m);
        List<Click> clicks = ClientMoveLogic.pullClicks(e.get(2), e, i -> i == 5, true);
        int before = total(m, Items.OAK_LOG);
        ItemStack[] cursor = applyPickups(m, clicks);
        assertTrue(cursor[0].isEmpty());
        assertEquals(before, total(m, Items.OAK_LOG));
        assertEquals(64, m.get(menuOfInv(20)).getCount());
    }

    @Test
    void pullWithLockedSlotsGivesBackWhatDoesNotFit() {
        List<ItemStack> m = emptyMenu();
        for (int i = 0; i < 36; i++) {
            m.set(menuOfInv(i), stack(Items.DIRT, 64));
        }
        m.set(menuOfInv(20), stack(Items.OAK_LOG, 60)); // cabem só 4
        m.set(2, stack(Items.OAK_LOG, 64));
        List<MenuEntry> e = entries(m);
        List<Click> clicks = ClientMoveLogic.pullClicks(e.get(2), e, i -> i == 5, true);
        int before = total(m, Items.OAK_LOG);
        ItemStack[] cursor = applyPickups(m, clicks);
        assertTrue(cursor[0].isEmpty(), "a sobra volta ao slot de origem, não fica no cursor");
        assertEquals(before, total(m, Items.OAK_LOG));
        assertEquals(64, m.get(menuOfInv(20)).getCount());
        assertEquals(60, m.get(2).getCount());
    }

    @Test
    void roomCountsOnlyUnlockedSlots() {
        List<ItemStack> m = emptyMenu();
        for (int i = 0; i < 36; i++) {
            m.set(menuOfInv(i), stack(Items.DIRT, 64));
        }
        m.set(menuOfInv(15), ItemStack.EMPTY);
        m.set(menuOfInv(16), ItemStack.EMPTY);
        assertEquals(128, ClientMoveLogic.room(entries(m), stack(Items.DIAMOND, 1), NONE_LOCKED));
        assertEquals(64, ClientMoveLogic.room(entries(m), stack(Items.DIAMOND, 1), i -> i == 15));
    }

    // ------------------------------------------------------------------ ordem e cache

    private static Candidate cand(int x, double dist) {
        return new Candidate(new BlockPos(x, 64, 0), null, dist);
    }

    @Test
    void orderSortsByRankThenDistanceAndCaps() {
        List<Candidate> list = List.of(cand(1, 1.0), cand(2, 2.0), cand(3, 3.0), cand(4, 4.0));
        List<Candidate> out = ClientMoveLogic.order(list, c -> c.pos().getX() == 3 ? 0 : 1, 3);
        assertEquals(List.of(3, 1, 2), out.stream().map(c -> c.pos().getX()).toList());
    }

    @Test
    void cacheReportsHasMissingAndUnknown() {
        ContentsCache cache = new ContentsCache();
        long[] chest = {1L, 2L};
        assertNull(cache.has(chest, s -> true));
        cache.put(chest, List.of(stack(Items.COBBLESTONE, 10), ItemStack.EMPTY));
        assertEquals(Boolean.TRUE, cache.has(new long[]{2L}, s -> s.is(Items.COBBLESTONE)), "as duas metades do baú duplo");
        assertEquals(Boolean.FALSE, cache.has(chest, s -> s.is(Items.DIAMOND)));
        assertNull(cache.has(new long[]{9L}, s -> true));
    }

    @Test
    void cacheStoresCopies() {
        ContentsCache cache = new ContentsCache();
        ItemStack s = stack(Items.COBBLESTONE, 10);
        cache.put(new long[]{1L}, List.of(s));
        s.setCount(0);
        assertEquals(Boolean.TRUE, cache.has(new long[]{1L}, x -> x.is(Items.COBBLESTONE) && x.getCount() == 10));
    }
}
