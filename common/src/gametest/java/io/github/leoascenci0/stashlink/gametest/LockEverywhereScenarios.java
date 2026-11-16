package io.github.leoascenci0.stashlink.gametest;

import io.github.leoascenci0.stashlink.bench.BenchLedger;
import io.github.leoascenci0.stashlink.bench.BenchPullService;
import io.github.leoascenci0.stashlink.bench.BenchResults;
import io.github.leoascenci0.stashlink.compat.mc.McCompat;
import io.github.leoascenci0.stashlink.config.Feature;
import io.github.leoascenci0.stashlink.config.PlayerPrefs;
import io.github.leoascenci0.stashlink.config.PlayerPrefsStore;
import io.github.leoascenci0.stashlink.config.StashLinkConfig;
import io.github.leoascenci0.stashlink.network.BenchPullRequest;
import io.github.leoascenci0.stashlink.network.OrganizeRequest;
import io.github.leoascenci0.stashlink.network.PullItemRequest;
import io.github.leoascenci0.stashlink.organize.OrganizeLogic;
import io.github.leoascenci0.stashlink.organize.OrganizeService;
import io.github.leoascenci0.stashlink.pull.PullItemService;
import io.github.leoascenci0.stashlink.quickstack.QuickStackReceive;
import io.github.leoascenci0.stashlink.refill.RefillService;
import io.github.leoascenci0.stashlink.slotlock.SlotLocks;
import io.github.leoascenci0.stashlink.source.Origin;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ServerboundPickItemFromBlockPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.BeaconMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Item 32: slots travados do jogador e reservas de baú valendo em TODAS as funções. Cenários comuns aos dois loaders
 * (o Fabric os registra em {@code LockEverywhereGameTests}, o NeoForge em {@code NeoForgeTestMod}).
 *
 * <p>Regra de todos os cenários de "slot travado": o slot travado nunca recebe item do mod (nem vazio, nem completando
 * pilha) e nunca perde item para o mod; e a soma de cada item (baús + jogador + cursor) nunca muda. O item que não tem
 * para onde ir por causa da trava fica onde estava (ou segue o plano B que o caminho já tinha), nunca vai ao chão.
 */
public final class LockEverywhereScenarios {
    /** O pedido do mesmo jogador só é aceito a cada 4 ticks; os cenários com vários passos andam de 5 em 5. */
    private static final int STEP = 5;
    private static final int INVENTORY = 36;

    private LockEverywhereScenarios() {
    }

    private static void check(GameTestHelper h, boolean ok, String msg) {
        h.assertTrue(ok, msg);
    }

    // ------------------------------------------------------------------------------------------------ ferramentas

    /**
     * Jogador de sobrevivência de verdade: o simulado do {@link Lab} herda "materiais infinitos" do criativo do GameTest,
     * e aí o jogo base descartaria o que não cabe em vez de recusar. Também desliga a "auto-compactação" do Upgraded Iron
     * Chests (mod de teste do Fabric), que mexeria nos baús depois de cada clique.
     */
    private static ServerPlayer survivor(Lab lab, double x, double y, double z) {
        ServerPlayer p = lab.player(x, y, z);
        p.getAbilities().instabuild = false;
        try {
            Class.forName("in2bubble.upgradedironchests.In2bubble")
                    .getMethod("setPlayerAutoCompaction", ServerPlayer.class, boolean.class).invoke(null, p, false);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // mod ausente (NeoForge) ou outra versão: sem compactação para desligar
        }
        return p;
    }

    /** Define as preferências do jogador: raio, baús ligados e os slots (0-35) que ele travou. */
    private static void lockSlots(ServerPlayer p, int radius, int... slots) {
        List<Integer> list = new ArrayList<>();
        for (int s : slots) {
            list.add(s);
        }
        PlayerPrefsStore.set(p.getUUID(), new PlayerPrefs(radius, 1, list, 0, radius));
    }

    private static int[] range(int from, int toInclusive) {
        int[] out = new int[toInclusive - from + 1];
        for (int i = 0; i < out.length; i++) {
            out[i] = from + i;
        }
        return out;
    }

