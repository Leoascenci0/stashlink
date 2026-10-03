package io.github.leoascenci0.stashlink.gametest;

import io.github.leoascenci0.stashlink.network.PullItemRequest;
import io.github.leoascenci0.stashlink.pull.PullItemService;
import io.github.leoascenci0.stashlink.quickstack.QuickStackService;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Item 18: o Litematica pede blocos diferentes um atrás do outro e o mod troca tudo no MESMO slot, devolvendo o
 * anterior à origem. Servidor de verdade, jogadores em sobrevivência. Regra de ouro (como no Item 11): a soma de
 * cada item (baús + jogadores) só muda pelo que o jogador "gasta" construindo; nada duplica nem some (item solto no
 * chão sairia da soma, então a conservação também prova que nada foi dropado).
 */
public class SwapGameTests {
    /** O pedido do mesmo jogador só é aceito a cada 4 ticks; os cenários andam de 5 em 5. */
    private static final int STEP = 5;

    private static final Item[] BLOCKS = {Items.COBBLESTONE, Items.DIRT, Items.OAK_PLANKS, Items.SPRUCE_PLANKS,
            Items.BIRCH_PLANKS, Items.STONE, Items.GRANITE, Items.DIORITE, Items.ANDESITE, Items.SAND, Items.GRAVEL,
            Items.OAK_LOG, Items.SPRUCE_LOG, Items.BIRCH_LOG, Items.GLASS, Items.BRICKS, Items.SANDSTONE, Items.CLAY,
            Items.OAK_SLAB, Items.SPRUCE_SLAB, Items.STONE_BRICKS, Items.MOSSY_COBBLESTONE, Items.OBSIDIAN,
            Items.NETHERRACK, Items.END_STONE, Items.PRISMARINE, Items.QUARTZ_BLOCK, Items.TERRACOTTA,
            Items.COAL_BLOCK, Items.IRON_BLOCK, Items.GOLD_BLOCK};

    private static void check(GameTestHelper h, boolean ok, String msg) {
        h.assertTrue(ok, msg);
    }

    private static void pull(ServerPlayer p, Item item) {
        PullItemService.handle(p, new PullItemRequest(item, 64));
    }

    private static int hotbarFilled(ServerPlayer p) {
        int n = 0;
        for (int i = 0; i < Inventory.getSelectionSize(); i++) {
            if (!p.getInventory().getItem(i).isEmpty()) {
                n++;
            }
        }
        return n;
    }


    private static int totalOf(List<Container> boxes, List<ServerPlayer> players, Item item) {
        int t = 0;
        for (Container c : boxes) {
            t += c.countItem(item);
        }
        for (ServerPlayer p : players) {
            t += Lab.carried(p, item);
        }
        return t;
    }

    /** Roda os passos um a cada {@link #STEP} ticks (dá tempo do intervalo mínimo entre pedidos) e dá o teste por feito. */
    private static void runSteps(GameTestHelper h, Lab lab, List<Runnable> steps) {
        for (int i = 0; i < steps.size(); i++) {
            h.runAfterDelay((long) STEP * (i + 1), steps.get(i));
        }
        h.runAfterDelay((long) STEP * (steps.size() + 1), () -> {
            lab.cleanup();
            h.succeed();
        });
    }


    // ---------------------------------------------------------------- o caso do Eliel

