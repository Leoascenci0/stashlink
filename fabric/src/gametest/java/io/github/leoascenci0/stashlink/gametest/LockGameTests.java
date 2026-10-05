package io.github.leoascenci0.stashlink.gametest;

import io.github.leoascenci0.stashlink.Constants;
import io.github.leoascenci0.stashlink.config.StashLinkConfig;
import io.github.leoascenci0.stashlink.lootall.LootAllService;
import io.github.leoascenci0.stashlink.network.LockSlotRequest;
import io.github.leoascenci0.stashlink.network.SlotLocksSync;
import io.github.leoascenci0.stashlink.quickstack.QuickStackService;
import io.github.leoascenci0.stashlink.slotlock.SlotLockService;
import io.github.leoascenci0.stashlink.slotlock.SlotLockSync;
import io.github.leoascenci0.stashlink.slotlock.SlotLocks;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Item 13: slot de baú travado (reservado) com um item. Servidor de verdade, jogadores em sobrevivência, cliques
 * reais no menu ({@code menu.clicked}). Regras de ouro: a prévia nunca vira item, e a soma de cada item (baús +
 * jogadores + cursor) nunca muda.
 */
public class LockGameTests {
    private static final Item COBBLE = Items.COBBLESTONE;
    private static final Item DIRT = Items.DIRT;
    private static final Item LOG = Items.OAK_LOG;

    /**
     * Jogador de sobrevivência sem a "auto-compactação" do Upgraded Iron Chests (mod de teste do Item 11): ele
     * reorganiza o baú vanilla depois de cada clique, direto no container, e atrapalharia os cliques daqui.
     * Sem o mod no classpath, nada acontece.
     */
    private static ServerPlayer player(Lab lab, double x, double y, double z) {
        ServerPlayer p = lab.player(x, y, z);
        try {
            Class.forName("in2bubble.upgradedironchests.In2bubble")
                    .getMethod("setPlayerAutoCompaction", ServerPlayer.class, boolean.class).invoke(null, p, false);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // mod ausente ou outra versão: sem compactação para desligar
        }
        return p;
    }

    private static void check(GameTestHelper h, boolean ok, String msg) {
        h.assertTrue(ok, msg);
    }

    /** Índice, no menu de 3 fileiras, do slot {@code i} do inventário do jogador. */
    private static int menuSlot(int playerSlot) {
        return playerSlot < 9 ? 54 + playerSlot : 27 + playerSlot - 9;
    }

    private static void lock(ServerPlayer p, AbstractContainerMenu menu, int slot) {
        SlotLockService.handle(p, new LockSlotRequest(menu.containerId, slot));
    }

    /**
     * Roda os passos um depois do outro, espaçados pelo intervalo que o servidor impõe entre pedidos de travar slot:
     * é assim que um jogador de verdade clica (o limite anti-flood ignora pedidos no mesmo tick).
     */
    private static void steps(GameTestHelper h, Runnable... steps) {
        int gap = StashLinkConfig.SLOT_LOCK_COOLDOWN_TICKS;
        steps[0].run();
        for (int i = 1; i < steps.length; i++) {
            h.runAfterDelay((long) i * gap, steps[i]);
        }
    }

    private static Item locked(Container c, int slot) {
        return SlotLocks.lockedItem(c, slot);
    }

    private static int total(List<Container> boxes, List<ServerPlayer> players, Item item) {
        int t = 0;
        for (Container c : boxes) {
            t += c.countItem(item);
        }
        for (ServerPlayer p : players) {
            t += Lab.carried(p, item);
        }
        return t;
    }

    // ---------------------------------------------------------------- travar e destravar

