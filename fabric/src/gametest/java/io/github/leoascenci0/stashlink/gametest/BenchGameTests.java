package io.github.leoascenci0.stashlink.gametest;

import io.github.leoascenci0.stashlink.bench.BenchOrder;
import io.github.leoascenci0.stashlink.bench.BenchPullService;
import io.github.leoascenci0.stashlink.bench.BenchResults;
import io.github.leoascenci0.stashlink.bench.BenchSync;
import io.github.leoascenci0.stashlink.compat.mc.BenchCompat;
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
import net.minecraft.world.inventory.LoomMenu;
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

    /** Cortador de pedra de verdade (bloco no mundo): ao fechar, o jogo devolve a entrada à mochila. */
    private static StonecutterMenu stonecutter(Lab lab, ServerPlayer p, int id) {
        StonecutterMenu menu = new StonecutterMenu(id, p.getInventory(),
                ContainerLevelAccess.create(lab.level, lab.bareAt(Blocks.STONECUTTER, p.blockPosition().below())));
        p.containerMenu = menu;
        return menu;
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
        Lab.prefs(p, 8, true);
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

    /** Baús e barris: teto 16 (mesmo com raio pedido 50). Shulkers nunca entram na bancada, perto ou longe. */
    @GameTest
    public void chestsReach32AndShulkersAreNeverBenchStorage(GameTestHelper h) {
        Lab lab = new Lab(h);
        Lab.fill(lab.chest(24, 2, 4), 0, Items.AMETHYST_SHARD, 5);                      // a 20 blocos: dentro dos 32 (Item 15)
        Lab.fill(lab.chest(44, 2, 8), 0, Items.FLINT, 5);                               // a ~40 blocos: além dos 32 dos baús
        Lab.fill(lab.block(Blocks.SHULKER_BOX, 34, 2, 4), 0, Items.CLAY_BALL, 5); // a 30 blocos: dentro dos 32 das shulkers
        Lab.fill(lab.block(Blocks.SHULKER_BOX, 44, 2, 4), 0, Items.BRICK, 5); // a 40 blocos: fora
        Lab.fill(lab.chest(10, 2, 4), 0, Items.QUARTZ, 5);                 // a 6 blocos: dentro
        ServerPlayer p = lab.player(4, 2, 4);
        // raio pedido 50 para baús (vira 32) e shulker no padrão do servidor (32)
        PlayerPrefsStore.set(p.getUUID(), new PlayerPrefs(50, 1, List.of()));
        check(h, PlayerPrefsStore.radius(p) == 32, "raio pedido 50 devia virar o teto 32: " + PlayerPrefsStore.radius(p));
        List<BenchPoolSync.Entry> seen = BenchSync.snapshot(p);
        List<Item> items = new ArrayList<>();
        for (BenchPoolSync.Entry e : seen) {
            items.add(e.item().getItem());
        }
        check(h, items.contains(Items.QUARTZ) && items.contains(Items.AMETHYST_SHARD) && !items.contains(Items.FLINT)
                && !items.contains(Items.CLAY_BALL) && !items.contains(Items.BRICK),
                "devia ver só os baús a 6 e a 20 (nem o baú a 40, nem shulker nenhuma): " + items);
        // Sem preferência, vale o padrão de 16: o baú a 20 sai.
        PlayerPrefsStore.set(p.getUUID(), new PlayerPrefs(PlayerPrefs.UNSET, 1, List.of()));
        check(h, PlayerPrefsStore.radius(p) == StashLinkConfig.effectiveRadius(), "sem preferência vale o padrão do servidor");
        if (StashLinkConfig.effectiveRadius() < 20) {
            List<Item> near = new ArrayList<>();
            for (BenchPoolSync.Entry e : BenchSync.snapshot(p)) {
                near.add(e.item().getItem());
            }
            check(h, near.contains(Items.QUARTZ) && !near.contains(Items.AMETHYST_SHARD),
                    "no padrão (16) o baú a 20 fica de fora: " + near);
        }
        clean(lab, h);
    }

    /** Cortador de pedra: o painel lista resultados (vermelho se falta material) e clicar monta a receita com o baú. */
    @GameTest
    public void stonecutterPanelListsResultsAndSetsUpTheRecipe(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(8, 2, 4);
        Lab.fill(chest, 0, TUFF, 10);
        ServerPlayer p = lab.player(4, 2, 4);
        Lab.prefs(p, 8, true);
        StonecutterMenu menu = new StonecutterMenu(5, p.getInventory());
        p.containerMenu = menu;
        // Só aparecem receitas de itens que o jogador conheceu: pedra "descoberta" mas sem material vira vermelho.
        p.getStats().setValue(p, net.minecraft.stats.Stats.ITEM_PICKED_UP.get(Items.STONE), 1);

        List<BenchPoolSync.Entry> list = BenchSync.snapshot(p);
        check(h, !list.isEmpty() && list.stream().allMatch(BenchPoolSync.Entry::isResult), "só resultados: " + list.size());
        BenchPoolSync.Entry first = list.get(0);
        check(h, !first.missing(), "com tufo no baú o primeiro resultado é possível");
        check(h, list.stream().anyMatch(BenchPoolSync.Entry::missing), "sem material fica vermelho");

        BenchPullService.handle(p, new BenchPullRequest(5, first.item(), false, first.id()));
        check(h, menu.getSlot(0).getItem().is(TUFF) && menu.getSlot(0).getItem().getCount() == 10,
                "a entrada veio do baú: " + menu.getSlot(0).getItem());
        check(h, Lab.count(chest, TUFF) == 0, "saiu do baú");
        check(h, !menu.getSlot(1).getItem().isEmpty(), "a receita foi escolhida: resultado no slot");
        clean(lab, h);
    }

    /** Tear: um resultado por padrão; clicar põe banner e corante nos slots certos e escolhe o padrão. */
    @GameTest
    public void loomPanelFillsTheRightSlots(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(8, 2, 4);
        Lab.fill(chest, 0, Items.BANNER.pick(net.minecraft.world.item.DyeColor.WHITE), 1);
        Lab.fill(chest, 1, Items.DYE.pick(net.minecraft.world.item.DyeColor.RED), 3);
        Lab.fill(chest, 2, Items.OAK_PLANKS, 9);
        ServerPlayer p = lab.player(4, 2, 4);
        Lab.prefs(p, 8, true);
        LoomMenu menu = new LoomMenu(1, p.getInventory(),
                ContainerLevelAccess.create(lab.level, lab.bareAt(Blocks.LOOM, p.blockPosition().below())));
        p.containerMenu = menu;

        List<BenchPoolSync.Entry> list = BenchSync.snapshot(p);
        BenchPoolSync.Entry red = list.stream().filter(e -> e.isColorPick() && e.color() == net.minecraft.world.item.DyeColor.RED.getId())
                .findFirst().orElse(null);
        check(h, red != null && !red.missing() && red.tab() == 0, "aba Cores: o corante vermelho do baú aparece e está disponível");
        check(h, list.stream().anyMatch(e -> e.isColorPick() && e.missing()) || list.stream().filter(BenchPoolSync.Entry::isColorPick).count() == 1,
                "só as cores conhecidas aparecem");
        BenchPoolSync.Entry first = list.stream().filter(BenchPoolSync.Entry::isResult).findFirst().orElseThrow();
        check(h, !first.missing() && first.tab() == 1 && items(list).stream().noneMatch(i -> i == Items.OAK_PLANKS),
                "padrão sem molde: aba Estandartes, possível");
        BenchPullService.handle(p, new BenchPullRequest(1, red.item(), false, first.id()));
        check(h, menu.getSlot(0).getItem().getItem() instanceof net.minecraft.world.item.BannerItem
                && !menu.getSlot(1).getItem().isEmpty() && menu.getSlot(2).getItem().isEmpty(),
                "banner no slot do banner, corante no do corante");
        check(h, !menu.getSlot(3).getItem().isEmpty(), "padrão escolhido: banner pronto no resultado");
        check(h, Lab.count(chest, Items.OAK_PLANKS) == 9, "tábuas intocadas");
        clean(lab, h);
    }

    /** Mesa de ferraria: abas Enfeites / Armaduras / Ferramentas e armas / Materiais, pelo slot que aceita o item. */
    @GameTest
    public void smithingTabsFollowTheSlots(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(8, 2, 4);
        Lab.fill(chest, 0, Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE, 2);
        Lab.fill(chest, 1, Items.DIAMOND_CHESTPLATE, 1);
        Lab.fill(chest, 2, Items.NETHERITE_INGOT, 3);
        Lab.fill(chest, 3, Items.DIAMOND_SWORD, 1);
        Lab.fill(chest, 4, Items.DIAMOND_PICKAXE, 1);
        ServerPlayer p = lab.player(4, 2, 4);
        Lab.prefs(p, 8, true);
        p.containerMenu = new net.minecraft.world.inventory.SmithingMenu(1, p.getInventory(),
                ContainerLevelAccess.create(lab.level, lab.bareAt(Blocks.SMITHING_TABLE, p.blockPosition().below())));
        List<BenchPoolSync.Entry> list = BenchSync.snapshot(p);
        java.util.Map<Item, Integer> tabs = new java.util.HashMap<>();
        for (BenchPoolSync.Entry e : list) {
            tabs.put(e.item().getItem(), e.tab());
        }
        check(h, tabs.get(Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE) == BenchCompat.SMITHING_TAB_TRIMS
                && tabs.get(Items.DIAMOND_CHESTPLATE) == BenchCompat.SMITHING_TAB_ARMOR
                && tabs.get(Items.DIAMOND_SWORD) == BenchCompat.SMITHING_TAB_TOOLS
                && tabs.get(Items.DIAMOND_PICKAXE) == BenchCompat.SMITHING_TAB_TOOLS
                && tabs.get(Items.NETHERITE_INGOT) == BenchCompat.SMITHING_TAB_MATERIALS, "abas da ferraria: " + tabs);
        clean(lab, h);
    }

    /** Encantamento e suporte de poções: abas pelo slot que aceita o item (lápis, livros, equipamento; garrafa, ingrediente, combustível). */
    @GameTest
    public void enchantingAndBrewingTabsFollowTheSlots(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(8, 2, 4);
        Lab.fill(chest, 0, Items.LAPIS_LAZULI, 5);
        Lab.fill(chest, 1, Items.BOOK, 2);
        Lab.fill(chest, 2, Items.DIAMOND_PICKAXE, 1);
        Lab.fill(chest, 3, Items.NETHER_WART, 3);
        Lab.fill(chest, 4, Items.BLAZE_POWDER, 3);
        ServerPlayer p = lab.player(4, 2, 4);
        Lab.prefs(p, 8, true);
        p.containerMenu = new net.minecraft.world.inventory.EnchantmentMenu(1, p.getInventory(),
                ContainerLevelAccess.create(lab.level, lab.bareAt(Blocks.ENCHANTING_TABLE, p.blockPosition().below())));
        java.util.Map<Item, Integer> tabs = new java.util.HashMap<>();
        for (BenchPoolSync.Entry e : BenchSync.snapshot(p)) {
            tabs.put(e.item().getItem(), e.tab());
        }
        // Lápis automático ligado (padrão): o lápis não aparece no painel; abas Armaduras/Ferramentas/Armas/Livros.
        check(h, !tabs.containsKey(Items.LAPIS_LAZULI) && tabs.get(Items.BOOK) == BenchCompat.GEAR_TAB_BOOKS
                && tabs.get(Items.DIAMOND_PICKAXE) == BenchCompat.GEAR_TAB_TOOLS, "abas do encantamento: " + tabs);
        p.containerMenu = new net.minecraft.world.inventory.BrewingStandMenu(2, p.getInventory());
        tabs.clear();
        for (BenchPoolSync.Entry e : BenchSync.snapshot(p)) {
            tabs.put(e.item().getItem(), e.tab());
        }
        check(h, tabs.get(Items.NETHER_WART) == 1 && tabs.get(Items.BLAZE_POWDER) == 2, "abas das poções: " + tabs);
        clean(lab, h);
    }

    /** Estação sem receita: clicar num item o põe no slot da estação que o aceita (lápis-lazúli no slot do lápis). */
    @GameTest
    public void panelPlacesTheItemInTheRightStationSlot(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(8, 2, 4);
        Lab.fill(chest, 0, Items.LAPIS_LAZULI, 20);
        ServerPlayer p = lab.player(4, 2, 4);
        Lab.prefs(p, 8, true);
        net.minecraft.world.inventory.EnchantmentMenu menu = new net.minecraft.world.inventory.EnchantmentMenu(1, p.getInventory(),
                ContainerLevelAccess.create(lab.level, lab.bareAt(Blocks.ENCHANTING_TABLE, p.blockPosition().below())));
        p.containerMenu = menu;
        BenchPullService.handle(p, new BenchPullRequest(1, new ItemStack(Items.LAPIS_LAZULI), false,
                io.github.leoascenci0.stashlink.bench.BenchResults.PLACE));
        check(h, menu.getSlot(1).getItem().is(Items.LAPIS_LAZULI) && menu.getSlot(1).getItem().getCount() == 20
                && menu.getSlot(0).getItem().isEmpty(), "lápis no slot 1: " + menu.getSlot(1).getItem() + " / " + menu.getSlot(0).getItem());
        check(h, menu.getCarried().isEmpty() && Lab.count(chest, Items.LAPIS_LAZULI) == 0, "cursor vazio, baú esvaziado");
        clean(lab, h);
    }

    /** O painel lista só o que serve na estação aberta: tear (banner/corante), fornalha (fuel/fundível), bancada (tudo). */
    @GameTest
    public void panelListsOnlyWhatTheStationAccepts(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(8, 2, 4);
        Lab.fill(chest, 0, Items.BANNER.pick(net.minecraft.world.item.DyeColor.WHITE), 1);
        Lab.fill(chest, 1, Items.DYE.pick(net.minecraft.world.item.DyeColor.RED), 3);
        Lab.fill(chest, 2, Items.OAK_PLANKS, 9);
        Lab.fill(chest, 3, Items.COAL, 4);
        Lab.fill(chest, 4, Items.RAW_IRON, 4);
        Lab.fill(chest, 5, Items.DIAMOND, 2);
        ServerPlayer p = lab.player(4, 2, 4);
        Lab.prefs(p, 8, true);

        p.containerMenu = new FurnaceMenu(2, p.getInventory());
        List<Item> furnace = items(BenchSync.snapshot(p));
        check(h, furnace.contains(Items.COAL) && furnace.contains(Items.RAW_IRON) && furnace.contains(Items.OAK_PLANKS)
                && !furnace.contains(Items.DYE.pick(net.minecraft.world.item.DyeColor.RED)) && !furnace.contains(Items.DIAMOND),
                "fornalha: combustível e fundível (tábua queima), nada de corante/diamante: " + furnace);

        table(lab, p, 3);
        check(h, items(BenchSync.snapshot(p)).size() == 6, "bancada: mostra tudo");
        clean(lab, h);
    }

    /** Pedido do Eliel (2026-10-05): a mochila também aparece no painel, mesmo com "usar baús" desligado; sai dela primeiro. */
    @GameTest
    public void panelListsTheBackpackAndTakesFromItFirst(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(8, 2, 4);
        Lab.fill(chest, 0, Items.RAW_IRON, 10);
        ServerPlayer p = lab.player(4, 2, 4);
        p.getInventory().setItem(0, new ItemStack(Items.RAW_IRON, 5));
        p.getInventory().setItem(1, new ItemStack(Items.COAL, 3));

        Lab.prefs(p, 8, false);   // baús desligados: só a mochila aparece
        FurnaceMenu menu = new FurnaceMenu(2, p.getInventory());
        p.containerMenu = menu;
        List<BenchPoolSync.Entry> off = BenchSync.snapshot(p);
        check(h, items(off).contains(Items.RAW_IRON) && items(off).contains(Items.COAL)
                && off.stream().filter(e -> e.item().is(Items.RAW_IRON)).findFirst().orElseThrow().count() == 5,
                "baús desligados: a mochila aparece (5 de ferro): " + off);

        Lab.prefs(p, 8, true);
        List<BenchPoolSync.Entry> on = BenchSync.snapshot(p);
        check(h, on.stream().filter(e -> e.item().is(Items.RAW_IRON)).findFirst().orElseThrow().count() == 15,
                "baús ligados: mochila + baú somam 15");

        BenchPullService.handle(p, new BenchPullRequest(2, new ItemStack(Items.RAW_IRON), true,
                io.github.leoascenci0.stashlink.bench.BenchResults.PLACE));
        check(h, menu.getSlot(0).getItem().is(Items.RAW_IRON) && menu.getSlot(0).getItem().getCount() == 1
                && Lab.count(chest, Items.RAW_IRON) == 10, "um de ferro veio da mochila; o baú não foi tocado");
        clean(lab, h);
    }

    /** Relato do Eliel: a ardósia abissal talhada aparecia duas vezes no cortador (sai de mais de uma pedra). */
    @GameTest
    public void stonecutterListsEachResultOnlyOnce(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(8, 2, 4);
        Lab.fill(chest, 0, Items.COBBLED_DEEPSLATE, 10);
        Lab.fill(chest, 1, Items.POLISHED_DEEPSLATE, 10);
        ServerPlayer p = lab.player(4, 2, 4);
        Lab.prefs(p, 8, true);
        StonecutterMenu menu = new StonecutterMenu(5, p.getInventory());
        p.containerMenu = menu;
        for (Item item : new Item[]{Items.COBBLED_DEEPSLATE, Items.POLISHED_DEEPSLATE, Items.CHISELED_DEEPSLATE}) {
            p.getStats().setValue(p, net.minecraft.stats.Stats.ITEM_PICKED_UP.get(item), 1);
        }
        List<BenchPoolSync.Entry> list = BenchSync.snapshot(p);
        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                check(h, !net.minecraft.world.item.ItemStack.isSameItemSameComponents(list.get(i).item(), list.get(j).item()),
                        "resultado repetido na lista: " + list.get(i).item());
            }
        }
        BenchPoolSync.Entry chiseled = list.stream().filter(e -> e.item().is(Items.CHISELED_DEEPSLATE)).findFirst().orElse(null);
        check(h, chiseled != null && !chiseled.missing(), "a ardósia talhada aparece, e é possível");
        BenchPullService.handle(p, new BenchPullRequest(5, chiseled.item(), false, chiseled.id()));
        check(h, !menu.getSlot(0).getItem().isEmpty() && !menu.getSlot(1).getItem().isEmpty(),
                "o clique monta a receita mesmo com duas pedras de origem");
        clean(lab, h);
    }

    /** Defumador só aceita comida e alto-forno só minério/metal, mas os dois queimam combustível como a fornalha. */
    @GameTest
    public void smokerAndBlastFurnacePanelsListFoodOreAndFuel(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(8, 2, 4);
        Lab.fill(chest, 0, Items.COAL, 4);
        Lab.fill(chest, 1, Items.RAW_IRON, 4);
        Lab.fill(chest, 2, Items.PORKCHOP, 4);
        Lab.fill(chest, 3, Items.COBBLESTONE, 4);
        ServerPlayer p = lab.player(4, 2, 4);
        Lab.prefs(p, 8, true);

        p.containerMenu = new net.minecraft.world.inventory.SmokerMenu(2, p.getInventory());
        List<Item> smoker = items(BenchSync.snapshot(p));
        check(h, smoker.contains(Items.PORKCHOP) && smoker.contains(Items.COAL) && !smoker.contains(Items.RAW_IRON)
                && !smoker.contains(Items.COBBLESTONE), "defumador: comida e combustível: " + smoker);

        p.containerMenu = new net.minecraft.world.inventory.BlastFurnaceMenu(3, p.getInventory());
        List<Item> blast = items(BenchSync.snapshot(p));
        check(h, blast.contains(Items.RAW_IRON) && blast.contains(Items.COAL) && !blast.contains(Items.PORKCHOP)
                && !blast.contains(Items.COBBLESTONE), "alto-forno: minério e combustível: " + blast);
        clean(lab, h);
    }

    private static List<Item> items(List<BenchPoolSync.Entry> entries) {
        List<Item> out = new ArrayList<>();
        for (BenchPoolSync.Entry e : entries) {
            out.add(e.item().getItem());
        }
        return out;
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

    /**
     * Fornalha com o painel (Item 16.3): abas Comida / Blocos / Minérios / Combustível pelo que ela funde; clicar
     * põe o combustível no slot de combustível e o resto na entrada. Nada some, nada duplica.
     */
    @GameTest
    public void furnaceTabsPutEachItemInTheRightSlot(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        Lab.fill(chest, 0, Items.COAL, 64);
        Lab.fill(chest, 1, Items.RAW_GOLD, 10);
        Lab.fill(chest, 2, Items.MUTTON, 5);
        Lab.fill(chest, 3, Items.COBBLESTONE, 8);
        Lab.fill(chest, 4, Items.LAVA_BUCKET, 1);
        ServerPlayer p = lab.player(4, 2, 4);
        Lab.prefs(p, 4, true);
        FurnaceMenu furnace = new FurnaceMenu(1, p.getInventory());
        p.containerMenu = furnace;
        java.util.Map<Item, Integer> tabs = new java.util.HashMap<>();
        for (BenchPoolSync.Entry e : BenchSync.snapshot(p)) {
            tabs.put(e.item().getItem(), e.tab());
        }
        check(h, tabs.get(Items.COAL) == BenchCompat.FURNACE_TAB_FUEL && tabs.get(Items.LAVA_BUCKET) == BenchCompat.FURNACE_TAB_FUEL
                && tabs.get(Items.RAW_GOLD) == BenchCompat.FURNACE_TAB_ORES && tabs.get(Items.MUTTON) == BenchCompat.FURNACE_TAB_FOOD
                && tabs.get(Items.COBBLESTONE) == BenchCompat.FURNACE_TAB_BLOCKS, "abas da fornalha: " + tabs);
        BenchPullService.handle(p, new BenchPullRequest(1, new ItemStack(Items.COAL), false, BenchResults.PLACE));
        check(h, furnace.slots.get(1).getItem().is(Items.COAL) && furnace.slots.get(1).getItem().getCount() == 64
                && furnace.slots.get(0).getItem().isEmpty() && Lab.count(chest, Items.COAL) == 0, "carvão no combustível");
        h.runAfterDelay(2, () -> {
            BenchPullService.handle(p, new BenchPullRequest(1, new ItemStack(Items.RAW_GOLD), false, BenchResults.PLACE));
            check(h, furnace.slots.get(0).getItem().is(Items.RAW_GOLD) && furnace.slots.get(0).getItem().getCount() == 10
                    && Lab.count(chest, Items.RAW_GOLD) == 0, "ouro bruto na entrada");
            furnace.slots.get(0).set(ItemStack.EMPTY);
            furnace.slots.get(1).set(ItemStack.EMPTY);
            clean(lab, h);
        });
    }

    /** Defumador: Comida / Combustível; alto-forno: Minérios / Combustível; com a aba Combustível trancada, o carvão não sai. */
    @GameTest
    public void smokerAndBlastFurnaceTabsAndFuelLock(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        Lab.fill(chest, 0, Items.CHARCOAL, 20);
        Lab.fill(chest, 1, Items.RAW_COPPER, 6);
        Lab.fill(chest, 2, Items.RABBIT, 4);
        ServerPlayer p = lab.player(4, 2, 4);
        Lab.prefs(p, 4, true);
        p.containerMenu = new net.minecraft.world.inventory.SmokerMenu(1, p.getInventory());
        java.util.Map<Item, Integer> smoker = new java.util.HashMap<>();
        for (BenchPoolSync.Entry e : BenchSync.snapshot(p)) {
            smoker.put(e.item().getItem(), e.tab());
        }
        check(h, smoker.get(Items.RABBIT) == BenchCompat.SMOKER_TAB_FOOD && smoker.get(Items.CHARCOAL) == BenchCompat.SMOKER_TAB_FUEL
                && !smoker.containsKey(Items.RAW_COPPER), "abas do defumador: " + smoker);
        p.containerMenu = new net.minecraft.world.inventory.BlastFurnaceMenu(2, p.getInventory());
        java.util.Map<Item, Integer> blast = new java.util.HashMap<>();
        for (BenchPoolSync.Entry e : BenchSync.snapshot(p)) {
            blast.put(e.item().getItem(), e.tab());
        }
        check(h, blast.get(Items.RAW_COPPER) == BenchCompat.BLAST_TAB_ORES && blast.get(Items.CHARCOAL) == BenchCompat.BLAST_TAB_FUEL
                && !blast.containsKey(Items.RABBIT), "abas do alto-forno: " + blast);
        // Trancar e pedir no mesmo tick: os testes rodam lado a lado e o clean() dos outros destranca tudo.
        StashLinkConfig.setFeatureLocked(Feature.BENCH_FUEL, true);
        BenchPullService.handle(p, new BenchPullRequest(2, new ItemStack(Items.CHARCOAL), false, BenchResults.PLACE));
        boolean listed = BenchSync.snapshot(p).stream().anyMatch(e -> e.item().is(Items.CHARCOAL));
        StashLinkConfig.setFeatureLocked(Feature.BENCH_FUEL, false);
        check(h, p.containerMenu.slots.get(1).getItem().isEmpty() && Lab.count(chest, Items.CHARCOAL) == 20 && !listed,
                "aba Combustível trancada: nada sai e o carvão não aparece");
        p.containerMenu = p.inventoryMenu;
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

    /** Revisão 1.0: pedido forjado de cursor respeita os mesmos filtros do painel (aqui: aba Combustível trancada). */
    @GameTest
    public void cursorRequestRespectsTheFuelLock(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        ServerPlayer p = lab.player(4, 2, 4);
        Lab.prefs(p, 8, true);
        Lab.fill(chest, 0, Items.COAL, 10);
        FurnaceMenu menu = new FurnaceMenu(1, p.getInventory());
        p.containerMenu = menu;
        // Trancar e pedir no mesmo tick: os testes rodam lado a lado e o clean() dos outros destranca tudo.
        StashLinkConfig.setFeatureLocked(Feature.BENCH_FUEL, true);
        BenchPullService.handle(p, new BenchPullRequest(1, new ItemStack(Items.COAL), false));
        check(h, menu.getCarried().isEmpty() && Lab.count(chest, Items.COAL) == 10,
                "combustível trancado: o carvão não vem ao cursor: " + menu.getCarried());
        p.containerMenu = p.inventoryMenu;
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

    /** Trocou de receita: o que sobrou na grade volta ao baú de origem, nunca para a mochila. */
    @GameTest
    public void switchingRecipeReturnsLeftoversToTheChest(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        Lab.fill(chest, 0, Items.MANGROVE_PLANKS, 5);
        Lab.fill(chest, 1, Items.BONE, 1);
        ServerPlayer p = lab.player(4, 2, 4);
        Lab.prefs(p, 8, true);
        CraftingMenu menu = table(lab, p, 1);

        place(p, menu, "stick", false);                      // 2 tábuas vão para a grade
        check(h, grid(menu, Items.MANGROVE_PLANKS) == 2 && Lab.count(chest, Items.MANGROVE_PLANKS) == 3, "1a receita");
        place(p, menu, "bone_meal", false);                  // outra receita, que não usa tábuas: elas voltam ao baú
        check(h, Lab.carried(p, Items.MANGROVE_PLANKS) == 0, "as tábuas não podiam ir parar na mochila: " + Lab.carried(p, Items.MANGROVE_PLANKS));
        check(h, grid(menu, Items.MANGROVE_PLANKS) == 0 && Lab.count(chest, Items.MANGROVE_PLANKS) == 5,
                "as 5 tábuas devem estar no baú: grade=" + grid(menu, Items.MANGROVE_PLANKS)
                        + " baú=" + Lab.count(chest, Items.MANGROVE_PLANKS));
        check(h, grid(menu, Items.BONE) == 1, "o osso foi para a grade");
        clean(lab, h);
    }

    /** Fechou a bancada sem craftar: o que veio do baú volta para o baú. */
    @GameTest
    public void closingTheBenchReturnsUnusedItemsToTheChest(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        Lab.fill(chest, 0, Items.MANGROVE_PLANKS, 5);
        ServerPlayer p = lab.player(4, 2, 4);
        Lab.prefs(p, 8, true);
        CraftingMenu menu = table(lab, p, 1);
        place(p, menu, "stick", false);
        check(h, Lab.count(chest, Items.MANGROVE_PLANKS) == 3, "saíram 2 tábuas");

        menu.removed(p);                                     // o jogo devolve a grade à mochila...
        p.containerMenu = p.inventoryMenu;
        BenchSync.tick(lab.level.getServer());               // ...e o mod devolve de lá ao baú (passa pelo tick do servidor)
        check(h, Lab.count(chest, Items.MANGROVE_PLANKS) == 5 && Lab.carried(p, Items.MANGROVE_PLANKS) == 0,
                "tudo devia estar de volta no baú: baú=" + Lab.count(chest, Items.MANGROVE_PLANKS)
                        + " mochila=" + Lab.carried(p, Items.MANGROVE_PLANKS));
        clean(lab, h);
    }

    /** Desconectar (ou parar o servidor) com a bancada aberta: o emprestado volta ao baú ANTES de o jogador ser salvo. */
    @GameTest
    public void disconnectWithBorrowedItemsReturnsThemToTheChest(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        Lab.fill(chest, 0, Items.WARPED_PLANKS, 5);
        ServerPlayer p = lab.player(4, 2, 4);
        Lab.prefs(p, 8, true);
        CraftingMenu menu = table(lab, p, 1);
        place(p, menu, "stick", false);
        check(h, Lab.count(chest, Items.WARPED_PLANKS) == 3, "saíram 2 tábuas do baú");

        BenchSync.release(p);                                // o ponto que o logout e a parada do servidor chamam
        check(h, p.containerMenu == p.inventoryMenu, "a estação devia ter sido fechada");
        check(h, Lab.count(chest, Items.WARPED_PLANKS) == 5 && Lab.carried(p, Items.WARPED_PLANKS) == 0,
                "tudo devia estar de volta no baú antes do save: baú=" + Lab.count(chest, Items.WARPED_PLANKS)
                        + " jogador=" + Lab.carried(p, Items.WARPED_PLANKS));
        clean(lab, h);
    }

    /** Caminho real: o Esc fecha a bancada e, antes de o servidor rodar um tick (jogo pausado), o jogador sai do mundo. */
    @GameTest
    public void disconnectRightAfterClosingTheBenchStillReturnsTheItems(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        Lab.fill(chest, 0, Items.CRIMSON_PLANKS, 5);
        ServerPlayer p = lab.player(4, 2, 4);
        Lab.prefs(p, 8, true);
        CraftingMenu menu = table(lab, p, 1);
        place(p, menu, "stick", false);
        check(h, Lab.count(chest, Items.CRIMSON_PLANKS) == 3, "saíram 2 tábuas do baú");

        p.doCloseContainer();                                // o pacote de fechar do cliente: o emprestado já volta ao baú na hora
        check(h, Lab.count(chest, Items.CRIMSON_PLANKS) == 5 && Lab.carried(p, Items.CRIMSON_PLANKS) == 0,
                "ao fechar, as 2 tábuas já estão no baú: baú=" + Lab.count(chest, Items.CRIMSON_PLANKS));
        System.gc();
        BenchSync.release(p);                                // logout, sem nenhum tick entre os dois
        check(h, Lab.count(chest, Items.CRIMSON_PLANKS) == 5 && Lab.carried(p, Items.CRIMSON_PLANKS) == 0,
                "tudo devia estar de volta no baú: baú=" + Lab.count(chest, Items.CRIMSON_PLANKS)
                        + " jogador=" + Lab.carried(p, Items.CRIMSON_PLANKS));
        clean(lab, h);
    }

    /** Muitas shulkers cheias e diferentes: o pacote da lista nunca passa do limite do protocolo (1 MiB), senão derruba o cliente. */
    @GameTest
    public void hugeSnapshotStaysUnderThePacketLimit(GameTestHelper h) {
        Lab lab = new Lab(h);
        for (int c = 0; c < 4; c++) {          // 4 baús x 27 shulkers: ~1,4 MB se fosse tudo no pacote
        Container chest = lab.chest(2, 2, 2 + c);
        for (int i = 0; i < 27; i++) {
            List<ItemStack> mid = new ArrayList<>();
            for (int j = 0; j < 27; j++) {
                List<ItemStack> inner = new ArrayList<>();
                for (int k = 0; k < 27; k++) {
                    ItemStack named = new ItemStack(Items.STONE_BUTTON);
                    named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                            net.minecraft.network.chat.Component.literal("n" + c + "_" + i + "_" + j + "_" + k));
                    inner.add(named);
                }
                ItemStack box = new ItemStack(Items.SHULKER_BOX);
                box.set(net.minecraft.core.component.DataComponents.CONTAINER,
                        net.minecraft.world.item.component.ItemContainerContents.fromItems(inner));
                mid.add(box);
            }
            ItemStack top = new ItemStack(Items.SHULKER_BOX);
            top.set(net.minecraft.core.component.DataComponents.CONTAINER,
                    net.minecraft.world.item.component.ItemContainerContents.fromItems(mid));
            chest.setItem(i, top);
        }
        }
        ServerPlayer p = lab.player(4, 2, 4);
        Lab.prefs(p, 8, true);
        CraftingMenu menu = table(lab, p, 1);

        List<BenchPoolSync.Entry> list = BenchSync.snapshot(p);
        net.minecraft.network.RegistryFriendlyByteBuf buf =
                new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), p.registryAccess());
        BenchPoolSync.STREAM_CODEC.encode(buf, new BenchPoolSync(menu.containerId, list));
        int size = buf.readableBytes();
        check(h, !list.isEmpty(), "a lista não pode vir vazia");
        check(h, size < 1_000_000, "o pacote passou do limite do protocolo: " + size + " bytes");
        clean(lab, h);
    }

    /** Pegou outro item no painel: o anterior (do cursor) volta ao baú de origem. */
    @GameTest
    public void panelSwapReturnsTheCursorItemToItsChest(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        Lab.fill(chest, 0, Items.AMETHYST_BLOCK, 64);
        Lab.fill(chest, 1, Items.LAPIS_BLOCK, 64);
        ServerPlayer p = lab.player(4, 2, 4);
        Lab.prefs(p, 8, true);
        StonecutterMenu menu = new StonecutterMenu(5, p.getInventory());
        p.containerMenu = menu;

        BenchPullService.handle(p, new BenchPullRequest(5, new ItemStack(Items.AMETHYST_BLOCK), false));
        check(h, menu.getCarried().is(Items.AMETHYST_BLOCK) && Lab.count(chest, Items.AMETHYST_BLOCK) == 0, "pegou ametista");
        h.runAfterDelay(2, () -> {
            BenchPullService.handle(p, new BenchPullRequest(5, new ItemStack(Items.LAPIS_BLOCK), false));
            check(h, menu.getCarried().is(Items.LAPIS_BLOCK) && menu.getCarried().getCount() == 64, "cursor com cobre");
            check(h, Lab.count(chest, Items.AMETHYST_BLOCK) == 64 && Lab.carried(p, Items.AMETHYST_BLOCK) == 0,
                    "a ametista devia ter voltado ao baú, não à mochila: baú=" + Lab.count(chest, Items.AMETHYST_BLOCK)
                            + " mochila=" + Lab.carried(p, Items.AMETHYST_BLOCK));
            clean(lab, h);
        });
    }

    /** Dois jogadores, os mesmos 2 itens: quem clicou primeiro fica com eles; o outro só os vê quando forem devolvidos. */
    @GameTest
    public void twoPlayersNeverShareTheSameItems(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        Lab.fill(chest, 0, Items.MANGROVE_PLANKS, 2);
        ServerPlayer a = lab.player(4, 2, 4);
        ServerPlayer b = lab.player(6, 2, 4);
        Lab.prefs(a, 8, true);
        Lab.prefs(b, 8, true);
        CraftingMenu ma = table(lab, a, 1);
        CraftingMenu mb = table(lab, b, 2);

        place(a, ma, "stick", false);
        place(b, mb, "stick", false);                        // B chega depois: já não há o que prometer
        check(h, grid(ma, Items.MANGROVE_PLANKS) == 2 && grid(mb, Items.MANGROVE_PLANKS) == 0
                && Lab.count(chest, Items.MANGROVE_PLANKS) == 0, "o primeiro a clicar fica com os 2 itens");
        check(h, Lab.carried(a, Items.MANGROVE_PLANKS) + Lab.carried(b, Items.MANGROVE_PLANKS) == 0, "nada solto nas mochilas");

        ma.removed(a);                                       // A desiste: os itens voltam ao baú
        a.containerMenu = a.inventoryMenu;
        BenchSync.tick(lab.level.getServer());
        check(h, Lab.count(chest, Items.MANGROVE_PLANKS) == 2, "devolvidos ao baú");
        place(b, mb, "stick", false);                        // agora B consegue
        check(h, grid(mb, Items.MANGROVE_PLANKS) == 2 && Lab.count(chest, Items.MANGROVE_PLANKS) == 0,
                "B pega os itens devolvidos");
        clean(lab, h);
    }

    /** "Usar baús como fonte: Não" vale também para a bancada. */
    @GameTest
    public void chestsOffBlocksTheBench(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        Lab.fill(chest, 0, Items.MANGROVE_PLANKS, 4);
        ServerPlayer p = lab.player(4, 2, 4);
        Lab.prefs(p, 8, false);
        CraftingMenu menu = table(lab, p, 1);
        place(p, menu, "stick", false);
        check(h, grid(menu, Items.MANGROVE_PLANKS) == 0 && Lab.count(chest, Items.MANGROVE_PLANKS) == 4,
                "com 'usar baús' em Não, a bancada não pode usar o baú");
        check(h, BenchSync.snapshot(p).stream().noneMatch(e -> e.item().is(Items.MANGROVE_PLANKS)), "nem listar no painel");
        Lab.prefs(p, 8, true);
        place(p, menu, "stick", false);
        check(h, grid(menu, Items.MANGROVE_PLANKS) == 2, "ligado, volta a funcionar");
        clean(lab, h);
    }

    /** Shulker, no inventário ou colocada, nunca é armazenamento da bancada (só baús e barris). */
    @GameTest
    public void shulkersNeverServeTheBench(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container placedBox = lab.block(Blocks.SHULKER_BOX, 2, 2, 2);
        Lab.fill(placedBox, 0, Items.MANGROVE_PLANKS, 8);
        ServerPlayer p = lab.player(4, 2, 4);
        Lab.prefs(p, 8, true);
        ItemStack carriedBox = new ItemStack(Items.SHULKER_BOX);
        io.github.leoascenci0.stashlink.storage.ShulkerStorage.write(carriedBox,
                List.of(new ItemStack(Items.MANGROVE_PLANKS, 8)));
        p.getInventory().setItem(20, carriedBox);
        CraftingMenu menu = table(lab, p, 1);

        place(p, menu, "stick", false);
        check(h, grid(menu, Items.MANGROVE_PLANKS) == 0 && Lab.count(placedBox, Items.MANGROVE_PLANKS) == 8,
                "a shulker colocada não serve à bancada");
        check(h, io.github.leoascenci0.stashlink.storage.ShulkerStorage.count(p.getInventory().getItem(20),
                s -> s.is(Items.MANGROVE_PLANKS)) == 8, "a shulker do inventário também não");
        check(h, BenchSync.snapshot(p).stream().noneMatch(e -> e.item().is(Items.MANGROVE_PLANKS)), "e nada no painel");
        clean(lab, h);
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

    /**
     * Fornalha: o emprestado que ficou no bloco é do jogador. Ao fechar, as lenhas PRÓPRIAS da mochila não podem ir
     * para o baú (troca de dono); o que ainda estava no cursor ao fechar continua voltando ao baú.
     */
    @GameTest
    public void furnaceKeepsBorrowedItemAndNeverTakesTheOwnersOwn(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        Lab.fill(chest, 0, Items.JUNGLE_LOG, 5);
        ServerPlayer p = lab.player(4, 2, 4);
        Lab.prefs(p, 8, true);
        p.getInventory().add(new ItemStack(Items.JUNGLE_LOG, 20));
        FurnaceMenu furnace = new FurnaceMenu(1, p.getInventory());
        p.containerMenu = furnace;

        BenchPullService.handle(p, new BenchPullRequest(1, new ItemStack(Items.JUNGLE_LOG), false, -2));   // painel: pôr na entrada
        // Mochila primeiro (2026-10-05): as 20 próprias vão antes; o baú só completa (5).
        check(h, furnace.getSlot(0).getItem().is(Items.JUNGLE_LOG) && furnace.getSlot(0).getItem().getCount() == 25
                && Lab.count(chest, Items.JUNGLE_LOG) == 0,
                "mochila primeiro e baú completa: " + furnace.getSlot(0).getItem());
        furnace.removed(p);
        p.containerMenu = p.inventoryMenu;
        BenchSync.tick(lab.level.getServer());
        check(h, Lab.carried(p, Items.JUNGLE_LOG) == 0 && Lab.count(chest, Items.JUNGLE_LOG) == 0,
                "nada volta ao baú e nada some: mochila=" + Lab.carried(p, Items.JUNGLE_LOG)
                        + " baú=" + Lab.count(chest, Items.JUNGLE_LOG));
        check(h, furnace.getSlot(0).getItem().getCount() == 25, "tudo ficou na fornalha");
        clean(lab, h);
    }

    /** Fornalha, item ainda no cursor ao fechar: esse nunca foi para o bloco, então volta ao baú. */
    @GameTest
    public void furnaceCursorItemStillReturnsToTheChest(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        Lab.fill(chest, 0, Items.ACACIA_LOG, 5);
        ServerPlayer p = lab.player(4, 2, 4);
        Lab.prefs(p, 8, true);
        p.getInventory().add(new ItemStack(Items.ACACIA_LOG, 20));
        FurnaceMenu furnace = new FurnaceMenu(1, p.getInventory());
        p.containerMenu = furnace;

        BenchPullService.handle(p, new BenchPullRequest(1, new ItemStack(Items.ACACIA_LOG), false));   // painel: pega no cursor
        check(h, furnace.getCarried().is(Items.ACACIA_LOG), "a lenha foi para o cursor");
        furnace.removed(p);
        p.containerMenu = p.inventoryMenu;
        BenchSync.tick(lab.level.getServer());
        check(h, Lab.carried(p, Items.ACACIA_LOG) == 20 && Lab.count(chest, Items.ACACIA_LOG) == 5,
                "o cursor volta ao baú e as 20 próprias ficam: mochila=" + Lab.carried(p, Items.ACACIA_LOG)
                        + " baú=" + Lab.count(chest, Items.ACACIA_LOG));
        clean(lab, h);
    }

    /** O snapshot do cliente pode estar velho: o pedido carrega a identidade da receita, não a posição na lista. */
    @GameTest
    public void staleRecipeListNeverBuildsTheWrongRecipe(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(8, 2, 4);
        Lab.fill(chest, 0, TUFF, 10);
        ServerPlayer p = lab.player(4, 2, 4);
        Lab.prefs(p, 8, true);
        StonecutterMenu menu = new StonecutterMenu(5, p.getInventory());
        p.containerMenu = menu;

        List<BenchPoolSync.Entry> stale = BenchSync.snapshot(p);                    // o que o cliente tem
        BenchPoolSync.Entry wanted = stale.stream().filter(e -> e.isResult() && !e.missing())
                .reduce((a, b) -> b).orElseThrow();                                 // o último possível
        Lab.fill(chest, 1, Items.BLACKSTONE, 10);                                   // a lista muda antes do clique
        Lab.fill(chest, 2, Items.GRANITE, 10);
        Lab.fill(chest, 3, Items.ANDESITE, 10);

        BenchPullService.handle(p, new BenchPullRequest(5, wanted.item(), false, wanted.id()));
        check(h, menu.getSlot(0).getItem().is(TUFF), "a entrada é a da receita pedida, não a de outra: " + menu.getSlot(0).getItem());
        check(h, menu.getSlot(1).getItem().is(wanted.item().getItem()),
                "o resultado é o pedido: " + menu.getSlot(1).getItem() + " esperado " + wanted.item());

        // Receita que sumiu da lista (ninguém tem mais material nem conhece): não monta nada.
        menu.removed(p);
        p.containerMenu = new StonecutterMenu(6, p.getInventory());
        chest.clearContent();
        BenchPullService.handle(p, new BenchPullRequest(6, wanted.item(), false, wanted.id()));
        check(h, p.containerMenu.getSlot(0).getItem().isEmpty(), "receita que sumiu não monta nada");
        clean(lab, h);
    }

    /** Craftar e fechar no mesmo tick: o caderno refaz a conta antes de assentar e nunca leva item próprio ao baú. */
    @GameTest
    public void craftAndCloseInTheSameTickNeverTakesOwnItems(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        Lab.fill(chest, 0, Items.CRIMSON_PLANKS, 5);
        ServerPlayer p = lab.player(4, 2, 4);
        Lab.prefs(p, 8, true);
        CraftingMenu menu = table(lab, p, 1);
        place(p, menu, "stick", false);                      // 2 tábuas do baú vão para a grade
        check(h, Lab.count(chest, Items.CRIMSON_PLANKS) == 3, "saíram 2 tábuas");

        for (int i = 1; i <= 9; i++) {                       // o jogador craftou: a grade foi gasta...
            menu.slots.get(i).set(ItemStack.EMPTY);
        }
        p.getInventory().add(new ItemStack(Items.CRIMSON_PLANKS, 5));   // ...e ele já tinha 5 tábuas próprias
        menu.removed(p);                                     // ...e fechou antes do próximo tick
        p.containerMenu = p.inventoryMenu;
        BenchSync.tick(lab.level.getServer());
        check(h, Lab.carried(p, Items.CRIMSON_PLANKS) == 5 && Lab.count(chest, Items.CRIMSON_PLANKS) == 3,
                "as 5 próprias ficam e o baú segue com 3: mochila=" + Lab.carried(p, Items.CRIMSON_PLANKS)
                        + " baú=" + Lab.count(chest, Items.CRIMSON_PLANKS));
        clean(lab, h);
    }

    // ---- Item 16.3, onda 3 (achados baixos) ----

    /** Item 8: clicar de novo na mesma receita não puxa mais nada, e o que foi emprestado volta inteiro ao fechar. */
    @GameTest(maxTicks = 80)
    public void repeatedClickOnTheSameRecipeNeverStacksOrDuplicates(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(8, 2, 4);
        Lab.fill(chest, 0, Items.DEEPSLATE, 10);
        Lab.fill(chest, 1, Items.TUFF_BRICKS, 10);
        ServerPlayer p = lab.player(4, 2, 4);
        Lab.prefs(p, 8, true);
        StonecutterMenu menu = stonecutter(lab, p, 5);
        BenchPoolSync.Entry wanted = BenchSync.snapshot(p).stream().filter(e -> e.isResult() && !e.missing())
                .findFirst().orElseThrow();
        BenchPullRequest click = new BenchPullRequest(5, wanted.item(), false, wanted.id());

        BenchPullService.handle(p, click);
        BenchPullService.handle(p, click);                   // no mesmo tick: o servidor só atende um
        check(h, menu.getSlot(0).getItem().is(Items.DEEPSLATE) && menu.getSlot(0).getItem().getCount() == 10
                && Lab.count(chest, Items.DEEPSLATE) == 0, "uma montagem só: slot=" + menu.getSlot(0).getItem());
        h.runAfterDelay(2, () -> {
            BenchPullService.handle(p, click);               // ticks seguintes: já está montada, nada novo sai
            check(h, menu.getSlot(0).getItem().getCount() == 10 && Lab.carried(p, Items.DEEPSLATE) == 0,
                    "montar de novo não acumula: " + menu.getSlot(0).getItem());
            h.runAfterDelay(2, () -> {
                BenchPullService.handle(p, new BenchPullRequest(5, new ItemStack(Items.DEEPSLATE), true, BenchResults.PLACE));
                BenchPullService.handle(p, new BenchPullRequest(5, new ItemStack(Items.DEEPSLATE), true, BenchResults.PLACE));
                check(h, menu.getSlot(0).getItem().getCount() == 10 && Lab.count(chest, Items.DEEPSLATE) == 0,
                        "slot cheio: pedido repetido não passa do máximo nem duplica");
                h.runAfterDelay(2, () -> {
                    BenchPullService.handle(p, new BenchPullRequest(5, new ItemStack(Items.TUFF_BRICKS), true));
                    BenchPullService.handle(p, new BenchPullRequest(5, new ItemStack(Items.TUFF_BRICKS), true));
                    check(h, menu.getCarried().getCount() == 1, "dois pedidos no mesmo tick: só um é atendido: " + menu.getCarried());
                    menu.removed(p);
                    p.containerMenu = p.inventoryMenu;
                    BenchSync.tick(lab.level.getServer());
                    check(h, Lab.count(chest, Items.TUFF_BRICKS) == 10 && Lab.carried(p, Items.TUFF_BRICKS) == 0,
                            "o item do cursor também volta ao baú: " + Lab.count(chest, Items.TUFF_BRICKS));
                    check(h, Lab.count(chest, Items.DEEPSLATE) == 10 && Lab.carried(p, Items.DEEPSLATE) == 0,
                        "ao fechar volta tudo, sem sobra nem falta: baú=" + Lab.count(chest, Items.DEEPSLATE)
                                + " mochila=" + Lab.carried(p, Items.DEEPSLATE));
                    clean(lab, h);
                });
            });
        });
    }

    /** Item 9: baú de origem cheio na hora de devolver: o item do cursor não fica preso, vai para a mochila. */
    @GameTest(maxTicks = 60)
    public void fullChestNeverLeavesTheCursorStuck(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(8, 2, 4);
        Lab.fill(chest, 0, Items.TUFF, 64);
        Lab.fill(chest, 1, Items.CALCITE, 64);
        ServerPlayer p = lab.player(4, 2, 4);
        Lab.prefs(p, 8, true);
        StonecutterMenu menu = new StonecutterMenu(5, p.getInventory());
        p.containerMenu = menu;

        BenchPullService.handle(p, new BenchPullRequest(5, new ItemStack(Items.TUFF), false));
        check(h, menu.getCarried().is(Items.TUFF) && menu.getCarried().getCount() == 64, "tufo no cursor");
        for (int i = 0; i < chest.getContainerSize(); i++) {          // enche o baú: não sobra espaço para devolver
            if (chest.getItem(i).isEmpty()) {
                Lab.fill(chest, i, Items.DIRT, 64);
            }
        }
        h.runAfterDelay(2, () -> {
            BenchPullService.handle(p, new BenchPullRequest(5, new ItemStack(Items.CALCITE), false));
            check(h, menu.getCarried().is(Items.CALCITE) && menu.getCarried().getCount() == 64,
                    "o cursor devia trocar para calcita, não ficar preso no tufo: " + menu.getCarried());
            check(h, Lab.carried(p, Items.TUFF) == 64 && Lab.count(chest, Items.TUFF) == 0,
                    "o tufo que não coube no baú foi para a mochila, sem sumir: " + Lab.carried(p, Items.TUFF));
            clean(lab, h);
        });
    }

    /** Item 10: o livro de receitas não atende rajada de pedidos no mesmo tick (cada um varreria os baús). */
    @GameTest
    public void recipeBookFloodIsCappedPerTick(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        Lab.fill(chest, 0, Items.PALE_OAK_PLANKS, 5);
        Lab.fill(chest, 1, Items.IRON_INGOT, 1);
        ServerPlayer p = lab.player(4, 2, 4);
        Lab.prefs(p, 8, true);
        CraftingMenu menu = table(lab, p, 1);

        place(p, menu, "stick", false);
        place(p, menu, "iron_nugget", false);
        place(p, menu, "stick", false);
        place(p, menu, "iron_nugget", false);                // 4 pedidos no tick: o limite
        int planksBefore = Lab.count(chest, Items.PALE_OAK_PLANKS);
        int ingotsBefore = Lab.count(chest, Items.IRON_INGOT);
        place(p, menu, "stick", false);                      // o 5º no mesmo tick é barrado
        check(h, Lab.count(chest, Items.PALE_OAK_PLANKS) == planksBefore && Lab.count(chest, Items.IRON_INGOT) == ingotsBefore,
                "o 5o pedido do tick não pode mexer nos baús: tábuas=" + Lab.count(chest, Items.PALE_OAK_PLANKS));
        check(h, Lab.count(chest, Items.PALE_OAK_PLANKS) + grid(menu, Items.PALE_OAK_PLANKS) + Lab.carried(p, Items.PALE_OAK_PLANKS) == 5
                        && Lab.count(chest, Items.IRON_INGOT) + grid(menu, Items.IRON_INGOT) + Lab.carried(p, Items.IRON_INGOT) == 1,
                "nada some nem duplica");
        clean(lab, h);
    }

    /** Item 11: o servidor só aceita os tipos de pedido que o cliente de verdade manda; o resto é ignorado. */
    @GameTest
    public void invalidRecipeIdsAreIgnored(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        Lab.fill(chest, 0, Items.BASALT, 64);
        int[] bad = {BenchPoolSync.Entry.COLOR_PICK, -4, -999, Integer.MIN_VALUE};
        for (int i = 0; i < bad.length; i++) {
            ServerPlayer p = lab.player(4, 2, 4);
            Lab.prefs(p, 8, true);
            StonecutterMenu menu = new StonecutterMenu(10 + i, p.getInventory());
            p.containerMenu = menu;
            BenchPullService.handle(p, new BenchPullRequest(10 + i, new ItemStack(Items.BASALT), false, bad[i]));
            check(h, menu.getCarried().isEmpty() && Lab.count(chest, Items.BASALT) == 64,
                    "recipeId " + bad[i] + " devia ser ignorado: cursor=" + menu.getCarried());
        }
        ServerPlayer ok = lab.player(4, 2, 4);              // o pedido normal do cursor (-1) continua valendo
        Lab.prefs(ok, 8, true);
        StonecutterMenu menu = new StonecutterMenu(20, ok.getInventory());
        ok.containerMenu = menu;
        BenchPullService.handle(ok, new BenchPullRequest(20, new ItemStack(Items.BASALT), false));
        check(h, menu.getCarried().is(Items.BASALT) && menu.getCarried().getCount() == 64, "pedido normal segue funcionando");
        clean(lab, h);
    }

    /** Item 12: um clique de receita varre os baús uma vez só (antes eram três varreduras). */
    @GameTest
    public void oneRecipeClickScansTheChestsOnce(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(8, 2, 4);
        Lab.fill(chest, 0, Items.PURPUR_BLOCK, 10);
        ServerPlayer p = lab.player(4, 2, 4);
        Lab.prefs(p, 8, true);
        StonecutterMenu menu = new StonecutterMenu(5, p.getInventory());
        p.containerMenu = menu;
        BenchPoolSync.Entry wanted = BenchSync.snapshot(p).stream().filter(e -> e.isResult() && !e.missing())
                .findFirst().orElseThrow();
        int before = io.github.leoascenci0.stashlink.bench.BenchPool.scanCount();
        BenchPullService.handle(p, new BenchPullRequest(5, wanted.item(), false, wanted.id()));
        int scans = io.github.leoascenci0.stashlink.bench.BenchPool.scanCount() - before;
        check(h, menu.getSlot(0).getItem().is(Items.PURPUR_BLOCK), "a receita foi montada");
        check(h, scans == 1, "um clique devia varrer os baús 1 vez, varreu " + scans);
        clean(lab, h);
    }

    /** Fechar com a mochila CHEIA: o jogo não tem onde pôr a grade e dropava; o emprestado volta ao baú antes disso. */
    @GameTest
    public void closingWithFullBagReturnsBorrowedGridToTheChest(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        Lab.fill(chest, 0, Items.JUNGLE_PLANKS, 5);
        ServerPlayer p = lab.player(4, 2, 4);
        Lab.prefs(p, 8, true);
        CraftingMenu menu = table(lab, p, 1);
        place(p, menu, "stick", false);
        check(h, Lab.count(chest, Items.JUNGLE_PLANKS) == 3 && grid(menu, Items.JUNGLE_PLANKS) == 2, "2 saíram para a grade");
        var bag = p.getInventory().getNonEquipmentItems();
        for (int i = 0; i < bag.size(); i++) {
            if (bag.get(i).isEmpty()) {
                bag.set(i, new ItemStack(Items.DIRT, 64));
            }
        }
        int dropsBefore = lab.level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(p.blockPosition()).inflate(6)).size();
        menu.removed(p);
        p.containerMenu = p.inventoryMenu;
        BenchSync.tick(lab.level.getServer());
        int dropsAfter = lab.level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(p.blockPosition()).inflate(6)).size();
        check(h, Lab.count(chest, Items.JUNGLE_PLANKS) == 5 && Lab.carried(p, Items.JUNGLE_PLANKS) == 0,
                "as 5 voltam ao baú: baú=" + Lab.count(chest, Items.JUNGLE_PLANKS) + " mochila=" + Lab.carried(p, Items.JUNGLE_PLANKS));
        check(h, dropsAfter == dropsBefore, "nada dropado no chão: " + (dropsAfter - dropsBefore));
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

    /** Botão direito num resultado do cortador: um de cada ingrediente (1 tufo), não um stack. */
    @GameTest
    public void rightClickOnAResultSetsUpOneOfEach(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(8, 2, 4);
        Lab.fill(chest, 0, TUFF, 10);
        ServerPlayer p = lab.player(4, 2, 4);
        Lab.prefs(p, 8, true);
        StonecutterMenu menu = new StonecutterMenu(5, p.getInventory());
        p.containerMenu = menu;
        p.getStats().setValue(p, net.minecraft.stats.Stats.ITEM_PICKED_UP.get(Items.STONE), 1);

        BenchPoolSync.Entry first = BenchSync.snapshot(p).get(0);
        check(h, !first.missing(), "com tufo no baú o primeiro resultado é possível");
        BenchPullService.handle(p, new BenchPullRequest(5, first.item(), true, first.id()));
        check(h, menu.getSlot(0).getItem().is(TUFF) && menu.getSlot(0).getItem().getCount() == 1,
                "um só tufo na entrada: " + menu.getSlot(0).getItem());
        check(h, Lab.count(chest, TUFF) == 9, "só um saiu do baú: " + Lab.count(chest, TUFF));
        check(h, !menu.getSlot(1).getItem().isEmpty(), "a receita foi escolhida");
        clean(lab, h);
    }

    /** A lista sai na ordem única (BenchOrder): possíveis antes dos vermelhos, cada grupo por nome. */
    @GameTest
    public void resultListFollowsTheSingleOrder(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(8, 2, 4);
        Lab.fill(chest, 0, TUFF, 10);
        ServerPlayer p = lab.player(4, 2, 4);
        Lab.prefs(p, 8, true);
        p.containerMenu = new StonecutterMenu(5, p.getInventory());
        p.getStats().setValue(p, net.minecraft.stats.Stats.ITEM_PICKED_UP.get(Items.STONE), 1);

        List<BenchPoolSync.Entry> list = BenchSync.snapshot(p);
        List<BenchPoolSync.Entry> sorted = new ArrayList<>(list);
        sorted.sort(BenchOrder.forServer());
        check(h, list.size() > 1 && list.equals(sorted), "a lista devia já sair ordenada pelo comparador único");
        int firstMissing = -1;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).missing() && firstMissing < 0) {
                firstMissing = i;
            }
            check(h, firstMissing < 0 || list.get(i).missing(), "um possível depois de um vermelho, na posição " + i);
        }
        clean(lab, h);
    }

    /** O raio mandado ao painel é o efetivo do servidor, e 0 se "usar baús" está desligado. */
    @GameTest
    public void panelRadiusIsTheEffectiveOne(GameTestHelper h) {
        Lab lab = new Lab(h);
        ServerPlayer p = lab.player(4, 2, 4);
        Lab.prefs(p, 5, true);
        check(h, BenchSync.radiusFor(p) == 5, "raio preferido dentro do teto: " + BenchSync.radiusFor(p));
        Lab.prefs(p, 5, false);
        check(h, BenchSync.radiusFor(p) == 0, "sem baús como fonte o raio é 0: " + BenchSync.radiusFor(p));
        int cap = StashLinkConfig.radiusCap();
        Lab.prefs(p, cap + 50, true);
        check(h, BenchSync.radiusFor(p) <= cap, "o raio nunca passa do teto do servidor: " + BenchSync.radiusFor(p));
        net.minecraft.network.RegistryFriendlyByteBuf buf =
                new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), p.registryAccess());
        BenchPoolSync.STREAM_CODEC.encode(buf, new BenchPoolSync(1, List.of(), 7));
        check(h, BenchPoolSync.STREAM_CODEC.decode(buf).radius() == 7, "o raio viaja no pacote");
        clean(lab, h);
    }

    /** Livro encantado com um encantamento só (guardado como no livro do jogo). */
    private static ItemStack book(Lab lab, ResourceKey<net.minecraft.world.item.enchantment.Enchantment> key) {
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        var holder = lab.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
        net.minecraft.world.item.enchantment.EnchantmentHelper.updateEnchantments(book, m -> m.set(holder, 1));
        return book;
    }

    /** Bigorna: com uma espada no 1º slot, a lista mostra só livros que servem nela; sem item, todos; desligado, todos. */
    @GameTest
    public void anvilBooksFollowTheItemInTheFirstSlot(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        ItemStack sharpness = book(lab, net.minecraft.world.item.enchantment.Enchantments.SHARPNESS);
        ItemStack protection = book(lab, net.minecraft.world.item.enchantment.Enchantments.PROTECTION);
        chest.setItem(0, sharpness.copy());
        chest.setItem(1, protection.copy());
        ServerPlayer p = lab.player(4, 2, 4);
        Lab.prefs(p, 8, true);
        net.minecraft.world.inventory.AnvilMenu menu = new net.minecraft.world.inventory.AnvilMenu(1, p.getInventory());
        p.containerMenu = menu;
        java.util.function.Function<ItemStack, Boolean> listed = book -> BenchSync.snapshot(p).stream()
                .anyMatch(e -> ItemStack.isSameItemSameComponents(e.item(), book));
        check(h, listed.apply(sharpness) && listed.apply(protection), "sem item: todos os livros");
        menu.getSlot(0).set(new ItemStack(Items.DIAMOND_SWORD));
        check(h, listed.apply(sharpness) && !listed.apply(protection), "espada: só afiação, proteção some");
        check(h, BenchCompat.listKey(menu) != 0, "o item no 1º slot muda a chave da lista (reenvio imediato)");
        menu.getSlot(0).set(new ItemStack(Items.IRON_CHESTPLATE));
        check(h, !listed.apply(sharpness) && listed.apply(protection), "peitoral: só proteção");
        PlayerPrefsStore.set(p.getUUID(), new PlayerPrefs(8, 1, List.of(), Feature.BENCH_BOOK_FILTER.bit(), 8));
        check(h, listed.apply(sharpness) && listed.apply(protection), "filtro desligado: todos os livros");
        menu.getSlot(0).set(ItemStack.EMPTY);
        p.containerMenu = p.inventoryMenu;
        clean(lab, h);
    }

    /**
     * Lápis automático: abrir a mesa põe 3 lápis do baú no slot; gastar faz completar de novo; fechar devolve ao baú o
     * que não foi gasto. Nada some, nada duplica: baú + slot + jogador = o que havia menos o gasto.
     */
    @GameTest
    public void autoLapisFillsRefillsAndReturnsOnClose(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        Lab.fill(chest, 0, Items.LAPIS_LAZULI, 10);
        ServerPlayer p = lab.player(4, 2, 4);
        Lab.prefs(p, 4, true);   // raio curto: os testes de lápis rodam lado a lado e não podem pegar do baú do vizinho
        net.minecraft.world.inventory.EnchantmentMenu menu = new net.minecraft.world.inventory.EnchantmentMenu(1, p.getInventory(),
                ContainerLevelAccess.create(lab.level, lab.bareAt(Blocks.ENCHANTING_TABLE, p.blockPosition().below())));
        p.containerMenu = menu;
        net.minecraft.world.inventory.Slot lapis = menu.getSlot(1);
        h.runAfterDelay(3, () -> {
            check(h, lapis.getItem().is(Items.LAPIS_LAZULI) && lapis.getItem().getCount() == BenchCompat.LAPIS_TARGET
                    && Lab.count(chest, Items.LAPIS_LAZULI) == 7, "abriu: 3 lápis no slot, 7 no baú: " + lapis.getItem());
            lapis.set(new ItemStack(Items.LAPIS_LAZULI, 1));     // um encantamento de nível 2 gastou 2
            h.runAfterDelay(15, () -> {
                check(h, lapis.getItem().getCount() == BenchCompat.LAPIS_TARGET && Lab.count(chest, Items.LAPIS_LAZULI) == 5,
                        "gastou 2: completou de novo (baú 5): " + lapis.getItem() + " baú=" + Lab.count(chest, Items.LAPIS_LAZULI));
                p.doCloseContainer();                            // fechar: o que sobrou volta ao baú, não à mochila
                check(h, Lab.count(chest, Items.LAPIS_LAZULI) == 8 && Lab.carried(p, Items.LAPIS_LAZULI) == 0,
                        "fechou: 8 no baú (10 menos os 2 gastos), nada na mochila: baú=" + Lab.count(chest, Items.LAPIS_LAZULI)
                                + " jogador=" + Lab.carried(p, Items.LAPIS_LAZULI));
                clean(lab, h);
            });
        });
    }

    /** Lápis automático + sair do servidor com a mesa aberta: o lápis volta ao baú antes do save do jogador. */
    @GameTest
    public void autoLapisReturnsOnDisconnect(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        Lab.fill(chest, 0, Items.LAPIS_LAZULI, 10);
        ServerPlayer p = lab.player(4, 2, 4);
        Lab.prefs(p, 4, true);   // raio curto: os testes de lápis rodam lado a lado e não podem pegar do baú do vizinho
        p.containerMenu = new net.minecraft.world.inventory.EnchantmentMenu(1, p.getInventory(),
                ContainerLevelAccess.create(lab.level, lab.bareAt(Blocks.ENCHANTING_TABLE, p.blockPosition().below())));
        h.runAfterDelay(3, () -> {
            check(h, Lab.count(chest, Items.LAPIS_LAZULI) == 7, "abriu: 3 saíram do baú");
            BenchSync.release(p);                                // logout / parada do servidor
            check(h, p.containerMenu == p.inventoryMenu && Lab.count(chest, Items.LAPIS_LAZULI) == 10
                    && Lab.carried(p, Items.LAPIS_LAZULI) == 0, "saiu: os 3 voltaram ao baú: baú="
                    + Lab.count(chest, Items.LAPIS_LAZULI) + " jogador=" + Lab.carried(p, Items.LAPIS_LAZULI));
            clean(lab, h);
        });
    }

    /** Lápis automático desligado: nada sai do baú sozinho, e o lápis volta a aparecer no painel (aba Livros). */
    @GameTest
    public void autoLapisOffLeavesTheChestAlone(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        Lab.fill(chest, 0, Items.LAPIS_LAZULI, 10);
        ServerPlayer p = lab.player(4, 2, 4);
        PlayerPrefsStore.set(p.getUUID(), new PlayerPrefs(4, 1, List.of(), Feature.BENCH_LAPIS.bit(), 4));
        net.minecraft.world.inventory.EnchantmentMenu menu = new net.minecraft.world.inventory.EnchantmentMenu(1, p.getInventory(),
                ContainerLevelAccess.create(lab.level, lab.bareAt(Blocks.ENCHANTING_TABLE, p.blockPosition().below())));
        p.containerMenu = menu;
        h.runAfterDelay(3, () -> {
            check(h, menu.getSlot(1).getItem().isEmpty() && Lab.count(chest, Items.LAPIS_LAZULI) == 10, "desligado: nada mexeu");
            check(h, BenchSync.snapshot(p).stream().anyMatch(e -> e.item().is(Items.LAPIS_LAZULI)
                    && e.tab() == BenchCompat.GEAR_TAB_BOOKS), "desligado: o lápis aparece no painel, aba Livros");
            p.containerMenu = p.inventoryMenu;
            clean(lab, h);
        });
    }

    /**
     * Sinalizador: o botão de um minério põe 1 dele no pagamento; slot cheio não tira mais; item que não é pagamento
     * ou que não há é ignorado; trocar de minério devolve o anterior ao baú; fechar devolve o que não foi usado.
     */
    @GameTest
    public void beaconPaymentButtonsPullOneFromStorage(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        Lab.fill(chest, 0, Items.IRON_INGOT, 5);
        Lab.fill(chest, 1, Items.GOLD_INGOT, 2);
        Lab.fill(chest, 2, Items.COPPER_INGOT, 9);
        ServerPlayer p = lab.player(4, 2, 4);
        Lab.prefs(p, 4, true);
        net.minecraft.world.inventory.BeaconMenu menu = new net.minecraft.world.inventory.BeaconMenu(1, p.getInventory());
        p.containerMenu = menu;
        net.minecraft.world.inventory.Slot pay = menu.getSlot(0);
        check(h, BenchCompat.stationOf(menu) == BenchCompat.Station.BEACON && !BenchCompat.usesPanel(menu),
                "sinalizador é estação, sem painel");
        check(h, BenchSync.snapshot(p).stream().allMatch(e -> !e.item().is(Items.COPPER_INGOT)),
                "a lista do sinalizador só tem pagamento (cobre não)");

        BenchPullService.handle(p, new BenchPullRequest(1, new ItemStack(Items.IRON_INGOT), true, BenchResults.PAY));
        check(h, pay.getItem().is(Items.IRON_INGOT) && pay.getItem().getCount() == 1 && Lab.count(chest, Items.IRON_INGOT) == 4,
                "1 ferro no pagamento: " + pay.getItem());
        h.runAfterDelay(2, () -> {
            BenchPullService.handle(p, new BenchPullRequest(1, new ItemStack(Items.IRON_INGOT), true, BenchResults.PAY));
            check(h, pay.getItem().getCount() == 1 && Lab.count(chest, Items.IRON_INGOT) == 4, "slot cheio: não tira mais");
            h.runAfterDelay(2, () -> {
                BenchPullService.handle(p, new BenchPullRequest(1, new ItemStack(Items.COPPER_INGOT), true, BenchResults.PAY));
                check(h, pay.getItem().is(Items.IRON_INGOT) && Lab.count(chest, Items.COPPER_INGOT) == 9, "cobre não é pagamento");
                h.runAfterDelay(2, () -> {
                    BenchPullService.handle(p, new BenchPullRequest(1, new ItemStack(Items.EMERALD), true, BenchResults.PAY));
                    check(h, pay.getItem().is(Items.IRON_INGOT) && Lab.count(chest, Items.IRON_INGOT) == 4,
                            "esmeralda que não há: nada muda");
                    h.runAfterDelay(2, () -> {
                        BenchPullService.handle(p, new BenchPullRequest(1, new ItemStack(Items.GOLD_INGOT), true, BenchResults.PAY));
                        check(h, pay.getItem().is(Items.GOLD_INGOT) && Lab.count(chest, Items.IRON_INGOT) == 5
                                && Lab.count(chest, Items.GOLD_INGOT) == 1, "trocou: o ferro voltou ao baú, ouro no slot");
                        p.doCloseContainer();
                        check(h, Lab.count(chest, Items.GOLD_INGOT) == 2 && Lab.carried(p, Items.GOLD_INGOT) == 0,
                                "fechou sem confirmar: o ouro voltou ao baú (nem mochila nem chão)");
                        clean(lab, h);
                    });
                });
            });
        });
    }

    /**
     * Sinalizador: pagamento do próprio jogador nunca vai para o baú. Trocar devolve a esmeralda dele à mochila (Item
     * 16.5, decisão do Eliel; antes ela ficava e a troca não acontecia). Trancado, nada sai.
     */
    @GameTest
    public void beaconSendsThePlayersOwnPaymentBackToTheBackpackAndRespectsTheLock(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        Lab.fill(chest, 0, Items.DIAMOND, 3);
        ServerPlayer p = lab.player(4, 2, 4);
        Lab.prefs(p, 4, true);
        net.minecraft.world.inventory.BeaconMenu menu = new net.minecraft.world.inventory.BeaconMenu(1, p.getInventory());
        p.containerMenu = menu;
        menu.getSlot(0).set(new ItemStack(Items.EMERALD));      // pagamento que o jogador pôs com a mão
        BenchPullService.handle(p, new BenchPullRequest(1, new ItemStack(Items.DIAMOND), true, BenchResults.PAY));
        check(h, menu.getSlot(0).getItem().is(Items.DIAMOND) && Lab.count(chest, Items.DIAMOND) == 2
                && Lab.count(chest, Items.EMERALD) == 0 && p.getInventory().countItem(Items.EMERALD) == 1,
                "a esmeralda do jogador volta à mochila (nunca ao baú) e o diamante do baú entra");
        menu.getSlot(0).set(ItemStack.EMPTY);
        Lab.fill(chest, 0, Items.DIAMOND, 3);                   // recomeça: o baú com os 3 diamantes
        h.runAfterDelay(2, () -> {
            // Trancar e pedir no mesmo tick: os testes rodam lado a lado e o clean() dos outros destranca tudo.
            StashLinkConfig.setFeatureLocked(Feature.BENCH_BEACON, true);
            BenchPullService.handle(p, new BenchPullRequest(1, new ItemStack(Items.DIAMOND), true, BenchResults.PAY));
            check(h, menu.getSlot(0).getItem().isEmpty() && Lab.count(chest, Items.DIAMOND) == 3, "trancado: nada sai");
            h.runAfterDelay(2, () -> {
                // Revisão 1.0: pedido forjado "pôr no slot" (PLACE) também respeita o cadeado do sinalizador.
                StashLinkConfig.setFeatureLocked(Feature.BENCH_BEACON, true);
                BenchPullService.handle(p, new BenchPullRequest(1, new ItemStack(Items.DIAMOND), true, BenchResults.PLACE));
                check(h, menu.getSlot(0).getItem().isEmpty() && Lab.count(chest, Items.DIAMOND) == 3,
                        "trancado: PLACE também não põe pagamento: " + menu.getSlot(0).getItem());
                p.containerMenu = p.inventoryMenu;
                clean(lab, h);
            });
        });
    }

    private static ItemStack potion(net.minecraft.core.Holder<net.minecraft.world.item.alchemy.Potion> type) {
        return net.minecraft.world.item.alchemy.PotionContents.createItemStack(Items.POTION, type);
    }

    private static BenchPoolSync.Entry entryOf(List<BenchPoolSync.Entry> list, ItemStack stack) {
        for (BenchPoolSync.Entry e : list) {
            if (e.isResult() && ItemStack.isSameItemSameComponents(e.item(), stack)) {
                return e;
            }
        }
        return null;
    }

    /**
     * Suporte de poções: a aba Poções lista o que dá para fazer (rapidez sim, resistência ao fogo não: falta creme de
     * magma); clicar em rapidez monta só o 1º passo (3 águas + verruga + pó de blaze, sem combustível), e depois do
     * preparo o próximo clique põe o açúcar sobre as estranhas que ficaram no suporte. Nada some, nada duplica.
     */
    @GameTest
    public void brewingListsPotionsAndSetsUpOneStepPerClick(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        ItemStack water = potion(net.minecraft.world.item.alchemy.Potions.WATER);
        for (int i = 0; i < 3; i++) {
            chest.setItem(i, water.copy());
        }
        Lab.fill(chest, 3, Items.NETHER_WART, 2);
        Lab.fill(chest, 4, Items.SUGAR, 1);
        Lab.fill(chest, 5, Items.BLAZE_POWDER, 1);
        ServerPlayer p = lab.player(4, 2, 4);
        Lab.prefs(p, 4, true);
        net.minecraft.world.inventory.BrewingStandMenu menu = new net.minecraft.world.inventory.BrewingStandMenu(1, p.getInventory());
        p.containerMenu = menu;
        ItemStack swift = potion(net.minecraft.world.item.alchemy.Potions.SWIFTNESS);
        ItemStack awkward = potion(net.minecraft.world.item.alchemy.Potions.AWKWARD);
        List<BenchPoolSync.Entry> list = BenchSync.snapshot(p);
        BenchPoolSync.Entry swiftEntry = entryOf(list, swift);
        BenchPoolSync.Entry fire = entryOf(list, potion(net.minecraft.world.item.alchemy.Potions.FIRE_RESISTANCE));
        check(h, swiftEntry != null && !swiftEntry.missing() && swiftEntry.tab() == 0, "rapidez listada e possível");
        check(h, fire != null && fire.missing(), "resistência ao fogo listada em vermelho (sem creme de magma)");
        check(h, entryOf(list, awkward) != null && !entryOf(list, awkward).missing(), "estranha possível");

        BenchPullService.handle(p, new BenchPullRequest(1, swift, false, swiftEntry.id()));
        for (int i = 0; i < 3; i++) {
            check(h, ItemStack.isSameItemSameComponents(menu.getSlot(i).getItem(), water), "garrafa de água no slot " + i);
        }
        check(h, menu.getSlot(3).getItem().is(Items.NETHER_WART) && menu.getSlot(3).getItem().getCount() == 1
                && menu.getSlot(4).getItem().is(Items.BLAZE_POWDER), "1º passo: verruga e pó de blaze");
        check(h, Lab.count(chest, Items.POTION) == 0 && Lab.count(chest, Items.NETHER_WART) == 1
                && Lab.count(chest, Items.BLAZE_POWDER) == 0 && Lab.count(chest, Items.SUGAR) == 1,
                "saiu do baú só o 1º passo (o açúcar fica)");

        // O suporte terminou: 3 estranhas, ingrediente gasto.
        for (int i = 0; i < 3; i++) {
            menu.getSlot(i).set(awkward.copy());
        }
        menu.getSlot(3).set(ItemStack.EMPTY);
        h.runAfterDelay(StashLinkConfig.BENCH_SWEEP_COOLDOWN_TICKS, () -> {
            BenchPoolSync.Entry again = entryOf(BenchSync.snapshot(p), swift);
            BenchPullService.handle(p, new BenchPullRequest(1, swift, false, again.id()));
            check(h, menu.getSlot(3).getItem().is(Items.SUGAR) && Lab.count(chest, Items.SUGAR) == 0,
                    "2º passo: açúcar sobre as estranhas do suporte");
            check(h, ItemStack.isSameItemSameComponents(menu.getSlot(0).getItem(), awkward)
                    && Lab.count(chest, Items.NETHER_WART) == 1, "as estranhas ficaram; nenhuma verruga a mais");
            // Outra poção num slot de garrafa: não mistura nem mexe.
            menu.getSlot(3).set(ItemStack.EMPTY);
            Lab.fill(chest, 4, Items.SUGAR, 1);
            menu.getSlot(0).set(potion(net.minecraft.world.item.alchemy.Potions.MUNDANE));
            h.runAfterDelay(StashLinkConfig.BENCH_SWEEP_COOLDOWN_TICKS, () -> {
                BenchPullService.handle(p, new BenchPullRequest(1, swift, false, again.id()));
                check(h, menu.getSlot(3).getItem().isEmpty() && Lab.count(chest, Items.SUGAR) == 1,
                        "poção diferente no suporte: nada muda");
                for (int i = 0; i < 5; i++) {
                    menu.getSlot(i).set(ItemStack.EMPTY);
                }
                p.containerMenu = p.inventoryMenu;
                clean(lab, h);
            });
        });
    }

    /** Suporte de poções com a função trancada: sem lista de poções e o clique não mexe em nada. */
    @GameTest
    public void brewingLockedKeepsTheOldPanel(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        chest.setItem(0, potion(net.minecraft.world.item.alchemy.Potions.WATER));
        Lab.fill(chest, 1, Items.NETHER_WART, 1);
        ServerPlayer p = lab.player(4, 2, 4);
        Lab.prefs(p, 4, true);
        net.minecraft.world.inventory.BrewingStandMenu menu = new net.minecraft.world.inventory.BrewingStandMenu(1, p.getInventory());
        p.containerMenu = menu;
        ItemStack awkward = potion(net.minecraft.world.item.alchemy.Potions.AWKWARD);
        int id = entryOf(BenchSync.snapshot(p), awkward).id();
        StashLinkConfig.setFeatureLocked(Feature.BENCH_BREWING, true);
        check(h, entryOf(BenchSync.snapshot(p), awkward) == null, "trancada: sem lista de poções");
        BenchPullService.handle(p, new BenchPullRequest(1, awkward, false, id));
        check(h, menu.getSlot(0).getItem().isEmpty() && Lab.count(chest, Items.NETHER_WART) == 1, "trancada: nada sai do baú");
        p.containerMenu = p.inventoryMenu;
        clean(lab, h);
    }

    /** Bigorna: a ferramenta vai sempre para o 1º slot e o material para o 2º, na ordem que o jogador clicar. */
    @GameTest
    public void anvilToolAlwaysGoesToTheFirstSlot(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        chest.setItem(0, new ItemStack(Items.IRON_INGOT, 2));   // um para cada rodada
        chest.setItem(1, new ItemStack(Items.IRON_PICKAXE));
        chest.setItem(2, new ItemStack(Items.DIAMOND_SWORD));
        ServerPlayer p = lab.player(4, 2, 4);
        Lab.prefs(p, 8, true);
        net.minecraft.world.inventory.AnvilMenu menu = new net.minecraft.world.inventory.AnvilMenu(1, p.getInventory());
        p.containerMenu = menu;
        BenchResults.place(p, menu, new ItemStack(Items.IRON_INGOT), true);      // material primeiro
        BenchResults.place(p, menu, new ItemStack(Items.IRON_PICKAXE), true);
        check(h, menu.getSlot(0).getItem().is(Items.IRON_PICKAXE) && menu.getSlot(1).getItem().is(Items.IRON_INGOT),
                "material primeiro: ferramenta no 1º, lingote no 2º: " + menu.getSlot(0).getItem() + " / " + menu.getSlot(1).getItem());
        menu.getSlot(0).set(ItemStack.EMPTY);
        menu.getSlot(1).set(ItemStack.EMPTY);
        BenchResults.place(p, menu, new ItemStack(Items.DIAMOND_SWORD), true);   // ferramenta primeiro
        BenchResults.place(p, menu, new ItemStack(Items.IRON_INGOT), true);
        check(h, menu.getSlot(0).getItem().is(Items.DIAMOND_SWORD) && menu.getSlot(1).getItem().is(Items.IRON_INGOT),
                "ferramenta primeiro: continua no 1º: " + menu.getSlot(0).getItem() + " / " + menu.getSlot(1).getItem());
        menu.getSlot(0).set(ItemStack.EMPTY);
        menu.getSlot(1).set(ItemStack.EMPTY);
        p.containerMenu = p.inventoryMenu;
        clean(lab, h);
    }

    /** Bigorna: o filtro do painel e a aba usam o mesmo predicado (armadura, ferramenta/etiqueta, arma, livro, material). */
    @GameTest
    public void anvilFilterAndTabShareOnePredicate(GameTestHelper h) {
        Lab lab = new Lab(h);
        ServerPlayer p = lab.player(4, 2, 4);
        p.getInventory().add(new ItemStack(Items.IRON_PICKAXE));
        net.minecraft.world.inventory.AnvilMenu menu = new net.minecraft.world.inventory.AnvilMenu(1, p.getInventory());
        p.containerMenu = menu;
        ItemStack[] stacks = {new ItemStack(Items.ENCHANTED_BOOK), new ItemStack(Items.DIAMOND_PICKAXE),
                new ItemStack(Items.NAME_TAG), new ItemStack(Items.IRON_INGOT), new ItemStack(Items.DIRT),
                new ItemStack(Items.IRON_CHESTPLATE), new ItemStack(Items.DIAMOND_SWORD)};
        int[] tabs = {BenchCompat.GEAR_TAB_BOOKS, BenchCompat.GEAR_TAB_TOOLS, BenchCompat.GEAR_TAB_TOOLS,
                BenchCompat.ANVIL_TAB_MATERIALS, BenchCompat.ANVIL_TAB_MATERIALS, BenchCompat.GEAR_TAB_ARMOR,
                BenchCompat.GEAR_TAB_WEAPONS};
        boolean[] relevant = {true, true, true, true, false, true, true};   // lingote conserta a picareta de ferro da mochila
        for (int i = 0; i < stacks.length; i++) {
            check(h, BenchCompat.slotTab(menu, stacks[i]) == tabs[i], "aba de " + stacks[i] + ": " + BenchCompat.slotTab(menu, stacks[i]));
            check(h, BenchCompat.relevant(menu, p, stacks[i]) == relevant[i], "filtro de " + stacks[i]);
        }
        p.containerMenu = p.inventoryMenu;
        clean(lab, h);
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
