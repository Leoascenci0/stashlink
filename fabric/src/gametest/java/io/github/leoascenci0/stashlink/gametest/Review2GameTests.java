package io.github.leoascenci0.stashlink.gametest;

import io.github.leoascenci0.stashlink.bench.BenchPullService;
import io.github.leoascenci0.stashlink.bench.BenchResults;
import io.github.leoascenci0.stashlink.config.Feature;
import io.github.leoascenci0.stashlink.config.StashLinkConfig;
import io.github.leoascenci0.stashlink.network.BenchPullRequest;
import io.github.leoascenci0.stashlink.network.OrganizeSync;
import io.github.leoascenci0.stashlink.organize.OrganizeService;
import io.github.leoascenci0.stashlink.quickstack.QuickStackService;
import io.github.leoascenci0.stashlink.refill.RefillService;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Segunda revisão completa (docs/REVISAO-1.1.md): um teste por bug corrigido. Cada um falhava no código anterior.
 */
public class Review2GameTests {
    /** Blocos "de outro jogador" (terreno protegido de mentira): o uso é negado como um mod de claims negaria. */
    private static final Set<BlockPos> CLAIMED = ConcurrentHashMap.newKeySet();

    static {
        UseBlockCallback.EVENT.register((player, level, hand, hit) ->
                CLAIMED.contains(hit.getBlockPos()) ? InteractionResult.FAIL : InteractionResult.PASS);
    }

    private static void check(GameTestHelper h, boolean ok, String msg) {
        h.assertTrue(ok, msg);
    }

    /**
     * Baú DUPLO aberto por outro jogador é pulado pela N. Antes só o baú simples era: a tela de quem abriu e a
     * varredura do mod criam cada uma o seu objeto para o mesmo par de blocos, e a comparação não os reconhecia.
     */
    @GameTest
    public void doubleChestOpenedByAnotherIsSkipped(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container dbl = lab.doubleChest(2, 2, 2);
        Lab.fill(dbl, 0, Items.COBBLESTONE, 5);
        ServerPlayer a = lab.player(3, 2, 3);
        ServerPlayer b = lab.player(4, 2, 4);
        Lab.prefs(b, 8, true);
        Lab.open(a, dbl, 1);
        Lab.give(b, 12, Items.COBBLESTONE, 20);
        QuickStackService.handle(b);
        check(h, Lab.count(dbl, Items.COBBLESTONE) == 5 && Lab.carried(b, Items.COBBLESTONE) == 20,
                "baú duplo aberto por A foi mexido: " + Lab.count(dbl, Items.COBBLESTONE));
        Lab.close(a);
        ServerPlayer c = lab.player(4, 2, 3);
        Lab.prefs(c, 8, true);
        Lab.give(c, 12, Items.COBBLESTONE, 7);
        QuickStackService.handle(c);
        check(h, Lab.count(dbl, Items.COBBLESTONE) == 12, "fechado, o baú duplo volta a receber: "
                + Lab.count(dbl, Items.COBBLESTONE));
        lab.cleanup();
        h.succeed();
    }

    /**
     * Baú duplo com UMA metade em terreno protegido: a N não guarda nele, seja qual for a metade negada. Antes o mod só
     * perguntava por uma das metades.
     */
    @GameTest
    public void doubleChestNeedsBothHalvesAllowed(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container dbl = lab.doubleChest(2, 2, 2);
        Lab.fill(dbl, 0, Items.COBBLESTONE, 5);
        BlockPos left = h.absolutePos(new BlockPos(2, 2, 2));
        BlockPos right = h.absolutePos(new BlockPos(3, 2, 2));
        try {
            for (BlockPos denied : List.of(left, right)) {
                CLAIMED.add(denied);
                ServerPlayer p = lab.player(3, 2, 4);
                Lab.prefs(p, 8, true);
                Lab.give(p, 12, Items.COBBLESTONE, 20);
                QuickStackService.handle(p);
                check(h, Lab.count(dbl, Items.COBBLESTONE) == 5, "metade negada (" + denied + "), mas o baú recebeu: "
                        + Lab.count(dbl, Items.COBBLESTONE));
                CLAIMED.remove(denied);
            }
        } finally {
            CLAIMED.remove(left);
            CLAIMED.remove(right);
        }
        lab.cleanup();
        h.succeed();
    }

