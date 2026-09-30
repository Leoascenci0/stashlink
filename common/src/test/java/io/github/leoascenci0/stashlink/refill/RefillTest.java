package io.github.leoascenci0.stashlink.refill;

import io.github.leoascenci0.stashlink.MinecraftTestSetup;
import io.github.leoascenci0.stashlink.source.PlayerShulkerSource;
import io.github.leoascenci0.stashlink.storage.ShulkerStorage;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RefillTest {
    @BeforeAll
    static void bootstrap() {
        MinecraftTestSetup.init();
    }

    private static ItemStack shulkerWith(ItemStack... contents) {
        ItemStack box = new ItemStack(Items.SHULKER_BOX);
        for (ItemStack s : contents) {
            assertTrue(ShulkerStorage.insert(box, s).isEmpty());
        }
        return box;
    }

    private static PlayerShulkerSource inventoryOf(ItemStack... stacks) {
        return new PlayerShulkerSource(new ArrayList<>(List.of(stacks)));
    }

    // ---- HandWatcher ----

    @Test
    void watcherFiresWhenHandGoesEmptyInSameSlot() {
        HandWatcher w = new HandWatcher();
        assertTrue(w.observe(3, new ItemStack(Items.COBBLESTONE, 1), true).isEmpty());
        ItemStack gone = w.observe(3, ItemStack.EMPTY, true);
        assertTrue(ItemStack.isSameItem(gone, new ItemStack(Items.COBBLESTONE)));
    }

    @Test
    void watcherDoesNotFireWhileStackJustShrinks() {
        HandWatcher w = new HandWatcher();
        w.observe(0, new ItemStack(Items.COBBLESTONE, 2), true);
        assertTrue(w.observe(0, new ItemStack(Items.COBBLESTONE, 1), true).isEmpty());
    }

    @Test
    void watcherIgnoresHotbarSlotChange() {
        HandWatcher w = new HandWatcher();
        w.observe(0, new ItemStack(Items.COBBLESTONE, 5), true);
        assertTrue(w.observe(1, ItemStack.EMPTY, true).isEmpty());
    }

    @Test
    void watcherDoesNotFireWhenInactiveAndForgetsThePast() {
        HandWatcher w = new HandWatcher();
        w.observe(0, new ItemStack(Items.COBBLESTONE, 1), true);
        assertTrue(w.observe(0, ItemStack.EMPTY, false).isEmpty());
        // Depois que voltou a ficar ativo, mão vazia continua vazia: nada a disparar.
        assertTrue(w.observe(0, ItemStack.EMPTY, true).isEmpty());
    }

    @Test
    void watcherFiresOnlyOnce() {
        HandWatcher w = new HandWatcher();
        w.observe(0, new ItemStack(Items.COBBLESTONE, 1), true);
        assertFalse(w.observe(0, ItemStack.EMPTY, true).isEmpty());
        assertTrue(w.observe(0, ItemStack.EMPTY, true).isEmpty());
    }

    // ---- RefillLogic ----

    @Test
    void refillTakesOneFullStackFromShulker() {
        ItemStack box = shulkerWith(new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.COBBLESTONE, 64),
                new ItemStack(Items.COBBLESTONE, 64));
        ItemStack result = RefillLogic.refill(new ItemStack(Items.COBBLESTONE, 1), inventoryOf(box));
        assertEquals(64, result.getCount());
        assertTrue(ItemStack.isSameItem(result, new ItemStack(Items.COBBLESTONE)));
        assertEquals(128, ShulkerStorage.count(box, s -> true));
    }

    @Test
    void refillTakesOnlyWhatExists() {
        ItemStack box = shulkerWith(new ItemStack(Items.COBBLESTONE, 10));
        ItemStack result = RefillLogic.refill(new ItemStack(Items.COBBLESTONE, 1), inventoryOf(box));
        assertEquals(10, result.getCount());
        assertEquals(0, ShulkerStorage.count(box, s -> true));
    }

    @Test
    void refillIsEmptyWithoutStock() {
        ItemStack box = shulkerWith(new ItemStack(Items.DIRT, 64));
        assertTrue(RefillLogic.refill(new ItemStack(Items.COBBLESTONE, 1), inventoryOf(box)).isEmpty());
        assertTrue(RefillLogic.refill(ItemStack.EMPTY, inventoryOf(box)).isEmpty());
    }

    @Test
    void refillRespectsMaxStackSize() {
        // Ender pearl empilha até 16: um stack só, não os 32 disponíveis.
        ItemStack box = shulkerWith(new ItemStack(Items.ENDER_PEARL, 16), new ItemStack(Items.ENDER_PEARL, 16));
        ItemStack result = RefillLogic.refill(new ItemStack(Items.ENDER_PEARL, 1), inventoryOf(box));
        assertEquals(16, result.getCount());
        assertEquals(16, ShulkerStorage.count(box, s -> true));
    }

    @Test
    void brokenToolIsReplacedByFreshOne() {
        ItemStack fresh = new ItemStack(Items.DIAMOND_PICKAXE);
        ItemStack box = shulkerWith(fresh);
        ItemStack worn = new ItemStack(Items.DIAMOND_PICKAXE);
        worn.set(DataComponents.DAMAGE, worn.getMaxDamage() - 1);
        ItemStack result = RefillLogic.refill(worn, inventoryOf(box));
        assertTrue(ItemStack.isSameItem(result, fresh));
        assertEquals(0, result.getDamageValue());
    }

    @Test
    void refillDoesNotTouchShulkerWithOtherItems() {
        ItemStack box = shulkerWith(new ItemStack(Items.DIRT, 64));
        RefillLogic.refill(new ItemStack(Items.COBBLESTONE, 1), inventoryOf(box));
        assertEquals(64, ShulkerStorage.count(box, s -> true));
    }

    // ---- swap com F ----

    @Test
    void swapToOtherHandIsDetected() {
        ItemStack stone = new ItemStack(Items.COBBLESTONE, 32);
        assertTrue(RefillLogic.movedToOtherHand(stone, ItemStack.EMPTY, new ItemStack(Items.COBBLESTONE, 32)));
        assertFalse(RefillLogic.movedToOtherHand(stone, ItemStack.EMPTY, ItemStack.EMPTY));
        // A outra mão já tinha o mesmo item e nada mudou: não foi troca.
        assertFalse(RefillLogic.movedToOtherHand(stone, new ItemStack(Items.COBBLESTONE, 5),
                new ItemStack(Items.COBBLESTONE, 5)));
    }
}
