package io.github.leoascenci0.stashlink.quickstack;

import io.github.leoascenci0.stashlink.MinecraftTestSetup;
import io.github.leoascenci0.stashlink.source.ContainerInsert;
import io.github.leoascenci0.stashlink.source.ContainerSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Item 8: a tecla N só move o que o container já tem, nunca cria nem perde item, e respeita hotbar/travas. */
class QuickStackLogicTest {
    @BeforeAll
    static void bootstrap() {
        MinecraftTestSetup.init();
    }

    /** Inventário de 36 slots: 0-8 hotbar, 9-35 mochila (como o do jogador). */
    private static List<ItemStack> inventory() {
        List<ItemStack> slots = new ArrayList<>();
        for (int i = 0; i < 36; i++) {
            slots.add(ItemStack.EMPTY);
        }
        return slots;
    }

    private static boolean hotbar(int slot) {
        return slot < 9;
    }

    private static ContainerSource.Entry allow(Container c) {
        return new ContainerSource.Entry(c, () -> true);
    }

    private static int count(List<ItemStack> slots, Item item) {
        int total = 0;
        for (ItemStack s : slots) {
            if (s.is(item)) {
                total += s.getCount();
            }
        }
        return total;
    }

    private static SimpleContainer single(ItemStack... stacks) {
        SimpleContainer c = new SimpleContainer(27);
        for (int i = 0; i < stacks.length; i++) {
            c.setItem(i, stacks[i]);
        }
        return c;
    }

    @Test
    void movesOnlyItemsTheContainerAlreadyHas() {
        List<ItemStack> inv = inventory();
        inv.set(10, new ItemStack(Items.DIRT, 30));
        inv.set(11, new ItemStack(Items.STONE, 20));
        SimpleContainer chest = single(new ItemStack(Items.DIRT, 5));

        QuickStackLogic.Result r = QuickStackLogic.stack(inv, QuickStackLogicTest::hotbar, List.of(allow(chest)));

        assertEquals(30, r.itemsMoved());
        assertEquals(1, r.containersUsed());
        assertEquals(35, chest.countItem(Items.DIRT));
        assertEquals(0, count(inv, Items.DIRT));
        assertEquals(20, count(inv, Items.STONE), "pedra não estava no baú: fica com o jogador");
        assertEquals(0, chest.countItem(Items.STONE));
    }

    @Test
    void containerWithoutMatchingItemIsIgnoredAndPermissionNotAsked() {
        List<ItemStack> inv = inventory();
        inv.set(10, new ItemStack(Items.DIRT, 30));
        SimpleContainer unrelated = single(new ItemStack(Items.STONE, 5));
        AtomicInteger asked = new AtomicInteger();

        QuickStackLogic.Result r = QuickStackLogic.stack(inv, QuickStackLogicTest::hotbar,
                List.of(new ContainerSource.Entry(unrelated, () -> {
                    asked.incrementAndGet();
                    return true;
                })));

        assertEquals(0, r.itemsMoved());
        assertEquals(0, asked.get(), "não pergunta a claim sobre baú irrelevante");
        assertEquals(30, count(inv, Items.DIRT));
    }

    @Test
    void deniedContainerIsUntouched() {
        List<ItemStack> inv = inventory();
        inv.set(10, new ItemStack(Items.DIRT, 30));
        SimpleContainer chest = single(new ItemStack(Items.DIRT, 5));

        QuickStackLogic.Result r = QuickStackLogic.stack(inv, QuickStackLogicTest::hotbar,
                List.of(new ContainerSource.Entry(chest, () -> false)));

        assertEquals(0, r.itemsMoved());
        assertEquals(5, chest.countItem(Items.DIRT));
        assertEquals(30, count(inv, Items.DIRT));
    }

    @Test
    void differentComponentsDoNotMatch() {
        List<ItemStack> inv = inventory();
        ItemStack named = new ItemStack(Items.DIAMOND_PICKAXE);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Minha picareta"));
        inv.set(10, named);
        SimpleContainer chest = single(new ItemStack(Items.DIAMOND_PICKAXE));

        QuickStackLogic.Result r = QuickStackLogic.stack(inv, QuickStackLogicTest::hotbar, List.of(allow(chest)));

        assertEquals(0, r.itemsMoved(), "mesmo item, componentes diferentes: não casa");
        assertFalse(inv.get(10).isEmpty());
        assertEquals(1, chest.countItem(Items.DIAMOND_PICKAXE));
    }

