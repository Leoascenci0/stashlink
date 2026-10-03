package io.github.leoascenci0.stashlink.gametest;

import io.github.leoascenci0.stashlink.bench.BenchPullService;
import io.github.leoascenci0.stashlink.bench.BenchSync;
import io.github.leoascenci0.stashlink.config.Feature;
import io.github.leoascenci0.stashlink.config.PlayerPrefs;
import io.github.leoascenci0.stashlink.config.PlayerPrefsStore;
import io.github.leoascenci0.stashlink.config.StashLinkConfig;
import io.github.leoascenci0.stashlink.lootall.LootAllService;
import io.github.leoascenci0.stashlink.network.BenchPoolSync;
import io.github.leoascenci0.stashlink.network.BenchPullRequest;
import io.github.leoascenci0.stashlink.quickstack.QuickStackService;
import io.github.leoascenci0.stashlink.refill.RefillService;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;

/**
 * Bancadas com armazenamento (Item 16). Servidor de verdade, jogadores em SOBREVIVÊNCIA: o livro de receitas e o
 * painel trazem item dos baús do raio; nada duplica nem some, e fornalha/suporte de poções/funil... com item
 * dentro <b>nunca</b> são tocados por reabastecer, N, W, livro de receitas nem painel.
 */
public class BenchGameTests {
    private static final Item PLANKS = Items.OAK_PLANKS;
    private static final Item STICK = Items.STICK;
    private static final Item COBBLE = Items.COBBLESTONE;
    private static final Item LOG = Items.OAK_LOG;
    private static final Item DIRT = Items.DIRT;
    // Itens exclusivos por teste: os GameTests rodam lado a lado no mesmo mundo, e jogadores de um teste enxergam
    // os baús do vizinho. Com item próprio, um teste nunca pega nem guarda o item do outro.
    private static final Item TUFF = Items.TUFF;
    private static final Item CALCITE = Items.CALCITE;
    private static final Item BASALT = Items.BASALT;
    private static final Item FZ_PLANKS = Items.DARK_OAK_PLANKS;
    private static final Item FZ_COBBLE = Items.COBBLED_DEEPSLATE;

    private static void check(GameTestHelper h, boolean ok, String msg) {
        h.assertTrue(ok, msg);
    }

    private static RecipeHolder<?> recipe(ServerLevel level, String path) {
        return level.recipeAccess().byKey(ResourceKey.create(Registries.RECIPE, Identifier.withDefaultNamespace(path)))
                .orElseThrow(() -> new IllegalStateException("receita não achada: " + path));
    }

    /** Bancada de verdade (bloco no mundo), para o menu poder devolver a grade ao fechar e ficar válido por perto. */
    private static CraftingMenu table(Lab lab, ServerPlayer p, int id) {
        CraftingMenu menu = new CraftingMenu(id, p.getInventory(),
                ContainerLevelAccess.create(lab.level, lab.bareAt(Blocks.CRAFTING_TABLE, p.blockPosition().below())));
        p.containerMenu = menu;
        return menu;
    }

    /** O livro de receitas pedindo a receita (o mesmo ponto que o pacote do cliente chama). */
    private static void place(ServerPlayer p, AbstractContainerMenu menu, String recipe, boolean useMax) {
        ((net.minecraft.world.inventory.RecipeBookMenu) menu).handlePlacement(useMax, false,
                recipe(p.level(), recipe), p.level(), p.getInventory());
    }

    /** Itens na grade 3x3 (slots 1..9 do menu da bancada). */
    private static int grid(AbstractContainerMenu menu, Item item) {
        int total = 0;
        for (int i = 1; i <= 9; i++) {
            ItemStack s = menu.slots.get(i).getItem();
            if (s.is(item)) {
                total += s.getCount();
            }
        }
        return total;
    }

    private static void clean(Lab lab, GameTestHelper h) {
        StashLinkConfig.lockedFeatures = EnumSet.noneOf(Feature.class);
        lab.cleanup();
        h.succeed();
    }