    /**
     * 35 pedidos de 31 tipos diferentes (de dois baús): a hotbar nunca passa de 1 slot, o que sobrou de cada tipo
     * volta ao baú de onde veio, e a soma de itens só cai pelo que foi "gasto".
     */
    @GameTest(maxTicks = 400)
    public void thirtyFiveRequestsKeepHotbarClean(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container big = lab.doubleChest(2, 2, 2);          // 54 slots
        Container small = lab.chest(6, 2, 6);
        ServerPlayer p = lab.player(4, 2, 4);
        Lab.prefs(p, 12, true);
        Container[] origin = new Container[BLOCKS.length];
        for (int i = 0; i < BLOCKS.length; i++) {
            origin[i] = i < 20 ? big : small;
            Lab.fill(origin[i], i < 20 ? i : i - 20, BLOCKS[i], 64);
        }
        List<Container> boxes = List.of(big, small);
        List<ServerPlayer> players = List.of(p);
        int[] expected = {BLOCKS.length * 64};
        int[] spent = new int[BLOCKS.length];

        List<Runnable> steps = new ArrayList<>();
        for (int i = 0; i < 35; i++) {
            final int n = i;
            final int idx = i % BLOCKS.length;
            steps.add(() -> {
                pull(p, BLOCKS[idx]);
                check(h, hotbarFilled(p) == 1, "pedido " + n + ": hotbar com " + hotbarFilled(p) + " slots usados");
                ItemStack held = p.getInventory().getItem(p.getInventory().getSelectedSlot());
                check(h, held.is(BLOCKS[idx]) && held.getCount() == 64 - spent[idx],
                        "pedido " + n + ": mão devia ter " + BLOCKS[idx] + ", tem " + held);
                if (n > 0) {
                    int prev = (n - 1) % BLOCKS.length;
                    int back = Lab.count(origin[prev], BLOCKS[prev]);
                    check(h, back == 64 - spent[prev], "pedido " + n + ": " + BLOCKS[prev] + " devia ter voltado ao baú de origem ("
                            + (64 - spent[prev]) + "), voltou " + back);
                }
                int use = 1 + n % 5;                      // constrói alguns blocos
                held.shrink(use);
                spent[idx] += use;
                expected[0] -= use;
                int sum = 0;
                for (Item item : BLOCKS) {
                    sum += totalOf(boxes, players, item);
                }
                check(h, sum == expected[0], "pedido " + n + ": soma " + sum + ", esperado " + expected[0]);
            });
        }
        runSteps(h, lab, steps);
    }

    // ---------------------------------------------------------------- origem cheia / fora do raio / quebrada

    /** Origem cheia: o item antigo vai para a mochila (nunca para o chão) e nada some. */
    @GameTest(maxTicks = 100)
    public void fullOriginFallsBackToBackpack(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container c1 = lab.chest(2, 2, 2);
        Container c2 = lab.chest(6, 2, 2);
        Lab.fill(c1, 0, Items.COBBLESTONE, 64);
        Lab.fill(c2, 0, Items.DIRT, 64);
        ServerPlayer p = lab.player(4, 2, 4);
        Lab.prefs(p, 12, true);
        List<Container> boxes = List.of(c1, c2);
        List<ServerPlayer> players = List.of(p);
        runSteps(h, lab, List.of(
                () -> {
                    pull(p, Items.COBBLESTONE);
                    for (int s = 0; s < c1.getContainerSize(); s++) {
                        if (c1.getItem(s).isEmpty()) {
                            c1.setItem(s, new ItemStack(Items.DIAMOND_SHOVEL));   // origem agora 100% cheia
                        }
                    }
                    p.getInventory().getItem(p.getInventory().getSelectedSlot()).shrink(10);
                },
                () -> {
                    pull(p, Items.DIRT);
                    check(h, Lab.count(c1, Items.COBBLESTONE) == 0, "baú cheio não pode ter recebido pedra");
                    check(h, p.getInventory().countItem(Items.COBBLESTONE) == 54, "pedra devia estar na mochila: "
                            + p.getInventory().countItem(Items.COBBLESTONE));
                    check(h, hotbarFilled(p) == 1, "hotbar devia ter só a terra");
                    check(h, totalOf(boxes, players, Items.COBBLESTONE) == 54, "soma de pedra mudou");
                    check(h, totalOf(boxes, players, Items.DIRT) == 64, "soma de terra mudou");
                }));
    }

