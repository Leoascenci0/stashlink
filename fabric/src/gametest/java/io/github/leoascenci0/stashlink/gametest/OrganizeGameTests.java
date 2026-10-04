package io.github.leoascenci0.stashlink.gametest;

import io.github.leoascenci0.stashlink.compat.mc.OrganizeCompat;
import io.github.leoascenci0.stashlink.config.Feature;
import io.github.leoascenci0.stashlink.config.PlayerPrefs;
import io.github.leoascenci0.stashlink.config.PlayerPrefsStore;
import io.github.leoascenci0.stashlink.config.StashLinkConfig;
import io.github.leoascenci0.stashlink.label.Label;
import io.github.leoascenci0.stashlink.label.Labels;
import io.github.leoascenci0.stashlink.network.OrganizeRequest;
import io.github.leoascenci0.stashlink.network.OrganizeSync;
import io.github.leoascenci0.stashlink.organize.OrganizeHighlight;
import io.github.leoascenci0.stashlink.organize.OrganizeLogic;
import io.github.leoascenci0.stashlink.organize.OrganizeService;
import io.github.leoascenci0.stashlink.organize.OrganizeSystem;
import io.github.leoascenci0.stashlink.quickstack.QuickStackReceive;
import io.github.leoascenci0.stashlink.quickstack.QuickStackService;
import io.github.leoascenci0.stashlink.slotlock.SlotLocks;
import io.github.leoascenci0.stashlink.source.ContainerSource;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Item 20: organizar o armazenamento. Servidor de verdade, baús de verdade, jogadores em sobrevivência. Cada teste usa itens
 * que nenhum outro usa (os testes rodam lado a lado no mesmo mundo) e um raio pequeno, para a arrumação não alcançar baús de
 * outros testes. A regra de ouro de todos: a soma de cada item nunca muda.
 */
public class OrganizeGameTests {
    private static final int RADIUS = 4;

    private static void check(GameTestHelper h, boolean ok, String msg) {
        h.assertTrue(ok, msg);
    }

    private static ServerPlayer player(Lab lab) {
        return player(lab, 0);
    }

    private static ServerPlayer player(Lab lab, int disabledMask) {
        ServerPlayer p = lab.player(4, 2, 4);
        PlayerPrefsStore.set(p.getUUID(), new PlayerPrefs(RADIUS, 1, List.of(), disabledMask, RADIUS));
        return p;
    }

    private static void request(ServerPlayer p, int action) {
        OrganizeService.handle(p, OrganizeRequest.of(action));
    }

    private static void organizeChest(ServerPlayer p, int menuId) {
        OrganizeService.handle(p, new OrganizeRequest(OrganizeRequest.CHEST, menuId, "", List.of(), BlockPos.ZERO));
    }

    private static OrganizeLogic.Totals total(Container... containers) {
        OrganizeLogic.Totals totals = new OrganizeLogic.Totals();
        for (Container c : containers) {
            totals.addAll(OrganizeLogic.snapshot(c));
        }
        return totals;
    }

    private static boolean same(Container c, List<ItemStack> snapshot) {
        return OrganizeLogic.sameContents(c, snapshot);
    }

    private static void resetLocks() {
        StashLinkConfig.lockedFeatures = EnumSet.noneOf(Feature.class);
    }

    // ----------------------------------------------------------------------------------------------- 20.1 um baú

    @GameTest
    public void chestOrganizeMergesAndSorts(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 3);
        ItemStack worn = new ItemStack(Items.GOLDEN_CHESTPLATE);
        worn.setDamageValue(5);
        chest.setItem(0, new ItemStack(Items.CALCITE, 10));
        chest.setItem(3, new ItemStack(Items.DRIPSTONE_BLOCK, 5));
        chest.setItem(5, worn);
        chest.setItem(6, new ItemStack(Items.GOLDEN_CHESTPLATE));
        chest.setItem(7, new ItemStack(Items.CALCITE, 20));
        chest.setItem(9, new ItemStack(Items.SWEET_BERRIES, 60));
        chest.setItem(12, new ItemStack(Items.DRIPSTONE_BLOCK, 30));
        chest.setItem(14, new ItemStack(Items.SWEET_BERRIES, 7));
        OrganizeLogic.Totals before = total(chest);

