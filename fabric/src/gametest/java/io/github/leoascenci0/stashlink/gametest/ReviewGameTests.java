package io.github.leoascenci0.stashlink.gametest;

import io.github.leoascenci0.stashlink.config.Feature;
import io.github.leoascenci0.stashlink.config.StashLinkConfig;
import io.github.leoascenci0.stashlink.bench.BenchPool;
import io.github.leoascenci0.stashlink.bench.BenchPullService;
import io.github.leoascenci0.stashlink.bench.BenchSync;
import io.github.leoascenci0.stashlink.config.PlayerPrefs;
import io.github.leoascenci0.stashlink.config.PlayerPrefsService;
import io.github.leoascenci0.stashlink.config.PlayerPrefsStore;
import io.github.leoascenci0.stashlink.label.LabelService;
import io.github.leoascenci0.stashlink.label.Labels;
import io.github.leoascenci0.stashlink.lootall.LootAllService;
import io.github.leoascenci0.stashlink.network.BenchPoolSync;
import io.github.leoascenci0.stashlink.network.BenchPullRequest;
import io.github.leoascenci0.stashlink.network.LockSlotRequest;
import io.github.leoascenci0.stashlink.network.OrganizeRequest;
import io.github.leoascenci0.stashlink.network.PlayerPrefsRequest;
import io.github.leoascenci0.stashlink.network.SetLabelRequest;
import io.github.leoascenci0.stashlink.organize.OrganizeService;
import io.github.leoascenci0.stashlink.slotlock.SlotLockService;
import io.github.leoascenci0.stashlink.slotlock.SlotLocks;
import io.github.leoascenci0.stashlink.network.ReceivesRequest;
import io.github.leoascenci0.stashlink.quickstack.QuickStackReceive;
import io.github.leoascenci0.stashlink.quickstack.QuickStackReceiveService;
import io.github.leoascenci0.stashlink.refill.RefillService;
import io.github.leoascenci0.stashlink.source.ContainerInsert;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.EnumSet;
import java.util.List;

/**
 * Revisão antes da 1.0 (docs/REVISAO-1.0.md): um teste por bug corrigido. Cada um falhava no código anterior.
 */
public class ReviewGameTests {
    private static void check(GameTestHelper h, boolean ok, String msg) {
        h.assertTrue(ok, msg);
    }

    /**
     * Último balde de água usado: o balde vazio vai para o inventário e um balde cheio do baú vem para a mão. Antes o
     * inventário juntava o balde vazio na própria mão e o reabastecimento por cima apagava o balde.
     */
    @GameTest
    public void refillKeepsTheEmptyBucket(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(4, 2, 4);
        Lab.fill(chest, 0, Items.WATER_BUCKET, 1);
        ServerPlayer p = lab.player(3, 2, 3);
        Lab.prefs(p, 8, true);
        int hand = p.getInventory().getSelectedSlot();
        p.getInventory().setItem(hand, new ItemStack(Items.WATER_BUCKET));
        RefillService.tickPlayer(p);
        p.getInventory().setItem(hand, new ItemStack(Items.BUCKET));       // usou o balde
        RefillService.tickPlayer(p);
        check(h, p.getInventory().getItem(hand).is(Items.WATER_BUCKET), "a mão devia ter o balde cheio do baú: "
                + p.getInventory().getItem(hand));
        check(h, Lab.carried(p, Items.BUCKET) == 1, "o balde vazio devia ficar no inventário, achei "
                + Lab.carried(p, Items.BUCKET));
        check(h, Lab.count(chest, Items.WATER_BUCKET) == 0, "o balde cheio saiu do baú");
        lab.cleanup();
        h.succeed();
    }

