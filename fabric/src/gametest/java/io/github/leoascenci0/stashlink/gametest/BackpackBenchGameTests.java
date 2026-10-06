package io.github.leoascenci0.stashlink.gametest;

import io.github.leoascenci0.stashlink.bench.BenchPullService;
import io.github.leoascenci0.stashlink.bench.BenchResults;
import io.github.leoascenci0.stashlink.bench.BenchSync;
import io.github.leoascenci0.stashlink.config.Feature;
import io.github.leoascenci0.stashlink.config.StashLinkConfig;
import io.github.leoascenci0.stashlink.network.BenchPoolSync;
import io.github.leoascenci0.stashlink.network.BenchPullRequest;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.BeaconMenu;
import net.minecraft.world.inventory.BlastFurnaceMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.inventory.LoomMenu;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.inventory.SmokerMenu;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Blocks;

import java.util.EnumSet;
import java.util.List;

/**
 * Item 16.5 (pedido do Eliel, 2026-10-05): <b>todas</b> as estações usam a mochila de quem abriu como fonte, não só os
 * baús. Item só na mochila (baús desligados) aparece e funciona; com mochila e baú, sai da mochila primeiro e o baú
 * completa; o caderno nunca leva item do jogador para o baú; e um jogador nunca recebe nem perde item da mochila de
 * outro, nem com a mesma estação aberta pelos dois.
 */
public class BackpackBenchGameTests {
    private static void check(GameTestHelper h, boolean ok, String msg) {
        h.assertTrue(ok, msg);
    }

    private static void clean(Lab lab, GameTestHelper h) {
        StashLinkConfig.lockedFeatures = EnumSet.noneOf(Feature.class);
        lab.cleanup();
        h.succeed();
    }

    /** Jogador com "usar baús" desligado: só a mochila conta (e baú de teste vizinho nenhum interfere). */
    private static ServerPlayer backpackOnly(Lab lab, double x, double z) {
        ServerPlayer p = lab.player(x, 2, z);
        Lab.prefs(p, 8, false);
        return p;
    }

    private static ContainerLevelAccess at(Lab lab, ServerPlayer p, net.minecraft.world.level.block.Block block) {
        return ContainerLevelAccess.create(lab.level, lab.bareAt(block, p.blockPosition().below()));
    }

    private static BenchPoolSync.Entry result(List<BenchPoolSync.Entry> list, Item item) {
        return list.stream().filter(e -> e.isResult() && e.item().is(item)).findFirst().orElse(null);
    }

    private static BenchPoolSync.Entry result(List<BenchPoolSync.Entry> list, ItemStack stack) {
        return list.stream().filter(e -> e.isResult() && ItemStack.isSameItemSameComponents(e.item(), stack))
                .findFirst().orElse(null);
    }

    private static boolean lists(ServerPlayer p, Item item) {
        return BenchSync.snapshot(p).stream().anyMatch(e -> !e.isResult() && e.item().is(item));
    }

    private static int own(ServerPlayer p, Item item) {
        return p.getInventory().countItem(item);
    }

    private static ItemStack potion(net.minecraft.core.Holder<net.minecraft.world.item.alchemy.Potion> type) {
        return PotionContents.createItemStack(Items.POTION, type);
    }

    private static boolean empty(AbstractContainerMenu menu, int... slots) {
        for (int slot : slots) {
            if (menu.getSlot(slot).hasItem()) {
                return false;
            }
        }
        return true;
    }

