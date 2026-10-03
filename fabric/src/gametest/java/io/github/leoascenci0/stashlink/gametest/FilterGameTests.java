package io.github.leoascenci0.stashlink.gametest;

import io.github.leoascenci0.stashlink.config.Feature;
import io.github.leoascenci0.stashlink.config.PlayerPrefs;
import io.github.leoascenci0.stashlink.config.PlayerPrefsStore;
import io.github.leoascenci0.stashlink.config.StashLinkConfig;
import io.github.leoascenci0.stashlink.label.Label;
import io.github.leoascenci0.stashlink.label.Labels;
import io.github.leoascenci0.stashlink.network.LockSlotRequest;
import io.github.leoascenci0.stashlink.network.ReceivesRequest;
import io.github.leoascenci0.stashlink.network.SlotLocksSync;
import io.github.leoascenci0.stashlink.platform.Services;
import io.github.leoascenci0.stashlink.quickstack.ItemCategory;
import io.github.leoascenci0.stashlink.quickstack.QuickStackReceive;
import io.github.leoascenci0.stashlink.quickstack.QuickStackReceiveService;
import io.github.leoascenci0.stashlink.quickstack.QuickStackService;
import io.github.leoascenci0.stashlink.quickstack.ReceiveHolder;
import io.github.leoascenci0.stashlink.slotlock.SlotLockService;
import io.github.leoascenci0.stashlink.slotlock.SlotLockSync;
import io.github.leoascenci0.stashlink.slotlock.SlotLocks;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.EnumSet;
import java.util.List;
import java.util.Random;

/**
 * Item 17: o que cada baú recebe com a tecla N. Servidor de verdade, jogadores em sobrevivência. Dois filtros:
 * o botão "recebe com a N" do baú (guardado no bloco) e as categorias de item de cada jogador (armadura, ferramentas,
 * armas, comida, poções). Cada teste usa itens que nenhum outro usa (os testes rodam lado a lado no mesmo mundo).
 */
public class FilterGameTests {
    private static final int ALL_CATEGORIES = Feature.CAT_ARMOR.bit() | Feature.CAT_TOOLS.bit()
            | Feature.CAT_WEAPONS.bit() | Feature.CAT_FOOD.bit() | Feature.CAT_POTIONS.bit();

    private static void check(GameTestHelper h, boolean ok, String msg) {
        h.assertTrue(ok, msg);
    }

    /** O intervalo mínimo da N é por jogador, então cada fase usa um jogador novo. */
    private static ServerPlayer player(Lab lab, int disabledMask) {
        ServerPlayer p = lab.player(4, 2, 4);
        PlayerPrefsStore.set(p.getUUID(), new PlayerPrefs(8, 1, List.of(), disabledMask, 8));
        return p;
    }

    private static void request(ServerPlayer p, int menuId, boolean receives) {
        QuickStackReceiveService.handle(p, new ReceivesRequest(menuId, receives));
    }

    private static int inBackpack(ServerPlayer p, Item item) {
        return p.getInventory().countItem(item);
    }

    private static void resetLocks() {
        StashLinkConfig.lockedFeatures = EnumSet.noneOf(Feature.class);
    }

    // ---------------------------------------------------------------- botão do baú

    @GameTest
    public void chestWithNOffNeverReceives(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        Lab.fill(chest, 0, Items.TUFF, 10);                           // o baú já tem tufo: a N guardaria nele
        ServerPlayer owner = player(lab, 0);
        Lab.open(owner, chest, 1);
        check(h, QuickStackReceive.accepts(chest), "o padrão é receber");
        request(owner, 1, false);
        check(h, !QuickStackReceive.accepts(chest), "o botão devia ter desligado");
        check(h, Lab.count(chest, Items.TUFF) == 10, "mexer no botão não move item");
        Lab.close(owner);

        ServerPlayer p = player(lab, 0);
        Lab.give(p, 12, Items.TUFF, 30);
        QuickStackService.handle(p);
        check(h, Lab.count(chest, Items.TUFF) == 10 && inBackpack(p, Items.TUFF) == 30,
                "baú com a N desligada nunca recebe nada. baú=" + Lab.count(chest, Items.TUFF));

        // ligar de novo: volta a receber
        ServerPlayer owner2 = player(lab, 0);
        Lab.open(owner2, chest, 2);
        request(owner2, 2, true);
        Lab.close(owner2);
        ServerPlayer p2 = player(lab, 0);
        Lab.give(p2, 12, Items.TUFF, 30);
        QuickStackService.handle(p2);
        check(h, Lab.count(chest, Items.TUFF) == 40 && inBackpack(p2, Items.TUFF) == 0, "ligada: a N guarda de novo");
        lab.cleanup();
        h.succeed();
    }