    private static ItemStack stack(ServerPlayer p, int slot) {
        return p.getInventory().getItem(slot);
    }

    private static boolean has(ServerPlayer p, int slot, Item item, int count) {
        ItemStack s = stack(p, slot);
        return s.is(item) && s.getCount() == count;
    }

    private static boolean empty(ServerPlayer p, int slot) {
        return stack(p, slot).isEmpty();
    }

    /** Enche os slots do inventário (exceto os de {@code except}) com picaretas: não empilham, ocupam o slot por inteiro. */
    private static void junk(ServerPlayer p, int from, int toInclusive, Set<Integer> except) {
        for (int i = from; i <= toInclusive; i++) {
            if (!except.contains(i)) {
                p.getInventory().setItem(i, new ItemStack(Items.DIAMOND_PICKAXE));
            }
        }
    }

    private static int total(Item item, List<Container> boxes, ServerPlayer p) {
        int t = 0;
        for (Container c : boxes) {
            t += Lab.count(c, item);
        }
        return t + Lab.carried(p, item);
    }

    /** Roda os passos um a cada {@link #STEP} ticks e dá o teste por feito no fim. */
    private static void runSteps(GameTestHelper h, Lab lab, List<Runnable> steps) {
        for (int i = 1; i < steps.size(); i++) {
            h.runAfterDelay((long) STEP * i, steps.get(i));
        }
        h.runAfterDelay((long) STEP * steps.size(), () -> {
            StashLinkConfig.lockedFeatures = EnumSet.noneOf(Feature.class);
            lab.cleanup();
            h.succeed();
        });
        steps.get(0).run();
    }

    private static void middleClick(ServerPlayer p, BlockPos pos) {
        p.connection.handlePickItemFromBlock(new ServerboundPickItemFromBlockPacket(pos, false));
    }

    private static void pull(ServerPlayer p, Item item) {
        PullItemService.handle(p, new PullItemRequest(item, 64));
    }

    // ------------------------------------------------------------------------- 1. botão do meio (pick block)

    /** Hotbar quase toda travada (inclusive o slot selecionado): o item vai para o único slot livre e nada mais é tocado. */
    public static void middleClickNeverUsesALockedSlot(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        Lab.fill(chest, 0, Items.CHERRY_PLANKS, 64);
        Lab.fill(chest, 1, Items.CHERRY_PLANKS, 20);
        BlockPos target = lab.bare(Blocks.CHERRY_PLANKS, 4, 2, 6);
        ServerPlayer p = survivor(lab, 4, 2, 4);
        lockSlots(p, 8, range(0, 7));
        p.getInventory().setSelectedSlot(0);

        middleClick(p, target);

        for (int i = 0; i < 8; i++) {
            check(h, empty(p, i), "slot travado " + i + " devia continuar vazio: " + stack(p, i));
        }
        check(h, has(p, 8, Items.CHERRY_PLANKS, 64), "o item devia ir para o único slot livre (8): " + stack(p, 8));
        check(h, p.getInventory().getSelectedSlot() == 8, "a seleção devia ir para o 8");
        check(h, total(Items.CHERRY_PLANKS, List.of(chest), p) == 84, "a soma de tábuas mudou");
        lab.cleanup();
        h.succeed();
    }

    /** Toda a hotbar travada: o botão do meio não traz nada, o baú fica como estava e o jogador é avisado. */
    public static void middleClickWithEveryHotbarSlotLockedBringsNothing(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        Lab.fill(chest, 0, Items.CHERRY_PLANKS, 64);
        BlockPos target = lab.bare(Blocks.CHERRY_PLANKS, 4, 2, 6);
        ServerPlayer p = survivor(lab, 4, 2, 4);
        lockSlots(p, 8, range(0, 8));

        middleClick(p, target);

        for (int i = 0; i < 9; i++) {
            check(h, empty(p, i), "slot travado " + i + " recebeu: " + stack(p, i));
        }
        check(h, Lab.count(chest, Items.CHERRY_PLANKS) == 64, "o baú devia continuar com 64");
        check(h, "stashlink.pick_block.hotbar_full".equals(Lab.overlayKey(p)), "devia avisar que não há slot livre: " + Lab.overlayKey(p));
        lab.cleanup();
        h.succeed();
    }