    /** Origem fora do raio no momento da devolução: vai para a mochila, e o baú distante não é tocado. */
    @GameTest(maxTicks = 100)
    public void originOutOfRangeFallsBackToBackpack(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container near = lab.chest(2, 2, 2);
        Container far = lab.chest(30, 2, 2);
        Lab.fill(near, 0, Items.COBBLESTONE, 64);
        Lab.fill(far, 0, Items.DIRT, 64);
        ServerPlayer p = lab.player(3, 2, 3);
        Lab.prefs(p, 8, true);
        List<Container> boxes = List.of(near, far);
        List<ServerPlayer> players = List.of(p);
        runSteps(h, lab, List.of(
                () -> {
                    pull(p, Items.COBBLESTONE);
                    p.getInventory().getItem(p.getInventory().getSelectedSlot()).shrink(4);
                    p.setPos(h.absoluteVec(new Vec3(29, 2, 3)));         // andou até o outro baú
                },
                () -> {
                    pull(p, Items.DIRT);
                    check(h, Lab.count(near, Items.COBBLESTONE) == 0, "baú fora do raio foi mexido");
                    check(h, p.getInventory().countItem(Items.COBBLESTONE) == 60, "pedra devia estar na mochila: "
                            + p.getInventory().countItem(Items.COBBLESTONE));
                    check(h, hotbarFilled(p) == 1, "hotbar devia ter só a terra");
                    check(h, totalOf(boxes, players, Items.COBBLESTONE) == 60, "soma de pedra mudou");
                }));
    }

    /** Baú de origem quebrado antes da devolução: o item não pode "entrar" nele (sumiria) — vai para a mochila. */
    @GameTest(maxTicks = 100)
    public void brokenOriginIsNeverUsed(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container c1 = lab.chest(2, 2, 2);
        Container c2 = lab.chest(6, 2, 2);
        Lab.fill(c1, 0, Items.COBBLESTONE, 64);
        Lab.fill(c2, 0, Items.DIRT, 64);
        ServerPlayer p = lab.player(4, 2, 4);
        Lab.prefs(p, 12, true);
        runSteps(h, lab, List.of(
                () -> {
                    pull(p, Items.COBBLESTONE);
                    lab.level.removeBlock(h.absolutePos(new BlockPos(2, 2, 2)), false);   // baú quebrado
                },
                () -> {
                    pull(p, Items.DIRT);
                    check(h, Lab.count(c1, Items.COBBLESTONE) == 0, "item entrou num baú que já não existe");
                    check(h, p.getInventory().countItem(Items.COBBLESTONE) == 64, "pedra devia estar na mochila: "
                            + p.getInventory().countItem(Items.COBBLESTONE));
                    check(h, hotbarFilled(p) == 1, "hotbar devia ter só a terra");
                }));
    }

    /** Mochila e origem cheias: o item antigo fica na mão e o novo vai para outro slot — nada some, nada cai. */
    @GameTest(maxTicks = 100)
    public void everythingFullKeepsOldItemInPlace(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container c1 = lab.chest(2, 2, 2);
        Container c2 = lab.chest(6, 2, 2);
        Lab.fill(c1, 0, Items.COBBLESTONE, 64);
        Lab.fill(c2, 0, Items.DIRT, 64);
        ServerPlayer p = lab.player(4, 2, 4);
        Lab.prefs(p, 12, true);
        List<Container> boxes = List.of(c1, c2);
        List<ServerPlayer> players = List.of(p);
        runSteps(h, lab, List.of(
                () -> {
                    pull(p, Items.COBBLESTONE);
                    for (int s = 0; s < c1.getContainerSize(); s++) {
                        if (c1.getItem(s).isEmpty()) {
                            c1.setItem(s, new ItemStack(Items.DIAMOND_SHOVEL));
                        }
                    }
                    for (int s = Inventory.getSelectionSize(); s < 36; s++) {
                        p.getInventory().setItem(s, new ItemStack(Items.DIAMOND_PICKAXE));
                    }
                },
                () -> {
                    pull(p, Items.DIRT);
                    check(h, Lab.carried(p, Items.COBBLESTONE) == 64, "pedra tinha de continuar na mão");
                    check(h, Lab.carried(p, Items.DIRT) == 64, "terra devia estar em outro slot da hotbar");
                    check(h, hotbarFilled(p) == 2, "hotbar: " + hotbarFilled(p));
                    check(h, totalOf(boxes, players, Items.COBBLESTONE) == 64, "soma de pedra mudou");
                }));
    }