    @GameTest(maxTicks = 60)
    public void lockAndUnlockWork(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        ServerPlayer p = player(lab, 4, 2, 4);
        Lab.fill(chest, 3, COBBLE, 10);
        ChestMenu menu = Lab.open(p, chest, 1);

        steps(h, () -> {
            lock(p, menu, 3);                                   // slot com item: trava para ele
            check(h, locked(chest, 3) == COBBLE, "slot 3 devia estar reservado para pedra");
            check(h, Lab.count(chest, COBBLE) == 10, "travar não pode mexer nos itens");
        }, () -> {
            lock(p, menu, 3);                                   // de novo: destrava
            check(h, locked(chest, 3) == null, "slot 3 devia ter sido destravado");
        }, () -> {
            lock(p, menu, 8);                                   // vazio e cursor vazio: nada a travar
            check(h, locked(chest, 8) == null, "sem item não há o que travar");
        }, () -> {
            menu.setCarried(new ItemStack(DIRT, 5));
            lock(p, menu, 8);                                   // vazio com item no cursor: reserva para ele
            check(h, locked(chest, 8) == DIRT, "slot 8 devia estar reservado para terra");
            check(h, menu.getCarried().getCount() == 5 && chest.getItem(8).isEmpty(), "o cursor e o slot ficam como estavam");
            menu.setCarried(ItemStack.EMPTY);
        }, () -> {
            lock(p, menu, 40);                                  // slot do próprio inventário: ignorado
            for (int i = 0; i < chest.getContainerSize(); i++) {
                if (i != 8) {
                    check(h, locked(chest, i) == null, "só o slot 8 devia estar travado");
                }
            }
            lock(p, menu, 999);                                 // índice inválido não derruba nada
            lock(p, menu, -1);
            p.containerMenu = p.inventoryMenu;
            lock(p, menu, 3);                                   // menu já não é o aberto: ignorado
            check(h, locked(chest, 3) == null, "pedido com menu fechado devia ser ignorado");
            lab.cleanup();
            h.succeed();
        });
    }

    // ---------------------------------------------------------------- a reserva bloqueia outros itens

    @GameTest
    public void reservedSlotRejectsOtherItems(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        ServerPlayer p = player(lab, 4, 2, 4);
        ChestMenu menu = Lab.open(p, chest, 1);
        menu.setCarried(new ItemStack(COBBLE, 1));
        lock(p, menu, 5);                                   // slot 5 reservado para pedra
        menu.setCarried(ItemStack.EMPTY);

        // clique com terra no cursor: não entra
        menu.setCarried(new ItemStack(DIRT, 7));
        menu.clicked(5, 0, ContainerInput.PICKUP, p);
        check(h, chest.getItem(5).isEmpty() && menu.getCarried().getCount() == 7, "terra não pode entrar no slot reservado");
        // clique com pedra no cursor: entra
        menu.setCarried(new ItemStack(COBBLE, 3));
        menu.clicked(5, 0, ContainerInput.PICKUP, p);
        check(h, chest.getItem(5).is(COBBLE) && chest.getItem(5).getCount() == 3, "pedra devia entrar: slot=" + chest.getItem(5) + " cursor=" + menu.getCarried() + " lock=" + locked(chest, 5) + " chestCobble=" + chest.countItem(COBBLE) + " where=" + java.util.stream.IntStream.range(0, 27).filter(i -> !chest.getItem(i).isEmpty()).boxed().toList() + " carried=" + Lab.carried(p, COBBLE));
        menu.setCarried(ItemStack.EMPTY);
        menu.clicked(5, 0, ContainerInput.PICKUP, p);       // pega a pedra de volta
        check(h, chest.getItem(5).isEmpty(), "pedra sai normalmente");
        menu.setCarried(ItemStack.EMPTY);
        p.getInventory().setItem(0, new ItemStack(DIRT, 9));
        // troca com a hotbar (tecla numérica): terra não entra
        menu.clicked(5, 0, ContainerInput.SWAP, p);
        check(h, chest.getItem(5).isEmpty() && p.getInventory().getItem(0).is(DIRT), "troca por número não pode trazer terra");
        // shift-clique de terra do inventário: vai para outros slots, nunca para o 5
        p.getInventory().setItem(10, new ItemStack(DIRT, 20));
        menu.quickMoveStack(p, menuSlot(10));
        check(h, chest.getItem(5).isEmpty(), "shift-clique não pode usar o slot reservado para outro item");
        check(h, Lab.count(chest, DIRT) == 20, "a terra foi para outros slots do baú");
        lab.cleanup();
        h.succeed();
    }

