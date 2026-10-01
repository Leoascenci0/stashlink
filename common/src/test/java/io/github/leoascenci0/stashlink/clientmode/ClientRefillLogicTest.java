package io.github.leoascenci0.stashlink.clientmode;

import io.github.leoascenci0.stashlink.MinecraftTestSetup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Item 10.3: as decisões do reabastecimento da mão no modo cliente — casamento de item (ignorando desgaste),
 * escolha do slot de origem, estado da mão, ordem dos containers pelo cache e condições em que NÃO age.
 */
class ClientRefillLogicTest {
    private static final int CONTAINER_SLOTS = 27;
    private static final int HOTBAR = 2;

    @BeforeAll
    static void bootstrap() {
        MinecraftTestSetup.init();
    }

    /** Menu tipo baú: 0-26 container, 27-53 mochila (inv 9-35), 54-62 hotbar (inv 0-8). */
    private static List<MenuEntry> menu(java.util.Map<Integer, ItemStack> filled) {
        List<MenuEntry> out = new ArrayList<>();
        for (int i = 0; i < CONTAINER_SLOTS + 36; i++) {
            int inv = -1;
            if (i >= CONTAINER_SLOTS + 27) {
                inv = i - (CONTAINER_SLOTS + 27);
            } else if (i >= CONTAINER_SLOTS) {
                inv = 9 + (i - CONTAINER_SLOTS);
            }
            out.add(new MenuEntry(i, inv, filled.getOrDefault(i, ItemStack.EMPTY)));
        }
        return out;
    }

    private static int hotbarMenu(int inv) {
        return CONTAINER_SLOTS + 27 + inv;
    }

    private static ItemStack damaged(int damage) {
        ItemStack s = new ItemStack(Items.IRON_PICKAXE);
        s.set(DataComponents.DAMAGE, damage);
        return s;
    }

    // ------------------------------------------------------------------ casamento de item

    @Test
    void sameForRefillIgnoresCountAndDamage() {
        assertTrue(ClientMoveLogic.sameForRefill(damaged(250), damaged(0)));
        assertTrue(ClientMoveLogic.sameForRefill(new ItemStack(Items.COBBLESTONE, 1), new ItemStack(Items.COBBLESTONE, 64)));
    }

    @Test
    void sameForRefillRequiresSameItemAndComponents() {
        assertFalse(ClientMoveLogic.sameForRefill(new ItemStack(Items.COBBLESTONE), new ItemStack(Items.DIRT)));
        ItemStack renamed = damaged(0);
        renamed.set(DataComponents.CUSTOM_NAME, Component.literal("Minha picareta"));
        assertFalse(ClientMoveLogic.sameForRefill(renamed, damaged(0)));
        assertFalse(ClientMoveLogic.sameForRefill(ItemStack.EMPTY, new ItemStack(Items.DIRT)));
    }

    @Test
    void sameForRefillDoesNotMutateItsArguments() {
        ItemStack a = damaged(100);
        ClientMoveLogic.sameForRefill(a, damaged(0));
        assertEquals(100, a.getOrDefault(DataComponents.DAMAGE, 0));
    }

    // ------------------------------------------------------------------ slot de origem

    @Test
    void refillSourcePicksLargestMatchingStackInContainerOnly() {
        ItemStack model = new ItemStack(Items.COBBLESTONE);
        List<MenuEntry> e = menu(java.util.Map.of(
                3, new ItemStack(Items.COBBLESTONE, 10),
                5, new ItemStack(Items.COBBLESTONE, 40),
                6, new ItemStack(Items.DIRT, 64),
                27, new ItemStack(Items.COBBLESTONE, 64))); // mochila: nunca é origem
        assertEquals(5, ClientMoveLogic.refillSource(e, model, Set.of()));
    }

    @Test
    void refillSourceSkipsTriedSlotsAndReportsNoneWhenMissing() {
        ItemStack model = new ItemStack(Items.COBBLESTONE);
        List<MenuEntry> e = menu(java.util.Map.of(5, new ItemStack(Items.COBBLESTONE, 40)));
        assertEquals(-1, ClientMoveLogic.refillSource(e, model, new HashSet<>(List.of(5))));
        assertEquals(-1, ClientMoveLogic.refillSource(menu(java.util.Map.of(5, new ItemStack(Items.DIRT))), model, Set.of()));
    }

    @Test
    void refillSourceMatchesBrokenToolWithNewOne() {
        List<MenuEntry> e = menu(java.util.Map.of(0, damaged(0)));
        assertEquals(0, ClientMoveLogic.refillSource(e, damaged(249), Set.of()));
    }

    @Test
    void refillClickIsSwapToSelectedHotbarSlot() {
        assertEquals(new Click(5, HOTBAR, Click.Kind.SWAP), ClientMoveLogic.refillClick(5, HOTBAR));
    }