    @GameTest
    public void craftsFromStorageOnly(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        ServerPlayer p = lab.player(4, 2, 4);
        Lab.prefs(p, 8, false);                           // "incluir baús" desligado: a bancada não depende disso
        Lab.fill(chest, 0, PLANKS, 2);
        CraftingMenu menu = table(lab, p, 1);
        check(h, menu.stillValid(p), "a bancada devia estar válida: pos=" + p.blockPosition() + " bloco=" + lab.level.getBlockState(p.blockPosition().below()));

        place(p, menu, "stick", false);
        check(h, grid(menu, PLANKS) == 2, "a grade devia ter as 2 tábuas do baú: " + grid(menu, PLANKS));
        check(h, Lab.count(chest, PLANKS) == 0, "o baú devia ter ficado sem tábuas");
        check(h, menu.slots.get(0).getItem().is(STICK) && menu.slots.get(0).getItem().getCount() == 4,
                "o resultado devia ser 4 gravetos: " + menu.slots.get(0).getItem());
        menu.clicked(0, 0, ContainerInput.PICKUP, p);      // pega o resultado
        check(h, p.containerMenu.getCarried().is(STICK) && p.containerMenu.getCarried().getCount() == 4,
                "o cursor devia ter 4 gravetos");
        check(h, Lab.carried(p, PLANKS) + grid(menu, PLANKS) + Lab.count(chest, PLANKS) == 0,
                "as tábuas foram gastas: nenhuma sobra nem some do nada");
        clean(lab, h);
    }

    @GameTest
    public void takesOnlyWhatIsMissing(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        ServerPlayer p = lab.player(4, 2, 4);
        Lab.prefs(p, 8, true);
        Lab.fill(chest, 0, PLANKS, 5);
        Lab.give(p, 20, PLANKS, 1);                        // já tem 1; a receita pede 2
        CraftingMenu menu = table(lab, p, 1);

        place(p, menu, "stick", false);
        check(h, Lab.count(chest, PLANKS) == 4, "só 1 tábua devia sair do baú: sobrou " + Lab.count(chest, PLANKS));
        check(h, grid(menu, PLANKS) == 2, "a grade devia ter 2 tábuas");
        check(h, Lab.carried(p, PLANKS) == 0, "a mochila não devia ficar com sobra");
        check(h, Lab.count(chest, PLANKS) + grid(menu, PLANKS) + Lab.carried(p, PLANKS) == 6, "total de tábuas mudou");
        clean(lab, h);
    }

    @GameTest
    public void bagIsEnoughTouchesNothing(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        ServerPlayer p = lab.player(4, 2, 4);
        Lab.prefs(p, 8, true);
        Lab.fill(chest, 0, PLANKS, 50);
        Lab.give(p, 20, PLANKS, 10);
        CraftingMenu menu = table(lab, p, 1);

        place(p, menu, "stick", false);
        check(h, Lab.count(chest, PLANKS) == 50, "a mochila bastava: o baú não podia ser tocado");
        clean(lab, h);
    }

    @GameTest
    public void respectsRadiusAndOtherPlayersChest(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container far = lab.chest(2, 2, 2);
        Container near = lab.chest(8, 2, 2);
        ServerPlayer p = lab.player(8, 2, 4);
        Lab.prefs(p, 3, true);                             // só enxerga 3 blocos
        Lab.fill(far, 0, PLANKS, 10);                      // a 6+ blocos: fora
        Lab.fill(near, 0, PLANKS, 10);
        ServerPlayer other = lab.player(8, 2, 5);
        ChestMenu seen = Lab.open(other, near, 7);          // outro jogador está olhando o baú perto

        CraftingMenu menu = table(lab, p, 1);
        place(p, menu, "stick", false);
        check(h, grid(menu, PLANKS) == 0, "fora do raio / baú aberto por outro: a grade devia ficar vazia");
        check(h, Lab.count(far, PLANKS) == 10 && Lab.count(near, PLANKS) == 10, "nenhum baú podia ser tocado");

        Lab.close(other);
        seen.broadcastChanges();
        p.containerMenu = p.inventoryMenu;
        menu = table(lab, p, 2);
        place(p, menu, "stick", false);
        check(h, grid(menu, PLANKS) == 2 && Lab.count(near, PLANKS) == 8,
                "liberado o baú, devia puxar do que está no raio");
        check(h, Lab.count(far, PLANKS) == 10, "o baú fora do raio nunca é tocado");
        clean(lab, h);
    }