    // ---------------------------------------------------------------- a prévia nunca vira item

    @GameTest
    public void ghostNeverBecomesARealItem(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        ServerPlayer p = player(lab, 4, 2, 4);
        Lab.prefs(p, 8, true);
        ChestMenu menu = Lab.open(p, chest, 1);
        menu.setCarried(new ItemStack(DIRT, 1));
        lock(p, menu, 4);
        menu.setCarried(ItemStack.EMPTY);
        check(h, locked(chest, 4) == DIRT, "reservado para terra");

        // o que o servidor mostra a QUALQUER cliente (com ou sem mod) é o slot vazio
        check(h, chest.getItem(4).isEmpty() && menu.slots.get(4).getItem().isEmpty(), "a prévia não é item de verdade");
        menu.clicked(4, 0, ContainerInput.PICKUP, p);                  // clicar no fantasma
        check(h, menu.getCarried().isEmpty(), "clicar no fantasma não pega nada");
        menu.clicked(4, 0, ContainerInput.QUICK_MOVE, p);              // shift-clicar no fantasma
        menu.clicked(4, 0, ContainerInput.PICKUP_ALL, p);              // clique duplo
        menu.clicked(4, 0, ContainerInput.CLONE, p);
        menu.clicked(4, 0, ContainerInput.THROW, p);
        LootAllService.handle(p);                                      // W
        check(h, total(List.of(chest), List.of(p), DIRT) == 0, "nenhuma terra surgiu: " + total(List.of(chest), List.of(p), DIRT));
        check(h, p.getInventory().isEmpty() && menu.getCarried().isEmpty(), "o jogador continua de mãos vazias");
        // o funil tira do container pelo Container.removeItem: o slot está vazio de verdade
        check(h, chest.removeItem(4, 64).isEmpty(), "o funil não tem o que tirar");
        check(h, locked(chest, 4) == DIRT, "a reserva continua depois de tudo isso");
        // sincronização: o cliente com o mod receberia a lista, o vanilla nada (só envia a quem registrou o canal)
        List<SlotLocksSync.Entry> snap = SlotLockSync.snapshot(p, menu);
        check(h, snap.size() == 1 && snap.get(0).menuSlot() == 4 && snap.get(0).item().is(DIRT), "retrato: " + snap);
        lab.cleanup();
        h.succeed();
    }

    // ---------------------------------------------------------------- N respeita a reserva

    @GameTest
    public void quickStackPrefersReservedSlot(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        ServerPlayer p = player(lab, 4, 2, 4);
        Lab.prefs(p, 8, true);
        Lab.fill(chest, 0, COBBLE, 10);                                // já tem pedra, no slot 0 (livre)
        ChestMenu menu = Lab.open(p, chest, 1);
        menu.setCarried(new ItemStack(COBBLE, 1));
        lock(p, menu, 20);                                             // slot 20 reservado para pedra
        menu.setCarried(ItemStack.EMPTY);
        Lab.close(p);
        Lab.give(p, 12, COBBLE, 30);
        QuickStackService.handle(p);
        check(h, chest.getItem(20).is(COBBLE) && chest.getItem(20).getCount() == 30, "a pedra devia ir para o slot reservado: " + chest.getItem(20));
        check(h, chest.getItem(0).getCount() == 10, "o stack do slot 0 não muda");
        check(h, Lab.carried(p, COBBLE) == 0, "tudo foi guardado");
        lab.cleanup();
        h.succeed();
    }

    @GameTest
    public void quickStackFeedsAnEmptyReservation(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container empty = lab.chest(2, 2, 2);                          // sem nenhum item, só uma reserva
        Container near = lab.chest(4, 2, 5);                           // mais perto, já tem terra
        ServerPlayer p = player(lab, 4, 2, 4);
        Lab.prefs(p, 8, true);
        Lab.fill(near, 0, DIRT, 5);
        ChestMenu menu = Lab.open(p, empty, 1);
        menu.setCarried(new ItemStack(DIRT, 1));
        lock(p, menu, 13);
        menu.setCarried(ItemStack.EMPTY);
        Lab.close(p);
        Lab.give(p, 12, DIRT, 40);
        Lab.give(p, 13, COBBLE, 9);
        QuickStackService.handle(p);
        check(h, empty.getItem(13).is(DIRT) && empty.getItem(13).getCount() == 40,
                "a terra devia ir para o baú com a reserva (mesmo o outro estando mais perto): " + empty.getItem(13));
        check(h, near.getItem(0).getCount() == 5, "o baú mais perto não recebe a terra reservada em outro");
        check(h, Lab.carried(p, COBBLE) == 9, "pedra sem baú que a queira continua com o jogador");
        lab.cleanup();
        h.succeed();
    }

