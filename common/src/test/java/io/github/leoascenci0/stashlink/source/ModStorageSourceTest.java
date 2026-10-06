package io.github.leoascenci0.stashlink.source;

import io.github.leoascenci0.stashlink.MinecraftTestSetup;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.ObjLongConsumer;

import static org.junit.jupiter.api.Assertions.*;

/** Item 26: baús e gavetas de outros mods nunca criam nem perdem item, e seguem as mesmas regras dos baús. */
class ModStorageSourceTest {
    @BeforeAll
    static void bootstrap() {
        MinecraftTestSetup.init();
    }

    /** Gaveta em memória: um "slot" por tipo de item, com capacidade grande (passa de 64). Só itens sem componentes. */
    static final class Drawer implements ModStorage {
        final Map<Item, Long> stock = new LinkedHashMap<>();
        final long capacity;
        boolean failExtract;

        Drawer(long capacity) {
            this.capacity = capacity;
        }

        Drawer with(Item item, long n) {
            stock.put(item, n);
            return this;
        }

        long has(Item item) {
            return stock.getOrDefault(item, 0L);
        }

        @Override
        public void forEach(ObjLongConsumer<ItemStack> sink) {
            stock.forEach((item, n) -> {
                if (n > 0) {
                    sink.accept(new ItemStack(item), n);
                }
            });
        }

        @Override
        public long count(ItemStack model) {
            return has(model.getItem());
        }

        @Override
        public long extract(ItemStack model, long amount, boolean simulate) {
            if (failExtract) {
                throw new IllegalStateException("mod com defeito");
            }
            long got = Math.min(amount, has(model.getItem()));
            if (!simulate && got > 0) {
                stock.put(model.getItem(), has(model.getItem()) - got);
            }
            return got;
        }

        @Override
        public long insert(ItemStack model, long amount, boolean simulate) {
            long in = Math.min(amount, capacity - has(model.getItem()));
            if (in <= 0) {
                return 0;
            }
            if (!simulate) {
                stock.put(model.getItem(), has(model.getItem()) + in);
            }
            return in;
        }

        @Override
        public Object identity() {
            return this;
        }
    }

    private static int next;

    private static ModStorageSource.Entry allow(ModStorage s) {
        return new ModStorageSource.Entry(s, () -> true, Set.of(new BlockPos(next++, 0, 0)));
    }

    private static ItemStack dirt() {
        return new ItemStack(Items.DIRT);
    }

    @Test
    void takesThousandsAndSplitsIntoNormalStacks() {
        Drawer drawer = new Drawer(100_000).with(Items.DIRT, 10_000);
        ModStorageSource source = new ModStorageSource(List.of(allow(drawer)));
        assertEquals(10_000, source.available(dirt()));

        List<ItemStack> taken = source.take(dirt(), 200);
        assertEquals(200, ItemSource.sum(taken));
        assertEquals(9_800, drawer.has(Items.DIRT));
        for (ItemStack stack : taken) {
            assertTrue(stack.getCount() <= stack.getMaxStackSize(), "cada stack cabe num slot: " + stack.getCount());
        }
        assertEquals(4, taken.size(), "64 + 64 + 64 + 8");
    }

    @Test
    void hugeAmountsNeverWrapAround() {
        Drawer a = new Drawer(Long.MAX_VALUE).with(Items.DIRT, Long.MAX_VALUE / 2);
        Drawer b = new Drawer(Long.MAX_VALUE).with(Items.DIRT, Long.MAX_VALUE / 2);
        ModStorageSource source = new ModStorageSource(List.of(allow(a), allow(b)));
        assertEquals(Integer.MAX_VALUE, source.available(dirt()));
        PrioritizedItemSource both = new PrioritizedItemSource(List.of(source, source));
        assertEquals(Integer.MAX_VALUE, both.available(dirt()));
        List<ItemStack> listed = new ArrayList<>();
        source.forEachStack(listed::add);
        assertEquals(2, listed.size());
        assertEquals(Integer.MAX_VALUE, listed.get(0).getCount());
    }

    @Test
    void sameBlockAtTwoPositionsCountsOnce() {
        // Baú duplo de outro mod que entrega o inventário inteiro nas duas metades: o mesmo objeto, duas posições.
        Drawer shared = new Drawer(1000).with(Items.DIRT, 30);
        ModStorageSource source = new ModStorageSource(List.of(allow(shared), allow(shared)));
        assertEquals(30, source.available(dirt()));
        assertEquals(30, ItemSource.sum(source.take(dirt(), 64)));
        assertEquals(0, shared.has(Items.DIRT));
    }

    @Test
    void permissionIsOnlyAskedForBlocksThatHoldTheItem() {
        AtomicInteger asked = new AtomicInteger();
        Drawer empty = new Drawer(1000).with(Items.STONE, 5);
        Drawer full = new Drawer(1000).with(Items.DIRT, 5);
        ModStorageSource source = new ModStorageSource(List.of(
                new ModStorageSource.Entry(empty, () -> { asked.incrementAndGet(); return true; }, Set.of(BlockPos.ZERO)),
                new ModStorageSource.Entry(full, () -> { asked.incrementAndGet(); return true; }, Set.of(BlockPos.ZERO.above()))));
        source.available(dirt());
        source.take(dirt(), 5);
        assertEquals(1, asked.get(), "só a gaveta com terra é consultada, e uma vez só");
    }