    /** Baús e barris: teto 16 (mesmo com raio pedido 50). Shulkers colocadas: padrão 32. */
    @GameTest
    public void chestsReach16AndShulkersReach32(GameTestHelper h) {
        Lab lab = new Lab(h);
        Lab.fill(lab.chest(24, 2, 4), 0, Items.AMETHYST_SHARD, 5);                      // a 20 blocos: além dos 16 dos baús
        Lab.fill(lab.block(Blocks.SHULKER_BOX, 34, 2, 4), 0, Items.CLAY_BALL, 5); // a 30 blocos: dentro dos 32 das shulkers
        Lab.fill(lab.block(Blocks.SHULKER_BOX, 44, 2, 4), 0, Items.BRICK, 5); // a 40 blocos: fora
        Lab.fill(lab.chest(10, 2, 4), 0, Items.QUARTZ, 5);                 // a 6 blocos: dentro
        ServerPlayer p = lab.player(4, 2, 4);
        // raio pedido 50 para baús (vira 16) e shulker no padrão do servidor (32)
        PlayerPrefsStore.set(p.getUUID(), new PlayerPrefs(50, 1, List.of()));
        List<BenchPoolSync.Entry> seen = BenchSync.snapshot(p);
        List<Item> items = new ArrayList<>();
        for (BenchPoolSync.Entry e : seen) {
            items.add(e.item().getItem());
        }
        check(h, items.contains(Items.QUARTZ) && items.contains(Items.CLAY_BALL)
                && !items.contains(Items.AMETHYST_SHARD) && !items.contains(Items.BRICK),
                "devia ver o baú a 6 e a shulker a 30, e nem o baú a 20 nem a shulker a 40: " + items);
        clean(lab, h);
    }

    /** Cadeado nos ajustes: trancado, o valor do servidor vale e o pedido pessoal do jogador é ignorado. */
    @GameTest
    public void lockedSettingsUseTheServerValue(GameTestHelper h) {
        Lab lab = new Lab(h);
        ServerPlayer p = lab.player(4, 2, 4);
        PlayerPrefsStore.set(p.getUUID(), new PlayerPrefs(3, 0, List.of(), 0, 5));
        check(h, PlayerPrefsStore.radius(p) == 3 && PlayerPrefsStore.shulkerRadius(p) == 5 && !PlayerPrefsStore.includeChests(p),
                "destrancado: valem as escolhas do jogador");
        StashLinkConfig.setFeatureLocked(Feature.RADIUS, true);
        StashLinkConfig.setFeatureLocked(Feature.SHULKER_RADIUS, true);
        StashLinkConfig.setFeatureLocked(Feature.CHESTS, true);
        check(h, PlayerPrefsStore.radius(p) == StashLinkConfig.effectiveRadius()
                        && PlayerPrefsStore.shulkerRadius(p) == StashLinkConfig.effectiveShulkerRadius()
                        && PlayerPrefsStore.includeChests(p) == StashLinkConfig.includeChests,
                "trancado: vale o valor do servidor, não o do jogador");
        clean(lab, h);
    }

    @GameTest
    public void fullBagLeavesChestsIntact(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        ServerPlayer p = lab.player(4, 2, 4);
        Lab.prefs(p, 8, true);
        Lab.fill(chest, 0, PLANKS, 2);
        // mochila cheia de coisas diferentes e de stack cheio: não cabe nem uma tábua
        Item[] fillers = {DIRT, COBBLE, LOG, Items.SAND, Items.GRAVEL, Items.STONE, Items.GRANITE, Items.DIORITE};
        for (int i = 0; i < 36; i++) {
            p.getInventory().setItem(i, new ItemStack(fillers[i % fillers.length], 64));
        }
        CraftingMenu menu = table(lab, p, 1);
        place(p, menu, "stick", false);
        check(h, Lab.count(chest, PLANKS) + grid(menu, PLANKS) + Lab.carried(p, PLANKS) == 2,
                "sem lugar na mochila, as tábuas não podem sumir nem duplicar: baú=" + Lab.count(chest, PLANKS)
                        + " grade=" + grid(menu, PLANKS) + " mochila=" + Lab.carried(p, PLANKS));
        check(h, Lab.count(chest, PLANKS) == 2, "sem lugar na mochila: devia voltar tudo ao baú");
        clean(lab, h);
    }