    /** Cortador com baús desligados: a pedra só na mochila lista a receita, o clique monta e fechar devolve à mochila. */
    @GameTest
    public void stonecutterWorksWithTheBackpackAlone(GameTestHelper h) {
        Lab lab = new Lab(h);
        ServerPlayer p = backpackOnly(lab, 4, 4);
        Lab.give(p, 0, Items.RED_SANDSTONE, 10);
        StonecutterMenu menu = new StonecutterMenu(1, p.getInventory(), at(lab, p, Blocks.STONECUTTER));
        p.containerMenu = menu;

        BenchPoolSync.Entry stairs = result(BenchSync.snapshot(p), Items.RED_SANDSTONE_STAIRS);
        check(h, stairs != null && !stairs.missing(), "só na mochila: a escada aparece e é possível");
        BenchPullService.handle(p, new BenchPullRequest(1, stairs.item(), false, stairs.id()));
        check(h, menu.getSlot(0).getItem().is(Items.RED_SANDSTONE) && menu.getSlot(0).getItem().getCount() == 10
                && own(p, Items.RED_SANDSTONE) == 0, "as 10 da mochila foram para a entrada: " + menu.getSlot(0).getItem());
        check(h, menu.getSlot(1).getItem().is(Items.RED_SANDSTONE_STAIRS), "a receita foi escolhida");
        p.doCloseContainer();
        check(h, Lab.carried(p, Items.RED_SANDSTONE) == 10, "fechou: as 10 voltaram para a mochila");
        clean(lab, h);
    }

    /**
     * Cortador com mochila + baú: a entrada leva as 10 da mochila e o baú completa a pilha (54). Cortar 3 gasta primeiro
     * o que era do jogador; ao fechar, os 54 emprestados voltam ao baú e o jogador fica com os 7 dele. Nada some.
     */
    @GameTest
    public void stonecutterTakesFromTheBackpackFirstAndTheChestCompletes(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(8, 2, 4);
        Lab.fill(chest, 0, Items.RED_SANDSTONE, 60);
        ServerPlayer p = lab.player(4, 2, 4);
        Lab.prefs(p, 8, true);
        Lab.give(p, 0, Items.RED_SANDSTONE, 10);
        StonecutterMenu menu = new StonecutterMenu(1, p.getInventory(), at(lab, p, Blocks.STONECUTTER));
        p.containerMenu = menu;

        BenchPoolSync.Entry stairs = result(BenchSync.snapshot(p), Items.RED_SANDSTONE_STAIRS);
        BenchPullService.handle(p, new BenchPullRequest(1, stairs.item(), false, stairs.id()));
        check(h, menu.getSlot(0).getItem().getCount() == 64 && own(p, Items.RED_SANDSTONE) == 0
                && Lab.count(chest, Items.RED_SANDSTONE) == 6,
                "10 da mochila + 54 do baú: entrada=" + menu.getSlot(0).getItem() + " baú=" + Lab.count(chest, Items.RED_SANDSTONE));
        for (int i = 0; i < 3; i++) {
            menu.clicked(1, 0, ContainerInput.PICKUP, p);      // corta uma escada por clique
        }
        check(h, menu.getCarried().is(Items.RED_SANDSTONE_STAIRS) && menu.getCarried().getCount() == 3
                && menu.getSlot(0).getItem().getCount() == 61, "3 escadas no cursor, 61 na entrada");
        p.doCloseContainer();
        check(h, Lab.count(chest, Items.RED_SANDSTONE) == 60, "os 54 emprestados voltaram ao baú: "
                + Lab.count(chest, Items.RED_SANDSTONE));
        check(h, Lab.carried(p, Items.RED_SANDSTONE) == 7 && Lab.carried(p, Items.RED_SANDSTONE_STAIRS) == 3,
                "o jogador fica com 7 (as 3 cortadas saíram das dele) e as 3 escadas: "
                        + Lab.carried(p, Items.RED_SANDSTONE));
        clean(lab, h);
    }

