package io.github.leoascenci0.stashlink.gametest;

import io.github.leoascenci0.stashlink.Constants;
import io.github.leoascenci0.stashlink.bench.BenchPool;
import io.github.leoascenci0.stashlink.bench.BenchSync;
import io.github.leoascenci0.stashlink.quickstack.QuickStackService;
import io.github.leoascenci0.stashlink.refill.RefillService;
import io.github.leoascenci0.stashlink.source.NearbyContainers;
import io.github.leoascenci0.stashlink.source.PlayerSources;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ServerboundPickItemFromBlockPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * Item 26: testes com o Sophisticated Storage de verdade (só quando ele está carregado: {@code gametestRuntimeOnly}).
 * Os itens entram e são contados pela própria tomada de itens do mod, como um funil faria; o StashLink age pelos mesmos
 * caminhos do jogo (N, mão, botão do meio, bancada). Cada teste confere a soma: nada duplica, nada some.
 */
final class ThirdPartyStorageTests {
    private static final String SS = "sophisticatedstorage";

    private ThirdPartyStorageTests() {
    }

    static <T> void register(BiConsumer<String, T> out, BiFunction<String, Consumer<GameTestHelper>, T> make) {
        if (!ModList.get().isLoaded(SS)) {
            return;
        }
        out.accept("sophisticated_chest_quick_stack_refill_and_middle_click",
                make.apply("", ThirdPartyStorageTests::chestQuickStackRefillAndMiddleClick));
        out.accept("sophisticated_chest_serves_the_bench", make.apply("", ThirdPartyStorageTests::chestServesTheBench));
        out.accept("sophisticated_double_chest_counts_once", make.apply("", ThirdPartyStorageTests::doubleChestCountsOnce));
        out.accept("sophisticated_limited_barrel_holds_more_than_a_stack",
                make.apply("", ThirdPartyStorageTests::limitedBarrelHoldsMoreThanAStack));
        out.accept("sophisticated_controller_and_shulker_are_not_storage",
                make.apply("", ThirdPartyStorageTests::controllerAndShulkerAreNotStorage));
    }

    private static void check(GameTestHelper h, boolean ok, String msg) {
        h.assertTrue(ok, msg);
    }

