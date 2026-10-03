package io.github.leoascenci0.stashlink.gametest;

import io.github.leoascenci0.stashlink.config.Feature;
import io.github.leoascenci0.stashlink.config.FeaturePolicyService;
import io.github.leoascenci0.stashlink.config.PlayerPrefs;
import io.github.leoascenci0.stashlink.config.PlayerPrefsStore;
import io.github.leoascenci0.stashlink.config.StashLinkConfig;
import io.github.leoascenci0.stashlink.label.LabelService;
import io.github.leoascenci0.stashlink.lootall.LootAllService;
import io.github.leoascenci0.stashlink.network.LockSlotRequest;
import io.github.leoascenci0.stashlink.network.PullItemRequest;
import io.github.leoascenci0.stashlink.network.SetFeatureLockRequest;
import io.github.leoascenci0.stashlink.pull.PullItemService;
import io.github.leoascenci0.stashlink.quickstack.QuickStackService;
import io.github.leoascenci0.stashlink.refill.RefillService;
import io.github.leoascenci0.stashlink.slotlock.SlotLockService;
import io.github.leoascenci0.stashlink.slotlock.SlotLocks;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.EnumSet;
import java.util.List;

/**
 * Liga/desliga + cadeado das funções (Item 23). Servidor de verdade, jogador em sobrevivência: com a função
 * trancada pelo servidor, ou desligada pelo próprio jogador, o pedido não faz nada; destrancada, funciona.
 * Cada fase usa um jogador novo, porque o intervalo mínimo entre pedidos é por jogador.
 */
public class FeatureGameTests {
    private static void check(GameTestHelper h, boolean ok, String msg) {
        h.assertTrue(ok, msg);
    }

    private static void lock(Feature f, boolean locked) {
        StashLinkConfig.setFeatureLocked(f, locked);
    }

    /** Cada teste começa sem nada trancado e termina deixando assim, para não contaminar os outros. */
    private static void resetLocks() {
        StashLinkConfig.lockedFeatures = EnumSet.noneOf(Feature.class);
    }

    private static void disableFor(ServerPlayer p, Feature f) {
        PlayerPrefsStore.set(p.getUUID(), new PlayerPrefs(8, 1, List.of(), f.bit()));
    }

    private static int cobbleInBackpack(ServerPlayer p) {
        return p.getInventory().countItem(Items.COBBLESTONE);
    }

    @GameTest
    public void quickStackRespectsLockAndPersonalToggle(GameTestHelper h) {
        resetLocks();
        Lab lab = new Lab(h);
        Container chest = lab.chest(4, 2, 4);
        Lab.fill(chest, 0, Items.COBBLESTONE, 1);          // o baú já tem pedra: a N guarda pedra nele

        ServerPlayer locked = lab.player(3, 2, 3);
        Lab.prefs(locked, 8, true);
        Lab.give(locked, 10, Items.COBBLESTONE, 20);
        lock(Feature.QUICK_STACK, true);
        QuickStackService.handle(locked);
        check(h, cobbleInBackpack(locked) == 20, "trancada: a N não podia guardar nada");
        lock(Feature.QUICK_STACK, false);

        ServerPlayer off = lab.player(3, 2, 3);
        Lab.give(off, 10, Items.COBBLESTONE, 20);
        disableFor(off, Feature.QUICK_STACK);
        QuickStackService.handle(off);
        check(h, cobbleInBackpack(off) == 20, "desligada pelo jogador: a N não podia guardar nada");

        ServerPlayer on = lab.player(3, 2, 3);
        Lab.prefs(on, 8, true);
        Lab.give(on, 10, Items.COBBLESTONE, 20);
        QuickStackService.handle(on);
        check(h, cobbleInBackpack(on) == 0 && Lab.count(chest, Items.COBBLESTONE) == 21,
                "ligada e destrancada: a N devia guardar. mochila=" + cobbleInBackpack(on));
        resetLocks();
        lab.cleanup();
        h.succeed();
    }

    @GameTest
    public void lootAllRespectsLock(GameTestHelper h) {
        resetLocks();
        Lab lab = new Lab(h);
        Container chest = lab.chest(4, 2, 4);
        Lab.fill(chest, 0, Items.DIRT, 30);

        ServerPlayer locked = lab.player(3, 2, 3);
        Lab.open(locked, chest, 1);
        lock(Feature.LOOT_ALL, true);
        LootAllService.handle(locked);
        check(h, Lab.count(chest, Items.DIRT) == 30 && Lab.carried(locked, Items.DIRT) == 0,
                "trancada: a W não podia pegar nada");
        lock(Feature.LOOT_ALL, false);

        ServerPlayer off = lab.player(3, 2, 3);
        Lab.open(off, chest, 2);
        disableFor(off, Feature.LOOT_ALL);
        LootAllService.handle(off);
        check(h, Lab.count(chest, Items.DIRT) == 30, "desligada pelo jogador: a W não podia pegar nada");

        ServerPlayer on = lab.player(3, 2, 3);
        Lab.open(on, chest, 3);
        LootAllService.handle(on);
        check(h, Lab.count(chest, Items.DIRT) == 0 && Lab.carried(on, Items.DIRT) == 30,
                "ligada e destrancada: a W devia pegar tudo");
        resetLocks();
        lab.cleanup();
        h.succeed();
    }

