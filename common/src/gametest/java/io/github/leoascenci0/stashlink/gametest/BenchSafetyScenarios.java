package io.github.leoascenci0.stashlink.gametest;

import io.github.leoascenci0.stashlink.Constants;
import io.github.leoascenci0.stashlink.StashLink;
import io.github.leoascenci0.stashlink.bench.BenchJournal;
import io.github.leoascenci0.stashlink.bench.BenchLedger;
import io.github.leoascenci0.stashlink.bench.BenchPullService;
import io.github.leoascenci0.stashlink.bench.BenchResults;
import io.github.leoascenci0.stashlink.network.BenchPullRequest;
import io.github.leoascenci0.stashlink.quickstack.QuickStackService;
import io.github.leoascenci0.stashlink.source.ContainerInsert;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Item 31: o caderno das bancadas à prova de morte, queda do servidor e troca de dimensão. Cenários comuns aos dois
 * loaders (o Fabric os registra em {@code BenchSafetyGameTests}, o NeoForge em {@code NeoForgeTestMod}).
 *
 * <p>Regra de todos: a soma de cada item (baús + mochilas + cursores + estações abertas + itens no chão) nunca muda,
 * e o que volta ao baú nunca passa do que foi emprestado. Os itens encantados e renomeados contam separados dos
 * comuns do mesmo tipo (o caderno anota a pilha inteira, não só o tipo).
 */
public final class BenchSafetyScenarios {
    private BenchSafetyScenarios() {
    }

    private static void check(GameTestHelper h, boolean ok, String msg) {
        h.assertTrue(ok, msg);
    }

    // ------------------------------------------------------------------------------------------------ ferramentas

    static ItemStack named(Item item, String name, int count) {
        ItemStack stack = new ItemStack(item, count);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
    }

    static ItemStack book(ServerLevel level, ResourceKey<Enchantment> enchantment, int lvl) {
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        ItemEnchantments.Mutable stored = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        stored.set(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(enchantment), lvl);
        book.set(DataComponents.STORED_ENCHANTMENTS, stored.toImmutable());
        return book;
    }

    /** Bancada de verdade (bloco no mundo, embaixo do jogador): ao fechar, o jogo devolve a grade à mochila. */
    static CraftingMenu table(Lab lab, ServerPlayer p, int id) {
        CraftingMenu menu = new CraftingMenu(id, p.getInventory(),
                ContainerLevelAccess.create(lab.level, lab.bareAt(Blocks.CRAFTING_TABLE, p.blockPosition().below())));
        p.containerMenu = menu;
        return menu;
    }

    /** Bigorna de verdade ao lado do jogador, apoiada num bloco (bigorna é bloco que cai: sem apoio, a tela fecha). */
    static AnvilMenu anvil(Lab lab, ServerPlayer p, int id) {
        BlockPos pos = p.blockPosition().south();
        lab.bareAt(Blocks.STONE, pos.below());
        AnvilMenu menu = new AnvilMenu(id, p.getInventory(),
                ContainerLevelAccess.create(lab.level, lab.bareAt(Blocks.ANVIL, pos)));
        p.containerMenu = menu;
        return menu;
    }

    static void recipeBook(ServerPlayer p, String recipe, boolean useMax) {
        var holder = p.level().recipeAccess()
                .byKey(ResourceKey.create(Registries.RECIPE, Identifier.withDefaultNamespace(recipe)))
                .orElseThrow();
        ((RecipeBookMenu) p.containerMenu).handlePlacement(useMax, false, holder, p.level(), p.getInventory());
    }

    /** Pedido do painel "ao cursor" (o mesmo ponto que o pacote do cliente chama). */
    static void toCursor(ServerPlayer p, ItemStack item, boolean one) {
        BenchPullService.handle(p, new BenchPullRequest(p.containerMenu.containerId, item.copyWithCount(1), one));
    }

    /** Pedido do painel "pôr no slot da estação". */
    static void toSlot(ServerPlayer p, ItemStack item, boolean one) {
        BenchPullService.handle(p, new BenchPullRequest(p.containerMenu.containerId, item.copyWithCount(1), one,
                BenchResults.PLACE));
    }

    /** Fecha a tela como o pacote de "fechei" do cliente faz. */
    static void close(ServerPlayer p) {
        if (p.containerMenu != p.inventoryMenu) {
            p.doCloseContainer();
        }
    }

    /** Slot de entrada da estação (não é da mochila nem é o slot de resultado). */
    static boolean stationInput(ServerPlayer p, AbstractContainerMenu menu, Slot slot) {
        if (slot.container == p.getInventory()) {
            return false;
        }
        return !(menu instanceof CraftingMenu && slot.index == 0) && !(menu instanceof AnvilMenu && slot.index == 2);
    }