    @GameTest
    public void shiftRecipeBuildsTheMaximumAndGivesLeftoversBack(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        Container barrel = lab.block(Blocks.BARREL, 3, 2, 2);
        ServerPlayer p = lab.player(4, 2, 4);
        Lab.prefs(p, 8, true);
        Lab.fill(chest, 0, PLANKS, 64);
        Lab.fill(barrel, 0, PLANKS, 37);
        CraftingMenu menu = table(lab, p, 1);

        place(p, menu, "stick", true);
        int total = Lab.count(chest, PLANKS) + Lab.count(barrel, PLANKS) + grid(menu, PLANKS) + Lab.carried(p, PLANKS);
        check(h, total == 101, "total de tábuas mudou: " + total);
        check(h, grid(menu, PLANKS) >= 2 && grid(menu, PLANKS) % 2 == 0, "devia montar várias receitas: " + grid(menu, PLANKS));
        check(h, Lab.carried(p, PLANKS) == 0, "o que sobrou do que foi trazido devia voltar aos baús");
        clean(lab, h);
    }

    @GameTest
    public void furnaceAndOtherStationsUseStorage(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        ServerPlayer p = lab.player(4, 2, 4);
        Lab.prefs(p, 8, true);
        Lab.fill(chest, 0, LOG, 5);
        FurnaceMenu furnace = new FurnaceMenu(1, p.getInventory());
        p.containerMenu = furnace;

        place(p, furnace, "charcoal", false);              // lenha -> carvão: o livro põe a lenha na entrada
        check(h, furnace.slots.get(0).getItem().is(LOG) && furnace.slots.get(0).getItem().getCount() >= 1,
                "a entrada da fornalha devia ter lenha vinda do baú: " + furnace.slots.get(0).getItem());
        check(h, Lab.count(chest, LOG) + furnace.slots.get(0).getItem().getCount() + Lab.carried(p, LOG) == 5,
                "total de lenha mudou");
        clean(lab, h);
    }

    @GameTest
    public void lockedOrDisabledDoesNothing(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        Lab.fill(chest, 0, PLANKS, 10);

        StashLinkConfig.setFeatureLocked(Feature.BENCH, true);
        ServerPlayer locked = lab.player(4, 2, 4);
        Lab.prefs(locked, 8, true);
        CraftingMenu m1 = table(lab, locked, 1);
        place(locked, m1, "stick", false);
        check(h, grid(m1, PLANKS) == 0 && Lab.count(chest, PLANKS) == 10, "trancada: o livro não podia usar o baú");
        StashLinkConfig.setFeatureLocked(Feature.BENCH, false);

        ServerPlayer off = lab.player(4, 2, 4);
        PlayerPrefsStore.set(off.getUUID(), new PlayerPrefs(8, 1, List.of(), Feature.BENCH.bit()));
        CraftingMenu m2 = table(lab, off, 2);
        place(off, m2, "stick", false);
        check(h, grid(m2, PLANKS) == 0 && Lab.count(chest, PLANKS) == 10, "desligada pelo jogador: nada podia sair do baú");

        ServerPlayer on = lab.player(4, 2, 4);
        Lab.prefs(on, 8, true);
        CraftingMenu m3 = table(lab, on, 3);
        place(on, m3, "stick", false);
        check(h, grid(m3, PLANKS) == 2 && Lab.count(chest, PLANKS) == 8, "ligada e destrancada: devia usar o baú");
        clean(lab, h);
    }