    /**
     * Mover o item da mão para a mochila (shift-clique com o inventário aberto) não é "esgotou": nada sai do baú. Usar o
     * último item de verdade continua reabastecendo.
     */
    @GameTest
    public void refillIgnoresItemMovedInsideTheInventory(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(4, 2, 4);
        Lab.fill(chest, 0, Items.COBBLESTONE, 64);
        ServerPlayer p = lab.player(3, 2, 3);
        Lab.prefs(p, 8, true);
        int hand = p.getInventory().getSelectedSlot();
        p.getInventory().setItem(hand, new ItemStack(Items.COBBLESTONE, 5));
        RefillService.tickPlayer(p);
        p.getInventory().setItem(hand, ItemStack.EMPTY);                       // shift-clique: foi para a mochila
        p.getInventory().setItem(20, new ItemStack(Items.COBBLESTONE, 5));
        RefillService.tickPlayer(p);
        check(h, Lab.count(chest, Items.COBBLESTONE) == 64 && Lab.carried(p, Items.COBBLESTONE) == 5,
                "só mudou de lugar: o baú não podia ceder nada, achei " + Lab.count(chest, Items.COBBLESTONE));

        p.getInventory().setItem(hand, new ItemStack(Items.COBBLESTONE, 1));
        RefillService.tickPlayer(p);
        p.getInventory().setItem(hand, ItemStack.EMPTY);                       // colocou o último bloco da mão
        RefillService.tickPlayer(p);
        check(h, p.getInventory().getItem(hand).is(Items.COBBLESTONE), "usou o último da mão: tinha de reabastecer");
        check(h, Lab.count(chest, Items.COBBLESTONE) + Lab.carried(p, Items.COBBLESTONE) == 69, "nada some nem duplica");
        lab.cleanup();
        h.succeed();
    }

    /** Sinalizador trancado: o pedido "ponha no meu cursor" (forjado) também não tira pagamento do baú. */
    @GameTest
    public void beaconLockAlsoCoversCursorRequests(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        Lab.fill(chest, 0, Items.DIAMOND, 3);
        ServerPlayer p = lab.player(4, 2, 4);
        Lab.prefs(p, 4, true);
        net.minecraft.world.inventory.BeaconMenu menu = new net.minecraft.world.inventory.BeaconMenu(1, p.getInventory());
        p.containerMenu = menu;
        // Trancar e pedir no mesmo tick: os testes rodam lado a lado e a limpeza dos outros destranca tudo.
        StashLinkConfig.setFeatureLocked(Feature.BENCH_BEACON, true);
        BenchPullService.handle(p, new BenchPullRequest(1, new ItemStack(Items.DIAMOND), false, BenchResults.CURSOR));
        boolean ok = menu.getCarried().isEmpty() && Lab.count(chest, Items.DIAMOND) == 3;
        String seen = "cursor " + menu.getCarried() + ", baú " + Lab.count(chest, Items.DIAMOND);
        menu.setCarried(ItemStack.EMPTY);
        p.containerMenu = p.inventoryMenu;
        StashLinkConfig.lockedFeatures = EnumSet.noneOf(Feature.class);
        check(h, ok, "sinalizador trancado: nada pode ir ao cursor (" + seen + ")");
        lab.cleanup();
        h.succeed();
    }