    /** O item vai para outro baú que também o tem, mesmo o desligado sendo o mais perto. */
    @GameTest
    public void aDisabledChestIsSkippedForTheNextOne(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container near = lab.chest(4, 2, 5);
        Container far = lab.chest(2, 2, 2);
        Lab.fill(near, 0, Items.BASALT, 5);
        Lab.fill(far, 0, Items.BASALT, 5);
        ServerPlayer owner = player(lab, 0);
        Lab.open(owner, near, 1);
        request(owner, 1, false);
        Lab.close(owner);
        ServerPlayer p = player(lab, 0);
        Lab.give(p, 12, Items.BASALT, 20);
        QuickStackService.handle(p);
        check(h, Lab.count(near, Items.BASALT) == 5, "o baú desligado (mais perto) não recebe");
        check(h, Lab.count(far, Items.BASALT) == 25 && inBackpack(p, Items.BASALT) == 0, "o outro baú recebe tudo");
        lab.cleanup();
        h.succeed();
    }

    /** Mesmo com um slot reservado para o item (Item 13), o baú com a N desligada não recebe. */
    @GameTest
    public void aReservationDoesNotOverrideTheButton(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        ServerPlayer owner = player(lab, 0);
        ChestMenu menu = Lab.open(owner, chest, 1);
        menu.setCarried(new ItemStack(Items.CALCITE, 1));
        SlotLockService.handle(owner, new LockSlotRequest(1, 7));
        menu.setCarried(ItemStack.EMPTY);
        check(h, SlotLocks.lockedItem(chest, 7) == Items.CALCITE, "reserva criada");
        request(owner, 1, false);
        Lab.close(owner);
        ServerPlayer p = player(lab, 0);
        Lab.give(p, 12, Items.CALCITE, 10);
        QuickStackService.handle(p);
        check(h, Lab.count(chest, Items.CALCITE) == 0 && inBackpack(p, Items.CALCITE) == 10,
                "a N desligada vale mais que a reserva");
        lab.cleanup();
        h.succeed();
    }

    @GameTest
    public void doubleChestButtonCoversBothHalves(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container big = lab.doubleChest(2, 2, 2);
        Lab.fill(big, 30, Items.DRIPSTONE_BLOCK, 4);                  // tem na metade de trás
        ServerPlayer owner = player(lab, 0);
        Lab.open(owner, big, 1);
        request(owner, 1, false);
        BlockEntity left = lab.level.getBlockEntity(h.absolutePos(new BlockPos(2, 2, 2)));
        BlockEntity right = lab.level.getBlockEntity(h.absolutePos(new BlockPos(3, 2, 2)));
        check(h, !QuickStackReceive.accepts((Container) left) && !QuickStackReceive.accepts((Container) right)
                && !QuickStackReceive.accepts(big), "as duas metades desligam juntas");
        Lab.close(owner);
        ServerPlayer p = player(lab, 0);
        Lab.give(p, 12, Items.DRIPSTONE_BLOCK, 9);
        QuickStackService.handle(p);
        check(h, Lab.count(big, Items.DRIPSTONE_BLOCK) == 4 && inBackpack(p, Items.DRIPSTONE_BLOCK) == 9,
                "o baú duplo não recebe");

        // uma metade religada à mão: o conjunto continua desligado (lado seguro)
        ((ReceiveHolder) left).stashlink$setReceivesQuickStack(true);
        check(h, !QuickStackReceive.accepts(big), "basta uma metade desligada");
        ServerPlayer owner2 = player(lab, 0);
        Lab.open(owner2, big, 2);
        request(owner2, 2, true);
        check(h, QuickStackReceive.accepts(big) && QuickStackReceive.accepts((Container) left)
                && QuickStackReceive.accepts((Container) right), "o pedido de ligar religa as duas");
        lab.cleanup();
        h.succeed();
    }

