package io.github.leoascenci0.stashlink.gametest;

import io.github.leoascenci0.stashlink.Constants;
import io.github.leoascenci0.stashlink.config.PlayerPrefs;
import io.github.leoascenci0.stashlink.config.PlayerPrefsStore;
import io.github.leoascenci0.stashlink.lootall.LootAllService;
import io.github.leoascenci0.stashlink.network.PullItemRequest;
import io.github.leoascenci0.stashlink.pull.PullItemService;
import io.github.leoascenci0.stashlink.quickstack.QuickStackService;
import io.github.leoascenci0.stashlink.refill.RefillService;
import io.github.leoascenci0.stashlink.source.NearbyContainers;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Item 11: cenários de multiplayer e carga rodando num servidor de verdade (jogadores simulados, baús de verdade).
 * Cada cenário confere a regra de ouro: a soma dos itens (baús + inventários + cursor) nunca muda.
 */
public class StashLinkGameTests {
    private static final Item COBBLE = Items.COBBLESTONE;
    private static final Item DIRT = Items.DIRT;

    private static void check(GameTestHelper h, boolean ok, String msg) {
        h.assertTrue(ok, msg);
    }

    // ---------------------------------------------------------------- multiplayer

    /** Dois jogadores apertam N no mesmo tick, com o mesmo baú ao alcance: nada duplica nem some. */
    @GameTest
    public void twoPlayersQuickStackSameChest(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(4, 2, 4);
        Lab.fill(chest, 0, COBBLE, 10);
        ServerPlayer a = lab.player(3, 2, 3);
        ServerPlayer b = lab.player(5, 2, 5);
        Lab.give(a, 12, COBBLE, 30);
        Lab.give(a, 13, DIRT, 40);
        Lab.give(b, 12, COBBLE, 50);

        QuickStackService.handle(a);
        QuickStackService.handle(b);

        int cobble = Lab.count(chest, COBBLE) + Lab.carried(a, COBBLE) + Lab.carried(b, COBBLE);
        check(h, cobble == 90, "pedra: esperado 90, achou " + cobble);
        check(h, Lab.count(chest, COBBLE) == 90, "toda a pedra devia estar no baú, achou " + Lab.count(chest, COBBLE));
        check(h, Lab.carried(a, DIRT) == 40, "terra de A nao pode sair (baú nao tem terra)");
        lab.cleanup();
        h.succeed();
    }

    /** Baú aberto por outro jogador é pulado pela N; fechado, volta a valer. */
    @GameTest
    public void quickStackSkipsChestOpenedByOther(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container open = lab.chest(2, 2, 2);
        Container free = lab.chest(6, 2, 6);
        Lab.fill(open, 0, COBBLE, 5);
        Lab.fill(free, 0, COBBLE, 5);
        ServerPlayer a = lab.player(3, 2, 3);
        ServerPlayer b = lab.player(5, 2, 5);
        Lab.open(a, open, 1);
        Lab.give(b, 12, COBBLE, 20);

        QuickStackService.handle(b);
        check(h, Lab.count(open, COBBLE) == 5, "baú aberto por A foi mexido: " + Lab.count(open, COBBLE));
        check(h, Lab.count(free, COBBLE) == 25, "baú livre devia receber tudo: " + Lab.count(free, COBBLE));

        // A fecha; um terceiro jogador (cooldown próprio) guarda no baú agora liberado.
        Lab.close(a);
        ServerPlayer c = lab.player(4, 2, 4);
        Lab.give(c, 12, COBBLE, 7);
        QuickStackService.handle(c);
        int total = Lab.count(open, COBBLE) + Lab.count(free, COBBLE) + Lab.carried(c, COBBLE) + Lab.carried(b, COBBLE);
        check(h, total == 37, "total devia ser 37, achou " + total);
        check(h, Lab.carried(c, COBBLE) == 0, "C devia ter guardado tudo");
        lab.cleanup();
        h.succeed();
    }

