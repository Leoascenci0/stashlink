package io.github.leoascenci0.stashlink.gametest;

import io.github.leoascenci0.stashlink.Constants;
import io.github.leoascenci0.stashlink.bench.BenchPool;
import io.github.leoascenci0.stashlink.bench.BenchSync;
import io.github.leoascenci0.stashlink.config.Feature;
import io.github.leoascenci0.stashlink.config.PlayerPrefs;
import io.github.leoascenci0.stashlink.config.PlayerPrefsStore;
import io.github.leoascenci0.stashlink.quickstack.QuickStackService;
import io.github.leoascenci0.stashlink.refill.RefillService;
import io.github.leoascenci0.stashlink.source.NearbyContainers;
import io.github.leoascenci0.stashlink.source.PlayerSources;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ServerboundPickItemFromBlockPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Item 26: baús e gavetas de outros mods. Os cenários usam a "gaveta" de teste ({@link ApiDrawerBlockEntity}), que só
 * tem a tomada de itens do loader e guarda milhares por slot. São os <b>mesmos</b> nos dois loaders: o Fabric e o
 * NeoForge só os registram. Cada cenário usa itens só dele (os testes rodam lado a lado no mesmo mundo) e confere a
 * soma: nada duplica, nada some.
 */
public final class ModStorageScenarios {
    private ModStorageScenarios() {
    }

    private static void check(GameTestHelper h, boolean ok, String msg) {
        h.assertTrue(ok, msg);
    }

    private static void finish(GameTestHelper h, Lab lab) {
        lab.cleanup();
        h.succeed();
    }

    private static int inHand(ServerPlayer p, Item item) {
        ItemStack held = p.getInventory().getItem(p.getInventory().getSelectedSlot());
        return held.is(item) ? held.getCount() : 0;
    }

    /** Desliga a função de outros mods só para este jogador (raio 8, baús ligados). */
    private static void modStorageOff(ServerPlayer p) {
        PlayerPrefsStore.set(p.getUUID(), new PlayerPrefs(8, 1, List.of(), Feature.MOD_STORAGE.bit(), 8));
    }

    /** N: guarda na gaveta o que ela já tem, até lotar; o resto e a hotbar ficam com o jogador. */
    public static void quickStackStoresIntoTheDrawer(GameTestHelper h) {
        Lab lab = new Lab(h);
        ApiDrawerBlockEntity drawer = lab.apiDrawer(2, 2, 2);
        drawer.put(0, Items.RED_SAND, 9_900);
        drawer.put(1, Items.SMOOTH_SANDSTONE, 1);
        ServerPlayer p = lab.player(4, 2, 4);
        Lab.prefs(p, 8, true);
        Lab.give(p, 0, Items.RED_SAND, 7);                       // hotbar: nunca é esvaziada
        Lab.give(p, 10, Items.RED_SAND, 64);
        Lab.give(p, 11, Items.RED_SAND, 64);
        Lab.give(p, 12, Items.RED_SAND, 64);
        Lab.give(p, 13, Items.SMOOTH_SANDSTONE, 10);
        Lab.give(p, 14, Items.CHERRY_LOG, 5);                    // a gaveta não tem: fica

        QuickStackService.handle(p);

        check(h, drawer.stock(Items.RED_SAND) == ApiDrawerBlockEntity.CAPACITY, "a gaveta devia lotar (10000): " + drawer.stock(Items.RED_SAND));
        check(h, drawer.stock(Items.RED_SAND) + Lab.carried(p, Items.RED_SAND) == 9_900 + 7 + 192, "areia vermelha duplicou ou sumiu");
        check(h, p.getInventory().getItem(0).getCount() == 7, "a hotbar não podia ser esvaziada");
        check(h, drawer.stock(Items.SMOOTH_SANDSTONE) == 11 && Lab.carried(p, Items.SMOOTH_SANDSTONE) == 0, "o arenito devia ir todo");
        check(h, Lab.carried(p, Items.CHERRY_LOG) == 5 && drawer.stock(Items.CHERRY_LOG) == 0, "a gaveta não tinha tronco: não guarda");
        finish(h, lab);
    }

