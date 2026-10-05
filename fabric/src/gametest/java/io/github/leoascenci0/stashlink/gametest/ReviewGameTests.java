package io.github.leoascenci0.stashlink.gametest;

import io.github.leoascenci0.stashlink.config.Feature;
import io.github.leoascenci0.stashlink.config.StashLinkConfig;
import io.github.leoascenci0.stashlink.network.ReceivesRequest;
import io.github.leoascenci0.stashlink.quickstack.QuickStackReceive;
import io.github.leoascenci0.stashlink.quickstack.QuickStackReceiveService;
import io.github.leoascenci0.stashlink.refill.RefillService;
import io.github.leoascenci0.stashlink.source.ContainerInsert;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.EnumSet;

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
}