    // ----------------------------------------------------------------------------- 2. Litematica (pedido de item)

    /**
     * Escolha do slot: o selecionado (travado e vazio) e um com o mesmo item (travado, com espaço) são pulados; a troca no
     * mesmo slot só acontece se ele não foi travado depois; sem slot livre, nada sai do baú.
     */
    public static void litematicaPullNeverUsesALockedSlot(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        Lab.fill(chest, 0, Items.COBBLESTONE, 64);
        Lab.fill(chest, 1, Items.DIRT, 64);
        Lab.fill(chest, 2, Items.STONE, 64);
        ServerPlayer p = survivor(lab, 4, 2, 4);
        Lab.give(p, 1, Items.COBBLESTONE, 10);               // do jogador, travado, mesmo item (poderia ser completado)
        junk(p, 3, 8, Set.of());
        lockSlots(p, 8, 0, 1);
        p.getInventory().setSelectedSlot(0);
        List<Container> boxes = List.of(chest);

        runSteps(h, lab, List.of(
                () -> {
                    pull(p, Items.COBBLESTONE);
                    check(h, empty(p, 0), "o slot selecionado travado devia ficar vazio: " + stack(p, 0));
                    check(h, has(p, 1, Items.COBBLESTONE, 10), "o slot travado com o mesmo item não pode ser completado: " + stack(p, 1));
                    check(h, has(p, 2, Items.COBBLESTONE, 64), "devia usar o primeiro slot livre (2): " + stack(p, 2));
                    check(h, total(Items.COBBLESTONE, boxes, p) == 74, "soma de pedregulho mudou");
                },
                () -> {
                    pull(p, Items.DIRT);                     // troca no mesmo slot (Item 18): o pedregulho volta ao baú
                    check(h, has(p, 2, Items.DIRT, 64), "a troca devia pôr terra no slot 2: " + stack(p, 2));
                    check(h, has(p, 1, Items.COBBLESTONE, 10), "slot travado intacto");
                    check(h, Lab.count(chest, Items.COBBLESTONE) == 64, "o pedregulho do mod devia voltar ao baú");
                },
                () -> {
                    lockSlots(p, 8, 0, 1, 2);               // o jogador travou o slot do mod depois
                    pull(p, Items.STONE);
                    check(h, has(p, 2, Items.DIRT, 64), "slot travado depois não pode ser trocado: " + stack(p, 2));
                    check(h, Lab.count(chest, Items.DIRT) == 0 && Lab.count(chest, Items.STONE) == 64,
                            "sem slot livre nada sai do baú nem volta a ele");
                    check(h, total(Items.DIRT, boxes, p) == 64 && total(Items.STONE, boxes, p) == 64, "soma mudou");
                }));
    }

    /**
     * A devolução do item antigo ao inventário (plano B quando o baú de origem não aceita) nunca usa slot travado: sem
     * outro lugar, o item antigo fica no slot do mod e o novo vai para outro slot. Nada vai ao chão.
     */
    public static void litematicaReturnNeverUsesALockedBackpackSlot(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container origin = lab.chest(2, 2, 2);
        Container other = lab.chest(6, 2, 2);
        Lab.fill(origin, 0, Items.COBBLESTONE, 64);
        Lab.fill(other, 0, Items.DIRT, 64);
        ServerPlayer p = survivor(lab, 4, 2, 4);
        junk(p, 9, 35, Set.of(9));
        lockSlots(p, 8, 9);                                  // o único vazio da mochila está travado
        p.getInventory().setSelectedSlot(0);
        List<Container> boxes = List.of(origin, other);

        runSteps(h, lab, List.of(
                () -> {
                    pull(p, Items.COBBLESTONE);
                    check(h, has(p, 0, Items.COBBLESTONE, 64), "devia ter puxado o pedregulho para o slot 0");
                    for (int i = 0; i < 27; i++) {            // a origem enche: não aceita a devolução
                        origin.setItem(i, new ItemStack(Items.GRAVEL, 64));
                    }
                },
                () -> {
                    pull(p, Items.DIRT);
                    check(h, empty(p, 9), "o slot travado da mochila recebeu a devolução: " + stack(p, 9));
                    check(h, has(p, 0, Items.COBBLESTONE, 64), "sem lugar para devolver, o item antigo fica onde estava: " + stack(p, 0));
                    check(h, has(p, 1, Items.DIRT, 64), "o item novo devia ir para outro slot da hotbar: " + stack(p, 1));
                    check(h, total(Items.COBBLESTONE, boxes, p) == 64 && total(Items.DIRT, boxes, p) == 64,
                            "a soma mudou (algo sumiu ou duplicou)");
                }));
    }

