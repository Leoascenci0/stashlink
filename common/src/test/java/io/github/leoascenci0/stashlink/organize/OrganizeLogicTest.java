package io.github.leoascenci0.stashlink.organize;

import io.github.leoascenci0.stashlink.MinecraftTestSetup;
import io.github.leoascenci0.stashlink.source.ContainerSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Item 20: arrumar um container e o plano do sistema, sem rede nem jogador. A soma de itens nunca muda. */
class OrganizeLogicTest {
    @BeforeAll
    static void bootstrap() {
        MinecraftTestSetup.init();
    }

    private static SimpleContainer chest() {
        return new SimpleContainer(27);
    }

    private static ContainerSource.Entry entry(Container c, int x) {
        return new ContainerSource.Entry(c, () -> true, Set.of(new BlockPos(x, 0, 0)));
    }

    private static int count(Container c, Item item) {
        return c.countItem(item);
    }

    // ------------------------------------------------------------------------------------------ 20.1 um baú

    @Test
    void mergesPartialStacksAndSortsByCategoryThenName() {
        SimpleContainer c = chest();
        c.setItem(0, new ItemStack(Items.CALCITE, 10));
        c.setItem(3, new ItemStack(Items.DRIPSTONE_BLOCK, 5));
        c.setItem(5, new ItemStack(Items.GOLDEN_CHESTPLATE, 1));
        c.setItem(7, new ItemStack(Items.CALCITE, 20));
        c.setItem(9, new ItemStack(Items.SWEET_BERRIES, 60));
        c.setItem(12, new ItemStack(Items.DRIPSTONE_BLOCK, 30));
        c.setItem(14, new ItemStack(Items.SWEET_BERRIES, 7));

        assertTrue(OrganizeLogic.tidy(c));

        // sem categoria primeiro (por nome do item), depois armadura, depois comida
        assertEquals(new ItemStack(Items.CALCITE, 30).getCount(), c.getItem(0).getCount());
        assertTrue(c.getItem(0).is(Items.CALCITE));
        assertTrue(c.getItem(1).is(Items.DRIPSTONE_BLOCK) && c.getItem(1).getCount() == 35);
        assertTrue(c.getItem(2).is(Items.GOLDEN_CHESTPLATE));
        assertTrue(c.getItem(3).is(Items.SWEET_BERRIES) && c.getItem(3).getCount() == 64);
        assertTrue(c.getItem(4).is(Items.SWEET_BERRIES) && c.getItem(4).getCount() == 3);
        for (int slot = 5; slot < 27; slot++) {
            assertTrue(c.getItem(slot).isEmpty(), "slot " + slot);
        }
    }

    @Test
    void tidyIsIdempotent() {
        SimpleContainer c = chest();
        Random rnd = new Random(7);
        Item[] items = {Items.CALCITE, Items.DRIPSTONE_BLOCK, Items.SWEET_BERRIES, Items.GOLDEN_PICKAXE, Items.MUD_BRICKS};
        for (int i = 0; i < 20; i++) {
            Item item = items[rnd.nextInt(items.length)];
            c.setItem(rnd.nextInt(27), new ItemStack(item, item.getDefaultMaxStackSize() == 1 ? 1 : 1 + rnd.nextInt(30)));
        }
        OrganizeLogic.tidy(c);
        List<ItemStack> once = OrganizeLogic.snapshot(c);
        assertFalse(OrganizeLogic.tidy(c), "arrumar de novo não muda nada");
        assertTrue(OrganizeLogic.sameContents(c, once));
    }

    @Test
    void differentComponentsStaySeparate() {
        SimpleContainer c = chest();
        ItemStack worn = new ItemStack(Items.GOLDEN_PICKAXE);
        worn.setDamageValue(10);
        c.setItem(0, worn);
        c.setItem(1, new ItemStack(Items.GOLDEN_PICKAXE));
        OrganizeLogic.tidy(c);
        assertEquals(2, countStacks(c));
    }

