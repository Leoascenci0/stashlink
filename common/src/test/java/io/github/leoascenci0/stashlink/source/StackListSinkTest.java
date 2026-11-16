package io.github.leoascenci0.stashlink.source;

import io.github.leoascenci0.stashlink.MinecraftTestSetup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Item 32: a "devolução à mochila" nunca escreve em slot travado, e a soma de itens se conserva. */
class StackListSinkTest {
    @BeforeAll
    static void bootstrap() {
        MinecraftTestSetup.init();
    }

    private static List<ItemStack> empty(int n) {
        return new ArrayList<>(Collections.nCopies(n, ItemStack.EMPTY));
    }

    @Test
    void lockedSlotsReceiveNothingNeitherToTopUpNorEmpty() {
        List<ItemStack> slots = empty(4);
        slots.set(0, new ItemStack(Items.COBBLESTONE, 10));   // travado, mesmo item (poderia ser completado)
        slots.set(1, new ItemStack(Items.COBBLESTONE, 10));
        // slot 2 travado e vazio; slot 3 livre e vazio
        StackListSink sink = new StackListSink(slots, i -> i == 0 || i == 2);

        ItemStack rest = sink.give(new ItemStack(Items.COBBLESTONE, 64));

        assertEquals(10, slots.get(0).getCount(), "slot travado com o mesmo item não é completado");
        assertTrue(slots.get(2).isEmpty(), "slot travado vazio continua vazio");
        assertEquals(64, slots.get(1).getCount());
        assertEquals(10, slots.get(3).getCount());
        assertEquals(0, rest.getCount());
        assertEquals(10 + 10 + 64, slots.stream().mapToInt(ItemStack::getCount).sum() + rest.getCount());
    }

    @Test
    void whatDoesNotFitComesBackAsLeftover() {
        List<ItemStack> slots = empty(2);
        StackListSink sink = new StackListSink(slots, i -> true);
        ItemStack rest = sink.give(new ItemStack(Items.DIRT, 30));
        assertEquals(30, rest.getCount());
        assertTrue(slots.stream().allMatch(ItemStack::isEmpty));
    }

    @Test
    void withoutLocksItStillWorksAsBefore() {
        List<ItemStack> slots = empty(2);
        slots.set(0, new ItemStack(Items.DIRT, 60));
        ItemStack rest = new StackListSink(slots).give(new ItemStack(Items.DIRT, 10));
        assertTrue(rest.isEmpty());
        assertEquals(64, slots.get(0).getCount());
        assertEquals(6, slots.get(1).getCount());
    }
}