    /** Quantos iguais a {@code model} (mesmo item e componentes) há nesta lista. */
    static int countIn(Iterable<ItemStack> stacks, ItemStack model) {
        int n = 0;
        for (ItemStack s : stacks) {
            if (!s.isEmpty() && ItemStack.isSameItemSameComponents(s, model)) {
                n += s.getCount();
            }
        }
        return n;
    }

    static int inContainer(Container c, ItemStack model) {
        List<ItemStack> all = new ArrayList<>();
        for (int i = 0; i < c.getContainerSize(); i++) {
            all.add(c.getItem(i));
        }
        return countIn(all, model);
    }

    /** Mochila inteira (com armadura e mão secundária) e cursor. */
    static int onPlayer(ServerPlayer p, ItemStack model) {
        List<ItemStack> all = new ArrayList<>();
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            all.add(p.getInventory().getItem(i));
        }
        all.add(p.containerMenu.getCarried());
        return countIn(all, model);
    }

    /** Nos slots de entrada da estação aberta (o que a queda do servidor perderia, junto com o cursor). */
    static int inStation(ServerPlayer p, ItemStack model) {
        AbstractContainerMenu menu = p.containerMenu;
        if (menu == p.inventoryMenu) {
            return 0;
        }
        List<ItemStack> all = new ArrayList<>();
        for (Slot slot : menu.slots) {
            if (stationInput(p, menu, slot)) {
                all.add(slot.getItem());
            }
        }
        return countIn(all, model);
    }

    static AABB area(GameTestHelper h) {
        return h.getBounds().inflate(3);
    }

    static int onGround(GameTestHelper h, ItemStack model) {
        List<ItemStack> all = new ArrayList<>();
        for (ItemEntity e : h.getLevel().getEntitiesOfClass(ItemEntity.class, area(h))) {
            all.add(e.getItem());
        }
        return countIn(all, model);
    }

    static void clearGround(GameTestHelper h) {
        for (ItemEntity e : h.getLevel().getEntitiesOfClass(ItemEntity.class, area(h))) {
            e.discard();
        }
    }

    /** Tudo o que existe deste item: baús, jogadores (mochila, cursor, estação) e chão. */
    static int total(GameTestHelper h, List<Container> boxes, List<ServerPlayer> players, ItemStack model) {
        int sum = onGround(h, model);
        for (Container c : boxes) {
            sum += inContainer(c, model);
        }
        for (ServerPlayer p : players) {
            sum += onPlayer(p, model) + inStation(p, model);
        }
        return sum;
    }

    /**
     * Morre de verdade e renasce na hora, no mesmo objeto (o teste segue usando o jogador). Chama {@code die} como o dano
     * fatal chama: o jogador simulado é invulnerável a dano (o "cliente" dele nunca termina de carregar o mundo).
     */
    static void dieAndRevive(ServerPlayer p, ServerLevel level) {
        p.setHealth(0f);
        p.die(p.damageSources().genericKill());
        p.setHealth(p.getMaxHealth());
    }

    /** Vai ao Nether e volta (dois teleportes de verdade, que trocam de mundo). */
    static boolean travel(ServerPlayer p, ServerLevel home) {
        ServerLevel nether = home.getServer().getLevel(Level.NETHER);
        if (nether == null) {
            return false;
        }
        Vec3 back = p.position();
        p.teleport(new TeleportTransition(nether, new Vec3(back.x, 100, back.z), Vec3.ZERO, 0f, 0f,
                TeleportTransition.DO_NOTHING));
        p.teleport(new TeleportTransition(home, back, Vec3.ZERO, 0f, 0f, TeleportTransition.DO_NOTHING));
        return p.level() == home;
    }

    /**
     * Simula a queda do servidor e a volta do jogador: o diário é gravado (de ida e volta pelo codec, como no disco),
     * a estação e o cursor se perdem (o jogo não os grava), e ao entrar de novo o diário devolve. Devolve o que o diário
     * guardava para este jogador.
     */
    static List<ItemStack> crashAndRejoin(ServerPlayer p) {
        MinecraftServer server = p.level().getServer();
        List<BenchJournal.Record> mine = new ArrayList<>();
        for (BenchJournal.Record r : BenchJournal.saveForTest(server)) {
            if (r.player().equals(p.getUUID())) {
                mine.add(r);
            }
        }
        AbstractContainerMenu menu = p.containerMenu;
        if (menu != p.inventoryMenu) {
            for (Slot slot : menu.slots) {
                if (slot.container != p.getInventory()) {
                    slot.set(ItemStack.EMPTY);
                }
            }
            menu.setCarried(ItemStack.EMPTY);
        }
        BenchLedger.forgetAsIfCrashed(p);
        p.containerMenu = p.inventoryMenu;   // sem "removed": o processo morreu, ninguém devolveu nada
        BenchJournal.loadForTest(server, mine);
        StashLink.onPlayerJoin(p);
        List<ItemStack> items = new ArrayList<>();
        mine.forEach(r -> items.addAll(r.items()));
        return items;
    }

    // ------------------------------------------------------------------------------------------------ cenários

    /** Morrer com a bancada aberta: o emprestado da grade e do cursor volta ao baú, nada dele cai no chão. */
    public static void deathWithBorrowedItemsReturnsThemToTheChest(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        ItemStack plain = new ItemStack(Items.ECHO_SHARD);
        ItemStack fancy = named(Items.ECHO_SHARD, "Caco do caderno", 1);
        chest.setItem(0, plain.copyWithCount(10));
        chest.setItem(1, fancy.copyWithCount(6));
        ServerPlayer p = lab.player(4, 2, 4);
        Lab.prefs(p, 8, true);
        p.getInventory().setItem(5, plain.copyWithCount(3));        // do próprio jogador: cai no chão ao morrer
        table(lab, p, 1);
        toSlot(p, plain, false);                                     // grade: 10 do baú... (3 da mochila primeiro)
        h.runAfterDelay(3, () -> {
            toCursor(p, fancy, false);                               // cursor: os 6 renomeados
            check(h, inContainer(chest, fancy) == 0 && onPlayer(p, fancy) == 6, "os renomeados foram ao cursor");
            check(h, inStation(p, plain) == 13 && inContainer(chest, plain) == 0, "a grade tem 3 próprios + 10 do baú");

            dieAndRevive(p, lab.level);
            check(h, p.containerMenu == p.inventoryMenu, "morrer devia fechar a bancada");
            check(h, inContainer(chest, plain) == 10, "as 10 emprestadas deviam voltar ao baú: " + inContainer(chest, plain));
            check(h, inContainer(chest, fancy) == 6, "os 6 renomeados do cursor deviam voltar: " + inContainer(chest, fancy));
            check(h, onGround(h, fancy) == 0 && onPlayer(p, fancy) == 0, "nada emprestado no chão nem no jogador");
            check(h, onGround(h, plain) + onPlayer(p, plain) == 3, "os 3 do jogador seguem dele (chão ou mochila)");
            clearGround(h);
            lab.cleanup();
            h.succeed();
        });
    }

    /**
     * Trocar de dimensão com a bancada aberta: o emprestado volta ao baú antes de sair do mundo. Quem fecha a tela é o
     * próprio jogo ({@code Player.remove(CHANGED_DIMENSION)}); o teste guarda esse comportamento numa versão nova.
     */
    public static void changingDimensionReturnsBorrowedItems(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        Lab.fill(chest, 0, Items.ACACIA_PLANKS, 12);
        ItemStack book = book(lab.level, Enchantments.MENDING, 1);
        chest.setItem(1, book.copy());
        ServerPlayer p = lab.player(4, 2, 4);
        Lab.prefs(p, 8, true);
        table(lab, p, 1);
        recipeBook(p, "stick", false);                              // 2 tábuas na grade
        h.runAfterDelay(3, () -> {
            toCursor(p, book, false);                                // o livro no cursor
            check(h, inContainer(chest, book) == 0 && Lab.count(chest, Items.ACACIA_PLANKS) == 10,
                    "saíram 2 tábuas e o livro: baú=" + Lab.count(chest, Items.ACACIA_PLANKS));
            check(h, travel(p, lab.level), "o teleporte devia ir ao Nether e voltar");
            check(h, p.containerMenu == p.inventoryMenu, "trocar de dimensão devia fechar a bancada");
            check(h, Lab.count(chest, Items.ACACIA_PLANKS) == 12 && inContainer(chest, book) == 1,
                    "tudo devia estar de volta no baú: tábuas=" + Lab.count(chest, Items.ACACIA_PLANKS)
                            + " livro=" + inContainer(chest, book));
            check(h, onPlayer(p, book) == 0 && Lab.carried(p, Items.ACACIA_PLANKS) == 0, "nada ficou com o jogador");
            lab.cleanup();
            h.succeed();
        });
    }

    /**
     * Sair do jogo com a grade cheia: o jogador já está desconectado quando a saída é tratada, e o jogo joga no chão o
     * que sobra na grade (não põe na mochila). O emprestado volta ao baú antes disso, e as tábuas do próprio jogador, do
     * mesmo tipo, ficam na mochila dele (antes iam ao baú no lugar das que caíram).
     */
    public static void logoutReturnsBorrowedAndKeepsOwnItems(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        Lab.fill(chest, 0, Items.JUNGLE_PLANKS, 6);
        ServerPlayer p = lab.player(4, 2, 4);
        Lab.prefs(p, 8, true);
        table(lab, p, 1);
        recipeBook(p, "stick", false);                              // 2 tábuas do baú na grade
        check(h, Lab.count(chest, Items.JUNGLE_PLANKS) == 4, "saíram 2 tábuas do baú");
        p.getInventory().setItem(9, new ItemStack(Items.JUNGLE_PLANKS, 5));   // tábuas do jogador, iguais
        p.disconnect();                                             // a saída chega com o jogador já desconectado
        io.github.leoascenci0.stashlink.bench.BenchSync.release(p);
        check(h, Lab.count(chest, Items.JUNGLE_PLANKS) == 6, "as 2 emprestadas voltaram: baú=" + Lab.count(chest, Items.JUNGLE_PLANKS));
        check(h, Lab.carried(p, Items.JUNGLE_PLANKS) == 5, "as 5 do jogador ficam com ele: " + Lab.carried(p, Items.JUNGLE_PLANKS));
        check(h, onGround(h, new ItemStack(Items.JUNGLE_PLANKS)) == 0, "nada no chão");
        clearGround(h);
        lab.cleanup();
        h.succeed();
    }

    /**
     * Livro encantado emprestado e não usado volta ao baú ao fechar (antes ia para a mochila); o livro do próprio
     * jogador, do mesmo tipo mas outro encantamento, fica com ele.
     */
    public static void unusedEnchantedBookGoesBackToTheChest(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        ItemStack borrowed = book(lab.level, Enchantments.SHARPNESS, 3);
        ItemStack mine = book(lab.level, Enchantments.SMITE, 2);
        chest.setItem(0, borrowed.copy());
        ServerPlayer p = lab.player(4, 2, 4);
        Lab.prefs(p, 8, true);
        p.getInventory().setItem(7, mine.copy());
        anvil(lab, p, 1);
        toSlot(p, borrowed, true);
        check(h, inStation(p, borrowed) == 1 && inContainer(chest, borrowed) == 0, "o livro do baú foi à bigorna");
        close(p);
        check(h, inContainer(chest, borrowed) == 1, "o livro emprestado devia voltar ao baú, não à mochila");
        check(h, onPlayer(p, borrowed) == 0 && onPlayer(p, mine) == 1 && inContainer(chest, mine) == 0,
                "o livro do jogador fica com ele");
        h.runAfterDelay(2, () -> {                                   // o acerto do tick seguinte não leva nada a mais
            check(h, inContainer(chest, borrowed) == 1 && onPlayer(p, mine) == 1, "nada mudou depois do tick");
            lab.cleanup();
            h.succeed();
        });
    }

    /**
     * Queda do servidor com a bancada aberta: o diário gravado no mundo devolve ao baú exatamente o que estava
     * emprestado (grade e cursor), uma vez só. Item do jogador movido para a mochila não entra no diário.
     */
    public static void crashJournalGivesBackExactlyWhatWasLent(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        Lab.fill(chest, 0, Items.CHERRY_PLANKS, 20);
        ItemStack fancy = named(Items.PRISMARINE_SHARD, "Diário 31", 1);
        chest.setItem(1, fancy.copyWithCount(5));
        ServerPlayer p = lab.player(4, 2, 4);
        Lab.prefs(p, 8, true);
        table(lab, p, 1);
        recipeBook(p, "stick", true);                               // tábuas na grade (todas do baú)
        h.runAfterDelay(3, () -> {
            toCursor(p, fancy, false);
            int inGrid = inStation(p, new ItemStack(Items.CHERRY_PLANKS));
            check(h, inGrid > 0 && Lab.count(chest, Items.CHERRY_PLANKS) == 20 - inGrid, "tábuas do baú na grade");
            // o jogador tira 1 tábua da grade para a mochila (shift-clique): essa já é dele, o save a guarda
            Slot taken = null;
            for (Slot s : p.containerMenu.slots) {
                if (stationInput(p, p.containerMenu, s) && s.getItem().is(Items.CHERRY_PLANKS)) {
                    taken = s;
                    break;
                }
            }
            ItemStack one = taken.getItem().split(1);
            taken.setChanged();
            p.getInventory().add(one);
            int stillInGrid = inStation(p, new ItemStack(Items.CHERRY_PLANKS));

            List<ItemStack> journal = crashAndRejoin(p);
            int plankJournal = countIn(journal, new ItemStack(Items.CHERRY_PLANKS));
            check(h, plankJournal == stillInGrid, "o diário devia ter só o que estava na grade: " + plankJournal
                    + " != " + stillInGrid);
            check(h, countIn(journal, fancy) == 5, "o diário devia ter os 5 renomeados do cursor");
            check(h, Lab.count(chest, Items.CHERRY_PLANKS) == 19 && inContainer(chest, fancy) == 5,
                    "o baú devia voltar ao que era, menos a tábua que o jogador pegou: tábuas="
                            + Lab.count(chest, Items.CHERRY_PLANKS));
            check(h, Lab.carried(p, Items.CHERRY_PLANKS) == 1, "a tábua que o jogador pegou segue com ele");
            StashLink.onPlayerJoin(p);                               // entrar de novo não devolve outra vez
            check(h, Lab.count(chest, Items.CHERRY_PLANKS) == 19 && inContainer(chest, fancy) == 5, "devolveu duas vezes");
            boolean listed = false;
            for (BenchJournal.Record r : BenchJournal.saveForTest(p.level().getServer())) {
                listed |= r.player().equals(p.getUUID());
            }
            check(h, !listed, "o diário devia esquecer o jogador depois de devolver");
            lab.cleanup();
            h.succeed();
        });
    }

    /** Fechar a bancada do jeito normal deixa o diário vazio para o jogador (nada a devolver depois de uma queda). */
    public static void closingNormallyLeavesTheJournalEmpty(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        Lab.fill(chest, 0, Items.BAMBOO_PLANKS, 8);
        ServerPlayer p = lab.player(4, 2, 4);
        Lab.prefs(p, 8, true);
        table(lab, p, 1);
        recipeBook(p, "stick", false);
        boolean before = false;
        for (BenchJournal.Record r : BenchJournal.saveForTest(p.level().getServer())) {
            before |= r.player().equals(p.getUUID());
        }
        check(h, before, "com a bancada aberta o diário devia anotar o emprestado");
        close(p);
        boolean after = false;
        for (BenchJournal.Record r : BenchJournal.saveForTest(p.level().getServer())) {
            after |= r.player().equals(p.getUUID());
        }
        check(h, !after, "fechada a bancada, o diário não devia ter nada do jogador");
        check(h, Lab.count(chest, Items.BAMBOO_PLANKS) == 8, "as tábuas voltaram ao baú");
        lab.cleanup();
        h.succeed();
    }

    /** Anti-flood: pedidos ao cursor/slot no tick seguinte são ignorados; 2 ticks depois, aceitos. */
    public static void cursorAndPlaceRequestsAreSpaced(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        Lab.fill(chest, 0, Items.SPRUCE_PLANKS, 30);
        ServerPlayer p = lab.player(4, 2, 4);
        Lab.prefs(p, 8, true);
        table(lab, p, 1);
        ItemStack model = new ItemStack(Items.SPRUCE_PLANKS);
        toCursor(p, model, true);
        check(h, onPlayer(p, model) == 1, "o primeiro pedido vale");
        h.runAfterDelay(1, () -> {
            toCursor(p, model, true);
            toSlot(p, model, true);
            check(h, onPlayer(p, model) == 1 && inStation(p, model) == 0, "no tick seguinte, ignorado");
            h.runAfterDelay(1, () -> {
                toCursor(p, model, true);
                check(h, onPlayer(p, model) == 2, "dois ticks depois, aceito: " + onPlayer(p, model));
                close(p);
                check(h, Lab.count(chest, Items.SPRUCE_PLANKS) == 30, "fechou: voltou tudo");
                lab.cleanup();
                h.succeed();
            });
        });
    }

    /** "Pôr no slot" passa pelo mesmo filtro do cursor: o livro que não serve na espada da bigorna não vai. */
    public static void placeUsesTheSameFilterAsTheCursor(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        ItemStack armorBook = book(lab.level, Enchantments.PROTECTION, 1);
        ItemStack swordBook = book(lab.level, Enchantments.LOOTING, 1);
        chest.setItem(0, armorBook.copy());
        chest.setItem(1, swordBook.copy());
        ServerPlayer p = lab.player(4, 2, 4);
        Lab.prefs(p, 8, true);
        AnvilMenu menu = anvil(lab, p, 1);
        menu.getSlot(0).set(new ItemStack(Items.DIAMOND_SWORD));
        toSlot(p, armorBook, true);
        check(h, menu.getSlot(1).getItem().isEmpty() && inContainer(chest, armorBook) == 1,
                "proteção não serve na espada: o painel não mostra, e o pedido também não passa");
        h.runAfterDelay(3, () -> {
            toSlot(p, swordBook, true);
            check(h, ItemStack.isSameItemSameComponents(menu.getSlot(1).getItem(), swordBook),
                    "pilhagem serve: vai ao slot");
            menu.getSlot(0).set(ItemStack.EMPTY);                    // a espada era só do teste
            close(p);
            check(h, inContainer(chest, swordBook) == 1, "fechou sem usar: o livro voltou ao baú");
            lab.cleanup();
            h.succeed();
        });
    }

    /**
     * Fuzz com 2 jogadores, bancada e bigorna, itens comuns, renomeados e encantados (inclusive um livro do próprio
     * jogador igual ao do baú), com morte, ida ao Nether, queda do servidor e entrada de novo no meio. A soma de cada
     * item (baús + jogadores + estações + chão) é conferida depois de CADA ação; na queda, o diário devolve no máximo o
     * que estava na estação e no cursor (o que o jogo perderia de qualquer jeito, sem o diário).
     */
    public static void benchFuzzWithDeathTravelAndCrash(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(1, 2, 1);
        Container barrel = lab.block(Blocks.BARREL, 5, 2, 1);
        List<Container> boxes = List.of(chest, barrel);
        ServerPlayer a = lab.player(3, 2, 4);
        ServerPlayer b = lab.player(6, 2, 5);
        List<ServerPlayer> players = List.of(a, b);
        Lab.prefs(a, 12, true);
        Lab.prefs(b, 12, true);
        ItemStack plain = new ItemStack(Items.ACACIA_PLANKS);
        ItemStack fancy = named(Items.ACACIA_PLANKS, "Tábua do caderno", 1);
        ItemStack bookA = book(lab.level, Enchantments.LUCK_OF_THE_SEA, 3);
        ItemStack bookB = book(lab.level, Enchantments.LURE, 2);
        ItemStack stick = new ItemStack(Items.STICK);
        List<ItemStack> models = List.of(plain, fancy, bookA, bookB);
        chest.setItem(0, plain.copyWithCount(64));
        chest.setItem(1, fancy.copyWithCount(20));
        chest.setItem(2, bookA.copy());
        chest.setItem(3, bookB.copy());
        barrel.setItem(0, plain.copyWithCount(40));
        barrel.setItem(1, bookA.copy());
        a.getInventory().setItem(9, plain.copyWithCount(10));
        a.getInventory().setItem(10, bookB.copy());                  // igual ao do baú, mas é do jogador
        b.getInventory().setItem(9, fancy.copyWithCount(5));

        int[] expected = new int[models.size()];
        for (int i = 0; i < models.size(); i++) {
            expected[i] = sumFor(h, boxes, players, models, i, stick);
        }
        Random rnd = new Random(31);
        String[] lastWhere = new String[4];
        String[] firstFail = new String[1];   // a 1ª falha (o GameTest mostraria a última)
        List<String> trail = new ArrayList<>();
        int[] stats = new int[5];   // retiradas, mortes com estação aberta, viagens com estação aberta, quedas que devolveram algo, livros na bigorna

        for (int tick = 1; tick <= 400; tick++) {
            final int t = tick;
            h.runAfterDelay(tick, () -> {
                for (int n = 0; n < 2 && firstFail[0] == null; n++) {
                    ServerPlayer p = rnd.nextBoolean() ? a : b;
                    int boxesBefore = 0;
                    for (ItemStack m : models) {
                        boxesBefore += inContainer(chest, m) + inContainer(barrel, m);
                    }
                    for (int i = 0; i < models.size(); i++) {
                        int now = sumFor(h, boxes, players, models, i, stick);
                        if (now != expected[i]) {
                            ItemStack m = models.get(i);
                            fail(h, firstFail, "tick " + t + " entre ações (no tick do jogo): "
                                    + m.getHoverName().getString() + " " + now + " != " + expected[i]
                                    + " (" + lastWhere[i] + " -> baús=" + (inContainer(chest, m) + inContainer(barrel, m))
                                    + " a=" + onPlayer(a, m) + "+" + inStation(a, m) + " b=" + onPlayer(b, m) + "+"
                                    + inStation(b, m) + " chão=" + onGround(h, m) + " bigornasCaindo="
                                    + h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class, area(h)).size() + ")"
                                    + " | " + trail.subList(Math.max(0, trail.size() - 10), trail.size()));
                        }
                    }
                    ItemStack model = models.get(rnd.nextInt(models.size()));
                    int action = rnd.nextInt(14);
                    String name;
                    switch (action) {
                        case 0, 1 -> {
                            name = "livro de receitas";
                            if (!(p.containerMenu instanceof CraftingMenu)) {
                                close(p);
                                table(lab, p, 1 + rnd.nextInt(100));
                            }
                            recipeBook(p, "stick", rnd.nextBoolean());
                        }
                        case 2, 3 -> {
                            name = "cursor " + model.getHoverName().getString();
                            if (p.containerMenu == p.inventoryMenu) {
                                if (rnd.nextBoolean()) {
                                    table(lab, p, 1 + rnd.nextInt(100));
                                } else {
                                    anvil(lab, p, 1 + rnd.nextInt(100));
                                }
                            }
                            toCursor(p, model, rnd.nextBoolean());
                        }
                        case 4 -> {
                            name = "slot da bigorna " + model.getHoverName().getString();
                            if (!(p.containerMenu instanceof AnvilMenu)) {
                                close(p);
                                anvil(lab, p, 1 + rnd.nextInt(100));
                            }
                            toSlot(p, model, rnd.nextBoolean());
                            if (model.is(Items.ENCHANTED_BOOK) && inStation(p, model) > 0) {
                                stats[4]++;
                            }
                        }
                        case 5 -> {
                            name = "fecha";
                            close(p);
                        }
                        case 6 -> {
                            name = "pega resultado";
                            // só graveto: tábua sozinha vira botão, duas lado a lado placa de pressão (a conta não os mede)
                            if (p.containerMenu instanceof CraftingMenu m && m.getCarried().isEmpty()
                                    && m.getSlot(0).getItem().is(Items.STICK)) {
                                m.clicked(0, 0, rnd.nextBoolean() ? ContainerInput.PICKUP : ContainerInput.QUICK_MOVE, p);
                            }
                        }
                        case 7 -> {
                            name = "morre";
                            if (stationHasLoan(p, models)) {
                                stats[1]++;
                            }
                            dieAndRevive(p, lab.level);
                        }
                        case 8 -> {
                            name = "vai ao Nether e volta";
                            if (stationHasLoan(p, models)) {
                                stats[2]++;
                            }
                            travel(p, lab.level);
                        }
                        case 9 -> {
                            name = "guarda tudo";
                            close(p);
                            for (int s = 0; s < p.getInventory().getContainerSize(); s++) {
                                ItemStack in = p.getInventory().getItem(s);
                                boolean ours = false;
                                for (ItemStack m : models) {
                                    ours |= ItemStack.isSameItemSameComponents(in, m);
                                }
                                if (ours) {
                                    p.getInventory().setItem(s, ContainerInsert.insert(boxes.get(rnd.nextInt(2)), in));
                                }
                            }
                        }
                        case 10 -> {
                            name = "queda do servidor";
                            // gravetos no cursor também se perderiam, e a conta das tábuas os usa: vão à mochila antes
                            ItemStack held = p.containerMenu.getCarried();
                            if (held.is(Items.STICK)) {
                                p.containerMenu.setCarried(ItemStack.EMPTY);
                                io.github.leoascenci0.stashlink.compat.mc.McCompat.placeBackInInventory(p, held);
                            }
                            int[] lost = new int[models.size()];
                            for (int i = 0; i < models.size(); i++) {
                                lost[i] = inStation(p, models.get(i)) + countIn(List.of(p.containerMenu.getCarried()), models.get(i));
                            }
                            List<ItemStack> journal = crashAndRejoin(p);
                            boolean gave = false;
                            for (int i = 0; i < models.size(); i++) {
                                int back = countIn(journal, models.get(i));
                                if (back > lost[i]) {
                                    fail(h, firstFail, "tick " + t + ": o diário devolveu " + back + " de "
                                            + models.get(i).getHoverName().getString() + ", mas só havia " + lost[i]);
                                }
                                // o resto (do próprio jogador) o jogo perde sem o mod também; tábuas renomeadas contam com as comuns
                                expected[i == 1 ? 0 : i] += back - lost[i];
                                gave |= back > 0;
                            }
                            if (gave) {
                                stats[3]++;
                            }
                        }
                        case 11 -> {
                            name = "shift-clique na estação";
                            AbstractContainerMenu m = p.containerMenu;
                            if (m != p.inventoryMenu && m.getCarried().isEmpty()) {
                                List<Slot> inputs = new ArrayList<>();
                                for (Slot s : m.slots) {
                                    if (stationInput(p, m, s) && s.hasItem()) {
                                        inputs.add(s);
                                    }
                                }
                                if (!inputs.isEmpty()) {
                                    m.clicked(inputs.get(rnd.nextInt(inputs.size())).index, 0, ContainerInput.QUICK_MOVE, p);
                                }
                            }
                        }
                        case 12 -> {
                            name = "solta o cursor na estação";
                            AbstractContainerMenu m = p.containerMenu;
                            if (m != p.inventoryMenu && !m.getCarried().isEmpty()) {
                                List<Slot> inputs = new ArrayList<>();
                                for (Slot s : m.slots) {
                                    if (stationInput(p, m, s)) {
                                        inputs.add(s);
                                    }
                                }
                                m.clicked(inputs.get(rnd.nextInt(inputs.size())).index, rnd.nextInt(2),
                                        ContainerInput.PICKUP, p);
                            }
                        }
                        default -> {
                            name = "N";
                            close(p);
                            QuickStackService.handle(p);
                        }
                    }
                    int boxesAfter = 0;
                    for (ItemStack m : models) {
                        boxesAfter += inContainer(chest, m) + inContainer(barrel, m);
                    }
                    if (name.startsWith("livro") || name.startsWith("cursor") || name.startsWith("slot")) {
                        if (boxesAfter < boxesBefore) {
                            stats[0]++;
                        }
                    }
                    for (int i = 0; i < models.size(); i++) {
                        ItemStack m = models.get(i);
                        lastWhere[i] = "soma=" + sumFor(h, boxes, players, models, i, stick) + " esperado=" + expected[i]
                                + " baús=" + (inContainer(chest, m) + inContainer(barrel, m)) + " a=" + onPlayer(a, m)
                                + "+" + inStation(a, m) + " b=" + onPlayer(b, m) + "+" + inStation(b, m) + " chão=" + onGround(h, m);
                    }
                    trail.add((p == a ? "a:" : "b:") + name + "@" + p.containerMenu.getClass().getSimpleName());
                    for (int i = 0; i < models.size(); i++) {
                        int now = sumFor(h, boxes, players, models, i, stick);
                        if (now != expected[i]) {
                            ItemStack m = models.get(i);
                            String where = "baús=" + (inContainer(chest, m) + inContainer(barrel, m))
                                    + " a=" + onPlayer(a, m) + "+" + inStation(a, m) + " b=" + onPlayer(b, m) + "+"
                                    + inStation(b, m) + " chão=" + onGround(h, m);
                            fail(h, firstFail, "tick " + t + " após '" + name + "': "
                                    + m.getHoverName().getString() + " " + now + " != " + expected[i] + " (" + where
                                    + ") | " + trail.subList(Math.max(0, trail.size() - 10), trail.size()));
                        }
                    }
                }
            });
        }
        h.runAfterDelay(410, () -> {
            check(h, firstFail[0] == null, String.valueOf(firstFail[0]));
            Constants.LOG.info("[STASHLINK-BENCHFUZZ31] retiradas={} mortes={} viagens={} quedas={} livrosNaBigorna={}",
                    stats[0], stats[1], stats[2], stats[3], stats[4]);
            check(h, stats[0] >= 40, "o fuzz quase não emprestou nada: " + stats[0]);
            check(h, stats[1] >= 3, "poucas mortes com algo emprestado: " + stats[1]);
            check(h, stats[2] >= 3, "poucas viagens com algo emprestado: " + stats[2]);
            check(h, stats[3] >= 3, "poucas quedas com algo a devolver: " + stats[3]);
            check(h, stats[4] >= 3, "poucos livros encantados na bigorna: " + stats[4]);
            for (ServerPlayer p : players) {
                close(p);
            }
            clearGround(h);
            lab.cleanup();
            h.succeed();
        });
    }

    private static void fail(GameTestHelper h, String[] firstFail, String msg) {
        if (firstFail[0] == null) {
            firstFail[0] = msg;
            Constants.LOG.error("[STASHLINK-BENCHFUZZ31] {}", msg);
        }
    }

    /** A estação aberta tem algo do armazenamento (pelo menos um item dos do teste na estação ou no cursor)? */
    private static boolean stationHasLoan(ServerPlayer p, List<ItemStack> models) {
        for (ItemStack m : models) {
            if (BenchLedger.owes(p, m)) {
                return true;
            }
        }
        return false;
    }

    /**
     * A soma conferida no fuzz para o modelo {@code i}. Tábuas: comuns e renomeadas numa conta só (o livro de receitas e a
     * grade aceitam qualquer tábua, então a renomeada também vira graveto) + 2 a cada 4 gravetos feitos; a renomeada
     * sozinha fica de fora (índice 1 vale 0). Livros encantados: cada um na sua conta, exata.
     */
    private static int sumFor(GameTestHelper h, List<Container> boxes, List<ServerPlayer> players, List<ItemStack> models,
                              int i, ItemStack stick) {
        if (i == 0) {
            return sum(h, boxes, players, models.get(0), stick) + total(h, boxes, players, models.get(1));
        }
        if (i == 1) {
            return 0;
        }
        return total(h, boxes, players, models.get(i));
    }

    /** Soma do item; para as tábuas comuns, cada 4 gravetos feitos valem as 2 tábuas gastas. */
    private static int sum(GameTestHelper h, List<Container> boxes, List<ServerPlayer> players, ItemStack model,
                           ItemStack madeFrom) {
        int n = total(h, boxes, players, model);
        if (madeFrom != null) {
            n += 2 * (total(h, boxes, players, madeFrom) / 4);
        }
        return n;
    }
}
