package io.github.leoascenci0.stashlink.source;

import io.github.leoascenci0.stashlink.MinecraftTestSetup;
import io.github.leoascenci0.stashlink.config.StashLinkConfig;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Item 6: a fonte de containers colocados nunca cria nem perde item, e respeita a permissão. */
class ContainerSourceTest {
    @BeforeAll
    static void bootstrap() {
        MinecraftTestSetup.init();
    }

    private static ItemStack dirt() {
        return new ItemStack(Items.DIRT);
    }

    private static SimpleContainer chest(ItemStack... stacks) {
        SimpleContainer c = new SimpleContainer(27);
        for (int i = 0; i < stacks.length; i++) {
            c.setItem(i, stacks[i]);
        }
        return c;
    }

    private static int total(Container c) {
        return c.countItem(Items.DIRT);
    }

    private static ContainerSource.Entry allow(Container c) {
        return new ContainerSource.Entry(c, () -> true);
    }

    @Test
    void takesFromContainersInOrderAndReportsAvailable() {
        SimpleContainer near = chest(new ItemStack(Items.DIRT, 10));
        SimpleContainer far = chest(new ItemStack(Items.DIRT, 64), new ItemStack(Items.DIRT, 20));
        ContainerSource source = new ContainerSource(List.of(allow(near), allow(far)));
        assertEquals(94, source.available(dirt()));

        List<ItemStack> taken = source.take(dirt(), 30);
        assertEquals(30, ItemSource.sum(taken));
        assertEquals(0, total(near), "o mais próximo esgota primeiro");
        assertEquals(64, total(far));
    }

    @Test
    void neverTakesMoreThanExistsOrOtherItems() {
        SimpleContainer c = chest(new ItemStack(Items.DIRT, 5), new ItemStack(Items.STONE, 64));
        ContainerSource source = new ContainerSource(List.of(allow(c)));
        assertEquals(5, ItemSource.sum(source.take(dirt(), 999)));
        assertEquals(64, c.countItem(Items.STONE));
    }

    @Test
    void takenStacksRespectMaxStackSize() {
        SimpleContainer c = chest(new ItemStack(Items.DIRT, 64), new ItemStack(Items.DIRT, 64));
        for (ItemStack s : new ContainerSource(List.of(allow(c))).take(dirt(), 100)) {
            assertTrue(s.getCount() <= s.getMaxStackSize());
        }
    }

    @Test
    void deniedContainerIsNeverTouched() {
        SimpleContainer denied = chest(new ItemStack(Items.DIRT, 64));
        SimpleContainer ok = chest(new ItemStack(Items.DIRT, 10));
        ContainerSource source = new ContainerSource(List.of(
                new ContainerSource.Entry(denied, () -> false), allow(ok)));
        assertEquals(10, source.available(dirt()));
        assertEquals(10, ItemSource.sum(source.take(dirt(), 64)));
        assertEquals(64, total(denied));
    }

    @Test
    void permissionIsOnlyAskedForContainersThatHoldTheItem() {
        AtomicInteger asked = new AtomicInteger();
        SimpleContainer irrelevant = chest(new ItemStack(Items.STONE, 64));
        SimpleContainer relevant = chest(new ItemStack(Items.DIRT, 5));
        ContainerSource source = new ContainerSource(List.of(
                new ContainerSource.Entry(irrelevant, () -> {
                    asked.incrementAndGet();
                    return true;
                }),
                new ContainerSource.Entry(relevant, () -> {
                    asked.incrementAndGet();
                    return true;
                })));
        source.available(dirt());
        source.take(dirt(), 5);
        assertEquals(1, asked.get(), "só o container com o item é consultado, e uma vez só");
    }

    @Test
    void giveReturnsToWhereItCameFromOnly() {
        SimpleContainer source1 = chest(new ItemStack(Items.DIRT, 30));
        SimpleContainer untouched = chest();
        ContainerSource source = new ContainerSource(List.of(allow(untouched), allow(source1)));
        List<ItemStack> taken = source.take(dirt(), 30);
        assertEquals(0, total(source1));

        ItemStack rest = source.give(taken.get(0).copyWithCount(30));
        assertTrue(rest.isEmpty());
        assertEquals(30, total(source1));
        assertEquals(0, total(untouched), "não despeja em container de onde nada saiu");
    }

    @Test
    void giveBeforeAnyTakeStoresNothing() {
        SimpleContainer c = chest();
        ItemStack in = new ItemStack(Items.DIRT, 5);
        ItemStack rest = new ContainerSource(List.of(allow(c))).give(in);
        assertEquals(5, rest.getCount());
        assertEquals(0, total(c));
    }

    @Test
    void randomTakeAndGiveConservesItems() {
        Random rnd = new Random(42);
        for (int round = 0; round < 200; round++) {
            List<ContainerSource.Entry> entries = new ArrayList<>();
            List<SimpleContainer> chests = new ArrayList<>();
            int initial = 0;
            for (int i = 0; i < 1 + rnd.nextInt(5); i++) {
                SimpleContainer c = new SimpleContainer(27);
                for (int slot = 0; slot < 27; slot++) {
                    if (rnd.nextInt(3) == 0) {
                        int n = 1 + rnd.nextInt(64);
                        c.setItem(slot, new ItemStack(Items.DIRT, n));
                        initial += n;
                    }
                }
                boolean allowed = rnd.nextInt(4) != 0;
                chests.add(c);
                entries.add(new ContainerSource.Entry(c, () -> allowed));
            }
            ContainerSource source = new ContainerSource(entries);
            List<ItemStack> taken = source.take(dirt(), 1 + rnd.nextInt(400));
            int takenTotal = ItemSource.sum(taken);
            int inChests = chests.stream().mapToInt(ContainerSourceTest::total).sum();
            assertEquals(initial, inChests + takenTotal, "tirar não pode criar nem perder item");

            if (takenTotal > 0 && rnd.nextBoolean()) {
                ItemStack back = taken.get(0).copyWithCount(takenTotal);
                // give aceita mais que o max stack aqui de propósito: a lógica de undo usa o stack já montado
                ItemStack rest = source.give(back);
                int afterGive = chests.stream().mapToInt(ContainerSourceTest::total).sum();
                assertEquals(initial, afterGive + rest.getCount(), "devolver não pode criar nem perder item");
            }
        }
    }

    @Test
    void radiusIsAlwaysClampedToHardCap() {
        int saved = StashLinkConfig.sourceRadius;
        try {
            StashLinkConfig.sourceRadius = 10_000;
            assertEquals(StashLinkConfig.HARD_MAX_RADIUS, StashLinkConfig.effectiveRadius());
            StashLinkConfig.sourceRadius = -5;
            assertEquals(0, StashLinkConfig.effectiveRadius());
        } finally {
            StashLinkConfig.sourceRadius = saved;
        }
    }
}