        ServerPlayer p = player(lab);
        Lab.open(p, chest, 1);
        organizeChest(p, 1);
        check(h, total(chest).equals(before), "a soma de itens mudou");
        check(h, chest.getItem(0).is(Items.CALCITE) && chest.getItem(0).getCount() == 30, "calcita junta: " + chest.getItem(0));
        check(h, chest.getItem(1).is(Items.DRIPSTONE_BLOCK) && chest.getItem(1).getCount() == 35, "pedra de gotejamento junta");
        check(h, chest.getItem(2).is(Items.GOLDEN_CHESTPLATE) && chest.getItem(3).is(Items.GOLDEN_CHESTPLATE),
                "armadura depois dos blocos; componentes diferentes ficam separados");
        check(h, chest.getItem(4).is(Items.SWEET_BERRIES) && chest.getItem(4).getCount() == 64
                && chest.getItem(5).getCount() == 3, "comida por último, stack cheio + resto");
        for (int slot = 6; slot < 27; slot++) {
            check(h, chest.getItem(slot).isEmpty(), "slot " + slot + " devia estar vazio");
        }
        // idempotente: outro jogador (o intervalo é por jogador) organiza de novo e nada muda
        List<ItemStack> once = OrganizeLogic.snapshot(chest);
        ServerPlayer q = player(lab);
        Lab.open(q, chest, 2);
        organizeChest(q, 2);
        check(h, same(chest, once), "organizar duas vezes mexeu de novo");
        lab.cleanup();
        h.succeed();
    }

    @GameTest
    public void chestOrganizeKeepsReservedSlots(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 3);
        chest.setItem(0, new ItemStack(Items.CALCITE, 5));
        chest.setItem(9, new ItemStack(Items.DRIPSTONE_BLOCK, 3));
        chest.setItem(20, new ItemStack(Items.CALCITE, 7));
        SlotLocks.toggle(chest, 4, new ItemStack(Items.AMETHYST_BLOCK));       // reservado e vazio
        SlotLocks.toggle(chest, 9, new ItemStack(Items.DRIPSTONE_BLOCK));      // reservado e ocupado
        OrganizeLogic.Totals before = total(chest);

        ServerPlayer p = player(lab);
        Lab.open(p, chest, 1);
        organizeChest(p, 1);
        check(h, total(chest).equals(before), "a soma de itens mudou");
        check(h, chest.getItem(4).isEmpty(), "o slot reservado vazio continua vazio (nada de outro item entra)");
        check(h, chest.getItem(9).is(Items.DRIPSTONE_BLOCK) && chest.getItem(9).getCount() == 3, "o slot reservado ocupado não é tocado");
        check(h, chest.getItem(0).is(Items.CALCITE) && chest.getItem(0).getCount() == 12, "calcita juntou nos slots livres");
        check(h, SlotLocks.lockedItem(chest, 4) == Items.AMETHYST_BLOCK && SlotLocks.lockedItem(chest, 9) == Items.DRIPSTONE_BLOCK,
                "as reservas continuam");
        lab.cleanup();
        h.succeed();
    }

    @GameTest
    public void chestOrganizeIsRefusedWhileAnotherPlayerHasItOpen(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 3);
        chest.setItem(0, new ItemStack(Items.CALCITE, 5));
        chest.setItem(9, new ItemStack(Items.CALCITE, 5));
        ServerPlayer other = player(lab);
        Lab.open(other, chest, 1);
        ServerPlayer p = player(lab);
        Lab.open(p, chest, 2);
        List<ItemStack> before = OrganizeLogic.snapshot(chest);
        organizeChest(p, 2);
        check(h, same(chest, before), "não organiza baú que outro jogador está olhando");
        Lab.close(other);
        Lab.close(p);
        ServerPlayer p2 = player(lab);
        Lab.open(p2, chest, 3);
        organizeChest(p2, 3);
        check(h, chest.getItem(0).getCount() == 10, "com o baú livre, organiza");
        lab.cleanup();
        h.succeed();
    }

    @GameTest
    public void chestOrganizeIgnoresAWrongMenuId(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 3);
        chest.setItem(0, new ItemStack(Items.CALCITE, 5));
        chest.setItem(9, new ItemStack(Items.CALCITE, 5));
        List<ItemStack> before = OrganizeLogic.snapshot(chest);
        ServerPlayer p = player(lab);
        Lab.open(p, chest, 1);
        organizeChest(p, 99);
        check(h, same(chest, before), "menu errado: nada muda");
        ServerPlayer q = player(lab);                                           // sem menu aberto
        organizeChest(q, 1);
        check(h, same(chest, before), "sem menu aberto: nada muda");
        lab.cleanup();
        h.succeed();
    }

    @GameTest
    public void doubleChestOrganizes(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.doubleChest(1, 2, 3);
        for (int i = 0; i < 20; i++) {
            chest.setItem(i * 2 % 54, new ItemStack(i % 2 == 0 ? Items.CALCITE : Items.MUD_BRICKS, 5 + i));
        }
        OrganizeLogic.Totals before = total(chest);
        ServerPlayer p = player(lab);
        Lab.open(p, chest, 1);
        organizeChest(p, 1);
        check(h, total(chest).equals(before), "a soma de itens mudou");
        check(h, chest.getItem(0).is(Items.CALCITE) && chest.getItem(2).is(Items.CALCITE) && chest.getItem(2).getCount() == 12
                && chest.getItem(3).is(Items.MUD_BRICKS) && chest.getItem(5).is(Items.MUD_BRICKS) && chest.getItem(6).isEmpty(),
                "calcita (3 stacks) e depois tijolos de lama (3 stacks), sem sobras");
        lab.cleanup();
        h.succeed();
    }

    @GameTest
    public void organizeFeatureLockedDoesNothing(GameTestHelper h) {
        resetLocks();
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 3);
        chest.setItem(0, new ItemStack(Items.CALCITE, 5));
        chest.setItem(9, new ItemStack(Items.CALCITE, 5));
        List<ItemStack> before = OrganizeLogic.snapshot(chest);
        StashLinkConfig.setFeatureLocked(Feature.ORGANIZE, true);
        ServerPlayer p = player(lab);
        Lab.open(p, chest, 1);
        organizeChest(p, 1);
        ServerPlayer viewer = player(lab);                                      // sem menu aberto: poderia pedir a prévia
        request(viewer, OrganizeRequest.PREVIEW);
        check(h, same(chest, before) && !OrganizeService.hasPending(viewer), "função trancada: nada acontece");
        resetLocks();
        // desligada pelo próprio jogador
        ServerPlayer q = player(lab, Feature.ORGANIZE.bit());
        Lab.open(q, chest, 2);
        organizeChest(q, 2);
        check(h, same(chest, before), "função desligada pelo jogador: nada acontece");
        lab.cleanup();
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------ 20.2 o sistema todo

    /** Três baús: calcita tem casa no B (50), pedra de gotejamento no C (40); o A só tem sobras. */
    private record Trio(Container a, Container b, Container c) {
        static Trio build(Lab lab) {
            Container a = lab.chest(2, 2, 3);
            Container b = lab.chest(6, 2, 3);
            Container c = lab.chest(4, 2, 6);
            a.setItem(7, new ItemStack(Items.CALCITE, 10));
            a.setItem(2, new ItemStack(Items.DRIPSTONE_BLOCK, 5));
            b.setItem(9, new ItemStack(Items.CALCITE, 50));
            b.setItem(1, new ItemStack(Items.MUD_BRICKS, 3));
            c.setItem(4, new ItemStack(Items.DRIPSTONE_BLOCK, 40));
            c.setItem(11, new ItemStack(Items.CALCITE, 3));
            return new Trio(a, b, c);
        }
    }

    @GameTest
    public void systemPreviewApplyAndUndo(GameTestHelper h) {
        Lab lab = new Lab(h);
        Trio t = Trio.build(lab);
        OrganizeLogic.Totals before = total(t.a(), t.b(), t.c());
        List<ItemStack> sa = OrganizeLogic.snapshot(t.a());
        List<ItemStack> sb = OrganizeLogic.snapshot(t.b());
        List<ItemStack> sc = OrganizeLogic.snapshot(t.c());
        ServerPlayer p = player(lab);

        request(p, OrganizeRequest.PREVIEW);
        check(h, OrganizeService.hasPending(p), "a prévia ficou guardada");
        check(h, same(t.a(), sa) && same(t.b(), sb) && same(t.c(), sc), "a prévia não pode mexer em nada");

        request(p, OrganizeRequest.APPLY);
        check(h, OrganizeService.hasApplied(p) && !OrganizeService.hasPending(p), "aplicado");
        check(h, Lab.count(t.b(), Items.CALCITE) == 63 && Lab.count(t.c(), Items.DRIPSTONE_BLOCK) == 45
                && Lab.count(t.a(), Items.CALCITE) == 0 && Lab.count(t.a(), Items.DRIPSTONE_BLOCK) == 0 && Lab.count(t.c(), Items.CALCITE) == 0,
                "cada item na sua casa. B calcita=" + Lab.count(t.b(), Items.CALCITE) + " C pedra=" + Lab.count(t.c(), Items.DRIPSTONE_BLOCK));
        check(h, total(t.a(), t.b(), t.c()).equals(before), "a soma de itens mudou ao aplicar");

        request(p, OrganizeRequest.UNDO);
        check(h, !OrganizeService.hasApplied(p), "desfeito");
        check(h, same(t.a(), sa) && same(t.b(), sb) && same(t.c(), sc), "desfazer devolve cada item ao slot de antes");
        lab.cleanup();
        h.succeed();
    }

    @GameTest
    public void applyIsRefusedWhenAChestChangedAfterThePreview(GameTestHelper h) {
        Lab lab = new Lab(h);
        Trio t = Trio.build(lab);
        ServerPlayer p = player(lab);
        request(p, OrganizeRequest.PREVIEW);
        t.a().setItem(20, new ItemStack(Items.MOSS_BLOCK, 4));                  // alguém mexeu depois da prévia
        List<ItemStack> sa = OrganizeLogic.snapshot(t.a());
        List<ItemStack> sb = OrganizeLogic.snapshot(t.b());
        List<ItemStack> sc = OrganizeLogic.snapshot(t.c());
        request(p, OrganizeRequest.APPLY);
        check(h, same(t.a(), sa) && same(t.b(), sb) && same(t.c(), sc), "prévia velha: nada é gravado");
        check(h, !OrganizeService.hasApplied(p) && !OrganizeService.hasPending(p), "a prévia velha é descartada");
        lab.cleanup();
        h.succeed();
    }

    @GameTest
    public void undoIsRefusedWhenAChestChangedAfterApplying(GameTestHelper h) {
        Lab lab = new Lab(h);
        Trio t = Trio.build(lab);
        ServerPlayer p = player(lab);
        request(p, OrganizeRequest.PREVIEW);
        request(p, OrganizeRequest.APPLY);
        t.b().setItem(25, new ItemStack(Items.MOSS_BLOCK, 4));
        OrganizeLogic.Totals now = total(t.a(), t.b(), t.c());
        List<ItemStack> sb = OrganizeLogic.snapshot(t.b());
        request(p, OrganizeRequest.UNDO);
        check(h, same(t.b(), sb) && total(t.a(), t.b(), t.c()).equals(now), "desfazer recusado não grava nada");
        check(h, OrganizeService.hasApplied(p), "o desfazer continua à espera (o jogador pode refazer o baú como estava)");
        lab.cleanup();
        h.succeed();
    }

    @GameTest
    public void systemNeverTouchesFurnaceOrStations(GameTestHelper h) {
        Lab lab = new Lab(h);
        Trio t = Trio.build(lab);
        Container furnace = lab.block(Blocks.FURNACE, 4, 2, 1);
        furnace.setItem(0, new ItemStack(Items.CALCITE, 8));                    // item igual aos dos baús
        furnace.setItem(1, new ItemStack(Items.COAL, 3));
        List<ItemStack> sf = OrganizeLogic.snapshot(furnace);
        ServerPlayer p = player(lab);
        request(p, OrganizeRequest.PREVIEW);
        request(p, OrganizeRequest.APPLY);
        check(h, furnace instanceof FurnaceBlockEntity && same(furnace, sf), "a fornalha nunca é tocada");
        check(h, Lab.count(t.b(), Items.CALCITE) == 63, "e o sistema foi organizado normalmente");
        lab.cleanup();
        h.succeed();
    }

    @GameTest
    public void systemLeavesAChestWithNOffAlone(GameTestHelper h) {
        Lab lab = new Lab(h);
        Trio t = Trio.build(lab);
        QuickStackReceive.set(t.b(), false);                                    // B (a casa da calcita) não recebe com a N
        List<ItemStack> sb = OrganizeLogic.snapshot(t.b());
        OrganizeLogic.Totals before = total(t.a(), t.b(), t.c());
        ServerPlayer p = player(lab);
        request(p, OrganizeRequest.PREVIEW);
        request(p, OrganizeRequest.APPLY);
        check(h, same(t.b(), sb), "baú com a N desligada não recebe, não é esvaziado e não é reordenado");
        check(h, Lab.count(t.a(), Items.CALCITE) == 13 && Lab.count(t.c(), Items.CALCITE) == 0,
                "entre os baús que recebem, a calcita vai para o que tem mais (A): " + Lab.count(t.a(), Items.CALCITE));
        check(h, total(t.a(), t.b(), t.c()).equals(before), "a soma de itens mudou");
        lab.cleanup();
        h.succeed();
    }

    @GameTest
    public void systemHonoursTheCategoryFilter(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container a = lab.chest(2, 2, 3);
        Container b = lab.chest(6, 2, 3);
        a.setItem(0, new ItemStack(Items.SWEET_BERRIES, 5));
        b.setItem(0, new ItemStack(Items.SWEET_BERRIES, 30));
        ServerPlayer p = player(lab, Feature.CAT_FOOD.bit());                  // este jogador desligou "comida" na N
        request(p, OrganizeRequest.PREVIEW);
        request(p, OrganizeRequest.APPLY);
        check(h, Lab.count(a, Items.SWEET_BERRIES) == 5 && Lab.count(b, Items.SWEET_BERRIES) == 30,
                "categoria desligada: a comida nunca se move");
        ServerPlayer q = player(lab);                                           // ligada: move
        request(q, OrganizeRequest.PREVIEW);
        request(q, OrganizeRequest.APPLY);
        check(h, Lab.count(a, Items.SWEET_BERRIES) == 0 && Lab.count(b, Items.SWEET_BERRIES) == 35, "categoria ligada: move");
        lab.cleanup();
        h.succeed();
    }

    @GameTest
    public void systemFillsAReservedSlotAndNeverEmptiesOne(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container a = lab.chest(2, 2, 3);
        Container b = lab.chest(6, 2, 3);
        a.setItem(2, new ItemStack(Items.CALCITE, 5));
        SlotLocks.toggle(a, 2, new ItemStack(Items.CALCITE));                  // A tem um slot reservado para calcita
        b.setItem(0, new ItemStack(Items.CALCITE, 50));
        b.setItem(9, new ItemStack(Items.DRIPSTONE_BLOCK, 7));
        SlotLocks.toggle(b, 9, new ItemStack(Items.DRIPSTONE_BLOCK));          // B tem um reservado ocupado
        a.setItem(11, new ItemStack(Items.DRIPSTONE_BLOCK, 30));               // A tem mais pedra, mas B reserva
        OrganizeLogic.Totals before = total(a, b);
        ServerPlayer p = player(lab);
        request(p, OrganizeRequest.PREVIEW);
        request(p, OrganizeRequest.APPLY);
        check(h, total(a, b).equals(before), "a soma de itens mudou");
        check(h, Lab.count(a, Items.CALCITE) == 55 && Lab.count(b, Items.CALCITE) == 0, "quem reserva é a casa: calcita foi para A");
        check(h, b.getItem(9).is(Items.DRIPSTONE_BLOCK) && Lab.count(b, Items.DRIPSTONE_BLOCK) == 37 && Lab.count(a, Items.DRIPSTONE_BLOCK) == 0,
                "pedra foi para a casa que a reserva (B), e o slot reservado de B continua ocupado");
        lab.cleanup();
        h.succeed();
    }

    @GameTest
    public void aChestOpenByAnotherPlayerIsLeftAlone(GameTestHelper h) {
        Lab lab = new Lab(h);
        Trio t = Trio.build(lab);
        ServerPlayer other = player(lab);
        Lab.open(other, t.b(), 1);                                              // B (a casa da calcita) está aberto por outro
        List<ItemStack> sb = OrganizeLogic.snapshot(t.b());
        OrganizeLogic.Totals before = total(t.a(), t.b(), t.c());
        ServerPlayer p = player(lab);
        request(p, OrganizeRequest.PREVIEW);
        request(p, OrganizeRequest.APPLY);
        check(h, same(t.b(), sb), "baú aberto por outro jogador não é mexido");
        check(h, total(t.a(), t.b(), t.c()).equals(before), "a soma de itens mudou");
        Lab.close(other);
        lab.cleanup();
        h.succeed();
    }

    @GameTest
    public void aSecondPlayerOpeningAChestBetweenPreviewAndApplyBlocksIt(GameTestHelper h) {
        Lab lab = new Lab(h);
        Trio t = Trio.build(lab);
        ServerPlayer p = player(lab);
        request(p, OrganizeRequest.PREVIEW);
        ServerPlayer other = player(lab);
        Lab.open(other, t.b(), 1);                                              // abriu depois da prévia
        List<ItemStack> sa = OrganizeLogic.snapshot(t.a());
        List<ItemStack> sb = OrganizeLogic.snapshot(t.b());
        List<ItemStack> sc = OrganizeLogic.snapshot(t.c());
        request(p, OrganizeRequest.APPLY);
        check(h, same(t.a(), sa) && same(t.b(), sb) && same(t.c(), sc), "aplicar com um baú do plano aberto por outro: nada grava");
        Lab.close(other);
        lab.cleanup();
        h.succeed();
    }

    @GameTest
    public void applyingTwiceOnlyAppliesOnce(GameTestHelper h) {
        Lab lab = new Lab(h);
        Trio t = Trio.build(lab);
        OrganizeLogic.Totals before = total(t.a(), t.b(), t.c());
        ServerPlayer p = player(lab);
        request(p, OrganizeRequest.PREVIEW);
        request(p, OrganizeRequest.APPLY);
        List<ItemStack> after = OrganizeLogic.snapshot(t.b());
        request(p, OrganizeRequest.APPLY);                                   // sem prévia nova: não há o que aplicar
        check(h, OrganizeLogic.sameContents(t.b(), after) && total(t.a(), t.b(), t.c()).equals(before), "aplicar de novo não faz nada");
        lab.cleanup();
        h.succeed();
    }

    // ----------------------------------------------------------------------------- 2 jogadores, soma conferida (fuzz)

    /**
     * 400 ações aleatórias em 4 baús (aplicar, desfazer, mexer à mão, ligar/desligar a N, reservar slot, abrir/fechar por outro
     * jogador, organizar um baú): depois de CADA ação a soma de cada item é a esperada, um plano nunca mexe em nada, e um slot
     * reservado nunca muda por causa de aplicar/desfazer.
     */
    @GameTest(maxTicks = 400)
    public void fuzzNeverDuplicatesOrLosesItems(GameTestHelper h) {
        Lab lab = new Lab(h);
        List<Container> boxes = List.of(lab.chest(2, 2, 3), lab.chest(6, 2, 3), lab.chest(4, 2, 6), lab.chest(1, 2, 1));
        Item[] items = {Items.CALCITE, Items.DRIPSTONE_BLOCK, Items.MUD_BRICKS, Items.SWEET_BERRIES, Items.GOLDEN_PICKAXE,
                Items.GOLDEN_CHESTPLATE, Items.PODZOL};
        Random rnd = new Random(2026);
        for (Container c : boxes) {
            for (int i = 0; i < 12; i++) {
                Item item = items[rnd.nextInt(items.length)];
                c.setItem(rnd.nextInt(27), new ItemStack(item, 1 + rnd.nextInt(item.getDefaultMaxStackSize())));
            }
        }
        ServerPlayer a = player(lab);
        ServerPlayer b = player(lab);
        OrganizeSystem.Plan applied = null;
        OrganizeLogic.Totals expected = total(boxes.toArray(new Container[0]));
        for (int step = 0; step < 400; step++) {
            ServerPlayer actor = rnd.nextBoolean() ? a : b;
            ServerPlayer other = actor == a ? b : a;
            switch (rnd.nextInt(8)) {
                case 0, 1 -> {                                                  // plano + aplicar (via API, sem o intervalo)
                    List<ContainerSource.Entry> entries = OrganizeService.entries(actor);
                    List<List<ItemStack>> snaps = boxes.stream().map(OrganizeLogic::snapshot).toList();
                    OrganizeSystem.Plan plan = OrganizeSystem.plan(entries, stack -> false);
                    for (int i = 0; i < boxes.size(); i++) {
                        check(h, same(boxes.get(i), snaps.get(i)), "o plano mexeu no baú " + i + " (passo " + step + ")");
                    }
                    if (!plan.isEmpty() && OrganizeSystem.apply(plan, OrganizeService.entries(actor))) {
                        applied = plan;
                        for (int i = 0; i < boxes.size(); i++) {
                            for (int slot = 0; slot < 27; slot++) {
                                Item reserved = SlotLocks.lockedItem(boxes.get(i), slot);
                                if (reserved != null) {
                                    // slot reservado nunca perde item; só pode crescer, e só com o item da reserva
                                    ItemStack was = snaps.get(i).get(slot);
                                    ItemStack now = boxes.get(i).getItem(slot);
                                    boolean untouched = ItemStack.matches(now, was);
                                    boolean grew = !was.isEmpty() && now.is(reserved) && ItemStack.isSameItemSameComponents(now, was)
                                            && now.getCount() >= was.getCount();
                                    boolean filled = was.isEmpty() && now.is(reserved);
                                    check(h, untouched || grew || filled,
                                            "slot reservado perdeu item ao aplicar (passo " + step + "): " + was + " -> " + now);
                                }
                            }
                        }
                    }
                }
                case 2 -> {                                                     // desfazer, se ainda der
                    if (applied != null) {
                        OrganizeSystem.undo(applied, OrganizeService.entries(actor));
                    }
                }
                case 3 -> {                                                     // alguém mexe à mão num baú
                    Container c = boxes.get(rnd.nextInt(boxes.size()));
                    int slot = rnd.nextInt(27);
                    c.setItem(slot, rnd.nextInt(3) == 0 ? ItemStack.EMPTY
                            : new ItemStack(items[rnd.nextInt(items.length)], 1 + rnd.nextInt(8)));
                    expected = total(boxes.toArray(new Container[0]));
                }
                case 4 -> QuickStackReceive.set(boxes.get(rnd.nextInt(boxes.size())), rnd.nextBoolean());
                case 5 -> {
                    Container c = boxes.get(rnd.nextInt(boxes.size()));
                    SlotLocks.toggle(c, rnd.nextInt(27), new ItemStack(items[rnd.nextInt(items.length)]));
                }
                case 6 -> {                                                     // outro jogador abre ou fecha um baú
                    if (rnd.nextBoolean()) {
                        Lab.open(other, boxes.get(rnd.nextInt(boxes.size())), 20 + step);
                    } else {
                        Lab.close(other);
                    }
                }
                default -> {                                                    // organizar um baú pelo botão
                    Container c = boxes.get(rnd.nextInt(boxes.size()));
                    ServerPlayer fresh = player(lab);                           // o intervalo é por jogador
                    Lab.open(fresh, c, 40 + step);
                    organizeChest(fresh, 40 + step);
                    Lab.close(fresh);
                }
            }
            check(h, total(boxes.toArray(new Container[0])).equals(expected), "a soma de itens mudou no passo " + step);
        }
        Lab.close(a);
        Lab.close(b);
        lab.cleanup();
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------ 20.3 busca e destaque

    @GameTest
    public void searchFindsTheContainersThatHaveTheItem(GameTestHelper h) {
        Lab lab = new Lab(h);
        Trio t = Trio.build(lab);
        BlockEntity beB = (BlockEntity) t.b();
        Labels.set(beB, new Label("Minerios", ""));
        ServerPlayer p = player(lab);

        // o cliente manda os itens que casam no idioma dele
        var hits = OrganizeService.find(p, new OrganizeRequest(OrganizeRequest.SEARCH, 0, "calcita",
                List.of(BuiltInRegistries.ITEM.getKey(Items.CALCITE)), BlockPos.ZERO));
        check(h, hits != null && hits.size() == 3, "calcita está nos 3 baús: " + (hits == null ? null : hits.size()));
        int total = hits.stream().mapToInt(hit -> hit.row().count()).sum();
        check(h, total == 63, "a soma bate com os baús: " + total);
        var inB = hits.stream().filter(hit -> hit.where().contains(beB.getBlockPos())).findFirst().orElseThrow();
        check(h, inB.row().count() == 50 && inB.row().fromName().equals("Minerios") && inB.row().item().is(Items.CALCITE),
                "linha do baú B: " + inB.row());
        for (int i = 1; i < hits.size(); i++) {
            double d0 = p.distanceToSqr(hits.get(i - 1).row().from().getX() + .5, hits.get(i - 1).row().from().getY() + .5,
                    hits.get(i - 1).row().from().getZ() + .5);
            double d1 = p.distanceToSqr(hits.get(i).row().from().getX() + .5, hits.get(i).row().from().getY() + .5,
                    hits.get(i).row().from().getZ() + .5);
            check(h, d0 <= d1 + 1e-6, "do mais perto ao mais longe");
        }

        // reserva sem lista de itens: casa pelo id em inglês
        var byText = OrganizeService.find(p, new OrganizeRequest(OrganizeRequest.SEARCH, 0, "mud bricks", List.of(), BlockPos.ZERO));
        check(h, byText != null && byText.size() == 1 && byText.get(0).row().count() == 3, "busca por texto");
        check(h, OrganizeService.find(p, new OrganizeRequest(OrganizeRequest.SEARCH, 0, "", List.of(), BlockPos.ZERO)) == null,
                "pedido vazio não busca nada");
        var none = OrganizeService.find(p, new OrganizeRequest(OrganizeRequest.SEARCH, 0, "sculk_catalyst", List.of(), BlockPos.ZERO));
        check(h, none != null && none.isEmpty(), "ninguém tem");
        lab.cleanup();
        h.succeed();
    }

    @GameTest
    public void searchOnlyReachesContainersInRange(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 3);                                    // a uns 2,8 blocos do jogador
        chest.setItem(0, new ItemStack(Items.PODZOL, 4));
        ServerPlayer p = lab.player(4, 2, 4);
        PlayerPrefsStore.set(p.getUUID(), new PlayerPrefs(1, 1, List.of(), 0, 1));   // raio mínimo
        var hits = OrganizeService.find(p, new OrganizeRequest(OrganizeRequest.SEARCH, 0, "podzol", List.of(), BlockPos.ZERO));
        check(h, hits != null && hits.isEmpty(), "fora do raio o baú não aparece: " + hits);
        ServerPlayer q = player(lab);
        var ok = OrganizeService.find(q, new OrganizeRequest(OrganizeRequest.SEARCH, 0, "podzol", List.of(), BlockPos.ZERO));
        check(h, ok != null && ok.size() == 1, "dentro do raio aparece");
        lab.cleanup();
        h.succeed();
    }

    @GameTest
    public void highlightIsOwnerOnlyTemporaryAndNeverSaved(GameTestHelper h) {
        Lab lab = new Lab(h);
        Trio t = Trio.build(lab);
        ServerPlayer p = player(lab);
        ServerPlayer other = player(lab);
        BlockPos pos = ((BlockEntity) t.b()).getBlockPos();

        OrganizeService.handle(p, new OrganizeRequest(OrganizeRequest.HIGHLIGHT, 0, "", List.of(), pos));
        check(h, OrganizeHighlight.count(p) == 1, "um contorno");
        Entity glow = h.getLevel().getEntitiesOfClass(Entity.class, new net.minecraft.world.phys.AABB(pos).inflate(3),
                e -> e.entityTags().contains(OrganizeCompat.HIGHLIGHT_TAG)).stream().findFirst().orElse(null);
        check(h, glow != null && OrganizeCompat.glows(glow), "a entidade brilha");
        check(h, !glow.shouldBeSaved(), "nunca vai para o disco");
        check(h, !OrganizeCompat.hiddenFrom(glow, p) && OrganizeCompat.hiddenFrom(glow, other), "só quem buscou vê");
        check(h, glow.getX() <= pos.getX() && glow.getX() > pos.getX() - 0.1, "contorno em volta do baú");

        // um destaque novo troca o anterior; fora do alcance não destaca
        ServerPlayer q = player(lab);
        OrganizeService.handle(q, new OrganizeRequest(OrganizeRequest.HIGHLIGHT, 0, "", List.of(), pos.offset(40, 0, 0)));
        check(h, OrganizeHighlight.count(q) == 0, "posição fora do alcance (ou sem container) não destaca");
        OrganizeHighlight.clear(p);
        check(h, OrganizeHighlight.count(p) == 0 && glow.isRemoved(), "limpar apaga");
        lab.cleanup();
        h.succeed();
    }

    @GameTest
    public void highlightAllMarksEveryContainerThatMatches(GameTestHelper h) {
        Lab lab = new Lab(h);
        Trio t = Trio.build(lab);
        ServerPlayer p = player(lab);
        OrganizeService.handle(p, new OrganizeRequest(OrganizeRequest.HIGHLIGHT_ALL, 0, "calcite", List.of(), BlockPos.ZERO));
        check(h, OrganizeHighlight.count(p) == 3, "calcita está em 3 baús: " + OrganizeHighlight.count(p));
        OrganizeHighlight.clear(p);
        lab.cleanup();
        h.succeed();
    }

    @GameTest(maxTicks = 400)
    public void highlightExpires(GameTestHelper h) {
        Lab lab = new Lab(h);
        Trio t = Trio.build(lab);
        ServerPlayer p = player(lab);
        BlockPos pos = ((BlockEntity) t.a()).getBlockPos();
        OrganizeService.handle(p, new OrganizeRequest(OrganizeRequest.HIGHLIGHT, 0, "", List.of(), pos));
        check(h, OrganizeHighlight.count(p) == 1, "contorno criado");
        h.runAfterDelay(OrganizeHighlight.DURATION_TICKS + 20, () -> {
            check(h, OrganizeHighlight.count(p) == 0, "o contorno venceu e foi apagado");
            lab.cleanup();
            h.succeed();
        });
    }

    // ------------------------------------------------------------------------------------------------------- pacotes

    @GameTest
    public void packetsRoundTrip(GameTestHelper h) {
        Lab lab = new Lab(h);
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), lab.level.registryAccess());
        OrganizeRequest request = new OrganizeRequest(OrganizeRequest.SEARCH, 7, "ferro",
                List.of(BuiltInRegistries.ITEM.getKey(Items.IRON_INGOT), BuiltInRegistries.ITEM.getKey(Items.IRON_NUGGET)), new BlockPos(1, 2, 3));
        OrganizeRequest.STREAM_CODEC.encode(buf, request);
        OrganizeRequest back = OrganizeRequest.STREAM_CODEC.decode(buf);
        check(h, back.equals(request), "pedido deformado: " + back);

        RegistryFriendlyByteBuf buf2 = new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), lab.level.registryAccess());
        OrganizeSync sync = new OrganizeSync(OrganizeSync.PREVIEW, List.of(new OrganizeSync.Row(new ItemStack(Items.CALCITE), 12,
                new BlockPos(1, 2, 3), new BlockPos(4, 5, 6), "A", "")), true, false, 2, OrganizeSync.MSG_NONE);
        OrganizeSync.STREAM_CODEC.encode(buf2, sync);
        OrganizeSync back2 = OrganizeSync.STREAM_CODEC.decode(buf2);
        check(h, back2.rows().size() == 1 && back2.rows().get(0).count() == 12 && back2.rows().get(0).item().is(Items.CALCITE)
                && back2.rows().get(0).to().equals(new BlockPos(4, 5, 6)) && back2.canApply() && !back2.canUndo() && back2.tidied() == 2,
                "resposta deformada");
        ServerPlayer p = player(lab);
        check(h, !io.github.leoascenci0.stashlink.platform.Services.PLATFORM.sendIfSupported(p, sync), "cliente sem o mod não recebe");
        lab.cleanup();
        h.succeed();
    }

    /** O pacote de pedido vem da rede: valores absurdos não derrubam o servidor nem mexem em nada. */
    @GameTest
    public void garbageRequestsAreIgnored(GameTestHelper h) {
        Lab lab = new Lab(h);
        Trio t = Trio.build(lab);
        List<ItemStack> sa = OrganizeLogic.snapshot(t.a());
        List<ItemStack> sb = OrganizeLogic.snapshot(t.b());
        ServerPlayer p = player(lab);
        for (int action : new int[]{-1, 7, 99, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            OrganizeService.handle(p, new OrganizeRequest(action, -5, "x", List.of(), new BlockPos(0, 0, 0)));
        }
        OrganizeService.handle(p, new OrganizeRequest(OrganizeRequest.HIGHLIGHT, 0, "", List.of(), new BlockPos(30000000, 400, -30000000)));
        OrganizeService.handle(p, new OrganizeRequest(OrganizeRequest.SEARCH, 0, "%%\u0000‮",
                List.of(BuiltInRegistries.ITEM.getKey(Items.AIR)), BlockPos.ZERO));
        check(h, same(t.a(), sa) && same(t.b(), sb), "pedidos absurdos não mexem em nada");
        lab.cleanup();
        h.succeed();
    }
}
