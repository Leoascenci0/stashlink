package io.github.leoascenci0.stashlink.lootall;

import io.github.leoascenci0.stashlink.MinecraftTestSetup;
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

import static org.junit.jupiter.api.Assertions.*;

/** Item 9: a tecla W move só o que cabe, nunca cria nem perde item, e respeita slots travados. */
class LootAllLogicTest {
    @BeforeAll
    static void bootstrap() {
        MinecraftTestSetup.init();
    }

    private static List<ItemStack> inventory() {
        List<ItemStack> slots = new ArrayList<>();
        for (int i = 0; i < 36; i++) {
            slots.add(ItemStack.EMPTY);
        }
        return slots;
    }

    private static SimpleContainer taker() {
        return new SimpleContainer(1);
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

    private static int count(Container c, Item item) {
        int total = 0;
        for (int i = 0; i < c.getContainerSize(); i++) {
            if (c.getItem(i).is(item)) {
                total += c.getItem(i).getCount();
            }
        }
        return total;
    }

    private static int total(Container c) {
        int total = 0;
        for (int i = 0; i < c.getContainerSize(); i++) {
            total += c.getItem(i).getCount();
        }
        return total;
    }

    private static int total(List<ItemStack> slots) {
        int total = 0;
        for (ItemStack s : slots) {
            total += s.getCount();
        }
        return total;
    }

    @Test
    void movesEverythingWhenThereIsRoom() {
        SimpleContainer chest = new SimpleContainer(27);
        chest.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        chest.setItem(5, new ItemStack(Items.DIAMOND, 3));
        List<ItemStack> inv = inventory();

        LootAllLogic.Result r = LootAllLogic.pull(chest, taker(), inv, i -> false);

        assertEquals(67, r.itemsMoved());
        assertEquals(0, r.itemsLeft());
        assertEquals(0, total(chest));
        assertEquals(64, count(inv, Items.COBBLESTONE));
        assertEquals(3, count(inv, Items.DIAMOND));
    }

    @Test
    void fullInventoryLeavesTheRestInTheChest() {
        SimpleContainer chest = new SimpleContainer(27);
        chest.setItem(0, new ItemStack(Items.DIAMOND, 10));
        chest.setItem(1, new ItemStack(Items.GOLD_INGOT, 10));
        List<ItemStack> inv = inventory();
        for (int i = 0; i < 36; i++) {
            inv.set(i, new ItemStack(Items.DIRT, 64));
        }

        LootAllLogic.Result r = LootAllLogic.pull(chest, taker(), inv, i -> false);

        assertEquals(0, r.itemsMoved());
        assertEquals(20, r.itemsLeft());
        assertEquals(10, count(chest, Items.DIAMOND));
        assertEquals(10, count(chest, Items.GOLD_INGOT));
        assertEquals(36 * 64, total(inv));
    }

    @Test
    void partialFitSplitsAStack() {
        SimpleContainer chest = new SimpleContainer(27);
        chest.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        List<ItemStack> inv = inventory();
        for (int i = 0; i < 36; i++) {
            inv.set(i, new ItemStack(Items.DIRT, 64));
        }
        inv.set(20, new ItemStack(Items.COBBLESTONE, 60)); // só 4 cabem

        LootAllLogic.Result r = LootAllLogic.pull(chest, taker(), inv, i -> false);

        assertEquals(4, r.itemsMoved());
        assertEquals(60, r.itemsLeft());
        assertEquals(64, inv.get(20).getCount());
        assertEquals(60, count(chest, Items.COBBLESTONE));
    }

    @Test
    void lockedSlotsNeverReceive() {
        SimpleContainer chest = new SimpleContainer(27);
        chest.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        chest.setItem(1, new ItemStack(Items.DIAMOND, 5));
        List<ItemStack> inv = inventory();
        inv.set(12, new ItemStack(Items.COBBLESTONE, 10)); // travado: não completa nem enche

        LootAllLogic.Result r = LootAllLogic.pull(chest, taker(), inv, i -> i == 12 || i == 13);

        assertEquals(69, r.itemsMoved());
        assertEquals(10, inv.get(12).getCount());
        assertTrue(inv.get(13).isEmpty());
    }

    @Test
    void fillsExistingStacksFirstThenBackpackBeforeHotbar() {
        SimpleContainer chest = new SimpleContainer(27);
        chest.setItem(0, new ItemStack(Items.COBBLESTONE, 30));
        chest.setItem(1, new ItemStack(Items.DIAMOND, 2));
        List<ItemStack> inv = inventory();
        inv.set(3, new ItemStack(Items.COBBLESTONE, 50)); // hotbar, completa primeiro

        LootAllLogic.pull(chest, taker(), inv, i -> false);

        assertEquals(64, inv.get(3).getCount());
        assertEquals(16, inv.get(9).getCount(), "o resto da pedra vai para o primeiro slot vazio da mochila");
        assertEquals(2, inv.get(10).getCount());
        assertTrue(inv.get(0).isEmpty(), "hotbar só é usada por último");
    }

    @Test
    void hotbarIsUsedWhenBackpackIsFull() {
        SimpleContainer chest = new SimpleContainer(27);
        chest.setItem(0, new ItemStack(Items.DIAMOND, 3));
        List<ItemStack> inv = inventory();
        for (int i = 9; i < 36; i++) {
            inv.set(i, new ItemStack(Items.DIRT, 64));
        }

        LootAllLogic.pull(chest, taker(), inv, i -> false);

        assertEquals(3, inv.get(0).getCount());
    }

    @Test
    void enchantedItemDoesNotMergeWithPlainOne() {
        ItemStack enchanted = new ItemStack(Items.DIAMOND_PICKAXE);
        enchanted.set(DataComponents.CUSTOM_NAME, Component.literal("Especial"));
        SimpleContainer chest = new SimpleContainer(27);
        chest.setItem(0, enchanted.copy());
        chest.setItem(1, new ItemStack(Items.DIAMOND_PICKAXE));
        List<ItemStack> inv = inventory();

        LootAllLogic.pull(chest, taker(), inv, i -> false);

        int named = 0;
        int plain = 0;
        for (ItemStack s : inv) {
            if (s.is(Items.DIAMOND_PICKAXE)) {
                if (s.has(DataComponents.CUSTOM_NAME)) {
                    named++;
                } else {
                    plain++;
                }
            }
        }
        assertEquals(1, named);
        assertEquals(1, plain);
    }

    @Test
    void emptyContainerMovesNothing() {
        LootAllLogic.Result r = LootAllLogic.pull(new SimpleContainer(27), taker(), inventory(), i -> false);
        assertEquals(0, r.itemsMoved());
        assertEquals(0, r.itemsLeft());
    }

    @Test
    void doubleChestAndBarrelWork() {
        ChestBlockEntity left = new ChestBlockEntity(BlockPos.ZERO, Blocks.CHEST.defaultBlockState());
        ChestBlockEntity right = new ChestBlockEntity(BlockPos.ZERO.east(), Blocks.CHEST.defaultBlockState());
        CompoundContainer both = new CompoundContainer(left, right);
        both.setItem(3, new ItemStack(Items.IRON_INGOT, 40));
        both.setItem(30, new ItemStack(Items.IRON_INGOT, 40));
        BarrelBlockEntity barrel = new BarrelBlockEntity(BlockPos.ZERO.north(), Blocks.BARREL.defaultBlockState());
        barrel.setItem(0, new ItemStack(Items.COAL, 12));

        List<ItemStack> inv = inventory();
        LootAllLogic.Result a = LootAllLogic.pull(both, taker(), inv, i -> false);
        LootAllLogic.Result b = LootAllLogic.pull(barrel, taker(), inv, i -> false);

        assertEquals(80, a.itemsMoved());
        assertEquals(12, b.itemsMoved());
        assertEquals(0, total(both));
        assertEquals(0, total(barrel));
        assertEquals(80, count(inv, Items.IRON_INGOT));
    }

    /** Conservação: em qualquer combinação, (container + inventário) antes == depois, item por item. */
    @Test
    void randomizedConservation() {
        Random rnd = new Random(42);
        Item[] items = {Items.COBBLESTONE, Items.DIAMOND, Items.ENDER_PEARL, Items.OAK_LOG, Items.DIAMOND_SWORD};
        for (int round = 0; round < 300; round++) {
            SimpleContainer chest = new SimpleContainer(27);
            List<ItemStack> inv = inventory();
            for (int i = 0; i < 27; i++) {
                if (rnd.nextInt(3) > 0) {
                    Item it = items[rnd.nextInt(items.length)];
                    chest.setItem(i, new ItemStack(it, 1 + rnd.nextInt(it.getDefaultMaxStackSize())));
                }
            }
            for (int i = 0; i < 36; i++) {
                if (rnd.nextInt(3) > 0) {
                    Item it = items[rnd.nextInt(items.length)];
                    inv.set(i, new ItemStack(it, 1 + rnd.nextInt(it.getDefaultMaxStackSize())));
                }
            }
            int[] before = new int[items.length];
            for (int k = 0; k < items.length; k++) {
                before[k] = count(chest, items[k]) + count(inv, items[k]);
            }
            int lockedSlot = rnd.nextInt(36);

            LootAllLogic.Result r = LootAllLogic.pull(chest, taker(), inv, i -> i == lockedSlot);

            for (int k = 0; k < items.length; k++) {
                assertEquals(before[k], count(chest, items[k]) + count(inv, items[k]), "round " + round);
            }
            assertEquals(total(chest), r.itemsLeft());
            for (ItemStack s : inv) {
                assertTrue(s.getCount() <= s.getMaxStackSize());
            }
        }
    }
}