    /** Reabastecer a mão tirando da gaveta (que tem bem mais de 64). */
    public static void refillTakesFromTheDrawer(GameTestHelper h) {
        Lab lab = new Lab(h);
        ApiDrawerBlockEntity drawer = lab.apiDrawer(2, 2, 2);
        drawer.put(0, Items.NETHER_BRICK, 5_000);
        ServerPlayer p = lab.player(4, 2, 4);
        Lab.prefs(p, 8, true);
        int hand = p.getInventory().getSelectedSlot();
        p.getInventory().setItem(hand, new ItemStack(Items.NETHER_BRICK, 1));
        RefillService.tickPlayer(p);                                 // o mod "vê" 1 tijolo na mão
        p.getInventory().setItem(hand, ItemStack.EMPTY);             // usou o último
        RefillService.tickPlayer(p);                                 // esgotou: reabastece

        check(h, inHand(p, Items.NETHER_BRICK) == 64, "a mão devia ter 64, tem " + inHand(p, Items.NETHER_BRICK));
        check(h, drawer.stock(Items.NETHER_BRICK) == 5_000 - 64, "a gaveta devia ter 4936: " + drawer.stock(Items.NETHER_BRICK));
        check(h, drawer.stock(Items.NETHER_BRICK) + Lab.carried(p, Items.NETHER_BRICK) == 5_000, "tijolo duplicou ou sumiu");
        finish(h, lab);
    }

    /** Botão do meio mirando um bloco: o item vem da gaveta para a hotbar (o mesmo pacote que o cliente manda). */
    public static void middleClickPullsFromTheDrawer(GameTestHelper h) {
        Lab lab = new Lab(h);
        ApiDrawerBlockEntity drawer = lab.apiDrawer(2, 2, 2);
        drawer.put(0, Items.HONEYCOMB_BLOCK, 3_000);
        BlockPos target = lab.bare(Blocks.HONEYCOMB_BLOCK, 4, 2, 6);
        ServerPlayer p = lab.player(4, 2, 4);
        Lab.prefs(p, 8, true);
        p.getAbilities().instabuild = false;

        p.connection.handlePickItemFromBlock(new ServerboundPickItemFromBlockPacket(target, false));

        check(h, inHand(p, Items.HONEYCOMB_BLOCK) == 64, "a mão devia ter 64, tem " + inHand(p, Items.HONEYCOMB_BLOCK));
        check(h, drawer.stock(Items.HONEYCOMB_BLOCK) + Lab.carried(p, Items.HONEYCOMB_BLOCK) == 3_000, "favo duplicou ou sumiu");
        finish(h, lab);
    }