    /** Item que o jogador pôs no slot (e levou o do mod para a mochila) nunca é devolvido nem trocado. */
    @GameTest(maxTicks = 100)
    public void playerOwnedSlotIsNeverTouched(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container c = lab.chest(4, 2, 2);
        Lab.fill(c, 0, Items.COBBLESTONE, 64);
        Lab.fill(c, 1, Items.DIRT, 64);
        ServerPlayer p = lab.player(4, 2, 4);
        Lab.prefs(p, 12, true);
        runSteps(h, lab, List.of(
                () -> {
                    pull(p, Items.COBBLESTONE);
                    int slot = p.getInventory().getSelectedSlot();
                    p.getInventory().setItem(20, p.getInventory().getItem(slot).copy());   // jogador guardou a pedra...
                    p.getInventory().setItem(slot, new ItemStack(Items.DIAMOND_PICKAXE));  // ...e pôs a picareta
                },
                () -> {
                    pull(p, Items.DIRT);
                    check(h, p.getInventory().countItem(Items.DIAMOND_PICKAXE) == 1
                            && p.getInventory().getItem(0).is(Items.DIAMOND_PICKAXE), "a picareta do jogador foi mexida");
                    check(h, Lab.carried(p, Items.COBBLESTONE) == 64, "pedra do jogador mudou");
                    check(h, Lab.carried(p, Items.DIRT) == 64, "terra devia estar na mão");
                }));
    }

    // ---------------------------------------------------------------- 2 jogadores

    /** Dois jogadores trocando blocos do MESMO baú: tudo conferido a cada passo; baú aberto por outro não recebe devolução. */
    @GameTest(maxTicks = 300)
    public void twoPlayersSwapOnSameChest(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container c = lab.chest(4, 2, 2);
        Lab.fill(c, 0, Items.COBBLESTONE, 64);
        Lab.fill(c, 1, Items.DIRT, 64);
        Lab.fill(c, 2, Items.OAK_PLANKS, 64);
        ServerPlayer a = lab.player(3, 2, 4);
        ServerPlayer b = lab.player(5, 2, 4);
        Lab.prefs(a, 12, true);
        Lab.prefs(b, 12, true);
        List<Container> boxes = List.of(c);
        List<ServerPlayer> players = List.of(a, b);
        Item[] items = {Items.COBBLESTONE, Items.DIRT, Items.OAK_PLANKS};
        Runnable conserved = () -> {
            for (Item it : items) {
                check(h, totalOf(boxes, players, it) == 64, it + ": soma " + totalOf(boxes, players, it));
            }
        };
        runSteps(h, lab, List.of(
                () -> { pull(a, Items.COBBLESTONE); conserved.run(); },
                () -> { pull(b, Items.DIRT); conserved.run(); },
                () -> { pull(a, Items.OAK_PLANKS); conserved.run();
                    check(h, Lab.count(c, Items.COBBLESTONE) == 64, "A devia ter devolvido a pedra ao baú"); },
                () -> { pull(b, Items.COBBLESTONE); conserved.run();
                    check(h, Lab.count(c, Items.DIRT) == 64, "B devia ter devolvido a terra ao baú"); },
                // B está olhando o baú: a devolução de A não pode entrar nele (iria para a mochila de A).
                () -> { Lab.open(b, c, 1); },
                () -> {
                    pull(a, Items.DIRT);
                    conserved.run();
                    check(h, Lab.count(c, Items.OAK_PLANKS) == 0, "baú aberto por B recebeu devolução de A");
                    check(h, a.getInventory().countItem(Items.OAK_PLANKS) == 64, "tábuas de A deviam estar na mochila");
                    check(h, hotbarFilled(a) == 1, "hotbar de A: " + hotbarFilled(a));
                }));
    }

    // ---------------------------------------------------------------- fuzz