    /** Dois jogadores com o MESMO baú aberto apertam W: o primeiro leva, o segundo não duplica. */
    @GameTest
    public void twoPlayersLootAllSameChest(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(4, 2, 4);
        Lab.fill(chest, 0, COBBLE, 64);
        Lab.fill(chest, 1, COBBLE, 36);
        Lab.fill(chest, 2, DIRT, 50);
        ServerPlayer a = lab.player(3, 2, 3);
        ServerPlayer b = lab.player(5, 2, 5);
        ChestMenu ma = Lab.open(a, chest, 1);
        ChestMenu mb = Lab.open(b, chest, 1);

        LootAllService.handle(a);
        LootAllService.handle(b);
        ma.broadcastChanges();
        mb.broadcastChanges();

        int cobble = Lab.count(chest, COBBLE) + Lab.carried(a, COBBLE) + Lab.carried(b, COBBLE);
        int dirt = Lab.count(chest, DIRT) + Lab.carried(a, DIRT) + Lab.carried(b, DIRT);
        check(h, cobble == 100, "pedra: esperado 100, achou " + cobble);
        check(h, dirt == 50, "terra: esperado 50, achou " + dirt);
        check(h, Lab.count(chest, COBBLE) == 0 && Lab.carried(a, COBBLE) == 100, "A devia ter levado tudo");
        lab.cleanup();
        h.succeed();
    }

    /** W com inventário quase cheio, dois jogadores: o resto fica no baú e cada um leva o que cabe. */
    @GameTest
    public void lootAllPartialTwoPlayers(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(4, 2, 4);
        for (int i = 0; i < 20; i++) {
            Lab.fill(chest, i, COBBLE, 64);
        }
        ServerPlayer a = lab.player(3, 2, 3);
        ServerPlayer b = lab.player(5, 2, 5);
        // A tem só 3 slots livres; B tem espaço de sobra.
        for (int s = 0; s < 33; s++) {
            Lab.give(a, s, Items.STONE, 64);
        }
        Lab.open(a, chest, 1);
        Lab.open(b, chest, 1);
        int before = 20 * 64;

        LootAllService.handle(a);
        check(h, Lab.carried(a, COBBLE) == 3 * 64, "A devia levar 192, levou " + Lab.carried(a, COBBLE));
        LootAllService.handle(b);
        int total = Lab.count(chest, COBBLE) + Lab.carried(a, COBBLE) + Lab.carried(b, COBBLE);
        check(h, total == before, "pedra: esperado " + before + ", achou " + total);
        check(h, Lab.count(chest, COBBLE) == 0, "B tinha espaço: o baú devia esvaziar");
        lab.cleanup();
        h.succeed();
    }

    /** Reabastecimento da mão tirando de um baú que OUTRO jogador está com aberto: o total se mantém. */
    @GameTest
    public void refillFromChestOpenedByOther(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(4, 2, 4);
        Lab.fill(chest, 0, COBBLE, 64);
        ServerPlayer a = lab.player(3, 2, 3);
        ServerPlayer b = lab.player(5, 2, 5);
        Lab.prefs(b, 8, true);
        ChestMenu ma = Lab.open(a, chest, 1);

        b.getInventory().setItem(b.getInventory().getSelectedSlot(), new ItemStack(COBBLE, 1));
        RefillService.tickPlayer(b);                     // o mod "vê" 1 pedra na mão
        b.getInventory().setItem(b.getInventory().getSelectedSlot(), ItemStack.EMPTY); // colocou o bloco
        RefillService.tickPlayer(b);                     // esgotou: reabastece
        ma.broadcastChanges();

        int total = Lab.count(chest, COBBLE) + Lab.carried(b, COBBLE) + Lab.carried(a, COBBLE);
        check(h, total == 64, "pedra: esperado 64, achou " + total);
        check(h, Lab.carried(b, COBBLE) == 64, "B devia ter a mão cheia de pedra: " + Lab.carried(b, COBBLE));

        // A aperta W logo depois: o que sobrou no baú (nada) não pode virar item novo.
        LootAllService.handle(a);
        total = Lab.count(chest, COBBLE) + Lab.carried(b, COBBLE) + Lab.carried(a, COBBLE);
        check(h, total == 64, "depois do W de A, esperado 64, achou " + total);
        lab.cleanup();
        h.succeed();
    }

