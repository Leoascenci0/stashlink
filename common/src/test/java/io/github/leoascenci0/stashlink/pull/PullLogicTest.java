package io.github.leoascenci0.stashlink.pull;

import io.github.leoascenci0.stashlink.MinecraftTestSetup;
import io.github.leoascenci0.stashlink.source.PlayerShulkerSource;
import io.github.leoascenci0.stashlink.storage.ShulkerStorage;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PullLogicTest {
    @BeforeAll
    static void bootstrap() {
        MinecraftTestSetup.init();
    }

    private static List<ItemStack> emptyHotbar() {
        return new ArrayList<>(Collections.nCopies(9, ItemStack.EMPTY));
    }

    private static ItemStack shulkerWith(ItemStack content) {
        ItemStack box = new ItemStack(Items.SHULKER_BOX);
        assertTrue(ShulkerStorage.insert(box, content).isEmpty());
        return box;
    }

    private static int total(List<ItemStack> stacks, ItemStack model) {
        int sum = 0;
        for (ItemStack s : stacks) {
            if (ItemStack.isSameItemSameComponents(s, model)) {
                sum += s.getCount();
            }
        }
        return sum;
    }

    @Test
    void prefersSelectedSlotWhenEmpty() {
        assertEquals(4, PullLogic.chooseSlot(emptyHotbar(), 4, new ItemStack(Items.COBBLESTONE)));
    }

    @Test
    void usesFirstEmptySlotWhenSelectedIsBusy() {
        List<ItemStack> hotbar = emptyHotbar();
        hotbar.set(0, new ItemStack(Items.DIAMOND_PICKAXE));
        hotbar.set(1, new ItemStack(Items.TORCH, 3));
        assertEquals(2, PullLogic.chooseSlot(hotbar, 0, new ItemStack(Items.COBBLESTONE)));
    }

    @Test
    void topsUpMatchingStackBeforeUsingEmptySlot() {
        List<ItemStack> hotbar = emptyHotbar();
        hotbar.set(0, new ItemStack(Items.DIAMOND_PICKAXE));
        hotbar.set(5, new ItemStack(Items.COBBLESTONE, 10));
        assertEquals(5, PullLogic.chooseSlot(hotbar, 0, new ItemStack(Items.COBBLESTONE)));
    }

    @Test
    void fullHotbarOfOtherThingsHasNoSlot() {
        List<ItemStack> hotbar = new ArrayList<>(Collections.nCopies(9, new ItemStack(Items.DIAMOND_PICKAXE)));
        assertEquals(PullLogic.NO_SLOT, PullLogic.chooseSlot(hotbar, 0, new ItemStack(Items.COBBLESTONE)));
    }

    @Test
    void pullsAStackFromShulkerAndConservesItems() {
        ItemStack box = shulkerWith(new ItemStack(Items.COBBLESTONE, 64));
        assertTrue(ShulkerStorage.insert(box, new ItemStack(Items.COBBLESTONE, 30)).isEmpty());
        List<ItemStack> inventory = new ArrayList<>(List.of(box));
        List<ItemStack> hotbar = emptyHotbar();
        ItemStack cobble = new ItemStack(Items.COBBLESTONE);
        int before = 94;

        int slot = PullLogic.pullIntoHotbar(hotbar, 2, cobble, 64, new PlayerShulkerSource(inventory));

        assertEquals(2, slot);
        assertEquals(64, hotbar.get(2).getCount());
        int left = ShulkerStorage.read(box).stream().mapToInt(ItemStack::getCount).sum();
        assertEquals(before, left + hotbar.get(2).getCount());
    }

    @Test
    void neverTakesMoreThanFitsInTheSlot() {
        ItemStack box = shulkerWith(new ItemStack(Items.COBBLESTONE, 64));
        List<ItemStack> inventory = new ArrayList<>(List.of(box));
        List<ItemStack> hotbar = emptyHotbar();
        hotbar.set(0, new ItemStack(Items.COBBLESTONE, 60));

        int slot = PullLogic.pullIntoHotbar(hotbar, 0, new ItemStack(Items.COBBLESTONE), 64, new PlayerShulkerSource(inventory));

        assertEquals(0, slot);
        assertEquals(64, hotbar.get(0).getCount());
        assertEquals(60, ShulkerStorage.read(box).stream().mapToInt(ItemStack::getCount).sum());
    }

    @Test
    void asksForNothingThatIsNotThere() {
        ItemStack box = shulkerWith(new ItemStack(Items.DIRT, 10));
        List<ItemStack> hotbar = emptyHotbar();
        int slot = PullLogic.pullIntoHotbar(hotbar, 0, new ItemStack(Items.COBBLESTONE), 64,
                new PlayerShulkerSource(new ArrayList<>(List.of(box))));
        assertEquals(PullLogic.NO_SLOT, slot);
        assertEquals(0, total(hotbar, new ItemStack(Items.COBBLESTONE)));
        assertEquals(10, ShulkerStorage.read(box).stream().mapToInt(ItemStack::getCount).sum());
    }

    @Test
    void ignoresNonPositiveOrEmptyRequests() {
        List<ItemStack> hotbar = emptyHotbar();
        assertEquals(PullLogic.NO_SLOT, PullLogic.pullIntoHotbar(hotbar, 0, ItemStack.EMPTY, 8, new PlayerShulkerSource(new ArrayList<>())));
        assertEquals(PullLogic.NO_SLOT, PullLogic.pullIntoHotbar(hotbar, 0, new ItemStack(Items.DIRT), 0, new PlayerShulkerSource(new ArrayList<>())));
    }

    @Test
    void throttleBlocksRepeatedRequestsOfSameItemOnly() {
        PullRequestThrottle t = new PullRequestThrottle(6);
        assertTrue(t.tryAcquire(Items.COBBLESTONE, 100));
        assertFalse(t.tryAcquire(Items.COBBLESTONE, 103));
        assertTrue(t.tryAcquire(Items.DIRT, 103));
        assertTrue(t.tryAcquire(Items.DIRT, 109));
        assertFalse(t.tryAcquire(Items.DIRT, 110));
    }
}
