package io.github.leoascenci0.stashlink.storage;

import io.github.leoascenci0.stashlink.compat.mc.TestCompat;
import io.github.leoascenci0.stashlink.MinecraftTestSetup;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

class ShulkerStorageTest {
    private static final Predicate<ItemStack> COBBLE = s -> s.is(Items.COBBLESTONE);
    private static final Predicate<ItemStack> ANY = s -> true;

    @BeforeAll
    static void bootstrap() {
        lookup = MinecraftTestSetup.init();
    }

    private static HolderLookup.Provider lookup;

    private static ItemStack enchanted(net.minecraft.world.item.Item item, ResourceKey<Enchantment> key, int level) {
        ItemStack stack = new ItemStack(item);
        ItemEnchantments.Mutable enchants = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        enchants.set(lookup.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key), level);
        stack.set(DataComponents.ENCHANTMENTS, enchants.toImmutable());
        return stack;
    }

    private static ItemStack shulker() {
        return new ItemStack(Items.SHULKER_BOX);
    }

    private static int total(ItemStack shulker) {
        return ShulkerStorage.count(shulker, ANY);
    }

    private static int sum(List<ItemStack> stacks) {
        return stacks.stream().mapToInt(ItemStack::getCount).sum();
    }

    @Test
    void detectsAllShulkerColorsButNotOtherItems() {
        assertTrue(ShulkerStorage.isShulker(new ItemStack(Items.SHULKER_BOX)));
        assertTrue(ShulkerStorage.isShulker(new ItemStack(TestCompat.dyedShulker(DyeColor.RED))));
        assertTrue(ShulkerStorage.isShulker(new ItemStack(TestCompat.dyedShulker(DyeColor.BLACK))));
        assertFalse(ShulkerStorage.isShulker(new ItemStack(Items.CHEST)));
        assertFalse(ShulkerStorage.isShulker(ItemStack.EMPTY));
    }

    @Test
    void emptyShulker() {
        ItemStack box = shulker();
        assertEquals(ShulkerStorage.SLOTS, ShulkerStorage.read(box).size());
        assertEquals(0, total(box));
        assertTrue(ShulkerStorage.extract(box, ANY, 64).isEmpty());
        assertTrue(ItemStack.isSameItemSameComponents(box, shulker()));
    }

    @Test
    void writeThenReadKeepsSlotPositions() {
        ItemStack box = shulker();
        List<ItemStack> slots = new ArrayList<>(Collections.nCopies(27, ItemStack.EMPTY));
        slots.set(5, new ItemStack(Items.DIAMOND, 3));
        slots.set(26, new ItemStack(Items.STICK, 7));
        ShulkerStorage.write(box, slots);

        List<ItemStack> back = ShulkerStorage.read(box);
        assertEquals(27, back.size());
        assertTrue(back.get(5).is(Items.DIAMOND));
        assertEquals(3, back.get(5).getCount());
        assertTrue(back.get(26).is(Items.STICK));
        assertTrue(back.get(0).isEmpty());
    }

    @Test
    void readReturnsCopies() {
        ItemStack box = shulker();
        ShulkerStorage.insert(box, new ItemStack(Items.COBBLESTONE, 10));
        ShulkerStorage.read(box).get(0).setCount(1);
        assertEquals(10, total(box));
    }

    @Test
    void fullShulkerRejectsInsertAndKeepsStack() {
        ItemStack box = shulker();
        assertTrue(ShulkerStorage.insert(box, new ItemStack(Items.COBBLESTONE, 27 * 64)).isEmpty());
        assertEquals(27 * 64, total(box));

        ItemStack extra = new ItemStack(Items.COBBLESTONE, 5);
        ItemStack left = ShulkerStorage.insert(box, extra);
        assertEquals(5, left.getCount());
        assertEquals(5, extra.getCount(), "stack de entrada não pode ser alterado");
        assertEquals(27 * 64, total(box));
    }

    @Test
    void fullShulkerExtractsExactlyWhatWasAsked() {
        ItemStack box = shulker();
        ShulkerStorage.insert(box, new ItemStack(Items.COBBLESTONE, 27 * 64));

        List<ItemStack> out = ShulkerStorage.extract(box, COBBLE, 100);
        assertEquals(100, sum(out));
        assertTrue(out.stream().allMatch(s -> s.getCount() <= 64));
        assertEquals(27 * 64 - 100, total(box));
    }

    @Test
    void extractMoreThanAvailableTakesEverythingAndClearsComponent() {
        ItemStack box = shulker();
        ShulkerStorage.insert(box, new ItemStack(Items.COBBLESTONE, 70));

        assertEquals(70, sum(ShulkerStorage.extract(box, COBBLE, 1000)));
        assertEquals(0, total(box));
        assertTrue(ItemStack.isSameItemSameComponents(box, shulker()), "shulker vazia volta a ser igual a uma recém-craftada");
    }

    @Test
    void extractZeroOrNegativeDoesNothing() {
        ItemStack box = shulker();
        ShulkerStorage.insert(box, new ItemStack(Items.COBBLESTONE, 10));
        assertTrue(ShulkerStorage.extract(box, COBBLE, 0).isEmpty());
        assertTrue(ShulkerStorage.extract(box, COBBLE, -5).isEmpty());
        assertEquals(10, total(box));
    }

    @Test
    void partialStacksAreFilledBeforeUsingNewSlots() {
        ItemStack box = shulker();
        ShulkerStorage.insert(box, new ItemStack(Items.COBBLESTONE, 60));
        assertTrue(ShulkerStorage.insert(box, new ItemStack(Items.COBBLESTONE, 10)).isEmpty());

        List<ItemStack> slots = ShulkerStorage.read(box);
        assertEquals(64, slots.get(0).getCount());
        assertEquals(6, slots.get(1).getCount());
        assertEquals(70, total(box));
    }

    @Test
    void extractSpansPartialStacksAcrossSlots() {
        ItemStack box = shulker();
        ShulkerStorage.write(box, List.of(
                new ItemStack(Items.COBBLESTONE, 10), new ItemStack(Items.DIAMOND, 2),
                new ItemStack(Items.COBBLESTONE, 5)));

        assertEquals(12, sum(ShulkerStorage.extract(box, COBBLE, 12)));
        assertEquals(3, ShulkerStorage.count(box, COBBLE));
        assertEquals(2, ShulkerStorage.count(box, s -> s.is(Items.DIAMOND)), "outros itens intocados");
    }

    @Test
    void itemsWithDifferentComponentsNeverMerge() {
        ItemStack box = shulker();
        ItemStack named = new ItemStack(Items.COBBLESTONE, 10);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Especial"));

        ShulkerStorage.insert(box, new ItemStack(Items.COBBLESTONE, 10));
        ShulkerStorage.insert(box, named);

        List<ItemStack> slots = ShulkerStorage.read(box);
        assertEquals(10, slots.get(0).getCount());
        assertEquals(10, slots.get(1).getCount());
        assertFalse(slots.get(0).has(DataComponents.CUSTOM_NAME));
        assertEquals("Especial", slots.get(1).get(DataComponents.CUSTOM_NAME).getString());
    }

    @Test
    void componentsSurviveExtraction() {
        ItemStack box = shulker();
        ItemStack tool = new ItemStack(Items.DIAMOND_PICKAXE);
        tool.set(DataComponents.CUSTOM_NAME, Component.literal("Picareta do Eliel"));
        tool.set(DataComponents.DAMAGE, 42);
        ShulkerStorage.insert(box, tool);

        List<ItemStack> out = ShulkerStorage.extract(box, s -> s.is(Items.DIAMOND_PICKAXE), 1);
        assertEquals(1, out.size());
        assertTrue(ItemStack.isSameItemSameComponents(tool, out.get(0)));
        assertEquals(42, out.get(0).get(DataComponents.DAMAGE));
    }

    @Test
    void enchantedItemsKeepEnchantmentsAndDoNotMergeWithPlainOnes() {
        ItemStack box = shulker();
        ItemStack sword = enchanted(Items.DIAMOND_SWORD, Enchantments.SHARPNESS, 5);
        ShulkerStorage.insert(box, new ItemStack(Items.DIAMOND_SWORD));
        ShulkerStorage.insert(box, sword);

        // sobrevive a gravar -> ler
        List<ItemStack> slots = ShulkerStorage.read(box);
        assertFalse(ItemStack.isSameItemSameComponents(slots.get(0), slots.get(1)));

        // o filtro exato pega só a encantada, e ela sai idêntica
        List<ItemStack> out = ShulkerStorage.extract(box, s -> ItemStack.isSameItemSameComponents(s, sword), 1);
        assertEquals(1, out.size());
        assertTrue(ItemStack.isSameItemSameComponents(sword, out.get(0)));
        assertEquals(1, total(box), "a espada comum continua lá");
    }

    @Test
    void enchantedBooksOfSameEnchantmentStackTogetherButDifferentLevelsDoNot() {
        ItemStack box = shulker();
        ItemStack sharp5 = enchanted(Items.DIAMOND_SWORD, Enchantments.SHARPNESS, 5);
        ItemStack sharp4 = enchanted(Items.DIAMOND_SWORD, Enchantments.SHARPNESS, 4);
        ShulkerStorage.insert(box, sharp5);
        ShulkerStorage.insert(box, sharp4);
        assertEquals(2, total(box));
        assertEquals(1, ShulkerStorage.count(box, s -> ItemStack.isSameItemSameComponents(s, sharp5)));
    }

    @Test
    void nonStackableItemsTakeOneSlotEach() {
        ItemStack box = shulker();
        for (int i = 0; i < 27; i++) {
            assertTrue(ShulkerStorage.insert(box, new ItemStack(Items.DIAMOND_PICKAXE)).isEmpty());
        }
        assertEquals(27, total(box));
        assertEquals(1, ShulkerStorage.insert(box, new ItemStack(Items.DIAMOND_PICKAXE)).getCount());
    }

    @Test
    void shulkerInsideShulkerIsRejected() {
        ItemStack box = shulker();
        ItemStack left = ShulkerStorage.insert(box, new ItemStack(TestCompat.dyedShulker(DyeColor.RED)));
        assertEquals(1, left.getCount());
        assertEquals(0, total(box));
    }

    @Test
    void nonShulkerIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> ShulkerStorage.read(new ItemStack(Items.CHEST)));
    }

    @Test
    void oversizedWriteIsRejected() {
        List<ItemStack> tooMany = Collections.nCopies(28, new ItemStack(Items.DIAMOND));
        assertThrows(IllegalArgumentException.class, () -> ShulkerStorage.write(shulker(), tooMany));
    }

    /** Nenhum item nasce nem some: o que entrou = o que está dentro + o que voltou para fora. */
    @Test
    void conservationOnRandomOperations() {
        Random rnd = new Random(42);
        ItemStack box = shulker();
        long expectedInside = 0;
        for (int i = 0; i < 2000; i++) {
            int n = 1 + rnd.nextInt(200);
            if (rnd.nextBoolean()) {
                ItemStack left = ShulkerStorage.insert(box, new ItemStack(Items.COBBLESTONE, n));
                expectedInside += n - left.getCount();
            } else {
                expectedInside -= sum(ShulkerStorage.extract(box, COBBLE, n));
            }
            assertEquals(expectedInside, total(box));
        }
    }
}