    /** Shift no resultado com mochila + baú: corta os 64 (10 dele + 54 do baú) em 128 lajes; nada volta, nada duplica. */
    @GameTest
    public void stonecutterShiftClickWithBackpackAndChestKeepsEveryItem(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(8, 2, 4);
        Lab.fill(chest, 0, Items.CUT_RED_SANDSTONE, 60);
        ServerPlayer p = lab.player(4, 2, 4);
        Lab.prefs(p, 8, true);
        Lab.give(p, 0, Items.CUT_RED_SANDSTONE, 10);
        StonecutterMenu menu = new StonecutterMenu(1, p.getInventory(), at(lab, p, Blocks.STONECUTTER));
        p.containerMenu = menu;

        BenchPoolSync.Entry slab = result(BenchSync.snapshot(p), Items.CUT_RED_SANDSTONE_SLAB);
        check(h, slab != null && !slab.missing(), "a laje de arenito vermelho cortado é possível");
        BenchPullService.handle(p, new BenchPullRequest(1, slab.item(), false, slab.id()));
        check(h, menu.getSlot(0).getItem().getCount() == 64, "pilha cheia na entrada");
        menu.clicked(1, 0, ContainerInput.QUICK_MOVE, p);       // shift no resultado: corta tudo o que couber
        int left = menu.getSlot(0).getItem().getCount();
        int slabs = Lab.carried(p, Items.CUT_RED_SANDSTONE_SLAB);
        check(h, left == 0 && slabs == 128, "cortou os 64 em 128 lajes: lajes=" + slabs + " sobrou=" + left);
        p.doCloseContainer();
        check(h, Lab.count(chest, Items.CUT_RED_SANDSTONE) == 6 && Lab.carried(p, Items.CUT_RED_SANDSTONE) == 0
                && Lab.carried(p, Items.CUT_RED_SANDSTONE_SLAB) == 128,
                "nada volta ao baú nem duplica: baú=" + Lab.count(chest, Items.CUT_RED_SANDSTONE)
                        + " jogador=" + Lab.carried(p, Items.CUT_RED_SANDSTONE));
        clean(lab, h);
    }

    /** Tear com baús desligados: estandarte e corante da mochila montam o padrão; fechar devolve tudo à mochila. */
    @GameTest
    public void loomWorksWithTheBackpackAlone(GameTestHelper h) {
        Lab lab = new Lab(h);
        ServerPlayer p = backpackOnly(lab, 4, 4);
        Item banner = Items.BANNER.pick(DyeColor.WHITE);
        Item dye = Items.DYE.pick(DyeColor.RED);
        Lab.give(p, 0, banner, 1);
        Lab.give(p, 1, dye, 3);
        LoomMenu menu = new LoomMenu(1, p.getInventory(), at(lab, p, Blocks.LOOM));
        p.containerMenu = menu;

        List<BenchPoolSync.Entry> list = BenchSync.snapshot(p);
        BenchPoolSync.Entry red = list.stream().filter(e -> e.isColorPick() && e.color() == DyeColor.RED.getId())
                .findFirst().orElse(null);
        BenchPoolSync.Entry first = list.stream().filter(BenchPoolSync.Entry::isResult).findFirst().orElse(null);
        check(h, red != null && !red.missing() && first != null && !first.missing(),
                "só na mochila: a cor vermelha e o padrão aparecem disponíveis");
        BenchPullService.handle(p, new BenchPullRequest(1, red.item(), false, first.id()));
        check(h, menu.getSlot(0).getItem().is(banner) && menu.getSlot(1).getItem().is(dye) && menu.getSlot(3).hasItem(),
                "estandarte e corante da mochila nos slots; padrão pronto");
        check(h, own(p, banner) == 0, "o estandarte saiu da mochila");
        p.doCloseContainer();
        check(h, Lab.carried(p, banner) == 1 && Lab.carried(p, dye) == 3, "fechou: tudo de volta à mochila");
        clean(lab, h);
    }