    @GameTest
    public void quickStackNeverUsesAReservationForAnotherItem(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        ServerPlayer p = player(lab, 4, 2, 4);
        Lab.prefs(p, 8, true);
        for (int i = 1; i < chest.getContainerSize(); i++) {
            Lab.fill(chest, i, LOG, 64);                               // tudo cheio, menos o slot 0
        }
        Lab.fill(chest, 1, COBBLE, 64);                                // o baú contém pedra (stack cheio)
        ChestMenu menu = Lab.open(p, chest, 1);
        menu.setCarried(new ItemStack(DIRT, 1));
        lock(p, menu, 0);                                              // o único slot livre é da terra
        menu.setCarried(ItemStack.EMPTY);
        Lab.close(p);
        Lab.give(p, 12, COBBLE, 30);
        QuickStackService.handle(p);
        check(h, chest.getItem(0).isEmpty(), "o slot reservado para terra não recebe pedra");
        check(h, Lab.carried(p, COBBLE) == 30, "a pedra fica com o jogador");
        lab.cleanup();
        h.succeed();
    }

    // ---------------------------------------------------------------- W respeita a reserva

    @GameTest
    public void lootAllKeepsTheReservation(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        ServerPlayer p = player(lab, 4, 2, 4);
        Lab.fill(chest, 2, COBBLE, 64);
        Lab.fill(chest, 3, DIRT, 12);
        ChestMenu menu = Lab.open(p, chest, 1);
        lock(p, menu, 2);                                              // slot com item: reservado para pedra
        LootAllService.handle(p);
        check(h, chest.isEmpty(), "W leva tudo, inclusive o que estava no slot reservado");
        check(h, locked(chest, 2) == COBBLE, "mas a reserva continua (o slot só ficou vazio)");
        check(h, Lab.carried(p, COBBLE) == 64 && Lab.carried(p, DIRT) == 12, "nada some");
        // e o slot reservado continua só aceitando pedra
        menu.setCarried(new ItemStack(DIRT, 1));
        menu.clicked(2, 0, ContainerInput.PICKUP, p);
        check(h, chest.getItem(2).isEmpty(), "terra continua recusada");
        lab.cleanup();
        h.succeed();
    }

    // ---------------------------------------------------------------- baú duplo, barril, shulker