    @GameTest
    public void panelPutsOneStackOnTheCursor(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        Container barrel = lab.block(Blocks.BARREL, 3, 2, 2);
        ServerPlayer p = lab.player(4, 2, 4);
        Lab.prefs(p, 8, true);
        Lab.fill(chest, 0, TUFF, 64);
        Lab.fill(barrel, 0, TUFF, 20);
        StonecutterMenu menu = new StonecutterMenu(5, p.getInventory());
        p.containerMenu = menu;

        BenchPullService.handle(p, new BenchPullRequest(5, new ItemStack(TUFF), false));
        check(h, menu.getCarried().is(TUFF) && menu.getCarried().getCount() == 64,
                "o cursor devia ter 64 pedras: " + menu.getCarried());
        check(h, Lab.count(chest, TUFF) + Lab.count(barrel, TUFF) == 20, "saíram 64 dos containers");

        // cursor cheio do mesmo item: nada muda
        h.runAfterDelay(2, () -> {
            BenchPullService.handle(p, new BenchPullRequest(5, new ItemStack(TUFF), false));
            check(h, menu.getCarried().getCount() == 64 && Lab.count(chest, TUFF) + Lab.count(barrel, TUFF) == 20,
                    "cursor cheio: não pode tirar mais");
            // cursor com outra coisa: ignora
            menu.setCarried(new ItemStack(DIRT, 3));
            h.runAfterDelay(2, () -> {
                BenchPullService.handle(p, new BenchPullRequest(5, new ItemStack(TUFF), false));
                check(h, menu.getCarried().is(DIRT) && menu.getCarried().getCount() == 3
                        && Lab.count(chest, TUFF) + Lab.count(barrel, TUFF) == 20, "cursor ocupado: ignora");
                menu.setCarried(ItemStack.EMPTY);
                h.runAfterDelay(2, () -> {
                    BenchPullService.handle(p, new BenchPullRequest(5, new ItemStack(TUFF), true));
                    check(h, menu.getCarried().is(TUFF) && menu.getCarried().getCount() == 1,
                            "botão direito: só 1");
                    check(h, Lab.count(chest, TUFF) + Lab.count(barrel, TUFF) == 19, "saiu 1");
                    clean(lab, h);
                });
            });
        });
    }

    /** Mundo criativo (o do Eliel): a bancada com armazenamento também vale; só o espectador fica de fora. */
    @GameTest
    public void worksInCreativeToo(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        Lab.fill(chest, 1, COBBLE, 5);                  // sobra algo no baú para a lista do painel
        Lab.fill(chest, 0, PLANKS, 2);
        ServerPlayer p = lab.player(4, 2, 4, net.minecraft.world.level.GameType.CREATIVE);
        Lab.prefs(p, 8, true);
        check(h, p.isCreative(), "o jogador devia estar em criativo");
        CraftingMenu menu = table(lab, p, 1);
        place(p, menu, "stick", false);
        check(h, grid(menu, PLANKS) == 2 && Lab.count(chest, PLANKS) == 0,
                "em criativo o livro também devia trazer do baú: grade=" + grid(menu, PLANKS));
        check(h, !BenchSync.snapshot(p).isEmpty(), "a lista do painel devia existir em criativo");
        clean(lab, h);
    }

    @GameTest
    public void panelRejectsWrongMenuIdLockedAndNonStation(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        Lab.fill(chest, 0, CALCITE, 64);

        ServerPlayer a = lab.player(4, 2, 4);
        Lab.prefs(a, 8, true);
        StonecutterMenu menu = new StonecutterMenu(5, a.getInventory());
        a.containerMenu = menu;
        BenchPullService.handle(a, new BenchPullRequest(99, new ItemStack(CALCITE), false));   // id errado
        check(h, menu.getCarried().isEmpty() && Lab.count(chest, CALCITE) == 64, "id de menu errado: ignora");

        ServerPlayer b = lab.player(4, 2, 4);                // menu de baú: não é estação
        Lab.prefs(b, 8, true);
        ChestMenu chestMenu = Lab.open(b, lab.chest(2, 2, 6), 6);
        BenchPullService.handle(b, new BenchPullRequest(6, new ItemStack(CALCITE), false));
        check(h, chestMenu.getCarried().isEmpty() && Lab.count(chest, CALCITE) == 64, "baú não é estação: ignora");

        ServerPlayer c = lab.player(4, 2, 4);                // sem menu aberto
        Lab.prefs(c, 8, true);
        BenchPullService.handle(c, new BenchPullRequest(0, new ItemStack(CALCITE), false));
        check(h, Lab.count(chest, CALCITE) == 64, "sem estação aberta: ignora");

        StashLinkConfig.setFeatureLocked(Feature.BENCH, true);
        ServerPlayer d = lab.player(4, 2, 4);
        Lab.prefs(d, 8, true);
        StonecutterMenu locked = new StonecutterMenu(8, d.getInventory());
        d.containerMenu = locked;
        BenchPullService.handle(d, new BenchPullRequest(8, new ItemStack(CALCITE), false));
        check(h, locked.getCarried().isEmpty() && Lab.count(chest, CALCITE) == 64, "trancada: ignora");
        clean(lab, h);
    }