    /** Mesmo caso com a mochila cheia: o balde vazio fica na mão e o cheio volta ao baú (nada some, nada duplica). */
    @GameTest
    public void refillWithFullBagKeepsTheEmptyBucketInHand(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(4, 2, 4);
        Lab.fill(chest, 0, Items.WATER_BUCKET, 1);
        ServerPlayer p = lab.player(3, 2, 3);
        Lab.prefs(p, 8, true);
        int hand = p.getInventory().getSelectedSlot();
        for (int i = 0; i < 36; i++) {
            if (i != hand) {
                p.getInventory().setItem(i, new ItemStack(Items.DIRT, 64));
            }
        }
        p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.DIRT, 64));   // a mão secundária também é lugar livre
        // O jogador simulado herda "construção infinita" do criativo do GameTest; com ela o Inventory.add aceita e
        // descarta o que não cabe. Em sobrevivência de verdade isso não existe.
        p.getAbilities().instabuild = false;
        p.getInventory().setItem(hand, new ItemStack(Items.WATER_BUCKET));
        RefillService.tickPlayer(p);
        p.getInventory().setItem(hand, new ItemStack(Items.BUCKET));       // usou o balde
        RefillService.tickPlayer(p);
        check(h, p.getInventory().getItem(hand).is(Items.BUCKET) && p.getInventory().getItem(hand).getCount() == 1,
                "sem lugar: o balde vazio continua na mão: " + p.getInventory().getItem(hand));
        check(h, Lab.count(chest, Items.WATER_BUCKET) == 1 && Lab.carried(p, Items.WATER_BUCKET) == 0,
                "e o balde cheio volta ao baú");
        lab.cleanup();
        h.succeed();
    }

    /** O botão "recebe com a N" faz parte da tecla N: trancada, o servidor não muda o baú. */
    @GameTest
    public void receivesButtonRespectsTheQuickStackLock(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        ServerPlayer p = lab.player(3, 2, 3);
        Lab.open(p, chest, 7);
        // Trancar e pedir no mesmo tick: os testes rodam lado a lado e outros destrancam tudo ao terminar.
        StashLinkConfig.setFeatureLocked(Feature.QUICK_STACK, true);
        QuickStackReceiveService.handle(p, new ReceivesRequest(7, false));
        boolean lockedKept = QuickStackReceive.accepts(chest);
        StashLinkConfig.lockedFeatures = EnumSet.noneOf(Feature.class);
        check(h, lockedKept, "trancada: o baú devia continuar recebendo");
        QuickStackReceiveService.handle(p, new ReceivesRequest(7, false));
        check(h, !QuickStackReceive.accepts(chest), "destrancada: o botão devia desligar o baú");
        Lab.close(p);
        lab.cleanup();
        h.succeed();
    }

    /** Tela de outro mod (loja, kit) feita com ChestMenu sobre um container de mentira: a tecla W não tira a vitrine. */
    @GameTest
    public void lootAllIgnoresFakeChestScreens(GameTestHelper h) {
        Lab lab = new Lab(h);
        ServerPlayer p = lab.player(3, 2, 3);
        SimpleContainer shop = new SimpleContainer(27);
        shop.setItem(0, new ItemStack(Items.DIAMOND, 5));
        p.containerMenu = ChestMenu.threeRows(9, p.getInventory(), shop);
        LootAllService.handle(p);
        check(h, shop.countItem(Items.DIAMOND) == 5 && Lab.carried(p, Items.DIAMOND) == 0,
                "a vitrine continua com os 5 diamantes e o jogador não ganhou nenhum");
        Lab.close(p);
        lab.cleanup();
        h.succeed();
    }

    /** Shulker colocada nunca recebe uma shulker (o jogo não deixa pela GUI; o mod também não). */
    @GameTest
    public void shulkerNeverGoesIntoAPlacedShulker(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container box = lab.block(Blocks.SHULKER_BOX, 2, 2, 2);
        ItemStack inner = new ItemStack(Items.SHULKER_BOX);
        check(h, ContainerInsert.capacity(box, inner) == 0, "nenhum espaço para shulker dentro de shulker");
        ItemStack rest = ContainerInsert.insert(box, inner.copy());
        check(h, rest.getCount() == 1 && box.isEmpty(), "a shulker volta inteira e a caixa fica vazia");
        check(h, ContainerInsert.capacity(box, new ItemStack(Items.DIRT)) > 0, "terra continua cabendo");
        lab.cleanup();
        h.succeed();
    }

    // ------------------------------------------------------------------------------------ Item 23: pedidos ao servidor

    /** Tela de outro mod (ChestMenu sobre container de mentira): Organizar, travar slot e o botão N não mexem em nada. */
    @GameTest
    public void fakeChestScreensAreNotStorage(GameTestHelper h) {
        Lab lab = new Lab(h);
        ServerPlayer p = lab.player(3, 2, 3);
        Lab.prefs(p, 8, true);
        SimpleContainer shop = new SimpleContainer(27);
        shop.setItem(5, new ItemStack(Items.DIRT, 3));
        shop.setItem(0, new ItemStack(Items.DIAMOND, 5));
        ChestMenu menu = ChestMenu.threeRows(9, p.getInventory(), shop);
        p.containerMenu = menu;

        OrganizeService.handle(p, new OrganizeRequest(OrganizeRequest.CHEST, 9, "", List.of(), BlockPos.ZERO));
        check(h, shop.getItem(0).is(Items.DIAMOND) && shop.getItem(0).getCount() == 5
                        && shop.getItem(5).is(Items.DIRT) && shop.getItem(5).getCount() == 3 && shop.getItem(1).isEmpty(),
                "Organizar não pode reordenar a vitrine de outro mod");

        SlotLockService.handle(p, new LockSlotRequest(9, 0));
        check(h, SlotLocks.lockedItem(shop, 0) == null, "travar slot não vale numa vitrine");

        check(h, QuickStackReceiveService.storageOf(p, menu) == null, "a vitrine não é um armazenamento para o botão N");
        QuickStackReceiveService.handle(p, new ReceivesRequest(9, false));
        check(h, shop.getItem(0).getCount() == 5, "e o pedido do botão N não muda nada");

        Lab.close(p);
        lab.cleanup();
        h.succeed();
    }

    /** O mesmo pedido de travar slot, 50 vezes no mesmo tick: o servidor atende 1; passado o intervalo, atende de novo. */
    @GameTest
    public void slotLockBurstIsServedOncePerInterval(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        Lab.fill(chest, 3, Items.COBBLESTONE, 10);
        ServerPlayer p = lab.player(3, 2, 3);
        Lab.prefs(p, 8, true);
        Lab.open(p, chest, 7);
        // Travar e destravar alternam: se os 50 fossem atendidos, o slot acabaria destravado (número par).
        for (int i = 0; i < 50; i++) {
            SlotLockService.handle(p, new LockSlotRequest(7, 3));
        }
        check(h, SlotLocks.lockedItem(chest, 3) == Items.COBBLESTONE, "só o 1º pedido da rajada vale: o slot fica travado");
        h.runAfterDelay(StashLinkConfig.SLOT_LOCK_COOLDOWN_TICKS + 1, () -> {
            SlotLockService.handle(p, new LockSlotRequest(7, 3));
            check(h, SlotLocks.lockedItem(chest, 3) == null, "passado o intervalo, o pedido volta a ser atendido");
            Lab.close(p);
            lab.cleanup();
            h.succeed();
        });
    }

    /** Botão N: rajada alternando ligar/desligar; só o primeiro pedido vale. */
    @GameTest
    public void receivesButtonBurstIsServedOncePerInterval(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        ServerPlayer p = lab.player(3, 2, 3);
        Lab.open(p, chest, 7);
        for (int i = 0; i < 50; i++) {
            QuickStackReceiveService.handle(p, new ReceivesRequest(7, i % 2 == 1));   // 1º desliga, 2º liga, ...
        }
        check(h, !QuickStackReceive.accepts(chest), "só o 1º pedido (desligar) vale; se todos valessem, terminaria ligado");
        h.runAfterDelay(StashLinkConfig.RECEIVES_COOLDOWN_TICKS + 1, () -> {
            QuickStackReceiveService.handle(p, new ReceivesRequest(7, true));
            check(h, QuickStackReceive.accepts(chest), "passado o intervalo, o pedido volta a ser atendido");
            Lab.close(p);
            lab.cleanup();
            h.succeed();
        });
    }

    /** Gravar rótulo: rajada com nomes diferentes; fica o do 1º pedido, e depois do intervalo grava outro. */
    @GameTest
    public void labelBurstIsServedOncePerInterval(GameTestHelper h) {
        Lab lab = new Lab(h);
        lab.chest(2, 2, 2);
        BlockPos pos = h.absolutePos(new BlockPos(2, 2, 2));
        ServerPlayer p = lab.player(3, 2, 3);
        for (int i = 0; i < 50; i++) {
            LabelService.handleSet(p, new SetLabelRequest(pos, "nome" + i, "x"));
        }
        check(h, Labels.get(Labels.at(lab.level, pos)).name().equals("nome0"), "só o 1º pedido grava");
        h.runAfterDelay(StashLinkConfig.LABEL_COOLDOWN_TICKS + 1, () -> {
            LabelService.handleSet(p, new SetLabelRequest(pos, "depois", "x"));
            check(h, Labels.get(Labels.at(lab.level, pos)).name().equals("depois"), "passado o intervalo, grava de novo");
            lab.cleanup();
            h.succeed();
        });
    }

    /** Preferências: rajada vira 1 aplicação na hora e só a ÚLTIMA depois do intervalo (nada se perde nem se acumula). */
    @GameTest(maxTicks = 80)
    public void prefsBurstKeepsOnlyTheLast(GameTestHelper h) {
        Lab lab = new Lab(h);
        ServerPlayer p = lab.player(3, 2, 3);
        for (int r = 1; r <= 5; r++) {
            PlayerPrefsService.handle(p, new PlayerPrefsRequest(new PlayerPrefs(r, 1, List.of(), 0, r)));
        }
        check(h, PlayerPrefsStore.radius(p) == 1, "na hora vale só o 1º pedido: " + PlayerPrefsStore.radius(p));
        h.runAfterDelay(StashLinkConfig.PLAYER_PREFS_COOLDOWN_TICKS + 2, () -> {
            check(h, PlayerPrefsStore.radius(p) == 5, "depois do intervalo vale o último: " + PlayerPrefsStore.radius(p));
            PlayerPrefsStore.remove(p.getUUID());
            lab.cleanup();
            h.succeed();
        });
    }

    /** Montar receita varre os baús: dois pedidos dentro do intervalo da bancada, só o primeiro varre. */
    @GameTest
    public void benchRecipeRequestsAreSpacedOut(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(8, 2, 4);
        Lab.fill(chest, 0, Items.PURPUR_BLOCK, 10);
        ServerPlayer p = lab.player(4, 2, 4);
        Lab.prefs(p, 8, true);
        StonecutterMenu menu = new StonecutterMenu(5, p.getInventory());
        p.containerMenu = menu;
        BenchPoolSync.Entry wanted = BenchSync.snapshot(p).stream().filter(e -> e.isResult() && !e.missing())
                .findFirst().orElseThrow();
        int before = BenchPool.scanCount();
        BenchPullService.handle(p, new BenchPullRequest(5, wanted.item(), false, wanted.id()));
        check(h, BenchPool.scanCount() - before == 1, "o 1º pedido varreu os baús uma vez");
        h.runAfterDelay(1, () -> {                       // dentro do intervalo (3 ticks)
            int mid = BenchPool.scanCount();
            BenchPullService.handle(p, new BenchPullRequest(5, wanted.item(), false, wanted.id()));
            check(h, BenchPool.scanCount() == mid, "o 2º pedido, dentro do intervalo, não pode varrer de novo");
            h.runAfterDelay(StashLinkConfig.BENCH_SWEEP_COOLDOWN_TICKS + 1, () -> {
                int late = BenchPool.scanCount();
                BenchPullService.handle(p, new BenchPullRequest(5, wanted.item(), false, wanted.id()));
                check(h, BenchPool.scanCount() > late, "passado o intervalo, volta a atender");
                menu.removed(p);
                p.containerMenu = p.inventoryMenu;
                lab.cleanup();
                h.succeed();
            });
        });
    }
}