    @GameTest(maxTicks = 60)
    public void doubleChestKeepsLocksInTheRightHalf(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container dbl = lab.doubleChest(3, 2, 6);
        ServerPlayer p = player(lab, 4, 2, 4);
        Lab.prefs(p, 12, true);
        ChestMenu menu = Lab.open(p, dbl, 1);
        steps(h, () -> {
            menu.setCarried(new ItemStack(COBBLE, 1));
            lock(p, menu, 3);                                              // primeira metade
        }, () -> {
            lock(p, menu, 40);                                             // segunda metade
            menu.setCarried(ItemStack.EMPTY);
            check(h, locked(dbl, 3) == COBBLE && locked(dbl, 40) == COBBLE, "as duas travas valem no baú duplo");
            check(h, locked(dbl, 4) == null && locked(dbl, 41) == null, "e só elas");

            // mesmo olhando o baú duplo como duas block entities independentes, cada uma tem a sua
            BlockPos left = h.absolutePos(new BlockPos(3, 2, 6));
            BlockPos right = h.absolutePos(new BlockPos(4, 2, 6));
            Container l = (Container) lab.level.getBlockEntity(left);
            Container r = (Container) lab.level.getBlockEntity(right);
            int inLeft = 0;
            int inRight = 0;
            for (int i = 0; i < 27; i++) {
                inLeft += locked(l, i) != null ? 1 : 0;
                inRight += locked(r, i) != null ? 1 : 0;
            }
            check(h, inLeft == 1 && inRight == 1, "uma trava em cada metade (" + inLeft + "/" + inRight + ")");

            // terra não entra em nenhum dos dois slots reservados, nem por shift-clique
            for (int slot : new int[]{3, 40}) {
                menu.setCarried(new ItemStack(DIRT, 4));
                menu.clicked(slot, 0, ContainerInput.PICKUP, p);
                check(h, dbl.getItem(slot).isEmpty(), "terra recusada no slot " + slot);
                menu.setCarried(ItemStack.EMPTY);
            }
            // N com baú duplo: a pedra vai para o slot reservado da segunda metade ou da primeira (o primeiro)
            Lab.close(p);
            Lab.give(p, 12, COBBLE, 20);
            QuickStackService.handle(p);
            // A ordem das metades no container que o jogo monta pode ser a inversa da do teste: vale qualquer um dos dois reservados.
            int inReserved = dbl.getItem(3).getCount() + dbl.getItem(40).getCount();
            check(h, inReserved == 20 && dbl.countItem(COBBLE) == 20, "N devia usar so os slots reservados do bau duplo: reservados=" + inReserved + " total=" + dbl.countItem(COBBLE));
            lab.cleanup();
            h.succeed();
        });
    }

    @GameTest(maxTicks = 60)
    public void barrelAndShulkerWork(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container barrel = lab.block(Blocks.BARREL, 2, 2, 2);
        Container shulker = lab.block(Blocks.SHULKER_BOX, 6, 2, 2);
        ServerPlayer p = player(lab, 4, 2, 4);

        ChestMenu bm = Lab.open(p, barrel, 1);
        steps(h, () -> {
            bm.setCarried(new ItemStack(COBBLE, 1));
            lock(p, bm, 7);
            bm.setCarried(new ItemStack(DIRT, 3));
            bm.clicked(7, 0, ContainerInput.PICKUP, p);
            check(h, locked(barrel, 7) == COBBLE && barrel.getItem(7).isEmpty(), "barril: reservado e recusa terra");
            bm.setCarried(ItemStack.EMPTY);
        }, () -> {
            ShulkerBoxMenu sm = new ShulkerBoxMenu(2, p.getInventory(), shulker);
            p.containerMenu = sm;
            sm.setCarried(new ItemStack(COBBLE, 1));
            lock(p, sm, 7);
            sm.setCarried(new ItemStack(DIRT, 3));
            sm.clicked(7, 0, ContainerInput.PICKUP, p);
            check(h, locked(shulker, 7) == COBBLE && shulker.getItem(7).isEmpty(), "shulker: reservada e recusa terra");
            sm.setCarried(new ItemStack(COBBLE, 3));
            sm.clicked(7, 0, ContainerInput.PICKUP, p);
            check(h, shulker.getItem(7).is(COBBLE), "shulker: aceita o item reservado");
            lab.cleanup();
            h.succeed();
        });
    }

    // ---------------------------------------------------------------- memória no baú (disco)