    /** O slot travado pelo CONFIG DO MUNDO também vale (o mesmo ponto único que as preferências do jogador). */
    public static void worldConfigLockedSlotAlsoCounts(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        Lab.fill(chest, 0, Items.COBBLESTONE, 64);
        ServerPlayer p = survivor(lab, 4, 2, 4);
        lockSlots(p, 8);
        p.getInventory().setSelectedSlot(0);
        java.util.Set<Integer> before = StashLinkConfig.lockedSlots;
        // Tudo no mesmo tick (os testes rodam lado a lado e a config é global).
        try {
            StashLinkConfig.lockedSlots = new java.util.TreeSet<>(Set.of(0));
            pull(p, Items.COBBLESTONE);
        } finally {
            StashLinkConfig.lockedSlots = before;
        }
        check(h, empty(p, 0) && has(p, 1, Items.COBBLESTONE, 64), "slot 0 travado pelo mundo: " + stack(p, 0) + " / " + stack(p, 1));
        lab.cleanup();
        h.succeed();
    }

    // ------------------------------------------------------------------------ 3. recipiente vazio do reabastecimento

    private static void refillBucketPrepare(ServerPlayer p, int hand) {
        p.getInventory().setItem(hand, new ItemStack(Items.WATER_BUCKET));
        RefillService.tickPlayer(p);
        p.getInventory().setItem(hand, new ItemStack(Items.BUCKET));       // usou o balde
        RefillService.tickPlayer(p);
    }

    /** O balde vazio não completa a pilha de baldes do slot travado nem usa o slot travado vazio: vai para o slot livre. */
    public static void refillEmptyContainerSkipsLockedSlots(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(4, 2, 4);
        Lab.fill(chest, 0, Items.WATER_BUCKET, 1);
        ServerPlayer p = survivor(lab, 3, 2, 3);
        int hand = p.getInventory().getSelectedSlot();
        junk(p, 0, 35, Set.of(hand, 20, 21, 22));
        p.getInventory().setItem(20, new ItemStack(Items.BUCKET, 3));      // travado, mesmo item, com espaço
        // 21 travado e vazio; 22 livre e vazio
        lockSlots(p, 8, 20, 21);

        refillBucketPrepare(p, hand);

        check(h, stack(p, hand).is(Items.WATER_BUCKET), "a mão devia ter o balde cheio do baú: " + stack(p, hand));
        check(h, has(p, 20, Items.BUCKET, 3), "a pilha travada não pode ser completada: " + stack(p, 20));
        check(h, empty(p, 21), "o slot travado vazio recebeu o balde: " + stack(p, 21));
        check(h, has(p, 22, Items.BUCKET, 1), "o balde vazio devia ir para o slot livre (22): " + stack(p, 22));
        check(h, Lab.count(chest, Items.WATER_BUCKET) == 0, "o balde cheio saiu do baú");
        lab.cleanup();
        h.succeed();
    }