    @Test
    void sameComponentsDoMatch() {
        List<ItemStack> inv = inventory();
        ItemStack named = new ItemStack(Items.DIAMOND_PICKAXE);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Minha picareta"));
        inv.set(10, named.copy());
        SimpleContainer chest = single(named.copy());

        assertEquals(1, QuickStackLogic.stack(inv, QuickStackLogicTest::hotbar, List.of(allow(chest))).itemsMoved());
        assertTrue(inv.get(10).isEmpty());
    }

    @Test
    void hotbarAndLockedSlotsAreNeverEmptied() {
        List<ItemStack> inv = inventory();
        inv.set(0, new ItemStack(Items.DIRT, 10)); // hotbar
        inv.set(12, new ItemStack(Items.DIRT, 10)); // travado
        inv.set(13, new ItemStack(Items.DIRT, 10)); // livre
        SimpleContainer chest = single(new ItemStack(Items.DIRT, 1));

        QuickStackLogic.Result r = QuickStackLogic.stack(inv, slot -> hotbar(slot) || slot == 12, List.of(allow(chest)));

        assertEquals(10, r.itemsMoved());
        assertEquals(10, inv.get(0).getCount());
        assertEquals(10, inv.get(12).getCount());
        assertTrue(inv.get(13).isEmpty());
        assertEquals(11, chest.countItem(Items.DIRT));
    }

    @Test
    void overflowGoesToNextContainerAndFullContainerKeepsLeftoverWithPlayer() {
        List<ItemStack> inv = inventory();
        inv.set(10, new ItemStack(Items.DIRT, 64));
        inv.set(11, new ItemStack(Items.DIRT, 64));
        // Primeiro baú: cheio de pedra, só um slot de terra com 60 -> cabem só 4.
        SimpleContainer first = new SimpleContainer(27);
        for (int i = 0; i < 27; i++) {
            first.setItem(i, new ItemStack(Items.STONE, 64));
        }
        first.setItem(0, new ItemStack(Items.DIRT, 60));
        SimpleContainer second = single(new ItemStack(Items.DIRT, 1));

        QuickStackLogic.Result r = QuickStackLogic.stack(inv, QuickStackLogicTest::hotbar,
                List.of(allow(first), allow(second)));

        assertEquals(128, r.itemsMoved());
        assertEquals(2, r.containersUsed());
        assertEquals(64, first.countItem(Items.DIRT));
        assertEquals(1 + 124, second.countItem(Items.DIRT));
        assertEquals(0, count(inv, Items.DIRT));
    }

    @Test
    void totallyFullContainerLeavesItemsWithPlayer() {
        List<ItemStack> inv = inventory();
        inv.set(10, new ItemStack(Items.DIRT, 30));
        SimpleContainer full = new SimpleContainer(27);
        for (int i = 0; i < 27; i++) {
            full.setItem(i, new ItemStack(Items.DIRT, 64));
        }

        QuickStackLogic.Result r = QuickStackLogic.stack(inv, QuickStackLogicTest::hotbar, List.of(allow(full)));

        assertEquals(0, r.itemsMoved());
        assertEquals(0, r.containersUsed());
        assertEquals(30, count(inv, Items.DIRT));
    }

    @Test
    void doubleChestActsAsOneContainerOfFiftyFourSlots() {
        List<ItemStack> inv = inventory();
        for (int i = 9; i < 36; i++) {
            inv.set(i, new ItemStack(Items.DIRT, 64));
        }
        // O item está só na metade "de cima"; as duas metades juntas são um baú de 54 slots.
        ChestBlockEntity left = new ChestBlockEntity(BlockPos.ZERO, Blocks.CHEST.defaultBlockState());
        ChestBlockEntity right = new ChestBlockEntity(new BlockPos(1, 0, 0), Blocks.CHEST.defaultBlockState());
        left.setItem(0, new ItemStack(Items.DIRT, 1));
        CompoundContainer both = new CompoundContainer(left, right);
        assertEquals(54, both.getContainerSize());

        QuickStackLogic.Result r = QuickStackLogic.stack(inv, QuickStackLogicTest::hotbar, List.of(allow(both)));

        assertEquals(27 * 64, r.itemsMoved());
        assertEquals(1, r.containersUsed(), "baú duplo conta como um baú só");
        assertEquals(27 * 64 + 1, left.countItem(Items.DIRT) + right.countItem(Items.DIRT));
        assertEquals(0, count(inv, Items.DIRT));
    }