    @GameTest(maxTicks = 60)
    public void locksSurviveSaveAndLoad(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        ServerPlayer p = player(lab, 4, 2, 4);
        ChestMenu menu = Lab.open(p, chest, 1);
        BlockPos pos = h.absolutePos(new BlockPos(2, 2, 2));
        BlockEntity be = lab.level.getBlockEntity(pos);
        steps(h, () -> {
            menu.setCarried(new ItemStack(COBBLE, 1));
            lock(p, menu, 5);
        }, () -> {
            menu.setCarried(new ItemStack(LOG, 1));
            lock(p, menu, 22);
            menu.setCarried(ItemStack.EMPTY);

            // o mesmo caminho do disco: salvar o bloco e carregar de novo (reiniciar o servidor)
            CompoundTag tag = be.saveWithFullMetadata(lab.level.registryAccess());
            BlockEntity loaded = BlockEntity.loadStatic(pos, be.getBlockState(), tag, lab.level.registryAccess());
            check(h, loaded instanceof Container, "o bloco recarregado devia ser um container");
            check(h, locked((Container) loaded, 5) == COBBLE && locked((Container) loaded, 22) == LOG,
                    "as travas voltaram do disco");
            check(h, locked((Container) loaded, 6) == null, "e só elas");
        }, () -> {
            // destravar apaga do disco também
            lock(p, menu, 5);
            CompoundTag after = be.saveWithFullMetadata(lab.level.registryAccess());
            BlockEntity reloaded = BlockEntity.loadStatic(pos, be.getBlockState(), after, lab.level.registryAccess());
            check(h, locked((Container) reloaded, 5) == null && locked((Container) reloaded, 22) == LOG, "destravar também persiste");
            lab.cleanup();
            h.succeed();
        });
    }

    @GameTest
    public void brokenChestTakesItsLocksWithIt(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        ServerPlayer p = player(lab, 4, 2, 4);
        ChestMenu menu = Lab.open(p, chest, 1);
        menu.setCarried(new ItemStack(COBBLE, 1));
        lock(p, menu, 5);
        menu.setCarried(ItemStack.EMPTY);
        Lab.close(p);
        BlockPos pos = h.absolutePos(new BlockPos(2, 2, 2));
        lab.level.removeBlock(pos, false);
        Container fresh = lab.block(Blocks.CHEST, 2, 2, 2);            // outro baú no mesmo lugar
        check(h, locked(fresh, 5) == null, "o baú novo não herda a reserva do antigo");
        lab.cleanup();
        h.succeed();
    }

    // ---------------------------------------------------------------- pacotes

    /** O pacote de sincronização sobrevive a ida e volta pela rede, e quem não tem o mod nunca recebe nada. */
    @GameTest
    public void syncPacketRoundTripsAndVanillaClientsGetNothing(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        ServerPlayer p = player(lab, 4, 2, 4);
        ChestMenu menu = Lab.open(p, chest, 1);
        menu.setCarried(new ItemStack(COBBLE, 1));
        lock(p, menu, 6);
        menu.setCarried(ItemStack.EMPTY);

        SlotLocksSync sent = new SlotLocksSync(menu.containerId, SlotLockSync.snapshot(p, menu));
        net.minecraft.network.RegistryFriendlyByteBuf buf = new net.minecraft.network.RegistryFriendlyByteBuf(
                io.netty.buffer.Unpooled.buffer(), lab.level.registryAccess());
        SlotLocksSync.STREAM_CODEC.encode(buf, sent);
        SlotLocksSync got = SlotLocksSync.STREAM_CODEC.decode(buf);
        check(h, got.containerId() == 1 && got.entries().size() == 1 && got.entries().get(0).menuSlot() == 6
                && got.entries().get(0).item().is(COBBLE), "pacote deformado: " + got);
        net.minecraft.network.RegistryFriendlyByteBuf req = new net.minecraft.network.RegistryFriendlyByteBuf(
                io.netty.buffer.Unpooled.buffer(), lab.level.registryAccess());
        LockSlotRequest.STREAM_CODEC.encode(req, new LockSlotRequest(7, 12));
        LockSlotRequest back = LockSlotRequest.STREAM_CODEC.decode(req);
        check(h, back.containerId() == 7 && back.menuSlot() == 12, "pedido deformado");

        // o jogador simulado não registrou o canal (é como um cliente vanilla): nada é enviado, nada quebra
        check(h, !io.github.leoascenci0.stashlink.platform.Services.PLATFORM.sendIfSupported(p, sent),
                "cliente sem o mod não pode receber o pacote");
        SlotLockSync.tick(lab.level.getServer());
        lab.cleanup();
        h.succeed();
    }

    // ---------------------------------------------------------------- 2 jogadores no mesmo baú