    /**
     * Bigorna: a espada veio do baú, o jogador a renomeou e fechou a tela com o resultado no cursor. O resultado é do
     * jogador e fica com ele; antes ia para o baú, por ser "do mesmo tipo" do item emprestado.
     */
    @GameTest
    public void anvilResultOnTheCursorStaysWithThePlayer(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        chest.setItem(0, new ItemStack(Items.DIAMOND_SWORD));
        ServerPlayer p = lab.player(4, 2, 4);
        Lab.prefs(p, 8, true);
        net.minecraft.world.inventory.AnvilMenu menu = new net.minecraft.world.inventory.AnvilMenu(1, p.getInventory());
        p.containerMenu = menu;
        BenchResults.place(p, menu, new ItemStack(Items.DIAMOND_SWORD), true);
        check(h, menu.getSlot(0).getItem().is(Items.DIAMOND_SWORD) && Lab.count(chest, Items.DIAMOND_SWORD) == 0,
                "a espada do baú foi para a bigorna");
        ItemStack named = menu.getSlot(0).getItem().copy();
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Cortadora"));
        menu.getSlot(0).set(ItemStack.EMPTY);                                  // a bigorna consumiu a entrada
        menu.setCarried(named);                                                // e o resultado está no cursor
        menu.removed(p);
        p.containerMenu = p.inventoryMenu;
        check(h, Lab.count(chest, Items.DIAMOND_SWORD) == 0, "o resultado renomeado não pode ir para o baú");
        check(h, p.getInventory().countItem(Items.DIAMOND_SWORD) == 1, "o resultado fica com o jogador");
        lab.cleanup();
        h.succeed();
    }

    /** O item sem alteração que veio do baú e ficou no cursor continua voltando ao baú ao fechar. */
    @GameTest
    public void borrowedItemOnTheCursorStillReturnsToTheChest(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        chest.setItem(0, new ItemStack(Items.DIAMOND_SWORD));
        ServerPlayer p = lab.player(4, 2, 4);
        Lab.prefs(p, 8, true);
        net.minecraft.world.inventory.AnvilMenu menu = new net.minecraft.world.inventory.AnvilMenu(1, p.getInventory());
        p.containerMenu = menu;
        BenchPullService.handle(p, new BenchPullRequest(1, new ItemStack(Items.DIAMOND_SWORD), false, BenchResults.CURSOR));
        check(h, menu.getCarried().is(Items.DIAMOND_SWORD), "a espada do baú veio para o cursor");
        menu.removed(p);
        p.containerMenu = p.inventoryMenu;
        check(h, Lab.count(chest, Items.DIAMOND_SWORD) == 1 && Lab.carried(p, Items.DIAMOND_SWORD) == 0,
                "sem uso, a espada emprestada volta ao baú: baú " + Lab.count(chest, Items.DIAMOND_SWORD));
        lab.cleanup();
        h.succeed();
    }

    /**
     * Organizar/busca: item "pesado" (shulker cheia de itens com nome comprido) vai no pacote só como o tipo do item, para
     * 256 linhas nunca passarem do teto do jogo (o que derrubava a conexão de quem pediu).
     */
    @GameTest
    public void organizeRowsNeverCarryHeavyItems(GameTestHelper h) {
        Lab lab = new Lab(h);
        ServerPlayer p = lab.player(4, 2, 4);
        List<ItemStack> inside = new ArrayList<>();
        for (int i = 0; i < 27; i++) {
            ItemStack stack = new ItemStack(Items.PAPER);
            stack.set(DataComponents.CUSTOM_NAME, Component.literal(("nome bem comprido " + i + " ").repeat(12)));
            inside.add(stack);
        }
        ItemStack heavy = new ItemStack(Items.SHULKER_BOX);
        heavy.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(inside));
        ItemStack icon = OrganizeService.icon(p, heavy);
        check(h, icon.is(Items.SHULKER_BOX) && icon.getComponentsPatch().isEmpty(),
                "item pesado tem de ir só como o tipo: " + icon.getComponentsPatch());
        check(h, io.github.leoascenci0.stashlink.compat.mc.BenchCompat.packetSize(p, icon) <= OrganizeSync.MAX_ITEM_BYTES,
                "o ícone cabe no teto por linha");
        ItemStack light = new ItemStack(Items.DIAMOND_SWORD);
        light.set(DataComponents.CUSTOM_NAME, Component.literal("Cortadora"));
        check(h, OrganizeService.icon(p, light) == light, "item leve vai como é (com nome e encantamentos)");
        lab.cleanup();
        h.succeed();
    }
}