    @Test
    void nothingToDoReturnsFalse() {
        SimpleContainer c = chest();
        c.setItem(0, new ItemStack(Items.CALCITE, 64));
        assertFalse(OrganizeLogic.tidy(c));
        assertNull(OrganizeLogic.layout(chest()));
    }

    @Test
    void layoutDoesNotTouchTheContainer() {
        SimpleContainer c = chest();
        c.setItem(3, new ItemStack(Items.CALCITE, 5));
        c.setItem(8, new ItemStack(Items.CALCITE, 5));
        List<ItemStack> before = OrganizeLogic.snapshot(c);
        assertNotNull(OrganizeLogic.layout(c));
        assertTrue(OrganizeLogic.sameContents(c, before));
    }

    @Test
    void randomTidyKeepsTheSum() {
        Random rnd = new Random(99);
        Item[] items = {Items.CALCITE, Items.DRIPSTONE_BLOCK, Items.SWEET_BERRIES, Items.GOLDEN_PICKAXE, Items.MUD_BRICKS,
                Items.ENDER_PEARL, Items.GOLDEN_CHESTPLATE};
        for (int round = 0; round < 300; round++) {
            SimpleContainer c = chest();
            for (int i = 0; i < 1 + rnd.nextInt(26); i++) {
                Item item = items[rnd.nextInt(items.length)];
                c.setItem(rnd.nextInt(27), new ItemStack(item, 1 + rnd.nextInt(item.getDefaultMaxStackSize())));
            }
            OrganizeLogic.Totals before = OrganizeLogic.totals(OrganizeLogic.snapshot(c));
            OrganizeLogic.tidy(c);
            assertEquals(before, OrganizeLogic.totals(OrganizeLogic.snapshot(c)), "rodada " + round);
            // nenhum stack passa do limite e não sobram dois parciais do mesmo item
            for (Item item : items) {
                int partial = 0;
                for (int slot = 0; slot < 27; slot++) {
                    ItemStack s = c.getItem(slot);
                    if (s.is(item) && s.getCount() < s.getMaxStackSize()) {
                        partial++;
                    }
                    assertTrue(s.getCount() <= s.getMaxStackSize());
                }
                assertTrue(partial <= 1, "mais de um stack parcial de " + item);
            }
        }
    }

    private static int countStacks(Container c) {
        int n = 0;
        for (int slot = 0; slot < c.getContainerSize(); slot++) {
            if (!c.getItem(slot).isEmpty()) {
                n++;
            }
        }
        return n;
    }

    // ------------------------------------------------------------------------------------------ 20.2 o sistema

    @Test
    void itemsGoToTheContainerThatHasMostOfThem() {
        SimpleContainer a = chest();
        SimpleContainer b = chest();
        SimpleContainer c = chest();
        a.setItem(7, new ItemStack(Items.CALCITE, 10));
        a.setItem(2, new ItemStack(Items.DRIPSTONE_BLOCK, 5));
        b.setItem(9, new ItemStack(Items.CALCITE, 50));
        b.setItem(1, new ItemStack(Items.MUD_BRICKS, 3));
        c.setItem(4, new ItemStack(Items.DRIPSTONE_BLOCK, 40));
        c.setItem(11, new ItemStack(Items.CALCITE, 3));
        List<ContainerSource.Entry> entries = List.of(entry(a, 1), entry(b, 2), entry(c, 3));
        OrganizeLogic.Totals before = total(a, b, c);
        List<ItemStack> sa = OrganizeLogic.snapshot(a);
        List<ItemStack> sb = OrganizeLogic.snapshot(b);
        List<ItemStack> sc = OrganizeLogic.snapshot(c);

        OrganizeSystem.Plan plan = OrganizeSystem.plan(entries, stack -> false);

        // o plano não mexeu em nada
        assertTrue(OrganizeLogic.sameContents(a, sa) && OrganizeLogic.sameContents(b, sb) && OrganizeLogic.sameContents(c, sc));
        assertFalse(plan.isEmpty());
        int moved = plan.moves().stream().mapToInt(OrganizeSystem.Move::count).sum();
        assertEquals(10 + 3 + 5, moved);

        assertTrue(OrganizeSystem.apply(plan, entries));
        assertEquals(0, count(a, Items.CALCITE) + count(a, Items.DRIPSTONE_BLOCK));
        assertEquals(63, count(b, Items.CALCITE));
        assertEquals(45, count(c, Items.DRIPSTONE_BLOCK));
        assertEquals(3, count(b, Items.MUD_BRICKS));
        assertEquals(before, total(a, b, c));

        // idempotente: um segundo plano não tem o que fazer
        assertTrue(OrganizeSystem.plan(entries, stack -> false).isEmpty());

        assertTrue(OrganizeSystem.undo(plan, entries));
        assertTrue(OrganizeLogic.sameContents(a, sa) && OrganizeLogic.sameContents(b, sb) && OrganizeLogic.sameContents(c, sc),
                "desfazer devolve cada item exatamente ao slot de antes");
    }

