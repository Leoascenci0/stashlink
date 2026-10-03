package io.github.leoascenci0.stashlink.gametest;

import io.github.leoascenci0.stashlink.config.Feature;
import io.github.leoascenci0.stashlink.config.PlayerPrefs;
import io.github.leoascenci0.stashlink.config.PlayerPrefsStore;
import io.github.leoascenci0.stashlink.config.StashLinkConfig;
import io.github.leoascenci0.stashlink.network.PullItemRequest;
import io.github.leoascenci0.stashlink.pull.PullItemService;
import io.github.leoascenci0.stashlink.storage.ShulkerStorage;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ServerboundPickItemFromBlockPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Item 19: o botão do meio do mouse mirando um bloco traz o item do armazenamento para a hotbar. O teste manda o
 * MESMO pacote que o cliente manda ({@code ServerboundPickItemFromBlockPacket}) para o servidor de verdade, então
 * passa pelo caminho do jogo base e pelo mixin. Jogadores em sobrevivência; cada teste usa itens só dele (os
 * testes rodam lado a lado no mesmo mundo) e confere a soma: nada duplica, nada some.
 */
public class PickBlockGameTests {
    /** O pedido do mesmo jogador só é aceito a cada 4 ticks; os cenários com vários passos andam de 5 em 5. */
    private static final int STEP = 5;

    private static void check(GameTestHelper h, boolean ok, String msg) {
        h.assertTrue(ok, msg);
    }

    /** Aperta o botão do meio mirando o bloco em {@code pos}: o mesmo pacote que o cliente manda. */
    private static void middleClick(ServerPlayer p, BlockPos pos) {
        p.connection.handlePickItemFromBlock(new ServerboundPickItemFromBlockPacket(pos, false));
    }

    private static List<ItemStack> hotbar(ServerPlayer p) {
        List<ItemStack> copy = new ArrayList<>();
        for (int i = 0; i < Inventory.getSelectionSize(); i++) {
            copy.add(p.getInventory().getItem(i).copy());
        }
        return copy;
    }

    private static boolean sameHotbar(List<ItemStack> before, ServerPlayer p) {
        for (int i = 0; i < before.size(); i++) {
            if (!ItemStack.matches(before.get(i), p.getInventory().getItem(i))) {
                return false;
            }
        }
        return true;
    }