    @GameTest
    public void buttonPersistsAndDefaultIsNotStored(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        BlockPos pos = h.absolutePos(new BlockPos(2, 2, 2));
        BlockEntity be = lab.level.getBlockEntity(pos);
        CompoundTag fresh = be.saveWithFullMetadata(lab.level.registryAccess());
        check(h, !fresh.contains(QuickStackReceive.KEY), "o padrão (recebe) não grava nada no bloco");

        ServerPlayer owner = player(lab, 0);
        Lab.open(owner, chest, 1);
        request(owner, 1, false);
        CompoundTag tag = be.saveWithFullMetadata(lab.level.registryAccess());
        BlockEntity loaded = BlockEntity.loadStatic(pos, be.getBlockState(), tag, lab.level.registryAccess());
        check(h, !QuickStackReceive.accepts((Container) loaded), "desligado volta do disco desligado");

        request(owner, 1, true);
        CompoundTag after = be.saveWithFullMetadata(lab.level.registryAccess());
        BlockEntity reloaded = BlockEntity.loadStatic(pos, be.getBlockState(), after, lab.level.registryAccess());
        check(h, QuickStackReceive.accepts((Container) reloaded) && !after.contains(QuickStackReceive.KEY),
                "religar também persiste");

        // quebrar o baú leva o botão: um baú novo no mesmo lugar recebe como sempre
        request(owner, 1, false);
        Lab.close(owner);
        lab.level.removeBlock(pos, false);
        Container again = lab.chest(2, 2, 2);
        check(h, QuickStackReceive.accepts(again), "baú novo no mesmo lugar começa recebendo");
        lab.cleanup();
        h.succeed();
    }

    /** Shulker: o botão e o rótulo vão juntos no item, também quando quebrada em SOBREVIVÊNCIA (drop real). */
    @GameTest
    public void shulkerKeepsTheButtonInItsDrop(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container box = lab.block(Blocks.SHULKER_BOX, 2, 2, 2);
        BlockPos pos = h.absolutePos(new BlockPos(2, 2, 2));
        ServerPlayer owner = player(lab, 0);
        owner.containerMenu = new ShulkerBoxMenu(1, owner.getInventory(), box);
        request(owner, 1, false);
        check(h, !QuickStackReceive.accepts(box), "shulker desligada");
        Lab.close(owner);
        Labels.set(lab.level.getBlockEntity(pos), new Label("Minérios", ""));

        BlockEntity old = lab.level.getBlockEntity(pos);
        List<ItemStack> drops = Block.getDrops(lab.level.getBlockState(pos), lab.level, pos, old);
        check(h, drops.size() == 1 && drops.get(0).is(Items.SHULKER_BOX), "dropou 1 shulker: " + drops);
        DataComponentMap components = drops.get(0).getComponents();
        lab.level.removeBlock(pos, false);

        Container fresh = lab.block(Blocks.SHULKER_BOX, 7, 2, 2);
        BlockEntity freshBe = lab.level.getBlockEntity(h.absolutePos(new BlockPos(7, 2, 2)));
        check(h, QuickStackReceive.accepts(fresh), "shulker nova começa recebendo");
        freshBe.applyComponents(components, DataComponentPatch.EMPTY);
        check(h, !QuickStackReceive.accepts(fresh), "o botão desligado voltou ao colocar (drop de sobrevivência)");
        check(h, Labels.get(freshBe).name().equals("Minérios"), "e o rótulo continua junto: " + Labels.get(freshBe));

        // caminho do criativo/pegar bloco (collectComponents) também leva os dois
        DataComponentMap picked = freshBe.collectComponents();
        Container third = lab.block(Blocks.SHULKER_BOX, 9, 2, 2);
        lab.level.getBlockEntity(h.absolutePos(new BlockPos(9, 2, 2))).applyComponents(picked, DataComponentPatch.EMPTY);
        check(h, !QuickStackReceive.accepts(third), "collectComponents leva o botão");

        // shulker ligada (padrão) solta o item sem dado nenhum do mod
        lab.block(Blocks.SHULKER_BOX, 11, 2, 2);
        BlockPos plainPos = h.absolutePos(new BlockPos(11, 2, 2));
        List<ItemStack> plainDrops = Block.getDrops(lab.level.getBlockState(plainPos), lab.level, plainPos,
                lab.level.getBlockEntity(plainPos));
        check(h, plainDrops.size() == 1 && !plainDrops.get(0).has(DataComponents.CUSTOM_DATA),
                "shulker padrão não leva dado do mod: " + plainDrops);
        lab.cleanup();
        h.succeed();
    }

