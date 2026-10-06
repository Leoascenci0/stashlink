package io.github.leoascenci0.stashlink.gametest;

import io.github.leoascenci0.stashlink.bench.BenchPool;
import io.github.leoascenci0.stashlink.quickstack.QuickStackService;
import io.github.leoascenci0.stashlink.refill.RefillService;
import io.github.leoascenci0.stashlink.source.NearbyContainers;
import io.github.leoascenci0.stashlink.source.PlayerSources;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ServerboundPickItemFromBlockPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Item 26 com gavetas de verdade (Storage Drawers, versão Fabric; a NeoForge 26.3.0.1 não carrega no NeoForge do
 * projeto). Os itens entram e são contados pela própria tomada de itens do mod, como um funil faria.
 */
public class StorageDrawersGameTests {
    private static final String SD = "storagedrawers";

    private static void check(GameTestHelper h, boolean ok, String msg) {
        h.assertTrue(ok, msg);
    }

    /** Sem o Storage Drawers carregado, o teste só registra que não rodou. */
    private static boolean missing(GameTestHelper h) {
        if (FabricLoader.getInstance().isModLoaded(SD)) {
            return false;
        }
        h.succeed();
        return true;
    }

    private static Block block(String path) {
        return BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath(SD, path));
    }

    private static Storage<ItemVariant> storage(Lab lab, BlockPos pos) {
        Storage<ItemVariant> storage = ItemStorage.SIDED.find(lab.level, pos, null);
        if (storage == null) {
            throw new IllegalStateException("sem tomada de itens em " + pos + ": " + lab.level.getBlockState(pos));
        }
        return storage;
    }

    private static long put(Storage<ItemVariant> storage, Item item, long n) {
        try (Transaction tx = Transaction.openOuter()) {
            long in = storage.insert(ItemVariant.of(item), n, tx);
            tx.commit();
            return in;
        }
    }

    private static long stock(Storage<ItemVariant> storage, Item item) {
        long total = 0;
        for (StorageView<ItemVariant> view : storage.nonEmptyViews()) {
            if (view.getResource().isOf(item)) {
                total += view.getAmount();
            }
        }
        return total;
    }

    private static ServerPlayer player(Lab lab) {
        ServerPlayer p = lab.player(4, 2, 4);
        Lab.prefs(p, 8, true);
        p.getAbilities().instabuild = false;
        return p;
    }

    private static int inHand(ServerPlayer p, Item item) {
        ItemStack held = p.getInventory().getItem(p.getInventory().getSelectedSlot());
        return held.is(item) ? held.getCount() : 0;
    }

    /** Gaveta de madeira (1 slot com milhares): N até lotar, mão e botão do meio tiram dela, soma sempre igual. */
    @GameTest
    public void fullDrawerServesQuickStackRefillAndMiddleClick(GameTestHelper h) {
        if (missing(h)) {
            return;
        }
        Lab lab = new Lab(h);
        BlockPos pos = lab.bare(block("oak_full_drawers_1"), 2, 2, 2);
        Storage<ItemVariant> drawer = storage(lab, pos);
        long first = put(drawer, Items.POLISHED_GRANITE, 1_000);
        check(h, first == 1_000, "a gaveta devia aceitar 1000: " + first);
        long room = put(drawer, Items.POLISHED_GRANITE, 1_000_000);   // descobre a capacidade e lota
        long capacity = first + room;
        try (Transaction tx = Transaction.openOuter()) {               // devolve a gaveta a "quase cheia"
            drawer.extract(ItemVariant.of(Items.POLISHED_GRANITE), 100, tx);
            tx.commit();
        }
        check(h, stock(drawer, Items.POLISHED_GRANITE) == capacity - 100, "sanidade: " + stock(drawer, Items.POLISHED_GRANITE));

        ServerPlayer a = player(lab);
        Lab.give(a, 10, Items.POLISHED_GRANITE, 64);
        Lab.give(a, 11, Items.POLISHED_GRANITE, 64);
        QuickStackService.handle(a);
        check(h, stock(drawer, Items.POLISHED_GRANITE) == capacity && Lab.carried(a, Items.POLISHED_GRANITE) == 28,
                "N: só cabiam 100 (sobram 28): gaveta=" + stock(drawer, Items.POLISHED_GRANITE) + " jogador=" + Lab.carried(a, Items.POLISHED_GRANITE));

        ServerPlayer b = player(lab);
        int hand = b.getInventory().getSelectedSlot();
        b.getInventory().setItem(hand, new ItemStack(Items.POLISHED_GRANITE, 1));
        RefillService.tickPlayer(b);
        b.getInventory().setItem(hand, ItemStack.EMPTY);
        RefillService.tickPlayer(b);
        check(h, inHand(b, Items.POLISHED_GRANITE) == 64 && stock(drawer, Items.POLISHED_GRANITE) == capacity - 64,
                "mão: 64 da gaveta; gaveta=" + stock(drawer, Items.POLISHED_GRANITE));

        ServerPlayer c = player(lab);
        BlockPos target = lab.bare(Blocks.POLISHED_GRANITE, 4, 2, 6);
        c.connection.handlePickItemFromBlock(new ServerboundPickItemFromBlockPacket(target, false));
        check(h, inHand(c, Items.POLISHED_GRANITE) == 64 && stock(drawer, Items.POLISHED_GRANITE) == capacity - 128,
                "botão do meio: 64 da gaveta; gaveta=" + stock(drawer, Items.POLISHED_GRANITE));
        lab.cleanup();
        h.succeed();
    }

    /**
     * Gaveta compactadora: o mesmo estoque aparece como bloco e como item (9 bolas de slime = 1 bloco). Pedir bolas
     * tira do estoque comum; a soma em bolas nunca muda, e a N guarda bolas nela porque ela "já tem".
     */
    @GameTest
    public void compactingDrawerKeepsTheSameStockInEveryForm(GameTestHelper h) {
        if (missing(h)) {
            return;
        }
        Lab lab = new Lab(h);
        BlockPos pos = lab.bare(block("compacting_drawers_2"), 2, 2, 2);
        Storage<ItemVariant> drawer = storage(lab, pos);
        check(h, put(drawer, Items.SLIME_BALL, 90) == 90, "a compactadora devia aceitar 90 bolas");
        check(h, stock(drawer, Items.SLIME_BLOCK) == 10, "90 bolas = 10 blocos: " + stock(drawer, Items.SLIME_BLOCK));

        ServerPlayer p = player(lab);
        check(h, PlayerSources.of(p).available(new ItemStack(Items.SLIME_BALL)) == 90, "fontes deviam ver 90 bolas");
        int hand = p.getInventory().getSelectedSlot();
        p.getInventory().setItem(hand, new ItemStack(Items.SLIME_BALL, 1));
        RefillService.tickPlayer(p);
        p.getInventory().setItem(hand, ItemStack.EMPTY);
        RefillService.tickPlayer(p);
        check(h, inHand(p, Items.SLIME_BALL) == 64 && stock(drawer, Items.SLIME_BALL) == 26,
                "mão: 64 bolas, sobram 26: gaveta=" + stock(drawer, Items.SLIME_BALL));
        check(h, stock(drawer, Items.SLIME_BLOCK) == 2, "26 bolas = 2 blocos inteiros: " + stock(drawer, Items.SLIME_BLOCK));

        ServerPlayer q = player(lab);
        Lab.give(q, 10, Items.SLIME_BALL, 10);
        QuickStackService.handle(q);
        check(h, stock(drawer, Items.SLIME_BALL) == 36 && Lab.carried(q, Items.SLIME_BALL) == 0, "N: as 10 bolas vão à compactadora");
        check(h, BenchPool.of(q).contents().stream().anyMatch(s -> s.item().is(Items.SLIME_BALL) && s.count() == 36),
                "o painel da bancada mostra as 36 bolas");
        lab.cleanup();
        h.succeed();
    }

    /** Controlador de gavetas (enxerga todas as ligadas a ele) nunca é armazenamento: contaria tudo duas vezes. */
    @GameTest
    public void controllerIsNotStorage(GameTestHelper h) {
        if (missing(h)) {
            return;
        }
        Lab lab = new Lab(h);
        BlockPos controller = lab.bare(block("controller"), 2, 2, 2);
        BlockPos drawer = lab.bare(block("oak_full_drawers_1"), 3, 2, 2);
        ServerPlayer p = player(lab);
        NearbyContainers.Found found = NearbyContainers.find(p, true);
        check(h, found.modStorage().stream().noneMatch(e -> e.where().contains(controller)), "o controlador não é armazenamento");
        check(h, found.modStorage().stream().anyMatch(e -> e.where().contains(drawer)), "a gaveta é armazenamento");
        lab.cleanup();
        h.succeed();
    }
}