    @Test
    void deniedBlockIsNeverTouchedNorListed() {
        Drawer drawer = new Drawer(1000).with(Items.DIRT, 50);
        ModStorageSource source = new ModStorageSource(List.of(
                new ModStorageSource.Entry(drawer, () -> false, Set.of(BlockPos.ZERO))));
        assertEquals(0, source.available(dirt()));
        assertTrue(source.take(dirt(), 10).isEmpty());
        List<ItemStack> listed = new ArrayList<>();
        source.forEachStack(listed::add);
        assertTrue(listed.isEmpty());
        assertEquals(50, drawer.has(Items.DIRT));
    }

    @Test
    void giveReturnsOnlyToWhereItCameFrom() {
        Drawer near = new Drawer(1000).with(Items.STONE, 1);
        Drawer far = new Drawer(1000).with(Items.DIRT, 20);
        ModStorageSource source = new ModStorageSource(List.of(allow(near), allow(far)));
        List<ItemStack> taken = source.take(dirt(), 20);
        assertEquals(20, ItemSource.sum(taken));
        for (ItemStack stack : taken) {
            assertTrue(source.give(stack).isEmpty());
        }
        assertEquals(20, far.has(Items.DIRT));
        assertEquals(0, near.has(Items.DIRT), "a gaveta da qual nada saiu não recebe");
        assertEquals(1, source.touchedPositions().size());
    }

    @Test
    void returningToFindsTheBlockByPosition() {
        Drawer drawer = new Drawer(1000);
        BlockPos pos = new BlockPos(7, 1, 7);
        ModStorageSource back = ModStorageSource.returningTo(List.of(
                new ModStorageSource.Entry(drawer, () -> true, Set.of(pos)),
                new ModStorageSource.Entry(new Drawer(1000), () -> true, Set.of(pos.above()))), Set.of(pos));
        assertTrue(back.give(new ItemStack(Items.DIRT, 40)).isEmpty());
        assertEquals(40, drawer.has(Items.DIRT));
    }

    @Test
    void brokenBlockKeepsWhatOthersAlreadyGave() {
        Drawer good = new Drawer(1000).with(Items.DIRT, 10);
        Drawer bad = new Drawer(1000).with(Items.DIRT, 10);
        bad.failExtract = true;
        ModStorageSource source = new ModStorageSource(List.of(allow(good), allow(bad)));
        List<ItemStack> taken = source.take(dirt(), 20);
        assertEquals(10, ItemSource.sum(taken), "o que a gaveta boa deu não some por causa da quebrada");
        assertEquals(10, bad.has(Items.DIRT));
    }

    @Test
    void stashOnlyStoresWhatTheBlockAlreadyHasAndWhatFits() {
        Drawer drawer = new Drawer(100).with(Items.DIRT, 90);
        List<ItemStack> slots = new ArrayList<>(List.of(new ItemStack(Items.DIRT, 64), new ItemStack(Items.STONE, 64),
                new ItemStack(Items.DIRT, 5)));
        int moved = ModStorageSource.stashInto(allow(drawer), slots, i -> i == 2, s -> false);
        assertEquals(10, moved, "só cabiam 10");
        assertEquals(100, drawer.has(Items.DIRT));
        assertEquals(54, slots.get(0).getCount());
        assertEquals(64, slots.get(1).getCount(), "pedra não estava na gaveta");
        assertEquals(5, slots.get(2).getCount(), "slot protegido (hotbar/travado) não é esvaziado");
    }

    @Test
    void stashAsksPermissionOnlyWhenThereIsSomethingToStore() {
        AtomicInteger asked = new AtomicInteger();
        Drawer drawer = new Drawer(1000).with(Items.STONE, 1);
        List<ItemStack> slots = new ArrayList<>(List.of(new ItemStack(Items.DIRT, 64)));
        ModStorageSource.Entry entry = new ModStorageSource.Entry(drawer, () -> { asked.incrementAndGet(); return false; }, Set.of(BlockPos.ZERO));
        assertEquals(0, ModStorageSource.stashInto(entry, slots, i -> false, s -> false));
        assertEquals(0, asked.get());
        slots.set(0, new ItemStack(Items.STONE, 3));
        assertEquals(0, ModStorageSource.stashInto(entry, slots, i -> false, s -> false), "sem permissão, nada entra");
        assertEquals(1, asked.get());
        assertEquals(3, slots.get(0).getCount());
    }

    @Test
    void randomTakeAndGiveConservesItems() {
        Random rnd = new Random(26);
        for (int round = 0; round < 200; round++) {
            List<Drawer> drawers = new ArrayList<>();
            List<ModStorageSource.Entry> entries = new ArrayList<>();
            long before = 0;
            for (int i = 0; i < 1 + rnd.nextInt(4); i++) {
                long n = rnd.nextInt(5000);
                Drawer d = new Drawer(6000).with(Items.DIRT, n);
                d.failExtract = rnd.nextInt(8) == 0;
                drawers.add(d);
                entries.add(allow(d));
                before += n;
            }
            ModStorageSource source = new ModStorageSource(entries);
            List<ItemStack> held = source.take(dirt(), rnd.nextInt(3000));
            long inHand = ItemSource.sum(held);
            if (rnd.nextBoolean()) {
                for (ItemStack stack : held) {
                    inHand -= stack.getCount() - source.give(stack).getCount();
                }
            }
            long after = inHand;
            for (Drawer d : drawers) {
                after += d.has(Items.DIRT);
            }
            assertEquals(before, after, "rodada " + round);
        }
    }
}