    // ---------------------------------------------------------------- pedidos inválidos

    @GameTest
    public void invalidRequestsAreIgnored(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        ServerPlayer p = player(lab, 0);
        Lab.open(p, chest, 1);
        request(p, 99, false);                                        // menu errado
        check(h, QuickStackReceive.accepts(chest), "id de menu errado é ignorado");
        Lab.close(p);
        request(p, 1, false);                                         // menu já fechado
        check(h, QuickStackReceive.accepts(chest), "sem menu aberto é ignorado");

        ServerPlayer far = lab.player(4, 2, 4);
        Lab.open(far, chest, 3);
        far.setPos(far.getX() + 200, far.getY(), far.getZ());         // longe demais: o menu deixa de ser válido
        request(far, 3, false);
        check(h, QuickStackReceive.accepts(chest), "jogador longe é ignorado");

        // baú do End: sem memória, nada a configurar (e nada quebra)
        ServerPlayer q = player(lab, 0);
        q.containerMenu = ChestMenu.threeRows(5, q.getInventory(), q.getEnderChestInventory());
        request(q, 5, false);
        check(h, QuickStackReceive.accepts(q.getEnderChestInventory()), "baú do End não é configurável");
        lab.cleanup();
        h.succeed();
    }

    @GameTest
    public void aChestOpenByAnotherPlayerCannotBeChanged(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        ServerPlayer a = player(lab, 0);
        ServerPlayer b = player(lab, 0);
        Lab.open(a, chest, 1);
        Lab.open(b, chest, 2);
        request(a, 1, false);
        check(h, QuickStackReceive.accepts(chest), "com outro jogador no baú, ninguém muda o botão");
        Lab.close(b);
        request(a, 1, false);
        check(h, !QuickStackReceive.accepts(chest), "sozinho no baú, muda");
        lab.cleanup();
        h.succeed();
    }

    // ---------------------------------------------------------------- categorias

    @GameTest
    public void itemsAreClassifiedByGameTags(GameTestHelper h) {
        check(h, ItemCategory.of(new ItemStack(Items.NETHERITE_CHESTPLATE)) == ItemCategory.ARMOR, "peitoral");
        check(h, ItemCategory.of(new ItemStack(Items.LEATHER_BOOTS)) == ItemCategory.ARMOR, "bota");
        check(h, ItemCategory.of(new ItemStack(Items.TURTLE_HELMET)) == ItemCategory.ARMOR, "capacete de tartaruga");
        check(h, ItemCategory.of(new ItemStack(Items.ELYTRA)) == ItemCategory.ARMOR, "asa-delta");
        check(h, ItemCategory.of(new ItemStack(Items.SHIELD)) == ItemCategory.ARMOR, "escudo");
        check(h, ItemCategory.of(new ItemStack(Items.DIAMOND_PICKAXE)) == ItemCategory.TOOLS, "picareta");
        check(h, ItemCategory.of(new ItemStack(Items.IRON_AXE)) == ItemCategory.TOOLS, "machado é ferramenta, não arma");
        check(h, ItemCategory.of(new ItemStack(Items.SHEARS)) == ItemCategory.TOOLS, "tesoura");
        check(h, ItemCategory.of(new ItemStack(Items.FISHING_ROD)) == ItemCategory.TOOLS, "vara");
        check(h, ItemCategory.of(new ItemStack(Items.NETHERITE_SWORD)) == ItemCategory.WEAPONS, "espada");
        check(h, ItemCategory.of(new ItemStack(Items.BOW)) == ItemCategory.WEAPONS, "arco");
        check(h, ItemCategory.of(new ItemStack(Items.TRIDENT)) == ItemCategory.WEAPONS, "tridente");
        check(h, ItemCategory.of(new ItemStack(Items.MACE)) == ItemCategory.WEAPONS, "maça");
        check(h, ItemCategory.of(new ItemStack(Items.COOKED_BEEF)) == ItemCategory.FOOD, "comida");
        check(h, ItemCategory.of(new ItemStack(Items.GOLDEN_APPLE)) == ItemCategory.FOOD, "maçã dourada");
        check(h, ItemCategory.of(new ItemStack(Items.POTION)) == ItemCategory.POTIONS, "poção");
        check(h, ItemCategory.of(new ItemStack(Items.SPLASH_POTION)) == ItemCategory.POTIONS, "poção de arremesso");
        check(h, ItemCategory.of(new ItemStack(Items.TIPPED_ARROW)) == null, "flecha de poção é munição, não poção");
        // blocos comuns e o resto ficam fora (não precisam de filtro)
        for (Item item : List.of(Items.COBBLESTONE, Items.OAK_PLANKS, Items.IRON_ORE, Items.DIAMOND, Items.STICK,
                Items.CHEST, Items.TORCH, Items.ENDER_PEARL, Items.ARROW)) {
            check(h, ItemCategory.of(new ItemStack(item)) == null, item + " não tem categoria");
        }
        check(h, ItemCategory.of(ItemStack.EMPTY) == null, "vazio não tem categoria");
        h.succeed();
    }