    @GameTest(maxTicks = 60)
    public void secondPlayerBlocksLockChanges(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        ServerPlayer a = player(lab, 4, 2, 4);
        ServerPlayer b = player(lab, 3, 2, 4);
        Lab.fill(chest, 3, COBBLE, 10);
        ChestMenu ma = Lab.open(a, chest, 1);
        ChestMenu mb = Lab.open(b, chest, 2);
        steps(h, () -> {
            lock(a, ma, 3);
            lock(b, mb, 3);
            check(h, locked(chest, 3) == null, "com dois jogadores no baú, ninguém muda travas");
            Lab.close(b);
        }, () -> {
            lock(a, ma, 3);
            check(h, locked(chest, 3) == COBBLE, "B saiu: A consegue travar");
            Lab.open(b, chest, 3);
            lock(b, b.containerMenu, 3);
            check(h, locked(chest, 3) == COBBLE, "B entrou de novo: a trava não muda");
            lab.cleanup();
            h.succeed();
        });
    }

    // ---------------------------------------------------------------- fuzz: 2 jogadores, 3000 ações

    /** Devolve o cursor ao inventário (sem soltar no chão) e só então fecha; se não couber, deixa aberto. */
    private static void safeClose(ServerPlayer p) {
        if (p.containerMenu == p.inventoryMenu) {
            return;
        }
        ItemStack carried = p.containerMenu.getCarried();
        if (!carried.isEmpty()) {
            p.getInventory().add(carried);
            if (!carried.isEmpty()) {
                return;
            }
        }
        Lab.close(p);
    }