    /** Bancada: o painel lista a gaveta (mais de 64), o livro de receitas tira dela e, ao fechar, o resto volta a ela. */
    public static void benchUsesTheDrawerAndGivesBackOnClose(GameTestHelper h) {
        Lab lab = new Lab(h);
        ApiDrawerBlockEntity drawer = lab.apiDrawer(2, 2, 2);
        drawer.put(0, Items.HONEYCOMB, 5_000);
        ServerPlayer p = lab.player(4, 2, 4);
        Lab.prefs(p, 8, true);

        int listed = 0;
        for (BenchPool.Stack s : BenchPool.of(p).contents()) {
            if (s.item().is(Items.HONEYCOMB)) {
                listed += s.count();
            }
        }
        check(h, listed == 5_000, "o painel devia listar 5000 favos da gaveta: " + listed);

        CraftingMenu menu = new CraftingMenu(1, p.getInventory(),
                ContainerLevelAccess.create(lab.level, lab.bareAt(Blocks.CRAFTING_TABLE, p.blockPosition().below())));
        p.containerMenu = menu;
        RecipeHolder<?> recipe = lab.level.recipeAccess()
                .byKey(ResourceKey.create(Registries.RECIPE, Identifier.withDefaultNamespace("honeycomb_block")))
                .orElseThrow(() -> new IllegalStateException("receita não achada"));
        ((RecipeBookMenu) menu).handlePlacement(false, false, recipe, lab.level, p.getInventory());
        int grid = 0;
        for (int i = 1; i <= 9; i++) {
            ItemStack s = menu.slots.get(i).getItem();
            grid += s.is(Items.HONEYCOMB) ? s.getCount() : 0;
        }
        check(h, grid == 4 && drawer.stock(Items.HONEYCOMB) == 4_996, "a grade devia ter 4 favos da gaveta: grade=" + grid
                + " gaveta=" + drawer.stock(Items.HONEYCOMB));

        menu.removed(p);                                     // o jogo devolve a grade à mochila...
        p.containerMenu = p.inventoryMenu;
        BenchSync.tick(lab.level.getServer());               // ...e o mod devolve de lá à gaveta
        check(h, drawer.stock(Items.HONEYCOMB) == 5_000 && Lab.carried(p, Items.HONEYCOMB) == 0,
                "tudo devia voltar à gaveta: gaveta=" + drawer.stock(Items.HONEYCOMB) + " mochila=" + Lab.carried(p, Items.HONEYCOMB));
        finish(h, lab);
    }

    /** Função desligada pelo jogador: N, mão, botão do meio e bancada não enxergam a gaveta. */
    public static void featureOffIgnoresTheDrawer(GameTestHelper h) {
        Lab lab = new Lab(h);
        ApiDrawerBlockEntity drawer = lab.apiDrawer(2, 2, 2);
        drawer.put(0, Items.SNOWBALL, 300);
        BlockPos target = lab.bare(Blocks.SNOW_BLOCK, 4, 2, 6);
        drawer.put(1, Items.SNOW_BLOCK, 300);
        ServerPlayer p = lab.player(4, 2, 4);
        Lab.prefs(p, 8, true);
        p.getAbilities().instabuild = false;
        modStorageOff(p);
        Lab.give(p, 10, Items.SNOWBALL, 16);

        QuickStackService.handle(p);
        check(h, Lab.carried(p, Items.SNOWBALL) == 16 && drawer.stock(Items.SNOWBALL) == 300, "a N não podia guardar na gaveta");
        check(h, PlayerSources.of(p).available(new ItemStack(Items.SNOWBALL)) == 0, "as fontes não podiam ver a gaveta");
        check(h, BenchPool.of(p).contents().stream().noneMatch(s -> s.item().is(Items.SNOWBALL)), "a bancada não podia ver a gaveta");
        p.connection.handlePickItemFromBlock(new ServerboundPickItemFromBlockPacket(target, false));
        check(h, Lab.carried(p, Items.SNOW_BLOCK) == 0 && drawer.stock(Items.SNOW_BLOCK) == 300, "o botão do meio não podia tirar da gaveta");
        finish(h, lab);
    }

    /** Uma tela "de outro mod": tipo desconhecido, como a tela de uma gaveta de verdade (não diz de que bloco é). */
    private static AbstractContainerMenu modScreen(ServerPlayer p) {
        AbstractContainerMenu menu = new AbstractContainerMenu(null, 7) {
            @Override
            public ItemStack quickMoveStack(Player player, int slot) {
                return ItemStack.EMPTY;
            }

            @Override
            public boolean stillValid(Player player) {
                return true;
            }
        };
        p.containerMenu = menu;
        return menu;
    }

