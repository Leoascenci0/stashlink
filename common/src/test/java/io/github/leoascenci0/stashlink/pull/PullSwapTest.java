package io.github.leoascenci0.stashlink.pull;

import io.github.leoascenci0.stashlink.MinecraftTestSetup;
import io.github.leoascenci0.stashlink.source.ItemSource;
import io.github.leoascenci0.stashlink.source.Origin;
import io.github.leoascenci0.stashlink.source.PlayerShulkerSource;
import io.github.leoascenci0.stashlink.source.PrioritizedItemSource;
import io.github.leoascenci0.stashlink.source.StackListSink;
import io.github.leoascenci0.stashlink.storage.ShulkerStorage;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Troca no mesmo slot (Item 18): o item antigo do mod volta e o novo ocupa o slot, sem criar nem perder nada. */
class PullSwapTest {
    private static final Origin SHULKERS = new Origin(true, "overworld", Set.of());

    @BeforeAll
    static void bootstrap() {
        MinecraftTestSetup.init();
    }

    private static List<ItemStack> emptyList(int n) {
        return new ArrayList<>(Collections.nCopies(n, ItemStack.EMPTY));
    }

    private static ItemStack box(ItemStack... content) {
        ItemStack box = new ItemStack(Items.SHULKER_BOX);
        for (ItemStack c : content) {
            assertTrue(ShulkerStorage.insert(box, c).isEmpty());
        }
        return box;
    }

    /** Um jogador de mentira: hotbar, mochila (onde ficam as shulkers) e o registro do slot do mod. */
    private static final class Rig {
        final List<ItemStack> hotbar = emptyList(9);
        final List<ItemStack> backpack = emptyList(27);
        final PlayerShulkerSource source = new PlayerShulkerSource(backpack);
        PulledSlot mine;

        Rig(ItemStack... boxes) {
            for (int i = 0; i < boxes.length; i++) {
                backpack.set(i, boxes[i]);
            }
        }

        PullLogic.Result request(Item item, int selected) {
            ItemStack model = new ItemStack(item);
            if (mine != null && !mine.stillOwned(hotbar)) {
                mine = null;
            }
            ItemSource returnTo = new PrioritizedItemSource(List.of(source, new StackListSink(backpack)));
            PullLogic.Result r = PullLogic.pull(hotbar, selected, model, 64, source,
                    mine == null ? null : mine.asOwned(), mine == null ? null : returnTo);
            mine = PulledSlot.next(mine, hotbar, r, model, SHULKERS);
            return r;
        }

        /** Tudo o que existe deste item: hotbar, mochila solta e dentro das shulkers. */
        int total(Item item) {
            int sum = 0;
            for (List<ItemStack> list : List.of(hotbar, backpack)) {
                for (ItemStack s : list) {
                    if (s.is(item)) {
                        sum += s.getCount();
                    } else if (ShulkerStorage.isShulker(s)) {
                        sum += ShulkerStorage.count(s, x -> x.is(item));
                    }
                }
            }
            return sum;
        }

        int hotbarFilled() {
            return (int) hotbar.stream().filter(s -> !s.isEmpty()).count();
        }

        void fillBackpackWithJunk() {
            for (int i = 0; i < backpack.size(); i++) {
                if (backpack.get(i).isEmpty()) {
                    backpack.set(i, new ItemStack(Items.DIAMOND_PICKAXE));
                }
            }
        }
    }

    @Test
    void swapsInPlaceAndReturnsTheOldItemToTheShulker() {
        Rig rig = new Rig(box(new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.OAK_PLANKS, 64)));
        assertEquals(3, rig.request(Items.COBBLESTONE, 3).slot());
        rig.hotbar.get(3).shrink(44);                      // construiu 44 blocos; sobram 20
        PullLogic.Result r = rig.request(Items.OAK_PLANKS, 3);