    @GameTest(maxTicks = 1700)
    public void lockFuzz(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container c1 = lab.chest(2, 2, 2);
        Container c2 = lab.block(Blocks.BARREL, 6, 2, 2);
        Container dbl = lab.doubleChest(3, 2, 6);
        List<Container> boxes = List.of(c1, c2, dbl);
        ServerPlayer a = player(lab, 4, 2, 4);
        ServerPlayer b = player(lab, 5, 2, 4);
        List<ServerPlayer> players = List.of(a, b);
        Lab.prefs(a, 12, true);
        Lab.prefs(b, 12, true);
        Item[] items = {COBBLE, DIRT, LOG};
        Random rnd = new Random(13);
        Lab.fill(c1, 0, COBBLE, 64);
        Lab.fill(c1, 3, DIRT, 40);
        Lab.fill(c2, 0, COBBLE, 30);
        Lab.fill(dbl, 0, LOG, 64);
        Lab.fill(dbl, 40, DIRT, 64);
        Lab.give(a, 10, COBBLE, 20);
        Lab.give(a, 11, LOG, 15);
        Lab.give(b, 10, DIRT, 25);
        Lab.give(b, 0, COBBLE, 5);
        int[] expected = new int[items.length];
        for (int i = 0; i < items.length; i++) {
            expected[i] = total(boxes, players, items[i]);
        }
        int[] stats = {0, 0};
        List<String> trail = new ArrayList<>();   // travas feitas, cliques recusados pela reserva

        for (int tick = 1; tick <= 1500; tick++) {
            final int t = tick;
            h.runAfterDelay(tick, () -> {
                for (int n = 0; n < 2; n++) {
                    ServerPlayer p = rnd.nextBoolean() ? a : b;
                    int action = rnd.nextInt(17);
                    String name;
                    AbstractContainerMenu m = p.containerMenu;
                    boolean open = m != p.inventoryMenu && m.slots.size() >= 54;
                    int slots = open ? m.slots.size() : 0;
                    switch (action) {
                        case 0 -> {
                            name = "N";
                            QuickStackService.handle(p);
                        }
                        case 1 -> {
                            name = "W";
                            LootAllService.handle(p);
                        }
                        case 2 -> {
                            name = "abre";
                            if (p.containerMenu == p.inventoryMenu) {
                                Lab.open(p, boxes.get(rnd.nextInt(boxes.size())), 1 + rnd.nextInt(100));
                            }
                        }
                        case 3 -> {
                            name = "fecha";
                            safeClose(p);
                        }
                        case 4, 5, 11, 12, 13, 14, 15 -> {
                            name = "trava";
                            if (open) {
                                int before = locks(boxes);
                                int pick = rnd.nextInt(m.slots.size() - 36);
                                if (rnd.nextInt(10) < 7) {
                                    List<Integer> filled = new ArrayList<>();
                                    for (int s = 0; s < m.slots.size() - 36; s++) {
                                        if (!m.slots.get(s).getItem().isEmpty()) {
                                            filled.add(s);
                                        }
                                    }
                                    if (!filled.isEmpty()) {
                                        pick = filled.get(rnd.nextInt(filled.size()));
                                    }
                                }
                                trail.add("lock " + pick + " menu=" + m.containerId + " slot=" + m.slots.get(pick).getItem() + " cursor=" + m.getCarried());
                                lock(p, m, pick);
                                stats[0] += locks(boxes) != before ? 1 : 0;
                            }
                        }
                        case 6 -> {
                            name = "clique";
                            if (open) {
                                int sl = rnd.nextInt(slots);
                                trail.add("pickup " + sl + " cursor=" + m.getCarried() + " slot=" + m.slots.get(sl).getItem() + " lock=" + SlotLocks.lockedItem(m.slots.get(sl).container, m.slots.get(sl).getContainerSlot()));
                                m.clicked(sl, rnd.nextInt(2), ContainerInput.PICKUP, p);
                            }
                        }
                        case 7 -> {
                            name = "shift-clique";
                            if (open) {
                                int sl = rnd.nextInt(slots);
                                trail.add("shift " + sl + " slot=" + m.slots.get(sl).getItem() + " menu=" + m.containerId);
                                m.clicked(sl, 0, ContainerInput.QUICK_MOVE, p);
                            }
                        }
                        case 8 -> {
                            name = "troca";
                            if (open) {
                                m.clicked(rnd.nextInt(slots), rnd.nextInt(9), ContainerInput.SWAP, p);
                            }
                        }
                        case 9 -> {
                            name = "clique-duplo";
                            if (open) {
                                m.clicked(rnd.nextInt(slots), 0, ContainerInput.PICKUP_ALL, p);
                            }
                        }
                        default -> {
                            name = "arrasta";
                            if (open) {
                                int s1 = rnd.nextInt(slots);
                                int s2 = rnd.nextInt(slots);
                                m.clicked(-999, 0, ContainerInput.QUICK_CRAFT, p);
                                m.clicked(s1, 1, ContainerInput.QUICK_CRAFT, p);
                                m.clicked(s2, 1, ContainerInput.QUICK_CRAFT, p);
                                m.clicked(-999, 2, ContainerInput.QUICK_CRAFT, p);
                            }
                        }
                    }
                    String who = "tick " + t + (p == a ? " A " : " B ") + name + " TRAIL " + trail.subList(Math.max(0, trail.size() - 6), trail.size());
                    for (int i = 0; i < items.length; i++) {
                        int now = total(boxes, players, items[i]);
                        check(h, now == expected[i], who + ": " + items[i] + " mudou: esperado " + expected[i] + ", achou " + now);
                    }
                    // Invariante da reserva: slot travado só contém o seu item (nada entra por clique, shift,
                    // troca, duplo ou arrasto; só o N e o próprio item).
                    for (Container c : boxes) {
                        for (int s = 0; s < c.getContainerSize(); s++) {
                            Item lk = locked(c, s);
                            if (lk != null && !c.getItem(s).isEmpty()) {
                                check(h, c.getItem(s).is(lk), who + " box" + boxes.indexOf(c) + ": slot " + s + " reservado para " + lk + " tem " + c.getItem(s));
                            }
                        }
                    }
                }
            });
        }
        h.runAfterDelay(1501, () -> {
            Constants.LOG.info("[STASHLINK-LOCKFUZZ] 3000 acoes, {} travas feitas", stats[0]);
            check(h, stats[0] > 60, "o fuzz quase não travou nada (" + stats[0] + "): teste vazio");
            lab.cleanup();
            h.succeed();
        });
    }

    private static int locks(List<Container> boxes) {
        int n = 0;
        for (Container c : boxes) {
            for (int s = 0; s < c.getContainerSize(); s++) {
                n += SlotLocks.lockedItem(c, s) != null ? 1 : 0;
            }
        }
        return n;
    }
}