    /**
     * Outro jogador com a tela de outro mod aberta perto da gaveta: por segurança, a N e o botão do meio não mexem nela.
     * Com uma tela do jogo (bancada) no mesmo lugar, mexem: essa tela com certeza não é a da gaveta.
     */
    public static void anotherPlayersScreenNearbyKeepsTheDrawerUntouched(GameTestHelper h) {
        Lab lab = new Lab(h);
        ApiDrawerBlockEntity drawer = lab.apiDrawer(2, 2, 2);
        drawer.put(0, Items.DRIED_KELP, 100);
        drawer.put(1, Items.DRIED_KELP_BLOCK, 100);
        BlockPos target = lab.bare(Blocks.DRIED_KELP_BLOCK, 4, 2, 6);
        ServerPlayer a = lab.player(5, 2, 5);
        Lab.prefs(a, 8, true);
        a.getAbilities().instabuild = false;
        ServerPlayer b = lab.player(2, 2, 3);
        Lab.prefs(b, 8, true);
        modScreen(b);                                         // B com a tela "da gaveta", ao lado dela
        Lab.give(a, 10, Items.DRIED_KELP, 20);

        QuickStackService.handle(a);
        check(h, Lab.carried(a, Items.DRIED_KELP) == 20 && drawer.stock(Items.DRIED_KELP) == 100, "com B olhando por perto, a N não guarda");
        a.connection.handlePickItemFromBlock(new ServerboundPickItemFromBlockPacket(target, false));
        check(h, Lab.carried(a, Items.DRIED_KELP_BLOCK) == 0 && drawer.stock(Items.DRIED_KELP_BLOCK) == 100, "nem o botão do meio tira");
        check(h, BenchPool.of(a).contents().stream().noneMatch(s -> s.item().is(Items.DRIED_KELP)), "nem a bancada vê");

        // B troca para uma bancada do jogo: essa tela não é a da gaveta. A N tem um intervalo por jogador: outro jogador.
        b.containerMenu = new CraftingMenu(2, b.getInventory(), ContainerLevelAccess.NULL);
        ServerPlayer c = lab.player(5, 2, 5);
        Lab.prefs(c, 8, true);
        Lab.give(c, 10, Items.DRIED_KELP, 20);
        QuickStackService.handle(c);
        check(h, Lab.carried(c, Items.DRIED_KELP) == 0 && drawer.stock(Items.DRIED_KELP) == 120, "com B na bancada, a N guarda");
        Lab.close(b);
        finish(h, lab);
    }

    /** Baú do jogo nunca conta duas vezes (o loader também o oferece pela tomada de itens, e ele está em c:chests). */
    public static void vanillaChestIsCountedOnce(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(1, 2, 1);
        Lab.fill(chest, 0, Items.QUARTZ, 10);
        ApiDrawerBlockEntity drawer = lab.apiDrawer(2, 2, 2);
        drawer.put(0, Items.QUARTZ, 20);
        ServerPlayer p = lab.player(4, 2, 4);
        Lab.prefs(p, 8, true);

        NearbyContainers.Found found = NearbyContainers.find(p, true);
        BlockPos chestPos = h.absolutePos(new BlockPos(1, 2, 1));
        check(h, found.modStorage().stream().noneMatch(e -> e.where().contains(chestPos)), "o baú do jogo não é bloco de outro mod");
        check(h, found.modStorage().stream().filter(e -> e.where().contains(drawer.getBlockPos())).count() == 1,
                "a gaveta aparece uma vez como bloco de outro mod");
        int listed = 0;
        for (BenchPool.Stack s : BenchPool.of(p).contents()) {
            if (s.item().is(Items.QUARTZ)) {
                listed += s.count();
            }
        }
        check(h, listed == 30, "baú (10) + gaveta (20) = 30, achou " + listed);
        check(h, PlayerSources.of(p).available(new ItemStack(Items.QUARTZ)) == 30, "as fontes deviam ver 30");
        finish(h, lab);
    }