    /** Jogador que cai (sai da lista do servidor) com o baú aberto não trava o baú para os outros. */
    @GameTest
    public void disconnectedViewerDoesNotLockChest(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(4, 2, 4);
        Lab.fill(chest, 0, COBBLE, 5);
        ServerPlayer a = lab.player(3, 2, 3);
        ServerPlayer b = lab.player(5, 2, 5);
        Lab.open(a, chest, 1);
        h.getLevel().getServer().getPlayerList().remove(a);
        Lab.give(b, 12, COBBLE, 10);

        QuickStackService.handle(b);
        check(h, Lab.count(chest, COBBLE) == 15, "baú devia receber depois que A caiu: " + Lab.count(chest, COBBLE));
        lab.cleanup();
        h.succeed();
    }

    // ---------------------------------------------------------------- containers e limites conhecidos

    /** Baú duplo é UMA fonte (54 slots), não duas. */
    @GameTest
    public void doubleChestIsOneContainer(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container dbl = lab.doubleChest(4, 2, 4);
        Lab.fill(dbl, 0, COBBLE, 10);
        Lab.fill(dbl, 30, COBBLE, 10);
        ServerPlayer a = lab.player(4, 2, 6);
        Lab.prefs(a, 8, true);
        List<?> all = NearbyContainers.findAllStorage(a);
        check(h, all.size() == 1, "baú duplo devia ser 1 container, achou " + all.size());

        Lab.give(a, 12, COBBLE, 30);
        QuickStackService.handle(a);
        check(h, Lab.count(dbl, COBBLE) == 50, "baú duplo devia ter 50, tem " + Lab.count(dbl, COBBLE));
        check(h, Lab.carried(a, COBBLE) == 0, "A devia ter guardado tudo");
        lab.cleanup();
        h.succeed();
    }

    /** Limite conhecido: baú com bloco em cima ainda serve como fonte server-side (documentado). */
    @GameTest
    public void chestWithBlockOnTopStillSource(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(4, 2, 4);
        Lab.fill(chest, 0, COBBLE, 10);
        lab.level.setBlock(h.absolutePos(new BlockPos(4, 3, 4)), Blocks.STONE.defaultBlockState(), 3);
        ServerPlayer a = lab.player(4, 2, 6);
        Lab.prefs(a, 8, true);
        check(h, NearbyContainers.findAllStorage(a).size() == 1, "baú coberto devia continuar achado (comportamento documentado)");
        lab.level.removeBlock(h.absolutePos(new BlockPos(4, 3, 4)), false);
        lab.cleanup();
        h.succeed();
    }

    /** Bug do Item 10.1: a tecla W não pode encher slots que o jogador travou nas preferências dele. */
    @GameTest
    public void lootAllRespectsPlayerLockedSlots(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(4, 2, 4);
        for (int i = 0; i < 10; i++) {
            Lab.fill(chest, i, COBBLE, 64);
        }
        ServerPlayer a = lab.player(3, 2, 3);
        List<Integer> locked = new ArrayList<>();
        for (int s = 9; s < 36; s++) {
            locked.add(s); // trava toda a mochila
        }
        PlayerPrefsStore.set(a.getUUID(), new PlayerPrefs(8, 0, locked));
        Lab.open(a, chest, 1);

        LootAllService.handle(a);
        Inventory inv = a.getInventory();
        for (int s = 9; s < 36; s++) {
            check(h, inv.getItem(s).isEmpty(), "slot travado " + s + " recebeu item");
        }
        check(h, Lab.carried(a, COBBLE) + Lab.count(chest, COBBLE) == 640, "itens duplicaram ou sumiram");
        lab.cleanup();
        h.succeed();
    }