    /**
     * 1500 ticks de pedidos, "gasto" de blocos, jogador mexendo na hotbar, abrindo/fechando baú e apertando N, com 2
     * jogadores e 3 baús (um duplo). Depois de CADA ação, a soma de cada item só pode ter mudado pelo que foi gasto.
     */
    @GameTest(maxTicks = 1700)
    public void swapFuzz(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container c1 = lab.chest(2, 2, 2);
        Container c2 = lab.chest(6, 2, 2);
        Container dbl = lab.doubleChest(3, 2, 6);
        List<Container> boxes = List.of(c1, c2, dbl);
        ServerPlayer a = lab.player(4, 2, 4);
        ServerPlayer b = lab.player(5, 2, 4);
        List<ServerPlayer> players = List.of(a, b);
        Lab.prefs(a, 12, true);
        Lab.prefs(b, 12, true);
        Item[] items = {Items.COBBLESTONE, Items.DIRT, Items.OAK_LOG, Items.SAND, Items.GLASS, Items.BRICKS};
        for (int i = 0; i < items.length; i++) {
            Lab.fill(boxes.get(i % 3), i, items[i], 64);
        }
        Lab.fill(dbl, 30, Items.COBBLESTONE, 40);
        Lab.give(a, 12, Items.SAND, 20);
        Lab.give(b, 12, Items.DIRT, 10);
        int[] expected = new int[items.length];
        for (int i = 0; i < items.length; i++) {
            expected[i] = totalOf(boxes, players, items[i]);
        }
        Random rnd = new Random(18);
        int[] pulls = {0};

        for (int step = 1; step <= 300; step++) {
            final int t = step;
            h.runAfterDelay((long) STEP * step, () -> {
                for (ServerPlayer p : players) {
                    Inventory inv = p.getInventory();
                    int action = rnd.nextInt(10);
                    String name;
                    int[] before = new int[items.length];
                    for (int i = 0; i < items.length; i++) {
                        before[i] = totalOf(boxes, players, items[i]);
                    }
                    switch (action) {
                        case 0, 1, 2, 3 -> {
                            name = "pedir";
                            pull(p, items[rnd.nextInt(items.length)]);
                        }
                        case 4, 5 -> {
                            name = "gastar";
                            ItemStack held = inv.getItem(inv.getSelectedSlot());
                            if (!held.isEmpty() && p.containerMenu == p.inventoryMenu) {
                                int use = Math.min(held.getCount(), 1 + rnd.nextInt(10));
                                for (int i = 0; i < items.length; i++) {
                                    if (held.is(items[i])) {
                                        expected[i] -= use;
                                    }
                                }
                                held.shrink(use);
                            }
                        }
                        case 6 -> {
                            name = "troca slots";
                            int x = rnd.nextInt(9);
                            int y = rnd.nextInt(9);
                            ItemStack tmp = inv.getItem(x);
                            inv.setItem(x, inv.getItem(y));
                            inv.setItem(y, tmp);
                        }
                        case 7 -> {
                            name = "guarda na mochila";
                            int from = rnd.nextInt(9);
                            for (int s = 9; s < 36 && !inv.getItem(from).isEmpty(); s++) {
                                if (inv.getItem(s).isEmpty()) {
                                    inv.setItem(s, inv.getItem(from));
                                    inv.setItem(from, ItemStack.EMPTY);
                                }
                            }
                        }
                        case 8 -> {
                            name = rnd.nextBoolean() ? "abre" : "fecha";
                            if (name.equals("abre") && p.containerMenu == p.inventoryMenu) {
                                Lab.open(p, boxes.get(rnd.nextInt(boxes.size())), 1 + rnd.nextInt(100));
                            } else {
                                Lab.close(p);
                            }
                        }
                        default -> {
                            name = "N";
                            QuickStackService.handle(p);
                        }
                    }
                    if (name.equals("pedir")) {
                        pulls[0]++;
                    }
                    for (int i = 0; i < items.length; i++) {
                        int now = totalOf(boxes, players, items[i]);
                        check(h, now == expected[i], "passo " + t + " " + (p == a ? "A" : "B") + " " + name + ": "
                                + items[i] + " esperado " + expected[i] + ", achou " + now + " (antes " + before[i] + ")");
                    }
                }
            });
        }
        h.runAfterDelay((long) STEP * 301, () -> {
            check(h, pulls[0] > 200, "o fuzz quase não pediu item (" + pulls[0] + ")");
            lab.cleanup();
            h.succeed();
        });
    }
}