    @GameTest
    public void snapshotListsOnlyChestsBarrelsAndShulkers(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        Lab.fill(chest, 0, Items.PRISMARINE_SHARD, 10);   // item exclusivo: outros testes rodam por perto
        Container furnace = lab.block(Blocks.FURNACE, 4, 2, 2);
        Lab.fill(furnace, 0, Items.GOLD_INGOT, 5);
        Container hopper = lab.block(Blocks.HOPPER, 5, 2, 2);
        Lab.fill(hopper, 0, Items.EMERALD, 5);
        ServerPlayer p = lab.player(4, 2, 4);
        Lab.prefs(p, 8, true);

        List<BenchPoolSync.Entry> list = BenchSync.snapshot(p);
        int shards = 0;
        for (BenchPoolSync.Entry e : list) {
            check(h, !e.item().is(Items.GOLD_INGOT) && !e.item().is(Items.EMERALD),
                    "o que está na fornalha ou no funil nunca pode aparecer: " + e.item());
            if (e.item().is(Items.PRISMARINE_SHARD)) {
                shards += e.count();
            }
        }
        check(h, shards == 10, "o baú devia aparecer com 10 fragmentos: " + shards);
        clean(lab, h);
    }

    /** O contrato central do item: o que está dentro de estações nunca é tocado, por nenhuma função do mod. */
    @GameTest
    public void stationsWithItemsInsideAreNeverTouched(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        Lab.fill(chest, 0, BASALT, 10);
        List<Container> stations = new ArrayList<>();
        stations.add(lab.block(Blocks.FURNACE, 4, 2, 2));
        stations.add(lab.block(Blocks.SMOKER, 5, 2, 2));
        stations.add(lab.block(Blocks.BLAST_FURNACE, 6, 2, 2));
        stations.add(lab.block(Blocks.BREWING_STAND, 7, 2, 2));
        stations.add(lab.block(Blocks.HOPPER, 8, 2, 2));
        stations.add(lab.block(Blocks.DISPENSER, 2, 2, 5));
        stations.add(lab.block(Blocks.DROPPER, 3, 2, 5));
        stations.add(lab.block(Blocks.CRAFTER, 4, 2, 5));
        for (Container s : stations) {
            for (int slot = 0; slot < Math.min(s.getContainerSize(), 5); slot++) {
                Lab.fill(s, slot, BASALT, 7 + slot);             // pedra: o item que a N e o reabastecimento procuram
            }
        }
        List<List<ItemStack>> before = new ArrayList<>();
        for (Container s : stations) {
            before.add(contents(s));
        }

        ServerPlayer p = lab.player(5, 2, 4);
        Lab.prefs(p, 16, true);
        Lab.give(p, 12, BASALT, 30);
        Lab.give(p, 13, PLANKS, 4);

        QuickStackService.handle(p);                              // N: só o baú recebe
        check(h, Lab.count(chest, BASALT) == 40, "a N devia guardar no baú comum: " + Lab.count(chest, BASALT));
        p.getInventory().setItem(p.getInventory().getSelectedSlot(), new ItemStack(BASALT, 1));
        RefillService.tickPlayer(p);                              // reabastecer: só do baú
        p.getInventory().setItem(p.getInventory().getSelectedSlot(), ItemStack.EMPTY);
        RefillService.tickPlayer(p);

        // W com cada estação aberta no menu: a W só vale para baú/barril/shulker
        p.containerMenu = new FurnaceMenu(3, p.getInventory(), stations.get(0), new SimpleContainerData(4));
        LootAllService.handle(p);
        p.containerMenu = p.inventoryMenu;

        // livro de receitas e painel
        CraftingMenu table = table(lab, p, 4);
        place(p, table, "stick", true);
        table.removed(p);
        StonecutterMenu cutter = new StonecutterMenu(5, p.getInventory());
        p.containerMenu = cutter;
        BenchPullService.handle(p, new BenchPullRequest(5, new ItemStack(BASALT), false));
        cutter.removed(p);
        p.containerMenu = p.inventoryMenu;

        for (int i = 0; i < stations.size(); i++) {
            check(h, sameContents(before.get(i), contents(stations.get(i))),
                    "a estação #" + i + " foi tocada: antes=" + before.get(i) + " depois=" + contents(stations.get(i)));
        }
        clean(lab, h);
    }