    @GameTest
    public void pullRespectsLock(GameTestHelper h) {
        resetLocks();
        Lab lab = new Lab(h);
        Container chest = lab.chest(4, 2, 4);
        Lab.fill(chest, 0, Items.NETHERITE_INGOT, 5);

        ServerPlayer locked = lab.player(3, 2, 3);
        Lab.prefs(locked, 8, true);
        lock(Feature.PULL, true);
        PullItemService.handle(locked, new PullItemRequest(Items.NETHERITE_INGOT, 5));
        check(h, Lab.carried(locked, Items.NETHERITE_INGOT) == 0 && Lab.count(chest, Items.NETHERITE_INGOT) == 5,
                "trancada: o pedido do Litematica não podia trazer nada");
        lock(Feature.PULL, false);

        ServerPlayer on = lab.player(3, 2, 3);
        Lab.prefs(on, 8, true);
        PullItemService.handle(on, new PullItemRequest(Items.NETHERITE_INGOT, 5));
        check(h, Lab.carried(on, Items.NETHERITE_INGOT) == 5, "destrancada: devia ter trazido o item");
        resetLocks();
        lab.cleanup();
        h.succeed();
    }

    @GameTest
    public void refillRespectsLock(GameTestHelper h) {
        resetLocks();
        Lab lab = new Lab(h);
        Container chest = lab.chest(4, 2, 4);
        Lab.fill(chest, 0, Items.COBBLESTONE, 64);

        ServerPlayer locked = lab.player(3, 2, 3);
        Lab.prefs(locked, 8, true);
        lock(Feature.REFILL, true);
        locked.getInventory().setItem(locked.getInventory().getSelectedSlot(), new ItemStack(Items.COBBLESTONE, 1));
        RefillService.tickPlayer(locked);
        locked.getInventory().setItem(locked.getInventory().getSelectedSlot(), ItemStack.EMPTY);
        RefillService.tickPlayer(locked);
        check(h, Lab.count(chest, Items.COBBLESTONE) == 64 && Lab.carried(locked, Items.COBBLESTONE) == 0,
                "trancada: a mão não podia ser reabastecida");
        lock(Feature.REFILL, false);

        ServerPlayer on = lab.player(3, 2, 3);
        Lab.prefs(on, 8, true);
        on.getInventory().setItem(on.getInventory().getSelectedSlot(), new ItemStack(Items.COBBLESTONE, 1));
        RefillService.tickPlayer(on);
        on.getInventory().setItem(on.getInventory().getSelectedSlot(), ItemStack.EMPTY);
        RefillService.tickPlayer(on);
        check(h, Lab.carried(on, Items.COBBLESTONE) == 64, "destrancada: a mão devia ser reabastecida");
        resetLocks();
        lab.cleanup();
        h.succeed();
    }

    @GameTest
    public void slotLockRespectsLock(GameTestHelper h) {
        resetLocks();
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        ServerPlayer p = lab.player(4, 2, 4);
        Lab.fill(chest, 3, Items.COBBLESTONE, 10);
        ChestMenu menu = Lab.open(p, chest, 1);

        lock(Feature.SLOT_LOCK, true);
        SlotLockService.handle(p, new LockSlotRequest(menu.containerId, 3));
        lock(Feature.SLOT_LOCK, false);
        check(h, SlotLocks.lockedItem(chest, 3) == null, "trancada: o Alt+clique não podia reservar o slot");

        SlotLockService.handle(p, new LockSlotRequest(menu.containerId, 3));
        check(h, SlotLocks.lockedItem(chest, 3) == Items.COBBLESTONE, "destrancada: o slot devia ser reservado");

        // Trancar a função depois: a reserva fica guardada, mas deixa de valer (terra volta a poder entrar).
        lock(Feature.SLOT_LOCK, true);
        check(h, SlotLocks.lockedItem(chest, 3) == null && SlotLocks.mayPlace(chest, 3, new ItemStack(Items.DIRT)),
                "trancada: a reserva existente não podia mais valer");
        lock(Feature.SLOT_LOCK, false);
        check(h, SlotLocks.lockedItem(chest, 3) == Items.COBBLESTONE, "destrancada: a reserva volta a valer");
        resetLocks();
        lab.cleanup();
        h.succeed();
    }

    @GameTest
    public void labelRespectsLock(GameTestHelper h) {
        resetLocks();
        Lab lab = new Lab(h);
        lab.chest(4, 2, 4);
        BlockPos pos = h.absolutePos(new BlockPos(4, 2, 4));
        ServerPlayer p = lab.player(3, 2, 3);

        lock(Feature.LABEL, true);
        check(h, !LabelService.apply(p, pos, "Pedras", "so pedra"), "trancada: nao podia nomear");
        lock(Feature.LABEL, false);
        check(h, LabelService.apply(p, pos, "Pedras", "so pedra"), "destrancada: devia nomear");

        disableFor(p, Feature.LABEL);
        check(h, !LabelService.apply(p, pos, "Outro", "x"), "desligada pelo jogador: nao podia nomear");
        resetLocks();
        lab.cleanup();
        h.succeed();
    }

    @GameTest
    public void onlyOperatorsCanLockFromTheScreen(GameTestHelper h) {
        resetLocks();
        Lab lab = new Lab(h);
        ServerPlayer plain = lab.player(3, 2, 3);          // jogador comum: não é dono nem operador
        FeaturePolicyService.handle(plain, new SetFeatureLockRequest(Feature.QUICK_STACK.bit(), true));
        check(h, !StashLinkConfig.isFeatureLocked(Feature.QUICK_STACK), "jogador comum não pode trancar");

        // O servidor (o comando, o dono) tranca e destranca.
        FeaturePolicyService.apply(h.getLevel().getServer(), Feature.QUICK_STACK, true);
        check(h, StashLinkConfig.isFeatureLocked(Feature.QUICK_STACK), "apply devia trancar");
        FeaturePolicyService.apply(h.getLevel().getServer(), Feature.QUICK_STACK, false);
        check(h, !StashLinkConfig.isFeatureLocked(Feature.QUICK_STACK), "apply devia destrancar");
        resetLocks();
        lab.cleanup();
        h.succeed();
    }
}