    /** Uma categoria desligada nunca é guardada, mesmo com o baú já tendo o item e mesmo com slot reservado. */
    @GameTest
    public void armorWithTheCategoryOffIsNeverStored(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        chest.setItem(0, new ItemStack(Items.GOLDEN_BOOTS));            // o baú já tem esse item
        ServerPlayer owner = player(lab, 0);
        ChestMenu menu = Lab.open(owner, chest, 1);
        menu.setCarried(new ItemStack(Items.GOLDEN_HELMET));
        SlotLockService.handle(owner, new LockSlotRequest(1, 9));       // slot reservado para o capacete
        menu.setCarried(ItemStack.EMPTY);
        Lab.close(owner);

        ServerPlayer off = player(lab, Feature.CAT_ARMOR.bit());
        off.getInventory().setItem(12, new ItemStack(Items.GOLDEN_BOOTS));
        off.getInventory().setItem(13, new ItemStack(Items.GOLDEN_HELMET));
        Lab.give(off, 14, Items.GOLDEN_CARROT, 5);                      // comida: outra categoria, continua valendo
        chest.setItem(1, new ItemStack(Items.GOLDEN_CARROT, 1));
        QuickStackService.handle(off);
        check(h, off.getInventory().getItem(12).is(Items.GOLDEN_BOOTS) && off.getInventory().getItem(13).is(Items.GOLDEN_HELMET),
                "armadura com a categoria desligada nunca é guardada");
        check(h, chest.countItem(Items.GOLDEN_BOOTS) == 1 && chest.countItem(Items.GOLDEN_HELMET) == 0,
                "o baú não ganhou armadura");
        check(h, inBackpack(off, Items.GOLDEN_CARROT) == 0 && chest.countItem(Items.GOLDEN_CARROT) == 6,
                "as outras categorias seguem normais");

        ServerPlayer on = player(lab, 0);
        on.getInventory().setItem(12, new ItemStack(Items.GOLDEN_BOOTS));
        on.getInventory().setItem(13, new ItemStack(Items.GOLDEN_HELMET));
        QuickStackService.handle(on);
        check(h, chest.countItem(Items.GOLDEN_BOOTS) == 2 && chest.getItem(9).is(Items.GOLDEN_HELMET),
                "com a categoria ligada guarda (e usa a reserva)");
        lab.cleanup();
        h.succeed();
    }