    /** Muitos pedidos aleatórios (N, mão, botão do meio) com gavetas e baús juntos: a soma nunca muda. */
    public static void randomRequestsNeverDuplicateOrLose(GameTestHelper h) {
        Lab lab = new Lab(h);
        Random rnd = new Random(26);
        List<ApiDrawerBlockEntity> drawers = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            ApiDrawerBlockEntity d = lab.apiDrawer(1 + 2 * i, 2, 1);
            d.put(0, Items.GLOWSTONE, rnd.nextInt(3_000));
            drawers.add(d);
        }
        Container chest = lab.chest(1, 2, 6);
        Lab.fill(chest, 0, Items.GLOWSTONE, 64);
        BlockPos target = lab.bare(Blocks.GLOWSTONE, 6, 2, 6);
        long before = 64;
        for (ApiDrawerBlockEntity d : drawers) {
            before += d.stock(Items.GLOWSTONE);
        }
        List<ServerPlayer> players = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            ServerPlayer p = lab.player(4, 2, 4);
            Lab.prefs(p, 8, true);
            p.getAbilities().instabuild = false;
            Lab.give(p, 10 + rnd.nextInt(20), Items.GLOWSTONE, 1 + rnd.nextInt(64));
            before += Lab.carried(p, Items.GLOWSTONE);
            players.add(p);
        }
        for (ServerPlayer p : players) {
            switch (rnd.nextInt(3)) {
                case 0 -> QuickStackService.handle(p);
                case 1 -> p.connection.handlePickItemFromBlock(new ServerboundPickItemFromBlockPacket(target, false));
                default -> {
                    int hand = p.getInventory().getSelectedSlot();
                    p.getInventory().setItem(hand, new ItemStack(Items.GLOWSTONE, 1));
                    RefillService.tickPlayer(p);
                    p.getInventory().setItem(hand, ItemStack.EMPTY);   // pôs 1 e "usou" o mesmo 1: a soma não muda
                    RefillService.tickPlayer(p);
                }
            }
        }
        long after = Lab.count(chest, Items.GLOWSTONE);
        for (ApiDrawerBlockEntity d : drawers) {
            after += d.stock(Items.GLOWSTONE);
        }
        for (ServerPlayer p : players) {
            after += Lab.carried(p, Items.GLOWSTONE);
        }
        check(h, after == before, "pedra luminosa duplicou ou sumiu: antes=" + before + " depois=" + after);
        finish(h, lab);
    }

    /** Custo da varredura com 289 gavetas no raio (só mede; o Item 26 compara com os 289 containers do jogo). */
    public static void loadWith289Drawers(GameTestHelper h) {
        Lab lab = new Lab(h);
        int grid = 17;
        for (int x = 0; x < grid; x++) {
            for (int z = 0; z < grid; z++) {
                ApiDrawerBlockEntity d = lab.apiDrawer(x, 2, z);
                d.put(0, (x + z) % 2 == 0 ? Items.STONE : Items.OAK_LOG, 1_000);
            }
        }
        ServerPlayer p = lab.player(grid / 2.0, 2, grid / 2.0);
        Lab.prefs(p, 32, true);
        int reps = 100;
        long t0 = System.nanoTime();
        for (int i = 0; i < reps; i++) {
            NearbyContainers.find(p);
        }
        long find = (System.nanoTime() - t0) / reps / 1000;
        t0 = System.nanoTime();
        for (int i = 0; i < reps; i++) {
            PlayerSources.of(p).available(new ItemStack(Items.DIAMOND));
        }
        long absent = (System.nanoTime() - t0) / reps / 1000;
        t0 = System.nanoTime();
        for (int i = 0; i < reps; i++) {
            BenchPool.of(p).contents();
        }
        long bench = (System.nanoTime() - t0) / reps / 1000;
        Constants.LOG.info("[STASHLINK-PERF] 289 gavetas de outro mod, raio 32, {} repeticoes (media, microsegundos): "
                + "find={} itemAusente={} benchContents={}", reps, find, absent, bench);
        check(h, NearbyContainers.find(p).modStorage().size() >= grid * grid, "devia achar as 289 gavetas (e as dos testes vizinhos)");
        finish(h, lab);
    }
}