    @Test
    void doubleChestMatchesItemFoundInEitherHalf() {
        ChestBlockEntity left = new ChestBlockEntity(BlockPos.ZERO, Blocks.CHEST.defaultBlockState());
        ChestBlockEntity right = new ChestBlockEntity(new BlockPos(1, 0, 0), Blocks.CHEST.defaultBlockState());
        right.setItem(5, new ItemStack(Items.DIRT, 1));
        List<ItemStack> inv = inventory();
        inv.set(10, new ItemStack(Items.DIRT, 10));

        QuickStackLogic.stack(inv, QuickStackLogicTest::hotbar, List.of(allow(new CompoundContainer(left, right))));

        assertEquals(11, left.countItem(Items.DIRT) + right.countItem(Items.DIRT));
        assertTrue(inv.get(10).isEmpty());
    }

    @Test
    void barrelWorksLikeAChest() {
        BarrelBlockEntity barrel = new BarrelBlockEntity(BlockPos.ZERO, Blocks.BARREL.defaultBlockState());
        barrel.setItem(3, new ItemStack(Items.COBBLESTONE, 10));
        List<ItemStack> inv = inventory();
        inv.set(10, new ItemStack(Items.COBBLESTONE, 64));
        inv.set(11, new ItemStack(Items.OAK_LOG, 64));

        QuickStackLogic.Result r = QuickStackLogic.stack(inv, QuickStackLogicTest::hotbar, List.of(allow(barrel)));

        assertEquals(64, r.itemsMoved());
        assertEquals(74, barrel.countItem(Items.COBBLESTONE));
        assertEquals(0, barrel.countItem(Items.OAK_LOG));
        assertEquals(64, count(inv, Items.OAK_LOG));
    }

    @Test
    void neverCreatesOrLosesItemsRandomized() {
        Random random = new Random(8);
        Item[] items = {Items.DIRT, Items.STONE, Items.STICK, Items.APPLE, Items.OAK_LOG};
        for (int round = 0; round < 300; round++) {
            List<ItemStack> inv = inventory();
            for (int i = 0; i < 36; i++) {
                if (random.nextInt(3) > 0) {
                    inv.set(i, new ItemStack(items[random.nextInt(items.length)], 1 + random.nextInt(64)));
                }
            }
            List<ContainerSource.Entry> targets = new ArrayList<>();
            List<Container> boxes = new ArrayList<>();
            for (int b = 0; b < 1 + random.nextInt(4); b++) {
                SimpleContainer box = new SimpleContainer(9 + random.nextInt(19));
                for (int s = 0; s < box.getContainerSize(); s++) {
                    if (random.nextInt(2) == 0) {
                        box.setItem(s, new ItemStack(items[random.nextInt(items.length)], 1 + random.nextInt(64)));
                    }
                }
                boxes.add(box);
                targets.add(allow(box));
            }
            int[] before = new int[items.length];
            for (int k = 0; k < items.length; k++) {
                before[k] = count(inv, items[k]);
                for (Container box : boxes) {
                    before[k] += ContainerInsert.count(box, new ItemStack(items[k]));
                }
            }
            int invBefore = 0;
            for (Item item : items) {
                invBefore += count(inv, item);
            }

            QuickStackLogic.Result r = QuickStackLogic.stack(inv, QuickStackLogicTest::hotbar, targets);

            int invAfter = 0;
            for (int k = 0; k < items.length; k++) {
                int after = count(inv, items[k]);
                invAfter += after;
                for (Container box : boxes) {
                    after += ContainerInsert.count(box, new ItemStack(items[k]));
                }
                assertEquals(before[k], after, "conservação de " + items[k]);
            }
            assertEquals(invBefore - invAfter, r.itemsMoved(), "o resumo bate com o que saiu do jogador");
            for (int i = 0; i < 9; i++) {
                assertTrue(inv.get(i).isEmpty() || inv.get(i).getCount() > 0);
            }
        }
    }

    @Test
    void isOrContainsSeesHalvesOfDoubleChest() {
        SimpleContainer a = new SimpleContainer(27);
        SimpleContainer b = new SimpleContainer(27);
        SimpleContainer other = new SimpleContainer(27);
        CompoundContainer both = new CompoundContainer(a, b);
        assertTrue(ContainerInsert.isOrContains(both, a));
        assertTrue(ContainerInsert.isOrContains(a, both));
        assertTrue(ContainerInsert.isOrContains(a, a));
        assertFalse(ContainerInsert.isOrContains(both, other));
    }
}