    private static int inHotbar(ServerPlayer p, Item item) {
        int n = 0;
        for (int i = 0; i < Inventory.getSelectionSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.is(item)) {
                n += s.getCount();
            }
        }
        return n;
    }

    private static ItemStack held(ServerPlayer p) {
        return p.getInventory().getItem(p.getInventory().getSelectedSlot());
    }

    /**
     * Jogador de sobrevivência de verdade. O simulado do {@link Lab} só troca o modo de jogo, mas herda do mundo de
     * teste (criativo) o "materiais infinitos", e aí o jogo base criaria o item do nada quando o mod não puxa.
     */
    private static ServerPlayer survivor(Lab lab, double x, double y, double z) {
        ServerPlayer p = lab.player(x, y, z);
        p.getAbilities().instabuild = false;
        return p;
    }

    private static void resetLocks() {
        StashLinkConfig.lockedFeatures = EnumSet.noneOf(Feature.class);
    }

    private static void finish(GameTestHelper h, Lab lab) {
        resetLocks();
        lab.cleanup();
        h.succeed();
    }

    // ---------------------------------------------------------------- o caso principal

    /**
     * Mirar um bloco, apertar o botão do meio: o stack (64) sai do baú para a hotbar, vai para a mão, e o que o
     * jogador já tinha na mão continua lá (o item novo vai para um slot livre, nunca por cima).
     */
    @GameTest
    public void middleClickBringsStackFromChest(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        Lab.fill(chest, 0, Items.CHERRY_PLANKS, 40);
        Lab.fill(chest, 1, Items.CHERRY_PLANKS, 30);
        BlockPos target = lab.bare(Blocks.CHERRY_PLANKS, 4, 2, 6);
        ServerPlayer p = survivor(lab, 4, 2, 4);
        Lab.prefs(p, 8, true);
        Lab.give(p, 0, Items.DIRT, 12);                    // mão ocupada por item do jogador
        p.getInventory().setSelectedSlot(0);
        List<ItemStack> before = hotbar(p);

        middleClick(p, target);

        check(h, held(p).is(Items.CHERRY_PLANKS) && held(p).getCount() == 64, "mão devia ter 64, tem " + held(p));
        check(h, p.getInventory().getSelectedSlot() == 1, "devia ir para o primeiro slot livre (1), foi " + p.getInventory().getSelectedSlot());
        check(h, p.getInventory().getItem(0).is(Items.DIRT) && p.getInventory().getItem(0).getCount() == 12, "o item do jogador não podia mudar");
        check(h, Lab.count(chest, Items.CHERRY_PLANKS) == 6 && Lab.carried(p, Items.CHERRY_PLANKS) == 64, "soma 70 = 6 no baú + 64");
        check(h, before.get(0).getCount() == 12, "sanidade");
        finish(h, lab);
    }

    /** Mirar um bloco que dá OUTRO item (trigo dá semente): usa o que o jogo base escolheria. */
    @GameTest
    public void blockThatGivesAnotherItemUsesTheBaseGameChoice(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        Lab.fill(chest, 0, Items.WHEAT_SEEDS, 10);
        BlockPos target = lab.bare(Blocks.WHEAT, 4, 2, 6);
        ServerPlayer p = survivor(lab, 4, 2, 4);
        Lab.prefs(p, 8, true);

        middleClick(p, target);

        check(h, held(p).is(Items.WHEAT_SEEDS) && held(p).getCount() == 10, "devia vir a semente, tem " + held(p));
        check(h, Lab.count(chest, Items.WHEAT_SEEDS) == 0, "o baú devia ficar vazio de sementes");
        finish(h, lab);
    }

    // ---------------------------------------------------------------- o que NÃO pode acontecer

    @GameTest
    public void outsideTheRadiusPullsNothing(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container far = lab.chest(14, 2, 14);              // ~14 blocos do jogador
        Lab.fill(far, 0, Items.BAMBOO_PLANKS, 64);
        BlockPos target = lab.bare(Blocks.BAMBOO_PLANKS, 4, 2, 6);
        ServerPlayer p = survivor(lab, 4, 2, 4);
        Lab.prefs(p, 4, true);
        List<ItemStack> before = hotbar(p);

        middleClick(p, target);

        check(h, Lab.count(far, Items.BAMBOO_PLANKS) == 64 && Lab.carried(p, Items.BAMBOO_PLANKS) == 0, "fora do raio não puxa");
        check(h, sameHotbar(before, p), "a hotbar não podia mudar");
        finish(h, lab);
    }

    /** Hotbar cheia de outras coisas: nada acontece, nada some, nada é trocado, a seleção não muda. */
    @GameTest
    public void fullHotbarDoesNothing(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        Lab.fill(chest, 0, Items.MUD_BRICKS, 64);
        BlockPos target = lab.bare(Blocks.MUD_BRICKS, 4, 2, 6);
        ServerPlayer p = survivor(lab, 4, 2, 4);
        Lab.prefs(p, 8, true);
        Item[] filler = {Items.DIRT, Items.COBBLESTONE, Items.STICK, Items.TORCH, Items.SAND, Items.GRAVEL, Items.CLAY_BALL,
                Items.BRICK, Items.BONE};
        for (int i = 0; i < filler.length; i++) {
            Lab.give(p, i, filler[i], 3 + i);
        }
        p.getInventory().setSelectedSlot(4);
        List<ItemStack> before = hotbar(p);

        middleClick(p, target);

        check(h, sameHotbar(before, p), "hotbar cheia: nada podia ser trocado nem sobrescrito");
        check(h, p.getInventory().getSelectedSlot() == 4, "a seleção não podia mudar");
        check(h, Lab.count(chest, Items.MUD_BRICKS) == 64 && Lab.carried(p, Items.MUD_BRICKS) == 0, "o baú não podia perder nada");
        finish(h, lab);
    }

    /** O jogo base resolve o que já está no inventário: o mod não puxa mais um stack do baú. */
    @GameTest
    public void itemAlreadyInInventoryStaysWithTheBaseGame(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        Lab.fill(chest, 0, Items.PACKED_MUD, 64);
        BlockPos target = lab.bare(Blocks.PACKED_MUD, 4, 2, 6);
        ServerPlayer p = survivor(lab, 4, 2, 4);
        Lab.prefs(p, 8, true);
        Lab.give(p, 20, Items.PACKED_MUD, 10);             // na mochila

        middleClick(p, target);

        check(h, Lab.count(chest, Items.PACKED_MUD) == 64, "o baú não podia ser tocado");
        check(h, Lab.carried(p, Items.PACKED_MUD) == 10 && held(p).is(Items.PACKED_MUD), "o jogo base traz o da mochila para a mão");
        finish(h, lab);
    }

    /** Função trancada pelo servidor ou desligada pelo jogador: o botão do meio fica como no jogo base. */
    @GameTest
    public void lockedOrDisabledDoesNothing(GameTestHelper h) {
        resetLocks();
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        Lab.fill(chest, 0, Items.SMOOTH_BASALT, 64);
        BlockPos target = lab.bare(Blocks.SMOOTH_BASALT, 4, 2, 6);

        ServerPlayer locked = survivor(lab, 4, 2, 4);
        Lab.prefs(locked, 8, true);
        StashLinkConfig.setFeatureLocked(Feature.PULL, true);
        middleClick(locked, target);
        StashLinkConfig.setFeatureLocked(Feature.PULL, false);
        check(h, Lab.carried(locked, Items.SMOOTH_BASALT) == 0 && Lab.count(chest, Items.SMOOTH_BASALT) == 64, "trancada: não puxa");

        ServerPlayer off = survivor(lab, 4, 2, 4);
        PlayerPrefsStore.set(off.getUUID(), new PlayerPrefs(8, 1, List.of(), Feature.PULL.bit()));
        middleClick(off, target);
        check(h, Lab.carried(off, Items.SMOOTH_BASALT) == 0 && Lab.count(chest, Items.SMOOTH_BASALT) == 64, "desligada por mim: não puxa");

        ServerPlayer on = survivor(lab, 4, 2, 4);
        Lab.prefs(on, 8, true);
        middleClick(on, target);
        check(h, Lab.carried(on, Items.SMOOTH_BASALT) == 64 && Lab.count(chest, Items.SMOOTH_BASALT) == 0, "ligada: puxa");
        finish(h, lab);
    }

    /** Criativo (o mundo do Eliel): o jogo base dá o item do nada, então o mod não mexe no baú. */
    @GameTest
    public void creativeLeavesTheChestAlone(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        Lab.fill(chest, 0, Items.RAW_COPPER_BLOCK, 64);
        BlockPos target = lab.bare(Blocks.RAW_COPPER_BLOCK, 4, 2, 6);
        ServerPlayer p = lab.player(4, 2, 4, net.minecraft.world.level.GameType.CREATIVE);
        Lab.prefs(p, 8, true);

        middleClick(p, target);

        check(h, Lab.count(chest, Items.RAW_COPPER_BLOCK) == 64, "criativo: o baú não podia ser tocado");
        finish(h, lab);
    }

    // ---------------------------------------------------------------- shulker e 2 jogadores

    /** Item guardado numa shulker que o jogador carrega também vale (mesmas fontes do Litematica). */
    @GameTest
    public void itemInsideACarriedShulkerComesToTheHotbar(GameTestHelper h) {
        Lab lab = new Lab(h);
        BlockPos target = lab.bare(Blocks.CRIMSON_PLANKS, 4, 2, 6);
        ServerPlayer p = survivor(lab, 4, 2, 4);
        Lab.prefs(p, 8, true);
        ItemStack box = new ItemStack(Items.SHULKER_BOX);
        ShulkerStorage.write(box, List.of(new ItemStack(Items.CRIMSON_PLANKS, 30)));
        p.getInventory().setItem(20, box);

        middleClick(p, target);

        check(h, inHotbar(p, Items.CRIMSON_PLANKS) == 30, "a hotbar devia ter 30, tem " + inHotbar(p, Items.CRIMSON_PLANKS));
        check(h, ShulkerStorage.count(p.getInventory().getItem(20), s -> s.is(Items.CRIMSON_PLANKS)) == 0, "a shulker devia ficar sem eles");
        check(h, Lab.carried(p, Items.CRIMSON_PLANKS) == 30, "soma 30 = hotbar (a shulker não conta no inventário direto)");
        finish(h, lab);
    }

    /**
     * Dois jogadores no mesmo baú: com outro jogador de GUI aberta nele, nada sai; liberado, o primeiro leva 64 e o
     * segundo o resto. A soma (80) fica igual o tempo todo: nenhuma duplicação.
     */
    @GameTest(maxTicks = 100)
    public void twoPlayersShareOneChestWithoutDupe(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        Lab.fill(chest, 0, Items.WARPED_PLANKS, 50);
        Lab.fill(chest, 1, Items.WARPED_PLANKS, 30);
        BlockPos target = lab.bare(Blocks.WARPED_PLANKS, 4, 2, 6);
        ServerPlayer a = survivor(lab, 4, 2, 4);
        ServerPlayer b = survivor(lab, 5, 2, 4);
        Lab.prefs(a, 8, true);
        Lab.prefs(b, 8, true);

        Lab.open(b, chest, 1);                              // b está com o baú aberto
        middleClick(a, target);
        check(h, Lab.carried(a, Items.WARPED_PLANKS) == 0 && Lab.count(chest, Items.WARPED_PLANKS) == 80,
                "baú aberto por outro jogador: nada podia sair");
        Lab.close(b);

        h.runAfterDelay(STEP, () -> {
            middleClick(a, target);
            middleClick(b, target);
            int total = Lab.count(chest, Items.WARPED_PLANKS) + Lab.carried(a, Items.WARPED_PLANKS) + Lab.carried(b, Items.WARPED_PLANKS);
            check(h, total == 80, "a soma tem de continuar 80, deu " + total);
            check(h, Lab.carried(a, Items.WARPED_PLANKS) == 64 && Lab.carried(b, Items.WARPED_PLANKS) == 16,
                    "a leva 64 e b leva o resto (a=" + Lab.carried(a, Items.WARPED_PLANKS) + ", b=" + Lab.carried(b, Items.WARPED_PLANKS) + ")");
            finish(h, lab);
        });
    }

    // ---------------------------------------------------------------- convivência com o Litematica

    /**
     * O botão do meio não mexe na troca no mesmo slot do Item 18: o item do Litematica continua sendo "do mod" e é
     * trocado no pedido seguinte dele; o do botão do meio é do jogador e nunca é devolvido.
     */
    @GameTest(maxTicks = 100)
    public void middleClickLeavesTheLitematicaSlotAlone(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        Lab.fill(chest, 0, Items.MOSS_BLOCK, 64);           // do Litematica (primeiro)
        Lab.fill(chest, 1, Items.PALE_OAK_PLANKS, 64);      // do botão do meio
        Lab.fill(chest, 2, Items.MUDDY_MANGROVE_ROOTS, 64); // do Litematica (segundo)
        BlockPos target = lab.bare(Blocks.PALE_OAK_PLANKS, 4, 2, 6);
        ServerPlayer p = survivor(lab, 4, 2, 4);
        Lab.prefs(p, 8, true);
        int[] wholeTotal = {192};

        h.runAfterDelay(STEP, () -> {
            PullItemService.handle(p, new PullItemRequest(Items.MOSS_BLOCK, 64));
            check(h, inHotbar(p, Items.MOSS_BLOCK) == 64, "Litematica trouxe o musgo");
        });
        h.runAfterDelay(STEP * 2, () -> {
            middleClick(p, target);
            check(h, inHotbar(p, Items.MOSS_BLOCK) == 64 && inHotbar(p, Items.PALE_OAK_PLANKS) == 64,
                    "o botão do meio põe o seu em outro slot e deixa o musgo");
        });
        h.runAfterDelay(STEP * 3, () -> {
            PullItemService.handle(p, new PullItemRequest(Items.MUDDY_MANGROVE_ROOTS, 64));
            check(h, inHotbar(p, Items.MOSS_BLOCK) == 0 && Lab.count(chest, Items.MOSS_BLOCK) == 64,
                    "o musgo do Litematica volta ao baú na troca");
            check(h, inHotbar(p, Items.PALE_OAK_PLANKS) == 64, "o item do botão do meio nunca é devolvido");
            int total = 0;
            for (Item item : List.of(Items.MOSS_BLOCK, Items.PALE_OAK_PLANKS, Items.MUDDY_MANGROVE_ROOTS)) {
                total += Lab.count(chest, item) + Lab.carried(p, item);
            }
            check(h, total == wholeTotal[0], "a soma tem de continuar 192, deu " + total);
            finish(h, lab);
        });
    }
}