    /** Cada categoria, uma por uma: desligada não guarda, ligada guarda. */
    @GameTest
    public void everyCategoryTogglesIndependently(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        Item[] items = {Items.NETHERITE_HELMET, Items.NETHERITE_HOE, Items.NETHERITE_SPEAR,
                Items.ENCHANTED_GOLDEN_APPLE, Items.LINGERING_POTION};
        Feature[] features = {Feature.CAT_ARMOR, Feature.CAT_TOOLS, Feature.CAT_WEAPONS, Feature.CAT_FOOD,
                Feature.CAT_POTIONS};
        for (int i = 0; i < items.length; i++) {
            chest.setItem(i, new ItemStack(items[i]));                  // o baú tem um de cada
        }
        for (int i = 0; i < items.length; i++) {
            ServerPlayer p = player(lab, features[i].bit());            // só a categoria i desligada
            for (int j = 0; j < items.length; j++) {
                p.getInventory().setItem(10 + j, new ItemStack(items[j]));
            }
            QuickStackService.handle(p);
            for (int j = 0; j < items.length; j++) {
                boolean kept = p.getInventory().countItem(items[j]) == 1;
                check(h, kept == (i == j), items[j] + " com só " + features[i] + " desligada: ficou na mochila=" + kept);
            }
            // devolve tudo para o próximo jogador começar do mesmo estado
            for (int j = 0; j < items.length; j++) {
                chest.setItem(j, new ItemStack(items[j]));
            }
            for (int s = 0; s < 36; s++) {
                p.getInventory().setItem(s, ItemStack.EMPTY);
            }
        }
        lab.cleanup();
        h.succeed();
    }

    @GameTest
    public void aServerLockedCategoryStopsEveryone(GameTestHelper h) {
        resetLocks();
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        Lab.fill(chest, 0, Items.HONEY_BOTTLE, 1);
        StashLinkConfig.setFeatureLocked(Feature.CAT_FOOD, true);
        ServerPlayer p = player(lab, 0);                                // o jogador deixou ligado, o servidor trancou
        Lab.give(p, 12, Items.HONEY_BOTTLE, 3);
        QuickStackService.handle(p);
        check(h, inBackpack(p, Items.HONEY_BOTTLE) == 3 && Lab.count(chest, Items.HONEY_BOTTLE) == 1,
                "categoria trancada no servidor: a N nunca guarda");
        resetLocks();
        ServerPlayer q = player(lab, 0);
        Lab.give(q, 12, Items.HONEY_BOTTLE, 3);
        QuickStackService.handle(q);
        check(h, inBackpack(q, Items.HONEY_BOTTLE) == 0, "destrancada volta a guardar");
        lab.cleanup();
        h.succeed();
    }

    /** Blocos comuns nunca dependem de filtro: sem categoria, com todas as categorias desligadas ainda guardam. */
    @GameTest
    public void plainBlocksIgnoreAllCategories(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        Lab.fill(chest, 0, Items.MANGROVE_PLANKS, 1);
        ServerPlayer p = player(lab, ALL_CATEGORIES);
        Lab.give(p, 12, Items.MANGROVE_PLANKS, 20);
        QuickStackService.handle(p);
        check(h, inBackpack(p, Items.MANGROVE_PLANKS) == 0 && Lab.count(chest, Items.MANGROVE_PLANKS) == 21,
                "blocos comuns seguem guardando");
        lab.cleanup();
        h.succeed();
    }

    // ---------------------------------------------------------------- pacote