    /** Sem slot livre além dos travados: o balde vazio fica na mão e o cheio volta ao baú (nada some, nada duplica). */
    public static void refillWithOnlyLockedRoomKeepsTheEmptyContainerInHand(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(4, 2, 4);
        Lab.fill(chest, 0, Items.WATER_BUCKET, 1);
        ServerPlayer p = survivor(lab, 3, 2, 3);
        int hand = p.getInventory().getSelectedSlot();
        junk(p, 0, 35, Set.of(hand, 20));
        p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.DIRT, 64));
        lockSlots(p, 8, 20);

        refillBucketPrepare(p, hand);

        check(h, empty(p, 20), "o slot travado vazio recebeu o balde: " + stack(p, 20));
        check(h, stack(p, hand).is(Items.BUCKET) && stack(p, hand).getCount() == 1, "o balde vazio devia ficar na mão: " + stack(p, hand));
        check(h, Lab.count(chest, Items.WATER_BUCKET) == 1 && Lab.carried(p, Items.WATER_BUCKET) == 0, "o balde cheio devia voltar ao baú");
        lab.cleanup();
        h.succeed();
    }

    // ------------------------------------------------------------------------ 4. devolução genérica (placeBack)

    /** Ponto único das devoluções de último recurso (caderno das bancadas, diário, sobra do reabastecimento). */
    public static void placeBackSkipsLockedSlots(GameTestHelper h) {
        Lab lab = new Lab(h);
        ServerPlayer p = survivor(lab, 4, 2, 4);
        Lab.give(p, 0, Items.DIRT, 30);
        Lab.give(p, 1, Items.DIRT, 60);
        lockSlots(p, 8, 0);                                  // slot 0: travado, com terra e espaço
        ItemStack in = new ItemStack(Items.DIRT, 10);

        McCompat.placeBackInInventory(p, in);

        check(h, has(p, 0, Items.DIRT, 30), "o slot travado foi completado: " + stack(p, 0));
        check(h, has(p, 1, Items.DIRT, 64), "devia completar o slot livre: " + stack(p, 1));
        check(h, has(p, 2, Items.DIRT, 6), "o resto devia ir ao próximo slot livre (2): " + stack(p, 2));
        check(h, in.isEmpty(), "o stack entregue devia ficar vazio, como no jogo base");
        check(h, Lab.carried(p, Items.DIRT) == 100, "a soma mudou");
        lab.cleanup();
        h.succeed();
    }

    /** Pagamento do próprio jogador no sinalizador volta à mochila (StackListSink): nunca para slot travado. */
    public static void beaconPaymentReturnSkipsLockedSlots(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        Lab.fill(chest, 0, Items.DIAMOND, 3);
        ServerPlayer p = survivor(lab, 4, 2, 4);
        lockSlots(p, 4, range(0, 9));
        BeaconMenu menu = new BeaconMenu(1, p.getInventory());
        p.containerMenu = menu;
        menu.getSlot(0).set(new ItemStack(Items.EMERALD));      // pagamento que o jogador pôs com a mão

        runSteps(h, lab, List.of(
                () -> {
                    BenchPullService.handle(p, new BenchPullRequest(1, new ItemStack(Items.DIAMOND), true, BenchResults.PAY));
                    for (int i = 0; i < 10; i++) {
                        check(h, empty(p, i), "slot travado " + i + " recebeu: " + stack(p, i));
                    }
                    check(h, has(p, 10, Items.EMERALD, 1), "a esmeralda devia ir ao primeiro slot livre (10): " + stack(p, 10));
                    check(h, menu.getSlot(0).getItem().is(Items.DIAMOND) && Lab.count(chest, Items.DIAMOND) == 2,
                            "o diamante do baú devia entrar no sinalizador");
                },
                () -> {
                    menu.getSlot(0).set(new ItemStack(Items.EMERALD));
                    p.getInventory().setItem(10, ItemStack.EMPTY);
                    lockSlots(p, 4, range(0, 35));            // agora a mochila toda está travada
                    BenchPullService.handle(p, new BenchPullRequest(1, new ItemStack(Items.DIAMOND), true, BenchResults.PAY));
                    check(h, menu.getSlot(0).getItem().is(Items.EMERALD), "sem lugar a esmeralda fica no slot (não troca): " + menu.getSlot(0).getItem());
                    for (int i = 0; i < INVENTORY; i++) {
                        check(h, empty(p, i), "slot travado " + i + " recebeu: " + stack(p, i));
                    }
                    check(h, Lab.count(chest, Items.DIAMOND) == 2, "o baú não pode perder o diamante");
                    p.containerMenu = p.inventoryMenu;
                }));
    }

    /**
     * O livro de receitas traz ingredientes do baú para a mochila antes de montar: com a mochila cheia e só um slot
     * livre TRAVADO, nada entra nele, os ingredientes voltam ao baú e a grade fica vazia.
     */
    public static void recipeBookNeverBringsIntoALockedSlot(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        Lab.fill(chest, 0, Items.OAK_PLANKS, 2);
        ServerPlayer p = survivor(lab, 4, 2, 4);
        Lab.prefs(p, 8, true);
        junk(p, 0, 35, Set.of(0));
        lockSlots(p, 8, 0);
        CraftingMenu menu = BenchSafetyScenarios.table(lab, p, 1);

        BenchSafetyScenarios.recipeBook(p, "stick", false);

        check(h, empty(p, 0), "o slot travado recebeu ingrediente: " + stack(p, 0));
        int inGrid = 0;
        for (int i = 1; i <= 9; i++) {
            inGrid += menu.slots.get(i).getItem().getCount();
        }
        check(h, inGrid == 0, "a grade devia ficar vazia (nada coube na mochila): " + inGrid);
        check(h, Lab.count(chest, Items.OAK_PLANKS) == 2, "as tábuas devem voltar ao baú: " + Lab.count(chest, Items.OAK_PLANKS));
        p.containerMenu = p.inventoryMenu;
        lab.cleanup();
        h.succeed();
    }

    /**
     * Fechar a estação devolve ao baú o que o mod emprestou e sobrou na mochila. O slot travado (que tem o mesmo item,
     * do jogador) nunca é a fonte: o que sai é do slot livre.
     */
    public static void ledgerSettleNeverTakesFromALockedSlot(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        BlockPos chestPos = h.absolutePos(new BlockPos(2, 2, 2));
        ServerPlayer p = survivor(lab, 4, 2, 4);
        Lab.give(p, 0, Items.OAK_PLANKS, 5);                // travado: do jogador
        Lab.give(p, 10, Items.OAK_PLANKS, 5);               // livre: o que sobrou do empréstimo
        lockSlots(p, 8, 0);
        BenchSafetyScenarios.table(lab, p, 1);
        BenchLedger.record(p, new ItemStack(Items.OAK_PLANKS), 3,
                new Origin(false, McCompat.dimensionOf(p), Set.of(chestPos)));
        p.containerMenu = p.inventoryMenu;                   // a estação fechou

        BenchLedger.tick(p);

        check(h, has(p, 0, Items.OAK_PLANKS, 5), "o slot travado perdeu item para o caderno: " + stack(p, 0));
        check(h, has(p, 10, Items.OAK_PLANKS, 2), "o empréstimo devia voltar do slot livre: " + stack(p, 10));
        check(h, Lab.count(chest, Items.OAK_PLANKS) == 3, "o baú devia receber 3: " + Lab.count(chest, Items.OAK_PLANKS));
        lab.cleanup();
        h.succeed();
    }

    // ---------------------------------------------------------------------------- parte 2: shift-clique e reserva

    private static int menuSlot(int playerSlot) {
        return playerSlot < 9 ? 54 + playerSlot : 27 + playerSlot - 9;
    }

    /**
     * Reserva (Item 13) para a TERRA com PEDREGULHO dentro (um funil pôs lá: a reserva só vale para menus). Shift-clicar
     * pedregulho do inventário não pode completar a pilha do slot reservado para outro item.
     */
    public static void shiftClickNeverCompletesAStackInAReservedSlotOfAnotherItem(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        SlotLocks.toggle(chest, 0, new ItemStack(Items.DIRT));
        chest.setItem(0, new ItemStack(Items.COBBLESTONE, 10));          // "o funil pôs"
        ServerPlayer p = survivor(lab, 3, 2, 3);
        Lab.give(p, 9, Items.COBBLESTONE, 20);
        ChestMenu menu = Lab.open(p, chest, 1);

        menu.clicked(menuSlot(9), 0, ContainerInput.QUICK_MOVE, p);

        check(h, has(chest, 0, Items.COBBLESTONE, 10), "a pilha do slot reservado para outro item foi completada: " + chest.getItem(0));
        check(h, Lab.count(chest, Items.COBBLESTONE) + Lab.carried(p, Items.COBBLESTONE) == 30, "a soma mudou");
        check(h, Lab.count(chest, Items.COBBLESTONE) == 30, "o resto devia ir para outro slot do baú (cabe): " + Lab.count(chest, Items.COBBLESTONE));
        Lab.close(p);
        lab.cleanup();
        h.succeed();
    }

    private static boolean has(Container c, int slot, Item item, int count) {
        ItemStack s = c.getItem(slot);
        return s.is(item) && s.getCount() == count;
    }

    /** Troca por tecla numérica e clique duplo (PICKUP_ALL) também respeitam a reserva; a soma nunca muda. */
    public static void numberKeyAndDoubleClickRespectTheReservation(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        SlotLocks.toggle(chest, 1, new ItemStack(Items.DIRT));           // reservado para terra, vazio
        SlotLocks.toggle(chest, 0, new ItemStack(Items.DIRT));
        chest.setItem(0, new ItemStack(Items.COBBLESTONE, 10));          // pedregulho dentro (funil)
        chest.setItem(5, new ItemStack(Items.COBBLESTONE, 7));
        ServerPlayer p = survivor(lab, 3, 2, 3);
        Lab.give(p, 0, Items.COBBLESTONE, 5);                            // hotbar 0
        ChestMenu menu = Lab.open(p, chest, 1);

        menu.clicked(1, 0, ContainerInput.SWAP, p);                      // tecla 1 sobre o slot reservado vazio
        check(h, chest.getItem(1).isEmpty(), "a tecla numérica pôs item no slot reservado: " + chest.getItem(1));
        check(h, has(p, 0, Items.COBBLESTONE, 5), "o item devia continuar na hotbar");

        menu.setCarried(new ItemStack(Items.COBBLESTONE, 1));
        menu.clicked(5, 0, ContainerInput.PICKUP_ALL, p);                // clique duplo: junta no cursor (só tira)
        check(h, chest.getItem(1).isEmpty(), "o clique duplo pôs item no slot reservado");
        check(h, Lab.count(chest, Items.COBBLESTONE) + Lab.carried(p, Items.COBBLESTONE) == 10 + 7 + 5 + 1, "a soma mudou no clique duplo");
        Lab.close(p);
        lab.cleanup();
        h.succeed();
    }

    // ------------------------------------------------------------------------------- parte 3: Organizar

    private record Rig(Lab lab, Container a, Container b, ServerPlayer p) {
    }

    /** Dois baús (A com 10 frutinhas e 10 calcitas, B com 50 de cada) e um jogador; o plano move tudo para o B. */
    private static Rig rig(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container a = lab.chest(2, 2, 3);
        Container b = lab.chest(6, 2, 3);
        a.setItem(0, new ItemStack(Items.SWEET_BERRIES, 10));
        a.setItem(1, new ItemStack(Items.CALCITE, 10));
        b.setItem(0, new ItemStack(Items.SWEET_BERRIES, 50));
        b.setItem(1, new ItemStack(Items.CALCITE, 50));
        ServerPlayer p = lab.player(4, 2, 4);
        PlayerPrefsStore.set(p.getUUID(), new PlayerPrefs(4, 1, List.of(), 0, 4));
        return new Rig(lab, a, b, p);
    }

    private static void organize(ServerPlayer p, int action) {
        OrganizeService.handle(p, OrganizeRequest.of(action));
    }

    private static boolean same(Container c, List<ItemStack> snapshot) {
        return OrganizeLogic.sameContents(c, snapshot);
    }

    private interface Change {
        void run(Rig rig);
    }

    /** Reserva criada no baú B (que recebe os itens do plano) depois da prévia. */
    private static final Change RESERVE = rig -> SlotLocks.toggle(rig.b(), 20, new ItemStack(Items.MUD_BRICKS));
    /** Botão "recebe com a N" desligado no baú B depois da prévia. */
    private static final Change BUTTON_N = rig -> QuickStackReceive.set(rig.b(), false);
    /** Categoria "comida" desligada pelo jogador depois da prévia (as frutinhas são comida). */
    private static final Change CATEGORY = rig -> PlayerPrefsStore.set(rig.p().getUUID(),
            new PlayerPrefs(4, 1, List.of(), Feature.CAT_FOOD.bit(), 4));

    /** Prévia, muda a regra, Aplicar: nada pode ser gravado. Depois de refazer a prévia (regra já mudada) o plano novo vale. */
    private static void changeBeforeApply(GameTestHelper h, Change change, boolean freshPlanStillApplies) {
        Rig r = rig(h);
        List<ItemStack> sa = OrganizeLogic.snapshot(r.a());
        organize(r.p(), OrganizeRequest.PREVIEW);
        check(h, OrganizeService.hasPending(r.p()), "a prévia devia ficar guardada");
        change.run(r);
        List<ItemStack> sb2 = OrganizeLogic.snapshot(r.b());
        organize(r.p(), OrganizeRequest.APPLY);
        check(h, same(r.a(), sa) && same(r.b(), sb2), "a regra mudou depois da prévia: nada podia ser gravado");
        check(h, !OrganizeService.hasApplied(r.p()), "não devia constar como aplicado");
        check(h, !OrganizeService.hasPending(r.p()), "a prévia velha é descartada");
        if (!freshPlanStillApplies) {
            r.lab().cleanup();
            h.succeed();
            return;
        }
        // Controle: sem a mudança o mesmo cenário aplica (o intervalo entre prévias é de 10 ticks).
        h.runAfterDelay(12, () -> {
            organize(r.p(), OrganizeRequest.PREVIEW);
            organize(r.p(), OrganizeRequest.APPLY);
            check(h, OrganizeService.hasApplied(r.p()), "sem a mudança no baú, uma prévia nova devia valer");
            r.lab().cleanup();
            h.succeed();
        });
    }

    public static void reservationCreatedAfterThePreviewInvalidatesApply(GameTestHelper h) {
        changeBeforeApply(h, RESERVE, true);
    }

    public static void buttonNChangedAfterThePreviewInvalidatesApply(GameTestHelper h) {
        changeBeforeApply(h, BUTTON_N, false);
    }

    public static void categoryTurnedOffAfterThePreviewInvalidatesApply(GameTestHelper h) {
        changeBeforeApply(h, CATEGORY, false);
    }

    /** Prévia, Aplicar (vale), muda a regra, Desfazer: nada pode ser gravado e o plano continua como "aplicado". */
    private static void changeBeforeUndo(GameTestHelper h, Change change) {
        Rig r = rig(h);
        List<ItemStack> sa = OrganizeLogic.snapshot(r.a());
        organize(r.p(), OrganizeRequest.PREVIEW);
        organize(r.p(), OrganizeRequest.APPLY);
        check(h, OrganizeService.hasApplied(r.p()), "o plano devia ter sido aplicado (sem mudança nenhuma ainda)");
        List<ItemStack> afterA = OrganizeLogic.snapshot(r.a());
        check(h, !same(r.a(), sa), "o plano devia ter movido algo do baú A");
        change.run(r);
        List<ItemStack> afterB = OrganizeLogic.snapshot(r.b());
        organize(r.p(), OrganizeRequest.UNDO);
        check(h, same(r.a(), afterA) && same(r.b(), afterB), "a regra mudou depois de aplicar: desfazer não podia gravar nada");
        check(h, OrganizeService.hasApplied(r.p()), "o plano continua aplicado (o desfazer foi recusado)");
        r.lab().cleanup();
        h.succeed();
    }

    public static void reservationCreatedAfterApplyInvalidatesUndo(GameTestHelper h) {
        changeBeforeUndo(h, RESERVE);
    }

    public static void buttonNChangedAfterApplyInvalidatesUndo(GameTestHelper h) {
        changeBeforeUndo(h, BUTTON_N);
    }

    public static void categoryTurnedOffAfterApplyInvalidatesUndo(GameTestHelper h) {
        changeBeforeUndo(h, CATEGORY);
    }
}