    /** Suporte de poções com mochila + baú: garrafas e verruga saem da mochila; do baú só o pó de blaze que ela não tem. */
    @GameTest
    public void brewingTakesFromTheBackpackFirst(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        ItemStack water = potion(Potions.WATER);
        for (int i = 0; i < 3; i++) {
            chest.setItem(i, water.copy());
        }
        Lab.fill(chest, 3, Items.NETHER_WART, 2);
        Lab.fill(chest, 4, Items.BLAZE_POWDER, 1);
        ServerPlayer p = lab.player(4, 2, 4);
        Lab.prefs(p, 4, true);   // raio curto, como os outros testes de poções (não alcança o baú do vizinho)
        for (int i = 0; i < 3; i++) {
            p.getInventory().setItem(i, water.copy());
        }
        Lab.give(p, 3, Items.NETHER_WART, 1);
        BrewingStandMenu menu = new BrewingStandMenu(1, p.getInventory());
        p.containerMenu = menu;

        ItemStack awkward = potion(Potions.AWKWARD);
        BenchPoolSync.Entry entry = result(BenchSync.snapshot(p), awkward);
        check(h, entry != null && !entry.missing(), "estranha possível");
        BenchPullService.handle(p, new BenchPullRequest(1, awkward, false, entry.id()));
        for (int i = 0; i < 3; i++) {
            check(h, ItemStack.isSameItemSameComponents(menu.getSlot(i).getItem(), water), "água no slot " + i);
        }
        check(h, menu.getSlot(3).getItem().is(Items.NETHER_WART) && menu.getSlot(4).getItem().is(Items.BLAZE_POWDER),
                "verruga e pó de blaze nos slots");
        check(h, own(p, Items.POTION) == 0 && own(p, Items.NETHER_WART) == 0, "as garrafas e a verruga vieram da mochila");
        check(h, Lab.count(chest, Items.POTION) == 3 && Lab.count(chest, Items.NETHER_WART) == 2
                && Lab.count(chest, Items.BLAZE_POWDER) == 0, "do baú só saiu o pó de blaze");
        for (int i = 0; i < 5; i++) {
            menu.getSlot(i).set(ItemStack.EMPTY);
        }
        p.containerMenu = p.inventoryMenu;
        clean(lab, h);
    }

    /**
     * Lápis automático com mochila + baú: completa com os 2 da mochila e 1 do baú; depois de gastar 1, completa do baú.
     * Ao fechar, só os 2 que vieram do baú voltam a ele; o jogador fica com o 1 dele que sobrou.
     */
    @GameTest
    public void autoLapisTakesFromTheBackpackFirst(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        Lab.fill(chest, 0, Items.LAPIS_LAZULI, 10);
        ServerPlayer p = lab.player(4, 2, 4);
        Lab.prefs(p, 4, true);   // raio curto: os testes de lápis rodam lado a lado
        Lab.give(p, 0, Items.LAPIS_LAZULI, 2);
        EnchantmentMenu menu = new EnchantmentMenu(1, p.getInventory(), at(lab, p, Blocks.ENCHANTING_TABLE));
        p.containerMenu = menu;
        Slot lapis = menu.getSlot(1);
        h.runAfterDelay(3, () -> {
            check(h, lapis.getItem().getCount() == 3 && own(p, Items.LAPIS_LAZULI) == 0
                    && Lab.count(chest, Items.LAPIS_LAZULI) == 9, "2 da mochila + 1 do baú: baú="
                    + Lab.count(chest, Items.LAPIS_LAZULI) + " mochila=" + own(p, Items.LAPIS_LAZULI));
            lapis.set(new ItemStack(Items.LAPIS_LAZULI, 2));   // um encantamento gastou 1
            h.runAfterDelay(15, () -> {
                check(h, lapis.getItem().getCount() == 3 && Lab.count(chest, Items.LAPIS_LAZULI) == 8,
                        "mochila vazia: completou do baú (8)");
                p.doCloseContainer();
                check(h, Lab.count(chest, Items.LAPIS_LAZULI) == 10 && Lab.carried(p, Items.LAPIS_LAZULI) == 1,
                        "fechou: os 2 do baú voltaram; o jogador fica com 1 (2 dele - 1 gasto): baú="
                                + Lab.count(chest, Items.LAPIS_LAZULI) + " jogador=" + Lab.carried(p, Items.LAPIS_LAZULI));
                clean(lab, h);
            });
        });
    }