    @GameTest
    public void syncPacketCarriesTheButton(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        ServerPlayer p = player(lab, 0);
        ChestMenu menu = Lab.open(p, chest, 1);
        check(h, SlotLockSync.receives(p, menu), "ligado por padrão");
        request(p, 1, false);
        check(h, !SlotLockSync.receives(p, menu), "desligado");
        SlotLocksSync sent = new SlotLocksSync(1, SlotLockSync.snapshot(p, menu), SlotLockSync.receives(p, menu));
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),
                lab.level.registryAccess());
        SlotLocksSync.STREAM_CODEC.encode(buf, sent);
        check(h, !SlotLocksSync.STREAM_CODEC.decode(buf).receives(), "o valor viaja pela rede");
        RegistryFriendlyByteBuf req = new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),
                lab.level.registryAccess());
        ReceivesRequest.STREAM_CODEC.encode(req, new ReceivesRequest(7, false));
        ReceivesRequest back = ReceivesRequest.STREAM_CODEC.decode(req);
        check(h, back.containerId() == 7 && !back.receives(), "pedido deformado");
        check(h, !Services.PLATFORM.sendIfSupported(p, sent), "cliente sem o mod não recebe");
        SlotLockSync.tick(lab.level.getServer());
        lab.cleanup();
        h.succeed();
    }

    // ---------------------------------------------------------------- fuzz: 2 jogadores, soma de itens conservada

    /**
     * 600 ações aleatórias de 2 jogadores (N, botão do baú, categorias, abrir/fechar) em 2 baús: depois de CADA ação
     * a soma de cada item (baús + jogadores) é a mesma, e um baú desligado nunca ganha item pela N.
     */
    @GameTest
    public void fuzzConservesItemsAndHonoursFilters(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container c1 = lab.chest(2, 2, 2);
        Container c2 = lab.chest(6, 2, 2);
        Item[] items = {Items.TUFF, Items.DEEPSLATE, Items.NETHERITE_BOOTS, Items.GOLDEN_CHESTPLATE, Items.SWEET_BERRIES,
                Items.GOLDEN_PICKAXE};
        for (Container c : List.of(c1, c2)) {
            for (int i = 0; i < items.length; i++) {
                c.setItem(i, new ItemStack(items[i], items[i].getDefaultMaxStackSize() == 1 ? 1 : 3));
            }
        }
        Random rnd = new Random(1717);
        ServerPlayer[] players = {player(lab, 0), player(lab, 0)};
        for (ServerPlayer p : players) {
            for (int i = 0; i < items.length; i++) {
                int n = items[i].getDefaultMaxStackSize() == 1 ? 1 : 20;
                p.getInventory().setItem(10 + i, new ItemStack(items[i], n));
            }
        }
        int[] before = new int[items.length];
        for (int i = 0; i < items.length; i++) {
            before[i] = c1.countItem(items[i]) + c2.countItem(items[i]) + inBackpack(players[0], items[i])
                    + inBackpack(players[1], items[i]);
        }
        int menuId = 10;
        for (int step = 0; step < 600; step++) {
            int who = rnd.nextInt(2);
            ServerPlayer p = players[who];
            Container target = rnd.nextBoolean() ? c1 : c2;
            switch (rnd.nextInt(5)) {
                case 0 -> {
                    int c1Before = c1.countItem(items[0]);
                    int c2Before = c2.countItem(items[0]);
                    Lab.close(p);
                    // jogador novo a cada N (o intervalo mínimo é por jogador); leva a mochila do antigo
                    ServerPlayer fresh = player(lab, (rnd.nextInt(32) << Feature.CAT_ARMOR.ordinal()) & ALL_CATEGORIES);
                    for (int s = 0; s < 36; s++) {
                        fresh.getInventory().setItem(s, p.getInventory().getItem(s).copy());
                        p.getInventory().setItem(s, ItemStack.EMPTY);
                    }
                    players[who] = fresh;
                    QuickStackService.handle(fresh);
                    if (!QuickStackReceive.accepts(c1)) {
                        check(h, c1.countItem(items[0]) == c1Before, "baú 1 desligado ganhou item na rodada " + step);
                    }
                    if (!QuickStackReceive.accepts(c2)) {
                        check(h, c2.countItem(items[0]) == c2Before, "baú 2 desligado ganhou item na rodada " + step);
                    }
                }
                case 1 -> {
                    Lab.open(p, target, ++menuId);
                    request(p, menuId, rnd.nextBoolean());
                    Lab.close(p);
                }
                case 2 -> {
                    ServerPlayer other = players[1 - who];
                    Lab.open(other, target, ++menuId);
                    boolean before2 = QuickStackReceive.accepts(target);
                    Lab.open(p, target, ++menuId);
                    request(p, menuId, rnd.nextBoolean());           // ignorado: o outro está com o baú aberto
                    check(h, QuickStackReceive.accepts(target) == before2, "mudou com o baú aberto por outro, rodada " + step);
                    Lab.close(p);
                    Lab.close(other);
                }
                case 3 -> Lab.close(p);
                default -> QuickStackReceive.set(target, rnd.nextBoolean());
            }
            for (int i = 0; i < items.length; i++) {
                int sum = c1.countItem(items[i]) + c2.countItem(items[i]) + inBackpack(players[0], items[i])
                        + inBackpack(players[1], items[i]);
                check(h, sum == before[i], items[i] + ": a soma mudou na rodada " + step + " (" + sum + " em vez de " + before[i] + ")");
            }
        }
        lab.cleanup();
        h.succeed();
    }
}