    private static List<ItemStack> contents(Container c) {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < c.getContainerSize(); i++) {
            out.add(c.getItem(i).copy());
        }
        return out;
    }

    private static boolean sameContents(List<ItemStack> a, List<ItemStack> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!ItemStack.matches(a.get(i), b.get(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Fuzz com 2 jogadores: livro de receitas (com e sem shift), painel, fechar a bancada, N e W em sequência. A
     * soma de tábuas e de pedra (baús + mochilas + cursores + grades) tem de ser a mesma depois de CADA ação.
     */
    @GameTest(maxTicks = 800)
    public void benchFuzzKeepsEveryItem(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container c1 = lab.chest(2, 2, 2);
        Container c2 = lab.block(Blocks.BARREL, 6, 2, 2);
        Container dbl = lab.doubleChest(3, 2, 6);
        List<Container> boxes = List.of(c1, c2, dbl);
        ServerPlayer a = lab.player(4, 2, 4);
        ServerPlayer b = lab.player(5, 2, 4);
        List<ServerPlayer> players = List.of(a, b);
        Lab.prefs(a, 12, true);
        Lab.prefs(b, 12, true);
        Lab.fill(c1, 0, FZ_PLANKS, 64);
        Lab.fill(c1, 5, FZ_COBBLE, 40);
        Lab.fill(c2, 0, FZ_PLANKS, 30);
        Lab.fill(dbl, 0, FZ_COBBLE, 64);
        Lab.fill(dbl, 30, FZ_PLANKS, 50);
        Lab.give(a, 10, FZ_PLANKS, 20);
        Lab.give(b, 10, FZ_COBBLE, 25);
        Item[] items = {FZ_PLANKS, FZ_COBBLE};
        int[] expected = new int[items.length];
        for (int i = 0; i < items.length; i++) {
            expected[i] = total(boxes, players, items[i]);
        }
        Random rnd = new Random(16);
        List<String> trail = new ArrayList<>();
        int[] pulls = {0};      // vezes em que o livro ou o painel de fato tiraram tábuas ou pedra dos baús

        for (int tick = 1; tick <= 600; tick++) {
            final int t = tick;
            h.runAfterDelay(tick, () -> {
                for (int n = 0; n < 2; n++) {
                    int inBoxes = total(boxes, List.of(), FZ_PLANKS) + total(boxes, List.of(), FZ_COBBLE);
                    ServerPlayer p = rnd.nextBoolean() ? a : b;
                    int action = rnd.nextInt(12);
                    String name;
                    switch (action) {
                        case 0, 1 -> {
                            name = "livro";
                            if (!(p.containerMenu instanceof CraftingMenu)) {
                                safeClose(p);                    // abrir outra tela fecha a anterior (devolve o cursor)
                                table(lab, p, 1 + rnd.nextInt(100));
                            }
                            place(p, p.containerMenu, rnd.nextBoolean() ? "stick" : "chest", rnd.nextBoolean());
                        }
                        case 2 -> {
                            name = "painel";
                            if (!(p.containerMenu instanceof StonecutterMenu)) {
                                safeClose(p);
                                p.containerMenu = new StonecutterMenu(1 + rnd.nextInt(100), p.getInventory());
                            }
                            BenchPullService.handle(p, new BenchPullRequest(p.containerMenu.containerId,
                                    new ItemStack(rnd.nextBoolean() ? FZ_PLANKS : FZ_COBBLE), rnd.nextBoolean()));
                        }
                        case 3 -> {
                            name = "fecha";
                            safeClose(p);
                        }
                        case 4 -> {
                            name = "N";
                            safeClose(p);
                            QuickStackService.handle(p);
                        }
                        case 5 -> {
                            name = "abre baú";
                            safeClose(p);
                            Lab.open(p, boxes.get(rnd.nextInt(boxes.size())), 1 + rnd.nextInt(100));
                        }
                        case 6 -> {
                            name = "W";
                            LootAllService.handle(p);
                        }
                        case 7 -> {
                            name = "pega resultado";
                            if (p.containerMenu instanceof CraftingMenu m && m.getCarried().isEmpty()) {
                                m.clicked(0, 0, rnd.nextBoolean() ? ContainerInput.PICKUP : ContainerInput.QUICK_MOVE, p);
                            }
                        }
                        case 8, 9 -> {
                            name = "guarda tudo";
                            // esvazia a mochila nos baús (como a N, mas inclusive a hotbar): força o livro e o painel a puxar
                            safeClose(p);
                            for (int s = 0; s < 36; s++) {
                                ItemStack in = p.getInventory().getItem(s);
                                if (in.is(FZ_PLANKS) || in.is(FZ_COBBLE)) {
                                    ItemStack rest = io.github.leoascenci0.stashlink.source.ContainerInsert
                                            .insert(boxes.get(rnd.nextInt(boxes.size())), in);
                                    p.getInventory().setItem(s, rest);
                                }
                            }
                        }
                        default -> {
                            name = "livro de novo";
                            // pedir de novo logo: não pode duplicar nem perder
                            if (p.containerMenu instanceof CraftingMenu) {
                                place(p, p.containerMenu, "stick", false);
                            }
                        }
                    }
                    if ((name.equals("livro") || name.equals("painel")) && total(boxes, List.of(), FZ_PLANKS) + total(boxes, List.of(), FZ_COBBLE) < inBoxes) {
                        pulls[0]++;
                    }
                    trail.add(name + "(tábuas=" + total(boxes, players, FZ_PLANKS) + " gravetos=" + total(boxes, players, STICK)
                            + " baús=" + total(boxes, players, Items.CHEST) + ")");
                    // sticks gastam tábuas: conta tábuas + 0,5*gravetos? Não: conferimos só a pedra (nunca consumida)
                    // e as tábuas COM os gravetos feitos (2 tábuas -> 4 gravetos; "chest" gasta 8 tábuas e faz 1 baú).
                    int cobble = total(boxes, players, FZ_COBBLE);
                    int planks = total(boxes, players, FZ_PLANKS) + 2 * (total(boxes, players, STICK) / 4)
                            + 8 * total(boxes, players, Items.CHEST);
                    // (o baú item consumido da receita não volta; as tábuas dele ficam "na conta" assim)
                    if (cobble != expected[1]) {
                        check(h, false, "tick " + t + " após '" + name + "': pedra " + cobble + " != " + expected[1]
                                + " | " + trail.subList(Math.max(0, trail.size() - 8), trail.size()));
                    }
                    if (planks != expected[0]) {
                        check(h, false, "tick " + t + " após '" + name + "': tábuas " + planks + " != " + expected[0]
                                + " | " + trail.subList(Math.max(0, trail.size() - 8), trail.size()));
                    }
                }
            });
        }
        h.runAfterDelay(620, () -> {
            check(h, pulls[0] >= 50, "o fuzz quase não usou o armazenamento: " + pulls[0] + " retiradas em 1200 ações");
            clean(lab, h);
        });
    }

    private static void safeClose(ServerPlayer p) {
        AbstractContainerMenu m = p.containerMenu;
        if (m != p.inventoryMenu) {
            m.removed(p);
            p.containerMenu = p.inventoryMenu;
        }
    }

    /** Tudo: baús, mochilas, cursores e as grades de bancada abertas. */
    private static int total(List<Container> boxes, List<ServerPlayer> players, Item item) {
        int sum = 0;
        for (Container c : boxes) {
            sum += Lab.count(c, item);
        }
        for (ServerPlayer p : players) {
            sum += Lab.carried(p, item);
            if (p.containerMenu instanceof CraftingMenu m) {
                sum += grid(m, item);
            }
        }
        return sum;
    }
}
