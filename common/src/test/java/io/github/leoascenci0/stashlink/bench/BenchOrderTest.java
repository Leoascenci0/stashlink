package io.github.leoascenci0.stashlink.bench;

import io.github.leoascenci0.stashlink.MinecraftTestSetup;
import io.github.leoascenci0.stashlink.network.BenchPoolSync;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Item 16.3: a ordem única das listas do painel e o corte do teto depois de ordenar. */
class BenchOrderTest {
    @BeforeAll
    static void bootstrap() {
        MinecraftTestSetup.init();
    }

    private static BenchPoolSync.Entry item(Item item, int count, boolean missing) {
        return new BenchPoolSync.Entry(new ItemStack(item), count, -1, missing, 0, -1);
    }

    @Test
    void availableComesBeforeMissingThenByName() {
        List<BenchPoolSync.Entry> list = new ArrayList<>(List.of(
                item(Items.ACACIA_LOG, 0, true), item(Items.STONE, 5, false), item(Items.DIRT, 5, false)));
        list.sort(BenchOrder.comparator(e -> e.item().getItem() == Items.STONE ? "b" : e.item().getItem() == Items.DIRT ? "a" : "0"));
        assertEquals(Items.DIRT, list.get(0).item().getItem());
        assertEquals(Items.STONE, list.get(1).item().getItem());
        assertTrue(list.get(2).missing(), "o vermelho vem por último, mesmo com nome que viria antes");
    }

    @Test
    void sameNameFallsBackToItemIdThenEntryId() {
        BenchPoolSync.Entry a = new BenchPoolSync.Entry(new ItemStack(Items.DIRT), 0, 9, false, 0, -1);
        BenchPoolSync.Entry b = new BenchPoolSync.Entry(new ItemStack(Items.DIRT), 0, 3, false, 0, -1);
        BenchPoolSync.Entry c = new BenchPoolSync.Entry(new ItemStack(Items.COBBLESTONE), 0, 1, false, 0, -1);
        List<BenchPoolSync.Entry> list = new ArrayList<>(List.of(a, b, c));
        list.sort(BenchOrder.comparator(e -> "igual"));
        assertEquals(List.of(c, b, a), list, "mesmo nome: id do item (cobblestone antes de dirt) e depois id da entrada");
    }

    @Test
    void capKeepsTheSameEntriesWhateverTheInputOrder() {
        List<BenchPoolSync.Entry> all = new ArrayList<>();
        List<Item> items = BuiltInRegistries.ITEM.stream().limit(BenchPoolSync.MAX_ENTRIES + 80).toList();
        for (int i = 0; i < items.size(); i++) {
            all.add(item(items.get(i), 1 + i % 7, i % 5 == 0));
        }
        List<BenchPoolSync.Entry> shuffled = new ArrayList<>(all);
        Collections.shuffle(shuffled, new Random(42));
        List<BenchPoolSync.Entry> one = BenchOrder.sortedAndCapped(all);
        List<BenchPoolSync.Entry> two = BenchOrder.sortedAndCapped(shuffled);
        assertEquals(BenchPoolSync.MAX_ENTRIES, one.size());
        assertEquals(one, two, "o que entra no pacote não depende da ordem da varredura");
        boolean seenMissing = false;
        for (BenchPoolSync.Entry entry : one) {
            seenMissing |= entry.missing();
            assertTrue(!seenMissing || entry.missing(), "possível depois de um vermelho");
        }
    }
}