    // ---------------------------------------------------------------- outros mods

    /**
     * Mods de baú de terceiros (aqui: o que estiver carregado, ex. Upgraded Iron Chests): o StashLink só reconhece
     * baú, barril e shulker de vanilla. O teste garante que um container de outro mod por perto não quebra nada
     * nem some com item, e REGISTRA se ele foi visto ou ignorado.
     */
    @GameTest
    public void moddedStorageIsHarmless(GameTestHelper h) {
        Lab lab = new Lab(h);
        ServerPlayer a = lab.player(4, 2, 4);
        Lab.prefs(a, 8, true);
        List<Container> modded = new ArrayList<>();
        List<String> names = new ArrayList<>();
        int n = 0;
        for (net.minecraft.world.level.block.Block block : net.minecraft.core.registries.BuiltInRegistries.BLOCK) {
            String id = String.valueOf(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block));
            if (id.startsWith("minecraft:") || !(block instanceof net.minecraft.world.level.block.EntityBlock)) {
                continue;
            }
            try {
                Container c = lab.block(block, 1 + (n % 7), 2, 1 + (n / 7));
                Lab.fill(c, 0, COBBLE, 10);
                modded.add(c);
                names.add(id);
                n++;
            } catch (RuntimeException e) {
                // bloco sem container (ex.: placa, cabeça): não interessa.
            }
        }
        Lab.give(a, 12, COBBLE, 30);
        int seen = NearbyContainers.findAllStorage(a).size();
        QuickStackService.handle(a);
        int total = Lab.carried(a, COBBLE);
        for (Container c : modded) {
            total += Lab.count(c, COBBLE);
        }
        Constants.LOG.info("[STASHLINK-COMPAT] containers de outros mods achados: {} {} ; vistos pelo StashLink: {}",
                modded.size(), names, seen);
        check(h, total == 30 + 10 * modded.size(), "item duplicou ou sumiu com mod de baú: " + total);
        lab.cleanup();
        h.succeed();
    }

    /** Documenta quais mods de terceiros estavam carregados quando os cenários rodaram. */
    @GameTest
    public void reportLoadedMods(GameTestHelper h) {
        List<String> ids = new ArrayList<>();
        for (String id : new String[] {"carpet", "upgraded_iron_chests", "litematica", "tweakeroo", "malilib"}) {
            if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded(id)) {
                ids.add(id);
            }
        }
        Constants.LOG.info("[STASHLINK-COMPAT] mods de terceiros carregados: {}", ids);
        h.succeed();
    }

    // ---------------------------------------------------------------- carga

    private static final int GRID = 17; // 17 x 17 = 289 containers

    private static long time(Runnable r, int reps, long[] worst) {
        long total = 0;
        for (int i = 0; i < reps; i++) {
            long t0 = System.nanoTime();
            r.run();
            long dt = System.nanoTime() - t0;
            total += dt;
            worst[0] = Math.max(worst[0], dt);
        }
        return total / reps;
    }

    /** 289 containers no raio: mede o custo (ms por operação) de cada coisa que o mod faz. Meta: bem abaixo de 50 ms/tick. */
    @GameTest(maxTicks = 600)
    public void loadWith289Containers(GameTestHelper h) {
        Lab lab = new Lab(h);
        List<Container> all = new ArrayList<>();
        Random rnd = new Random(42);
        for (int x = 0; x < GRID; x++) {
            for (int z = 0; z < GRID; z++) {
                Container c = switch ((x + z) % 3) {
                    case 0 -> lab.chest(x, 2, z);
                    case 1 -> lab.block(Blocks.BARREL, x, 2, z);
                    default -> lab.block(Blocks.SHULKER_BOX, x, 2, z);
                };
                for (int i = 0; i < 12; i++) {
                    c.setItem(rnd.nextInt(c.getContainerSize()), new ItemStack(i % 2 == 0 ? Items.STONE : Items.OAK_LOG, 1 + rnd.nextInt(64)));
                }
                all.add(c);
            }
        }
        // O item procurado fica só no último (pior caso para a ordem "mais perto primeiro").
        Lab.fill(all.get(all.size() - 1), 0, Items.DIAMOND, 64);

        ServerPlayer p = lab.player(GRID / 2.0, 2, GRID / 2.0);
        Lab.prefs(p, 16, true);
        long[] worst = new long[1];
        int reps = 100;

        long find = time(() -> NearbyContainers.find(p), reps, worst);
        long findWorst = worst[0];
        worst[0] = 0;
        long findAll = time(() -> NearbyContainers.findAllStorage(p), reps, worst);
        long findAllWorst = worst[0];

        // Cada repetição usa um jogador novo (o cooldown é por jogador); eles são criados ANTES de medir, para o
        // custo de criar jogador simulado não entrar na conta.
        List<ServerPlayer> pool = new ArrayList<>();
        for (int i = 0; i < 3 * reps; i++) {
            ServerPlayer q = lab.player(GRID / 2.0, 2, GRID / 2.0);
            Lab.prefs(q, 16, true);
            pool.add(q);
        }
        int[] next = {0};

        worst[0] = 0;
        long quick = time(() -> {
            ServerPlayer q = pool.get(next[0]++);
            Lab.give(q, 12, Items.STONE, 20);
            QuickStackService.handle(q);
        }, reps, worst);
        long quickWorst = worst[0];

        // Pior caso do reabastecimento: o item está só no último container, longe, e a mão esvazia.
        worst[0] = 0;
        long refill = time(() -> {
            ServerPlayer q = pool.get(next[0]++);
            int slot = q.getInventory().getSelectedSlot();
            q.getInventory().setItem(slot, new ItemStack(Items.DIAMOND, 1));
            RefillService.tickPlayer(q);
            q.getInventory().setItem(slot, ItemStack.EMPTY);
            RefillService.tickPlayer(q);
            // devolve o diamante ao último container para a próxima repetição achar de novo
            q.getInventory().clearContent();
            Lab.fill(all.get(all.size() - 1), 0, Items.DIAMOND, 64);
        }, reps, worst);
        long refillWorst = worst[0];

        // Pior caso do pedido do Litematica: item que não existe em lugar nenhum (varre tudo e desiste).
        worst[0] = 0;
        long pull = time(() -> {
            ServerPlayer q = pool.get(next[0]++);
            PullItemService.handle(q, new PullItemRequest(Items.NETHERITE_INGOT, 1));
        }, reps, worst);
        long pullWorst = worst[0];

        // Custo parado: mão sem mudança, 289 containers por perto. Não pode varrer nada.
        worst[0] = 0;
        long idle = time(() -> RefillService.tickPlayer(p), 10000, worst);

        String report = String.format(
                "[STASHLINK-PERF] 289 containers, raio 16, %d repeticoes (media/pior, microsegundos): "
                        + "find=%d/%d findAll=%d/%d quickStack(N)=%d/%d refill(pior caso)=%d/%d pull(item ausente)=%d/%d idleTick=%d",
                reps, find / 1000, findWorst / 1000, findAll / 1000, findAllWorst / 1000, quick / 1000, quickWorst / 1000,
                refill / 1000, refillWorst / 1000, pull / 1000, pullWorst / 1000, idle / 1000);
        Constants.LOG.info(report);

        lab.cleanup();
        // Orçamento: uma operação não pode passar de 10% do tick (5 ms); o tick parado, de 0,05 ms.
        long budgetNs = 5_000_000L;
        check(h, find < budgetNs && findAll < budgetNs && quick < budgetNs && refill < budgetNs && pull < budgetNs,
                "alguma operação passou de 5 ms em média: " + report);
        check(h, idle < 50_000L, "tick parado passou de 0,05 ms: " + idle + " ns");
        h.succeed();
    }

    // ---------------------------------------------------------------- fuzz de corrida

    /** Onde cada item está agora (por container e por jogador): muda quando algo é movido. */
    private static String signature(List<Container> boxes, List<ServerPlayer> players, Item[] items) {
        StringBuilder sb = new StringBuilder();
        for (Item it : items) {
            for (Container c : boxes) {
                sb.append(c.countItem(it)).append(',');
            }
            for (ServerPlayer p : players) {
                sb.append(Lab.carried(p, it)).append(',');
            }
        }
        return sb.toString();
    }

    /** Guarda o stack da mão num slot livre da mochila (o jogador "abre espaço"); nada some. */
    private static void putHandAway(ServerPlayer p, int slot) {
        Inventory inv = p.getInventory();
        ItemStack hand = inv.getItem(slot);
        if (hand.isEmpty()) {
            return;
        }
        for (int s = 9; s < 36; s++) {
            if (inv.getItem(s).isEmpty()) {
                inv.setItem(s, hand.copy());
                inv.setItem(slot, ItemStack.EMPTY);
                return;
            }
        }
    }

    /** Ação "à mão" do jogador, sem o mod: leva um stack da mochila para um baú, ou de um baú para a mochila. */
    private static void mix(Random rnd, List<Container> boxes, ServerPlayer p) {
        Inventory inv = p.getInventory();
        Container c = boxes.get(rnd.nextInt(boxes.size()));
        if (rnd.nextBoolean()) {
            int from = 9 + rnd.nextInt(27);
            for (int k = 0; k < 27 && inv.getItem(from).isEmpty(); k++) {
                from = 9 + (from - 9 + 1) % 27; // procura o próximo slot com item
            }
            ItemStack stack = inv.getItem(from);
            for (int s = 0; !stack.isEmpty() && s < c.getContainerSize(); s++) {
                if (c.getItem(s).isEmpty()) {
                    c.setItem(s, stack.copy());
                    inv.setItem(from, ItemStack.EMPTY);
                    return;
                }
            }
        } else {
            int from = rnd.nextInt(c.getContainerSize());
            for (int k = 0; k < c.getContainerSize() && c.getItem(from).isEmpty(); k++) {
                from = (from + 1) % c.getContainerSize();
            }
            ItemStack stack = c.getItem(from);
            for (int s = 9; !stack.isEmpty() && s < 36; s++) {
                if (inv.getItem(s).isEmpty()) {
                    inv.setItem(s, stack.copy());
                    c.setItem(from, ItemStack.EMPTY);
                    return;
                }
            }
        }
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

    /**
     * Dois jogadores fazem N, W, reabastecer, pedir item, abrir/fechar baú e shift-clicar, em ordem aleatória, por
     * 1500 ticks, em volta de 3 baús (um duplo). Depois de CADA ação, a soma de cada item tem de ser a mesma.
     */
    @GameTest(maxTicks = 2000)
    public void raceFuzz(GameTestHelper h) {
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
        Item[] items = {COBBLE, DIRT, Items.OAK_LOG};
        Random rnd = new Random(7);
        Lab.fill(c1, 0, COBBLE, 64);
        Lab.fill(c1, 3, DIRT, 40);
        Lab.fill(c2, 0, COBBLE, 30);
        Lab.fill(dbl, 0, Items.OAK_LOG, 64);
        Lab.fill(dbl, 40, DIRT, 64);
        Lab.give(a, 10, COBBLE, 20);
        Lab.give(a, 11, Items.OAK_LOG, 15);
        Lab.give(b, 10, DIRT, 25);
        Lab.give(b, 0, COBBLE, 5);
        int[] expected = new int[items.length];
        for (int i = 0; i < items.length; i++) {
            expected[i] = totalOf(boxes, players, items[i]);
        }
        List<String> log = new ArrayList<>();
        int[] moves = {0};
        java.util.Map<String, Integer> byAction = new java.util.TreeMap<>();

        for (int tick = 1; tick <= 1500; tick++) {
            final int t = tick;
            h.runAfterDelay(tick, () -> {
                for (int n = 0; n < 2; n++) {
                    ServerPlayer p = rnd.nextBoolean() ? a : b;
                    ServerPlayer other = p == a ? b : a;
                    int action = rnd.nextInt(9);
                    String sigBefore = signature(boxes, players, items);
                    String name;
                    switch (action) {
                        case 0 -> {
                            name = "N";
                            QuickStackService.handle(p);
                        }
                        case 1 -> {
                            name = "W";
                            LootAllService.handle(p);
                        }
                        case 2 -> {
                            name = "abre";
                            if (p.containerMenu == p.inventoryMenu) {
                                Lab.open(p, boxes.get(rnd.nextInt(boxes.size())), 1 + rnd.nextInt(100));
                            }
                        }
                        case 3 -> {
                            name = "fecha";
                            Lab.close(p);
                        }
                        case 4 -> {
                            name = "shift-clique";
                            ChestMenu m = p.containerMenu instanceof ChestMenu cm ? cm : null;
                            if (m != null) {
                                int slot = rnd.nextInt(m.slots.size());
                                m.quickMoveStack(p, slot);
                                m.broadcastChanges();
                            }
                        }
                        case 5 -> {
                            name = "pedir";
                            PullItemService.handle(p, new PullItemRequest(items[rnd.nextInt(items.length)], 1 + rnd.nextInt(64)));
                        }
                        case 6 -> {
                            name = "reabastecer";
                            int slot = p.getInventory().getSelectedSlot();
                            Item it = items[rnd.nextInt(items.length)];
                            if (p.containerMenu == p.inventoryMenu) {
                                putHandAway(p, slot);
                            }
                            if (p.containerMenu == p.inventoryMenu && p.getInventory().getItem(slot).isEmpty()) {
                                p.getInventory().setItem(slot, new ItemStack(it, 1));
                                RefillService.tickPlayer(p);
                                // "Usou" o item: o bloco colocado sai do mundo, e o total não muda (criou +1 e gastou 1).
                                p.getInventory().setItem(slot, ItemStack.EMPTY);
                                RefillService.tickPlayer(p);
                            }
                        }
                        default -> {
                            name = "embaralha";
                            mix(rnd, boxes, p);
                        }
                    }
                    if (!sigBefore.equals(signature(boxes, players, items))) {
                        moves[0]++;
                        byAction.merge(name, 1, Integer::sum);
                    }
                    for (int i = 0; i < items.length; i++) {
                        int now = totalOf(boxes, players, items[i]);
                        if (now != expected[i]) {
                            log.add("tick " + t + " " + (p == a ? "A" : "B") + " " + name);
                            check(h, false, items[i] + " mudou: esperado " + expected[i] + ", achou " + now
                                    + " apos " + String.join(" | ", log.subList(Math.max(0, log.size() - 1), log.size())));
                        }
                    }
                    log.add("tick " + t + " " + (p == a ? "A" : "B") + " " + name);
                }
            });
        }
        h.runAfterDelay(1501, () -> {
            Constants.LOG.info("[STASHLINK-FUZZ] 3000 acoes, {} moveram itens: {}", moves[0], byAction);
            check(h, moves[0] > 300, "o fuzz quase nao moveu item (" + moves[0] + "): teste vazio");
            lab.cleanup();
            h.succeed();
        });
    }
}