    @Test
    void applyAndUndoRefuseWhenSomethingChanged() {
        SimpleContainer a = chest();
        SimpleContainer b = chest();
        a.setItem(0, new ItemStack(Items.CALCITE, 10));
        b.setItem(0, new ItemStack(Items.CALCITE, 50));
        List<ContainerSource.Entry> entries = List.of(entry(a, 1), entry(b, 2));
        OrganizeSystem.Plan plan = OrganizeSystem.plan(entries, stack -> false);
        a.setItem(5, new ItemStack(Items.MUD_BRICKS, 1));               // alguém mexeu depois da prévia
        List<ItemStack> sa = OrganizeLogic.snapshot(a);
        List<ItemStack> sb = OrganizeLogic.snapshot(b);
        assertFalse(OrganizeSystem.apply(plan, entries));
        assertTrue(OrganizeLogic.sameContents(a, sa) && OrganizeLogic.sameContents(b, sb), "nada foi gravado");

        a.removeItemNoUpdate(5);
        assertTrue(OrganizeSystem.apply(plan, entries));
        b.setItem(20, new ItemStack(Items.MUD_BRICKS, 2));              // e depois de aplicar
        List<ItemStack> sb2 = OrganizeLogic.snapshot(b);
        assertFalse(OrganizeSystem.undo(plan, entries));
        assertTrue(OrganizeLogic.sameContents(b, sb2), "desfazer recusado não grava nada");
    }

    @Test
    void containerWithoutPermissionIsLeftAlone() {
        SimpleContainer a = chest();
        SimpleContainer b = chest();
        a.setItem(0, new ItemStack(Items.CALCITE, 10));
        b.setItem(0, new ItemStack(Items.CALCITE, 50));
        List<ContainerSource.Entry> entries = List.of(entry(a, 1),
                new ContainerSource.Entry(b, () -> false, Set.of(new BlockPos(2, 0, 0))));
        OrganizeSystem.Plan plan = OrganizeSystem.plan(entries, stack -> false);
        assertTrue(plan.isEmpty(), "sem permissão no baú de destino, nada se move");
        assertEquals(10, count(a, Items.CALCITE));
        assertEquals(50, count(b, Items.CALCITE));
    }

    @Test
    void excludedItemsNeverMove() {
        SimpleContainer a = chest();
        SimpleContainer b = chest();
        a.setItem(0, new ItemStack(Items.SWEET_BERRIES, 5));
        b.setItem(0, new ItemStack(Items.SWEET_BERRIES, 30));
        List<ContainerSource.Entry> entries = List.of(entry(a, 1), entry(b, 2));
        OrganizeSystem.Plan plan = OrganizeSystem.plan(entries, stack -> stack.is(Items.SWEET_BERRIES));
        assertTrue(plan.moves().isEmpty());
        assertEquals(5, count(a, Items.SWEET_BERRIES));
    }