    /** Lápis automático com baús desligados: sai da mochila, e fechar devolve tudo à mochila. */
    @GameTest
    public void autoLapisWorksWithTheBackpackAlone(GameTestHelper h) {
        Lab lab = new Lab(h);
        ServerPlayer p = backpackOnly(lab, 4, 4);
        Lab.give(p, 0, Items.LAPIS_LAZULI, 5);
        EnchantmentMenu menu = new EnchantmentMenu(1, p.getInventory(), at(lab, p, Blocks.ENCHANTING_TABLE));
        p.containerMenu = menu;
        h.runAfterDelay(3, () -> {
            check(h, menu.getSlot(1).getItem().getCount() == 3 && own(p, Items.LAPIS_LAZULI) == 2,
                    "3 da mochila no slot: " + menu.getSlot(1).getItem());
            p.doCloseContainer();
            check(h, Lab.carried(p, Items.LAPIS_LAZULI) == 5, "fechou: os 5 de volta à mochila");
            clean(lab, h);
        });
    }

    /**
     * Sinalizador com baús desligados: paga com o ferro da mochila; trocar para ouro devolve o ferro à mochila. Com a
     * mochila cheia, o pagamento do jogador não tem para onde ir: não troca (nunca vai ao chão nem a baú).
     */
    @GameTest
    public void beaconPaysFromTheBackpackAndSwapsTheOwnPaymentBack(GameTestHelper h) {
        Lab lab = new Lab(h);
        ServerPlayer p = backpackOnly(lab, 4, 4);
        Lab.give(p, 0, Items.IRON_INGOT, 1);
        Lab.give(p, 1, Items.GOLD_INGOT, 1);
        BeaconMenu menu = new BeaconMenu(1, p.getInventory());
        p.containerMenu = menu;
        Slot pay = menu.getSlot(0);

        BenchPullService.handle(p, new BenchPullRequest(1, new ItemStack(Items.IRON_INGOT), true, BenchResults.PAY));
        check(h, pay.getItem().is(Items.IRON_INGOT) && own(p, Items.IRON_INGOT) == 0, "pagou com o ferro da mochila");
        BenchResults.pay(p, menu, new ItemStack(Items.GOLD_INGOT));
        check(h, pay.getItem().is(Items.GOLD_INGOT) && own(p, Items.IRON_INGOT) == 1 && own(p, Items.GOLD_INGOT) == 0,
                "trocou: o ferro voltou à mochila, ouro no slot");

        int size = p.getInventory().getNonEquipmentItems().size();
        for (int i = 1; i < size; i++) {
            p.getInventory().setItem(i, new ItemStack(Items.DIRT, 64));   // o ferro fica no slot 0: mochila cheia
        }
        BenchResults.pay(p, menu, new ItemStack(Items.IRON_INGOT));
        check(h, pay.getItem().is(Items.GOLD_INGOT) && own(p, Items.IRON_INGOT) == 1,
                "mochila cheia: o ouro do jogador fica e o ferro não sai");
        for (int i = 1; i < size; i++) {
            p.getInventory().setItem(i, ItemStack.EMPTY);
        }
        pay.set(ItemStack.EMPTY);
        p.containerMenu = p.inventoryMenu;
        clean(lab, h);
    }

