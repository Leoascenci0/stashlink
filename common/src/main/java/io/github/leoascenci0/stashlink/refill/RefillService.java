package io.github.leoascenci0.stashlink.refill;

import io.github.leoascenci0.stashlink.Constants;
import io.github.leoascenci0.stashlink.source.ItemSource;
import io.github.leoascenci0.stashlink.source.PlayerShulkerSource;
import io.github.leoascenci0.stashlink.source.PrioritizedItemSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reabastecimento automático das mãos (F1). Roda só no servidor; os loaders apenas chamam {@link #tick} no fim
 * de cada tick do servidor. Cliente vanilla funciona: ele só recebe a atualização normal de inventário.
 */
public final class RefillService {
    private static final int CLEANUP_INTERVAL_TICKS = 200;

    private static final Map<UUID, PlayerState> STATES = new HashMap<>();

    private RefillService() {
    }

    private static final class PlayerState {
        final HandWatcher mainHand = new HandWatcher();
        final HandWatcher offHand = new HandWatcher();
        int drops;
    }

    public static void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            try {
                tickPlayer(player);
            } catch (RuntimeException e) {
                // Um erro aqui nunca pode derrubar o servidor nem o tick dos outros jogadores.
                Constants.LOG.error("Falha no reabastecimento de {}", player.getGameProfile().name(), e);
            }
        }
        if (server.getTickCount() % CLEANUP_INTERVAL_TICKS == 0) {
            STATES.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
        }
    }

    private static void tickPlayer(ServerPlayer player) {
        PlayerState state = STATES.computeIfAbsent(player.getUUID(), id -> new PlayerState());

        // Q (soltar item) esvazia a mão de propósito: o contador de "itens soltos" sobe e ignoramos este tick.
        int drops = player.getStats().getValue(Stats.CUSTOM.get(Stats.DROP));
        boolean dropped = drops != state.drops;
        state.drops = drops;

        boolean active = !dropped
                && player.isAlive()
                && !player.isCreative()
                && !player.isSpectator()
                // Inventário de outro container aberto (baú, a própria shulker...) = jogador está mexendo nos itens.
                && player.containerMenu == player.inventoryMenu
                && player.containerMenu.getCarried().isEmpty();

        Inventory inventory = player.getInventory();
        ItemStack mainBefore = state.mainHand.last();
        ItemStack offBefore = state.offHand.last();
        ItemStack mainNow = player.getItemInHand(InteractionHand.MAIN_HAND);
        ItemStack offNow = player.getItemInHand(InteractionHand.OFF_HAND);

        ItemStack mainGone = state.mainHand.observe(inventory.getSelectedSlot(), mainNow, active);
        ItemStack offGone = state.offHand.observe(0, offNow, active);

        if (!mainGone.isEmpty() && !RefillLogic.movedToOtherHand(mainGone, offBefore, offNow)) {
            refillHand(player, InteractionHand.MAIN_HAND, mainGone);
        }
        if (!offGone.isEmpty() && !RefillLogic.movedToOtherHand(offGone, mainBefore, mainNow)) {
            refillHand(player, InteractionHand.OFF_HAND, offGone);
        }
    }

    private static void refillHand(ServerPlayer player, InteractionHand hand, ItemStack lastSeen) {
        // As shulkers estão nos slots normais do inventário; as mãos estão vazias, então não se misturam.
        ItemSource source = new PrioritizedItemSource(List.of(
                new PlayerShulkerSource(player.getInventory().getNonEquipmentItems())));
        ItemStack refill = RefillLogic.refill(lastSeen, source);
        if (!refill.isEmpty()) {
            // O servidor sincroniza o slot alterado com o cliente no próximo envio de inventário do jogador.
            player.setItemInHand(hand, refill);
        }
    }
}