    // ------------------------------------------------------------------ estado da mão

    @Test
    void handStateEmptyRefilledOccupied() {
        ItemStack model = new ItemStack(Items.COBBLESTONE);
        assertEquals(ClientMoveLogic.HandState.EMPTY, ClientMoveLogic.handState(menu(java.util.Map.of()), model, HOTBAR));
        assertEquals(ClientMoveLogic.HandState.REFILLED, ClientMoveLogic.handState(
                menu(java.util.Map.of(hotbarMenu(HOTBAR), new ItemStack(Items.COBBLESTONE, 30))), model, HOTBAR));
        assertEquals(ClientMoveLogic.HandState.OCCUPIED, ClientMoveLogic.handState(
                menu(java.util.Map.of(hotbarMenu(HOTBAR), new ItemStack(Items.DIRT))), model, HOTBAR));
    }

    @Test
    void handStateLooksOnlyAtSelectedHotbarSlot() {
        ItemStack model = new ItemStack(Items.COBBLESTONE);
        // Item em OUTRO slot da hotbar não conta: a mão (slot 2) continua vazia.
        List<MenuEntry> e = menu(java.util.Map.of(hotbarMenu(5), new ItemStack(Items.COBBLESTONE, 64)));
        assertEquals(ClientMoveLogic.HandState.EMPTY, ClientMoveLogic.handState(e, model, HOTBAR));
    }

    // ------------------------------------------------------------------ ordem pelo cache

    private static Candidate cand(int x, double dist) {
        return new Candidate(new BlockPos(x, 64, 0), null, dist);
    }

    @Test
    void candidatesOrderedByCacheThenDistance() {
        ItemStack model = new ItemStack(Items.COBBLESTONE);
        Candidate hasItem = cand(1, 16.0);     // visto COM o item, mais longe
        Candidate neverSeen = cand(2, 1.0);    // nunca visto, o mais perto
        Candidate seenWithout = cand(3, 4.0);  // visto SEM o item
        Candidate neverSeen2 = cand(4, 9.0);
        ContentsCache cache = new ContentsCache();
        cache.put(hasItem.keys(), List.of(new ItemStack(Items.COBBLESTONE, 5)));
        cache.put(seenWithout.keys(), List.of(new ItemStack(Items.DIRT, 5)));
        List<Candidate> ordered = ClientMoveLogic.order(List.of(seenWithout, neverSeen2, hasItem, neverSeen),
                c -> ClientMoveLogic.cacheRank(cache.has(c.keys(), s -> ClientMoveLogic.sameForRefill(s, model))), 8);
        assertEquals(List.of(hasItem, neverSeen, neverSeen2, seenWithout), ordered);
    }

    @Test
    void cacheMatchIgnoresDamageForTools() {
        ContentsCache cache = new ContentsCache();
        Candidate c = cand(1, 1.0);
        cache.put(c.keys(), List.of(damaged(0)));
        assertEquals(0, ClientMoveLogic.cacheRank(
                cache.has(c.keys(), s -> ClientMoveLogic.sameForRefill(s, damaged(249)))));
    }

    @Test
    void cacheRankValues() {
        assertEquals(0, ClientMoveLogic.cacheRank(Boolean.TRUE));
        assertEquals(1, ClientMoveLogic.cacheRank(null));
        assertEquals(2, ClientMoveLogic.cacheRank(Boolean.FALSE));
    }

    @Test
    void orderRespectsContainerLimit() {
        List<Candidate> many = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            many.add(cand(i, i));
        }
        assertEquals(ClientModeEngine.MAX_CONTAINERS_REFILL,
                ClientMoveLogic.order(many, c -> 1, ClientModeEngine.MAX_CONTAINERS_REFILL).size());
    }

    // ------------------------------------------------------------------ quando NÃO age

    @Test
    void refillAllowedOnlyInNormalGameplay() {
        assertTrue(ClientMoveLogic.refillAllowed(false, false, true, false, false, false, true));
    }

    @Test
    void refillNotAllowedInEachBlockingSituation() {
        assertFalse(ClientMoveLogic.refillAllowed(true, false, true, false, false, false, true), "tela aberta");
        assertFalse(ClientMoveLogic.refillAllowed(false, true, true, false, false, false, true), "agachado");
        assertFalse(ClientMoveLogic.refillAllowed(false, false, false, false, false, false, true), "morto");
        assertFalse(ClientMoveLogic.refillAllowed(false, false, true, true, false, false, true), "espectador");
        assertFalse(ClientMoveLogic.refillAllowed(false, false, true, false, true, false, true), "criativo");
        assertFalse(ClientMoveLogic.refillAllowed(false, false, true, false, false, true, true), "Q apertada");
        assertFalse(ClientMoveLogic.refillAllowed(false, false, true, false, false, false, false), "item no cursor");
    }
}