        assertEquals(3, r.slot());
        assertEquals(20, r.returned());
        assertTrue(rig.hotbar.get(3).is(Items.OAK_PLANKS));
        assertEquals(64, rig.hotbar.get(3).getCount());
        assertEquals(20, rig.total(Items.COBBLESTONE));
        assertEquals(1, rig.hotbarFilled());
    }

    @Test
    void thirtyOneDifferentItemsKeepTheHotbarCleanAndConserveEverything() {
        Item[] items = {Items.COBBLESTONE, Items.DIRT, Items.OAK_PLANKS, Items.SPRUCE_PLANKS, Items.BIRCH_PLANKS,
                Items.STONE, Items.GRANITE, Items.DIORITE, Items.ANDESITE, Items.SAND, Items.GRAVEL, Items.OAK_LOG,
                Items.SPRUCE_LOG, Items.BIRCH_LOG, Items.GLASS, Items.BRICKS, Items.SANDSTONE, Items.CLAY,
                Items.OAK_SLAB, Items.SPRUCE_SLAB, Items.STONE_BRICKS, Items.MOSSY_COBBLESTONE, Items.OBSIDIAN,
                Items.NETHERRACK, Items.END_STONE, Items.PRISMARINE, Items.QUARTZ_BLOCK, Items.TERRACOTTA,
                Items.COAL_BLOCK, Items.IRON_BLOCK, Items.GOLD_BLOCK};
        ItemStack[] boxes = new ItemStack[3];
        for (int b = 0; b < boxes.length; b++) {
            boxes[b] = new ItemStack(Items.SHULKER_BOX);
            for (int i = b * 11; i < Math.min(items.length, b * 11 + 11); i++) {
                assertTrue(ShulkerStorage.insert(boxes[b], new ItemStack(items[i], 64)).isEmpty());
            }
        }
        Rig rig = new Rig(boxes);
        int expected = items.length * 64;
        for (int round = 0; round < 2; round++) {
            for (int i = 0; i < items.length; i++) {
                PullLogic.Result r = rig.request(items[i], 0);
                assertNotEquals(PullLogic.NO_SLOT, r.slot(), "pedido " + i);
                assertEquals(1, rig.hotbarFilled(), "hotbar suja no pedido " + i);
                int spent = 1 + i % 5;
                rig.hotbar.get(r.slot()).shrink(spent);       // gasta alguns blocos construindo
                expected -= spent;
                int sum = 0;
                for (Item item : items) {
                    sum += rig.total(item);
                }
                assertEquals(expected, sum, "soma de itens após o pedido " + i);
            }
        }
    }

    @Test
    void neverTouchesAnItemThePlayerPutInTheSlot() {
        Rig rig = new Rig(box(new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.DIRT, 64)));
        rig.request(Items.COBBLESTONE, 0);
        rig.backpack.set(10, rig.hotbar.get(0));                    // o jogador guardou o cobble...
        rig.hotbar.set(0, new ItemStack(Items.DIAMOND_PICKAXE));    // ...e pôs a picareta no slot
        PullLogic.Result r = rig.request(Items.DIRT, 0);

        assertTrue(rig.hotbar.get(0).is(Items.DIAMOND_PICKAXE));
        assertEquals(1, r.slot());                                  // o novo foi para outro slot
        assertEquals(0, r.returned());
        assertEquals(64, rig.total(Items.COBBLESTONE));
    }

    @Test
    void onlyReturnsWhatTheModPutNotWhatThePlayerAddedAfterwards() {
        Rig rig = new Rig(box(new ItemStack(Items.COBBLESTONE, 30), new ItemStack(Items.DIRT, 64)));
        rig.request(Items.COBBLESTONE, 0);                          // mod pôs 30
        rig.hotbar.set(0, new ItemStack(Items.COBBLESTONE, 40));    // jogador juntou 10 do chão no mesmo slot
        PullLogic.Result r = rig.request(Items.DIRT, 0);

        assertEquals(30, r.returned());
        assertTrue(rig.hotbar.get(0).is(Items.COBBLESTONE));        // os 10 do jogador ficam
        assertEquals(10, rig.hotbar.get(0).getCount());
        assertEquals(1, r.slot());                                  // slot não ficou livre: dirt vai para outro
        assertEquals(40, rig.total(Items.COBBLESTONE));
    }

    @Test
    void fullOriginFallsBackToTheBackpackNeverLosingItems() {
        ItemStack full = new ItemStack(Items.SHULKER_BOX);
        for (int i = 0; i < ShulkerStorage.SLOTS; i++) {
            // 27 itens diferentes não existem à toa aqui: enche com picaretas (não empilham, ocupam 1 slot cada)
            assertTrue(ShulkerStorage.insert(full, new ItemStack(Items.DIAMOND_PICKAXE)).isEmpty());
        }
        Rig rig = new Rig(box(new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.DIRT, 64)));
        rig.backpack.set(1, full);
        rig.request(Items.COBBLESTONE, 0);
        // a shulker de origem agora tem espaço (saiu 1 stack); enche de novo para a origem ficar cheia
        ItemStack origin = rig.backpack.get(0);
        for (int i = 0; i < ShulkerStorage.SLOTS; i++) {
            ShulkerStorage.insert(origin, new ItemStack(Items.DIAMOND_SHOVEL));
        }
        int before = rig.total(Items.COBBLESTONE);
        PullLogic.Result r = rig.request(Items.DIRT, 0);

        assertEquals(0, r.slot());
        assertEquals(64, r.returned());
        assertEquals(before, rig.total(Items.COBBLESTONE));
        assertTrue(rig.backpack.stream().anyMatch(s -> s.is(Items.COBBLESTONE)), "foi para a mochila");
    }

    @Test
    void ifNothingAcceptsTheOldItemItStaysAndTheNewOneGoesElsewhere() {
        Rig rig = new Rig(box(new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.DIRT, 64)));
        rig.request(Items.COBBLESTONE, 0);
        ItemStack origin = rig.backpack.get(0);
        for (int i = 0; i < ShulkerStorage.SLOTS; i++) {
            ShulkerStorage.insert(origin, new ItemStack(Items.DIAMOND_SHOVEL));
        }
        rig.fillBackpackWithJunk();
        int before = rig.total(Items.COBBLESTONE);
        PullLogic.Result r = rig.request(Items.DIRT, 0);

        assertEquals(1, r.slot());
        assertEquals(0, r.returned());
        assertTrue(rig.hotbar.get(0).is(Items.COBBLESTONE));
        assertEquals(64, rig.hotbar.get(0).getCount());
        assertEquals(before, rig.total(Items.COBBLESTONE));
        assertEquals(64, rig.total(Items.DIRT));
    }

    @Test
    void anImpossibleRequestLeavesTheHotbarAlone() {
        Rig rig = new Rig(box(new ItemStack(Items.COBBLESTONE, 64)));
        rig.request(Items.COBBLESTONE, 0);
        PullLogic.Result r = rig.request(Items.DIAMOND_BLOCK, 0);   // não existe em lugar nenhum

        assertEquals(PullLogic.NO_SLOT, r.slot());
        assertEquals(0, r.returned());
        assertTrue(rig.hotbar.get(0).is(Items.COBBLESTONE));
        assertEquals(64, rig.hotbar.get(0).getCount());
    }

    @Test
    void topsUpTheModSlotKeepingItOwned() {
        Rig rig = new Rig(box(new ItemStack(Items.COBBLESTONE, 40), new ItemStack(Items.DIRT, 64)));
        rig.request(Items.COBBLESTONE, 0);                          // pôs 40
        rig.hotbar.get(0).shrink(10);                               // sobram 30
        ShulkerStorage.insert(rig.backpack.get(0), new ItemStack(Items.COBBLESTONE, 20));
        PullLogic.Result top = rig.request(Items.COBBLESTONE, 0);   // completa com 20
        assertEquals(20, top.taken());
        assertEquals(50, rig.hotbar.get(0).getCount());

        PullLogic.Result swap = rig.request(Items.DIRT, 0);
        assertEquals(50, swap.returned());                          // os 50 (do mod) voltam
        assertEquals(50, rig.total(Items.COBBLESTONE));
    }
}