    /**
     * As estações de item solto com baús desligados: o item só na mochila aparece no painel e o clique o põe no slot
     * certo (ferraria, bigorna, amolar, cartografia, encantamento, defumador, alto-forno e o ingrediente das poções).
     */
    @GameTest
    public void looseItemStationsWorkWithTheBackpackAlone(GameTestHelper h) {
        Lab lab = new Lab(h);
        ServerPlayer p = backpackOnly(lab, 4, 4);
        record Case(AbstractContainerMenu menu, Item item, int slot) {
        }
        List<Case> cases = List.of(
                new Case(new SmithingMenu(1, p.getInventory()), Items.COAST_ARMOR_TRIM_SMITHING_TEMPLATE, 0),
                new Case(new AnvilMenu(2, p.getInventory()), Items.GOLDEN_PICKAXE, 0),
                new Case(new GrindstoneMenu(3, p.getInventory()), Items.GOLDEN_SWORD, 0),
                new Case(new CartographyTableMenu(4, p.getInventory()), Items.PAPER, 1),
                new Case(new EnchantmentMenu(5, p.getInventory()), Items.BOOK, 0),
                new Case(new SmokerMenu(6, p.getInventory()), Items.MUTTON, 0),
                new Case(new BlastFurnaceMenu(7, p.getInventory()), Items.RAW_COPPER, 0),
                new Case(new BrewingStandMenu(8, p.getInventory()), Items.SPIDER_EYE, 3));
        for (Case c : cases) {
            Lab.give(p, 0, c.item(), 1);
            p.containerMenu = c.menu();
            check(h, lists(p, c.item()), c.menu().getClass().getSimpleName() + ": " + c.item() + " da mochila aparece");
            BenchResults.place(p, c.menu(), new ItemStack(c.item()), false);
            check(h, c.menu().getSlot(c.slot()).getItem().is(c.item()) && own(p, c.item()) == 0,
                    c.menu().getClass().getSimpleName() + ": foi para o slot " + c.slot() + ": " + c.menu().getSlot(c.slot()).getItem());
            c.menu().getSlot(c.slot()).set(ItemStack.EMPTY);
        }
        p.containerMenu = p.inventoryMenu;
        clean(lab, h);
    }