    @Test
    void fullHomeKeepsTheRestWhereItWas() {
        SimpleContainer a = chest();
        SimpleContainer b = chest();
        for (int slot = 0; slot < 27; slot++) {
            b.setItem(slot, new ItemStack(Items.CALCITE, 64));          // casa cheia
        }
        a.setItem(0, new ItemStack(Items.CALCITE, 10));
        List<ContainerSource.Entry> entries = List.of(entry(a, 1), entry(b, 2));
        OrganizeSystem.Plan plan = OrganizeSystem.plan(entries, stack -> false);
        assertTrue(plan.moves().isEmpty());
        assertEquals(10, count(a, Items.CALCITE));
        assertEquals(27 * 64, count(b, Items.CALCITE));
    }

    @Test
    void randomSystemsKeepTheSumThroughPlanApplyAndUndo() {
        Random rnd = new Random(2024);
        Item[] items = {Items.CALCITE, Items.DRIPSTONE_BLOCK, Items.SWEET_BERRIES, Items.GOLDEN_PICKAXE, Items.MUD_BRICKS,
                Items.ENDER_PEARL, Items.GOLDEN_CHESTPLATE};
        for (int round = 0; round < 200; round++) {
            List<SimpleContainer> boxes = new ArrayList<>();
            List<ContainerSource.Entry> entries = new ArrayList<>();
            int n = 2 + rnd.nextInt(5);
            for (int i = 0; i < n; i++) {
                SimpleContainer c = chest();
                for (int k = 0; k < rnd.nextInt(24); k++) {
                    Item item = items[rnd.nextInt(items.length)];
                    c.setItem(rnd.nextInt(27), new ItemStack(item, 1 + rnd.nextInt(item.getDefaultMaxStackSize())));
                }
                boxes.add(c);
                entries.add(entry(c, i));
            }
            OrganizeLogic.Totals before = total(boxes.toArray(new Container[0]));
            List<List<ItemStack>> snaps = boxes.stream().map(OrganizeLogic::snapshot).toList();
            OrganizeSystem.Plan plan = OrganizeSystem.plan(entries, stack -> stack.is(Items.ENDER_PEARL));
            for (int i = 0; i < n; i++) {
                assertTrue(OrganizeLogic.sameContents(boxes.get(i), snaps.get(i)), "o plano mexeu no baú " + i + " (rodada " + round + ")");
            }
            if (plan.isEmpty()) {
                continue;
            }
            assertTrue(OrganizeSystem.apply(plan, entries));
            assertEquals(before, total(boxes.toArray(new Container[0])), "soma depois de aplicar, rodada " + round);
            for (SimpleContainer c : boxes) {
                assertEquals(0, countPartialsOfSameItem(c), "stacks parciais do mesmo item no mesmo baú, rodada " + round);
            }
            assertTrue(OrganizeSystem.undo(plan, entries));
            assertEquals(before, total(boxes.toArray(new Container[0])));
            for (int i = 0; i < n; i++) {
                assertTrue(OrganizeLogic.sameContents(boxes.get(i), snaps.get(i)), "desfazer não restaurou o baú " + i);
            }
        }
    }

    private static int countPartialsOfSameItem(Container c) {
        int bad = 0;
        for (Item item : new Item[]{Items.CALCITE, Items.DRIPSTONE_BLOCK, Items.SWEET_BERRIES, Items.MUD_BRICKS, Items.ENDER_PEARL}) {
            int partial = 0;
            for (int slot = 0; slot < c.getContainerSize(); slot++) {
                ItemStack s = c.getItem(slot);
                if (s.is(item) && s.getCount() < s.getMaxStackSize()) {
                    partial++;
                }
            }
            if (partial > 1) {
                bad++;
            }
        }
        return bad;
    }

    private static OrganizeLogic.Totals total(Container... containers) {
        OrganizeLogic.Totals totals = new OrganizeLogic.Totals();
        for (Container c : containers) {
            totals.addAll(OrganizeLogic.snapshot(c));
        }
        return totals;
    }
}