    private static Block block(String path) {
        return BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath(SS, path));
    }

    private static ResourceHandler<ItemResource> handler(Lab lab, BlockPos pos) {
        ResourceHandler<ItemResource> handler = lab.level.getCapability(Capabilities.Item.BLOCK, pos, null);
        if (handler == null) {
            throw new IllegalStateException("sem tomada de itens em " + pos + ": " + lab.level.getBlockState(pos));
        }
        return handler;
    }

    /** Guarda pela tomada do próprio mod (como um funil); devolve quanto entrou. */
    private static int put(ResourceHandler<ItemResource> handler, Item item, int n) {
        try (Transaction tx = Transaction.openRoot()) {
            int in = handler.insert(ItemResource.of(item), n, tx);
            tx.commit();
            return in;
        }
    }

    private static long stock(ResourceHandler<ItemResource> handler, Item item) {
        long total = 0;
        for (int i = 0; i < handler.size(); i++) {
            if (handler.getResource(i).is(item)) {
                total += handler.getAmountAsLong(i);
            }
        }
        return total;
    }

    private static ServerPlayer player(Lab lab, double x, double y, double z) {
        ServerPlayer p = lab.player(x, y, z);
        Lab.prefs(p, 8, true);
        p.getAbilities().instabuild = false;
        return p;
    }

    private static int inHand(ServerPlayer p, Item item) {
        ItemStack held = p.getInventory().getItem(p.getInventory().getSelectedSlot());
        return held.is(item) ? held.getCount() : 0;
    }

    private static void chestQuickStackRefillAndMiddleClick(GameTestHelper h) {
        Lab lab = new Lab(h);
        BlockPos pos = lab.bare(block("chest"), 2, 2, 2);
        ResourceHandler<ItemResource> chest = handler(lab, pos);
        check(h, put(chest, Items.TUFF, 10) == 10 && put(chest, Items.BRICKS, 100) == 100
                && put(chest, Items.BOOKSHELF, 70) == 70, "o baú do Sophisticated devia aceitar os itens do teste");

        ServerPlayer a = player(lab, 4, 2, 4);
        Lab.give(a, 10, Items.TUFF, 64);
        QuickStackService.handle(a);
        check(h, stock(chest, Items.TUFF) == 74 && Lab.carried(a, Items.TUFF) == 0, "N: o tufo devia ir ao baú: baú="
                + stock(chest, Items.TUFF) + " jogador=" + Lab.carried(a, Items.TUFF));

        ServerPlayer b = player(lab, 4, 2, 4);
        int hand = b.getInventory().getSelectedSlot();
        b.getInventory().setItem(hand, new ItemStack(Items.BRICKS, 1));
        RefillService.tickPlayer(b);
        b.getInventory().setItem(hand, ItemStack.EMPTY);
        RefillService.tickPlayer(b);
        check(h, inHand(b, Items.BRICKS) == 64 && stock(chest, Items.BRICKS) == 36, "mão: devia vir 64 tijolos do baú: mão="
                + inHand(b, Items.BRICKS) + " baú=" + stock(chest, Items.BRICKS));

        ServerPlayer c = player(lab, 4, 2, 4);
        BlockPos target = lab.bare(Blocks.BOOKSHELF, 4, 2, 6);
        c.connection.handlePickItemFromBlock(new ServerboundPickItemFromBlockPacket(target, false));
        check(h, inHand(c, Items.BOOKSHELF) == 64 && stock(chest, Items.BOOKSHELF) == 6, "botão do meio: devia vir 64 estantes: mão="
                + inHand(c, Items.BOOKSHELF) + " baú=" + stock(chest, Items.BOOKSHELF));
        lab.cleanup();
        h.succeed();
    }

    private static void chestServesTheBench(GameTestHelper h) {
        Lab lab = new Lab(h);
        BlockPos pos = lab.bare(block("chest"), 2, 2, 2);
        ResourceHandler<ItemResource> chest = handler(lab, pos);
        put(chest, Items.BIRCH_PLANKS, 5);
        ServerPlayer p = player(lab, 4, 2, 4);
        check(h, BenchPool.of(p).contents().stream().anyMatch(s -> s.item().is(Items.BIRCH_PLANKS) && s.count() == 5),
                "o painel devia listar as 5 tábuas do baú do Sophisticated");

        CraftingMenu menu = new CraftingMenu(1, p.getInventory(),
                ContainerLevelAccess.create(lab.level, lab.bareAt(Blocks.CRAFTING_TABLE, p.blockPosition().below())));
        p.containerMenu = menu;
        ((RecipeBookMenu) menu).handlePlacement(false, false, lab.level.recipeAccess()
                .byKey(ResourceKey.create(Registries.RECIPE, Identifier.withDefaultNamespace("stick"))).orElseThrow(),
                lab.level, p.getInventory());
        check(h, stock(chest, Items.BIRCH_PLANKS) == 3, "a receita devia tirar 2 tábuas do baú: " + stock(chest, Items.BIRCH_PLANKS));

        menu.removed(p);
        p.containerMenu = p.inventoryMenu;
        BenchSync.tick(lab.level.getServer());
        check(h, stock(chest, Items.BIRCH_PLANKS) == 5 && Lab.carried(p, Items.BIRCH_PLANKS) == 0,
                "ao fechar, as tábuas deviam voltar ao baú: baú=" + stock(chest, Items.BIRCH_PLANKS));
        lab.cleanup();
        h.succeed();
    }

    /** Baú duplo do Sophisticated: as duas metades dão o mesmo inventário; o StashLink precisa contar uma vez só. */
    private static void doubleChestCountsOnce(GameTestHelper h) {
        Lab lab = new Lab(h);
        BlockState base = block("chest").defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH);
        BlockPos left = lab.state(base.setValue(BlockStateProperties.CHEST_TYPE, ChestType.LEFT), 2, 2, 2);
        BlockPos right = lab.state(base.setValue(BlockStateProperties.CHEST_TYPE, ChestType.RIGHT), 3, 2, 2);
        BlockEntity main = lab.level.getBlockEntity(right);
        BlockEntity other = lab.level.getBlockEntity(left);
        try {
            main.getClass().getMethod("joinWithChest", main.getClass()).invoke(main, other);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("não consegui juntar o baú duplo do Sophisticated", e);
        }
        put(handler(lab, right), Items.GLOWSTONE_DUST, 30);
        check(h, stock(handler(lab, left), Items.GLOWSTONE_DUST) == 30, "as duas metades deviam mostrar os mesmos 30");
        Constants.LOG.info("[STASHLINK-COMPAT] Sophisticated: metades do baú duplo devolvem o mesmo objeto? {}",
                handler(lab, left) == handler(lab, right));

        ServerPlayer p = player(lab, 4, 2, 4);
        check(h, PlayerSources.of(p).available(new ItemStack(Items.GLOWSTONE_DUST)) == 30,
                "fontes: devia ver 30 (uma vez só), viu " + PlayerSources.of(p).available(new ItemStack(Items.GLOWSTONE_DUST)));
        int listed = 0;
        for (BenchPool.Stack s : BenchPool.of(p).contents()) {
            listed += s.item().is(Items.GLOWSTONE_DUST) ? s.count() : 0;
        }
        check(h, listed == 30, "bancada: devia listar 30, listou " + listed);
        lab.cleanup();
        h.succeed();
    }

    /** Barril limitado: um slot só, que guarda bem mais que 64. Nada pode supor stack ≤ 64. */
    private static void limitedBarrelHoldsMoreThanAStack(GameTestHelper h) {
        Lab lab = new Lab(h);
        BlockPos pos = lab.bare(block("limited_barrel_1"), 2, 2, 2);
        ResourceHandler<ItemResource> barrel = handler(lab, pos);
        int in = put(barrel, Items.COBBLED_DEEPSLATE, 1_000);
        check(h, in > 64, "o barril limitado devia aceitar mais que 64 num slot: " + in);
        ServerPlayer p = player(lab, 4, 2, 4);
        check(h, PlayerSources.of(p).available(new ItemStack(Items.COBBLED_DEEPSLATE)) == in, "fontes deviam ver " + in);

        int hand = p.getInventory().getSelectedSlot();
        p.getInventory().setItem(hand, new ItemStack(Items.COBBLED_DEEPSLATE, 1));
        RefillService.tickPlayer(p);
        p.getInventory().setItem(hand, ItemStack.EMPTY);
        RefillService.tickPlayer(p);
        check(h, inHand(p, Items.COBBLED_DEEPSLATE) == 64 && stock(barrel, Items.COBBLED_DEEPSLATE) == in - 64,
                "mão: 64 do barril; barril=" + stock(barrel, Items.COBBLED_DEEPSLATE));
        lab.cleanup();
        h.succeed();
    }

    /** Controlador (enxerga a rede inteira) e shulker ficam de fora da lista padrão: nunca viram armazenamento. */
    private static void controllerAndShulkerAreNotStorage(GameTestHelper h) {
        Lab lab = new Lab(h);
        BlockPos controller = lab.bare(block("controller"), 2, 2, 2);
        BlockPos shulker = lab.bare(block("shulker_box"), 3, 2, 2);
        BlockPos chest = lab.bare(block("chest"), 4, 2, 2);
        ServerPlayer p = player(lab, 4, 2, 4);
        NearbyContainers.Found found = NearbyContainers.find(p, true);
        check(h, found.modStorage().stream().noneMatch(e -> e.where().contains(controller)), "o controlador não é armazenamento");
        check(h, found.modStorage().stream().noneMatch(e -> e.where().contains(shulker)), "a shulker não é armazenamento (por enquanto)");
        check(h, found.modStorage().stream().anyMatch(e -> e.where().contains(chest)), "o baú é armazenamento");
        lab.cleanup();
        h.succeed();
    }
}