    /**
     * Dois jogadores lado a lado, baús desligados: tudo está na mochila de A, nada na de B. Em nenhuma estação B vê ou
     * usa o que é de A (fornalha, cortador, lápis automático, sinalizador, poções), nem com a mesma fornalha aberta
     * pelos dois; e A usa o dele normalmente, sem tocar em B.
     */
    @GameTest
    public void twoPlayersNeverUseEachOthersBackpack(GameTestHelper h) {
        Lab lab = new Lab(h);
        ServerPlayer a = backpackOnly(lab, 4, 4);
        ServerPlayer b = backpackOnly(lab, 6, 4);
        Lab.give(a, 0, Items.RAW_COPPER, 8);
        Lab.give(a, 1, Items.LAPIS_LAZULI, 6);
        Lab.give(a, 2, Items.RED_SANDSTONE, 6);
        Lab.give(a, 3, Items.IRON_INGOT, 2);
        a.getInventory().setItem(4, potion(Potions.WATER));
        Lab.give(a, 5, Items.NETHER_WART, 1);
        Lab.give(a, 6, Items.BLAZE_POWDER, 1);
        Lab.give(b, 0, Items.DIRT, 1);   // B só tem terra

        // Mesma fornalha (bloco de verdade) aberta pelos dois.
        Container block = lab.block(Blocks.FURNACE, 4, 1, 6);
        FurnaceMenu fa = new FurnaceMenu(1, a.getInventory(), block, new SimpleContainerData(4));
        FurnaceMenu fb = new FurnaceMenu(2, b.getInventory(), block, new SimpleContainerData(4));
        a.containerMenu = fa;
        b.containerMenu = fb;
        check(h, !lists(b, Items.RAW_COPPER) && lists(a, Items.RAW_COPPER), "só A vê o cobre dele");
        BenchResults.place(b, fb, new ItemStack(Items.RAW_COPPER), false);
        BenchPullService.handle(b, new BenchPullRequest(2, new ItemStack(Items.RAW_COPPER), false, BenchResults.CURSOR));
        check(h, !block.getItem(0).is(Items.RAW_COPPER) && fb.getCarried().isEmpty() && own(a, Items.RAW_COPPER) == 8,
                "B pede o cobre de A: nada acontece");
        BenchResults.place(a, fa, new ItemStack(Items.RAW_COPPER), false);
        check(h, block.getItem(0).is(Items.RAW_COPPER) && block.getItem(0).getCount() == 8 && own(a, Items.RAW_COPPER) == 0,
                "A põe o cobre dele na fornalha");
        BenchResults.place(b, fb, new ItemStack(Items.RAW_COPPER), false);
        check(h, block.getItem(0).getCount() == 8 && own(b, Items.RAW_COPPER) == 0 && fb.getCarried().isEmpty(),
                "a fornalha nunca é fonte: B continua sem cobre");
        block.setItem(0, ItemStack.EMPTY);

        // Cortador: a receita que A pode fazer é vermelha (ou ausente) para B, e o pedido de B não monta nada.
        StonecutterMenu sa = new StonecutterMenu(3, a.getInventory());
        a.containerMenu = sa;
        BenchPoolSync.Entry stairs = result(BenchSync.snapshot(a), Items.RED_SANDSTONE_STAIRS);
        check(h, stairs != null && !stairs.missing(), "para A a escada é possível");
        a.containerMenu = a.inventoryMenu;
        StonecutterMenu sb = new StonecutterMenu(4, b.getInventory());
        b.containerMenu = sb;
        BenchPoolSync.Entry forB = result(BenchSync.snapshot(b), Items.RED_SANDSTONE_STAIRS);
        check(h, forB == null || forB.missing(), "para B não é possível: o arenito é de A");
        BenchResults.craft(b, sb, stairs.id(), stairs.item(), false);
        check(h, empty(sb, 0) && own(a, Items.RED_SANDSTONE) == 6, "o pedido de B não tira o arenito de A");

        // Poções: o passo que A pode montar não sai da mochila de A para o suporte de B.
        ItemStack awkward = potion(Potions.AWKWARD);
        a.containerMenu = new BrewingStandMenu(5, a.getInventory());
        BenchPoolSync.Entry brewA = result(BenchSync.snapshot(a), awkward);
        check(h, brewA != null && !brewA.missing(), "para A a estranha é possível");
        a.containerMenu = a.inventoryMenu;
        BrewingStandMenu pb = new BrewingStandMenu(6, b.getInventory());
        b.containerMenu = pb;
        BenchPoolSync.Entry brewB = result(BenchSync.snapshot(b), awkward);
        check(h, brewB == null || brewB.missing(), "para B a estranha é vermelha");
        BenchResults.craft(b, pb, brewA.id(), awkward, false);
        check(h, empty(pb, 0, 1, 2, 3, 4) && own(a, Items.POTION) == 1 && own(a, Items.NETHER_WART) == 1
                && own(a, Items.BLAZE_POWDER) == 1, "nada saiu da mochila de A");

        // Sinalizador: B não paga com o ferro de A.
        BeaconMenu bb = new BeaconMenu(7, b.getInventory());
        b.containerMenu = bb;
        BenchResults.pay(b, bb, new ItemStack(Items.IRON_INGOT));
        check(h, empty(bb, 0) && own(a, Items.IRON_INGOT) == 2, "B não paga com o ferro de A");

        // Lápis automático: a mesa de B nunca puxa o lápis de A.
        EnchantmentMenu eb = new EnchantmentMenu(8, b.getInventory(), at(lab, b, Blocks.ENCHANTING_TABLE));
        b.containerMenu = eb;
        h.runAfterDelay(3, () -> {
            check(h, empty(eb, 1) && own(a, Items.LAPIS_LAZULI) == 6, "a mesa de B não puxou o lápis de A");
            check(h, own(b, Items.DIRT) == 1 && own(b, Items.RAW_COPPER) + own(b, Items.LAPIS_LAZULI)
                    + own(b, Items.RED_SANDSTONE) + own(b, Items.IRON_INGOT) == 0, "B não recebeu nada de A");
            b.doCloseContainer();
            clean(lab, h);
        });
    }
}
