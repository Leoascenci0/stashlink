package io.github.leoascenci0.stashlink.source;

import io.github.leoascenci0.stashlink.MinecraftTestSetup;
import io.github.leoascenci0.stashlink.storage.ShulkerStorage;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ItemSourceTest {
    private static HolderLookup.Provider lookup;

    @BeforeAll
    static void bootstrap() {
        lookup = MinecraftTestSetup.init();
    }

    private static ItemStack cobble() {
        return new ItemStack(Items.COBBLESTONE);
    }

    /** Shulker com {@code total} cobblestones (em stacks de 64). */
    private static ItemStack shulkerWithCobble(int total) {
        ItemStack box = new ItemStack(Items.SHULKER_BOX);
        assertTrue(ShulkerStorage.insert(box, new ItemStack(Items.COBBLESTONE, total)).isEmpty());
        return box;
    }

    private static PlayerShulkerSource inventoryOf(ItemStack... stacks) {
        return new PlayerShulkerSource(new ArrayList<>(List.of(stacks)));
    }

    private static int inside(ItemStack shulker) {
        return ShulkerStorage.count(shulker, s -> true);
    }

    /** Fonte de mentira que só registra a ordem em que foi chamada. */
    private record Fake(String name, int stock, List<String> log) implements ItemSource {
        @Override
        public int available(ItemStack item) {
            return stock;
        }

        @Override
        public List<ItemStack> take(ItemStack item, int n) {
            log.add(name);
            int out = Math.min(n, stock);
            return out == 0 ? List.of() : List.of(item.copyWithCount(out));
        }
    }

    @Test
    void inventorySourceCountsOnlyShulkersContent() {
        ItemStack loose = new ItemStack(Items.COBBLESTONE, 30);
        PlayerShulkerSource source = inventoryOf(loose, shulkerWithCobble(100), new ItemStack(Items.CHEST),
                shulkerWithCobble(50));
        assertEquals(150, source.available(cobble()));
        assertEquals(0, source.available(new ItemStack(Items.DIRT)));
    }

    @Test
    void inventorySourceTakesAcrossShulkersInSlotOrder() {
        ItemStack first = shulkerWithCobble(40);
        ItemStack second = shulkerWithCobble(100);
        PlayerShulkerSource source = inventoryOf(first, second);
        List<ItemStack> taken = source.take(cobble(), 70);
        assertEquals(70, ItemSource.sum(taken));
        assertTrue(taken.stream().allMatch(s -> s.getCount() <= 64));
        assertEquals(0, inside(first));
        assertEquals(70, inside(second));
        assertEquals(70, source.available(cobble()));
    }

    @Test
    void takeMoreThanAvailableReturnsWhatExists() {
        PlayerShulkerSource source = inventoryOf(shulkerWithCobble(20));
        assertEquals(20, ItemSource.sum(source.take(cobble(), 999)));
        assertEquals(0, source.available(cobble()));
        assertTrue(source.take(cobble(), 5).isEmpty());
        assertTrue(source.take(cobble(), 0).isEmpty());
        assertTrue(source.take(cobble(), -3).isEmpty());
    }

    @Test
    void differentComponentsAreDifferentItems() {
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        ItemStack enchanted = new ItemStack(Items.DIAMOND_SWORD);
        ItemEnchantments.Mutable enchants = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        enchants.set(lookup.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS), 3);
        enchanted.set(DataComponents.ENCHANTMENTS, enchants.toImmutable());

        ItemStack box = new ItemStack(Items.SHULKER_BOX);
        ShulkerStorage.insert(box, sword);
        ShulkerStorage.insert(box, enchanted);
        PlayerShulkerSource source = inventoryOf(box);
        assertEquals(1, source.available(sword));
        assertEquals(1, source.available(enchanted));
        assertTrue(ItemStack.isSameItemSameComponents(enchanted, source.take(enchanted, 5).get(0)));
        assertEquals(1, source.available(sword));
    }

    @Test
    void aggregatorSumsAllSources() {
        List<String> log = new ArrayList<>();
        PrioritizedItemSource all = new PrioritizedItemSource(List.of(
                new Fake("a", 10, log), new Fake("b", 25, log), new Fake("c", 0, log)));
        assertEquals(35, all.available(cobble()));
    }

    @Test
    void aggregatorTakesFromFirstSourceUntilDoneThenNext() {
        List<String> log = new ArrayList<>();
        PrioritizedItemSource all = new PrioritizedItemSource(List.of(
                new Fake("a", 10, log), new Fake("b", 25, log), new Fake("c", 40, log)));

        assertEquals(8, ItemSource.sum(all.take(cobble(), 8)));
        assertEquals(List.of("a"), log); // a bastou: b e c nem foram consultadas

        log.clear();
        assertEquals(30, ItemSource.sum(all.take(cobble(), 30)));
        assertEquals(List.of("a", "b"), log); // 10 de a + 20 de b

        log.clear();
        assertEquals(75, ItemSource.sum(all.take(cobble(), 500)));
        assertEquals(List.of("a", "b", "c"), log);
    }

    @Test
    void changingPriorityOrderChangesWhichSourceIsDrainedFirst() {
        ItemStack near = shulkerWithCobble(64);
        ItemStack far = shulkerWithCobble(64);
        ItemSource nearSrc = inventoryOf(near);
        ItemSource farSrc = inventoryOf(far);

        new PrioritizedItemSource(List.of(farSrc, nearSrc)).take(cobble(), 10);
        assertEquals(64, inside(near));
        assertEquals(54, inside(far));

        new PrioritizedItemSource(List.of(nearSrc, farSrc)).take(cobble(), 10);
        assertEquals(54, inside(near));
        assertEquals(54, inside(far));
    }

    @Test
    void aggregatorOverRealInventorySourcesKeepsTotalConsistent() {
        ItemStack a = shulkerWithCobble(100);
        ItemStack b = shulkerWithCobble(100);
        PrioritizedItemSource all = new PrioritizedItemSource(List.of(inventoryOf(a), inventoryOf(b)));
        assertEquals(200, all.available(cobble()));
        assertEquals(150, ItemSource.sum(all.take(cobble(), 150)));
        assertEquals(50, all.available(cobble()));
        assertEquals(0, inside(a));
        assertEquals(50, inside(b));
    }

    @Test
    void failingSourceDoesNotLoseItemsAlreadyTaken() {
        ItemSource broken = new ItemSource() {
            @Override
            public int available(ItemStack item) {
                throw new IllegalStateException("boom");
            }

            @Override
            public List<ItemStack> take(ItemStack item, int n) {
                throw new IllegalStateException("boom");
            }
        };
        PrioritizedItemSource all = new PrioritizedItemSource(List.of(inventoryOf(shulkerWithCobble(30)), broken,
                inventoryOf(shulkerWithCobble(50))));
        assertEquals(80, all.available(cobble()));
        assertEquals(60, ItemSource.sum(all.take(cobble(), 60)));
        assertEquals(20, all.available(cobble()));
    }
}
