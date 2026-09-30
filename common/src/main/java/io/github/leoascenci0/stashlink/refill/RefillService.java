package io.github.leoascenci0.stashlink.refill;

import io.github.leoascenci0.stashlink.Constants;
import io.github.leoascenci0.stashlink.source.ContainerSource;
import io.github.leoascenci0.stashlink.source.LazyItemSource;
import io.github.leoascenci0.stashlink.source.NearbyContainers;
import io.github.leoascenci0.stashlink.source.PlayerShulkerSource;
import io.github.leoascenci0.stashlink.source.PrioritizedItemSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

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
        /** Quem é o dono deste estado. Relogar ou renascer cria outro objeto de jogador: o estado velho não vale. */
        final ServerPlayer owner;
        final HandWatcher mainHand = new HandWatcher();
        final HandWatcher offHand = new HandWatcher();
        int drops;

        PlayerState(ServerPlayer owner) {
            this.owner = owner;
            this.drops = owner.getStats().getValue(Stats.CUSTOM.get(Stats.DROP));
        }
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

    /** Chamado pelo mixin (início do tick do jogador) e pelo fim do tick do servidor; é idempotente. */
    public static void tickPlayer(ServerPlayer player) {
        PlayerState state = STATES.get(player.getUUID());
        if (state == null || state.owner != player) {
            // Primeiro tick, relogin ou respawn: recomeça do zero, sem "lembrar" da mão da sessão anterior.
            state = new PlayerState(player);
            STATES.put(player.getUUID(), state);
        }

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

    private static <T> Supplier<T> memoize(Supplier<T> factory) {
        List<T> box = new ArrayList<>(1);
        return () -> {
            if (box.isEmpty()) {
                box.add(factory.get());
            }
            return box.get(0);
        };
    }

    private static void refillHand(ServerPlayer player, InteractionHand hand, ItemStack lastSeen) {
        // As shulkers estão nos slots normais do inventário; as mãos ficam fora desta lista.
        PlayerShulkerSource shulkers = new PlayerShulkerSource(player.getInventory().getNonEquipmentItems());
        // Prioridade: shulkers no inventário, shulkers colocadas no raio, baús/barris no raio. As duas últimas
        // são preguiçosas: a varredura só acontece se as anteriores não bastarem.
        Supplier<NearbyContainers.Found> nearby = memoize(() -> NearbyContainers.find(player));
        PrioritizedItemSource sources = new PrioritizedItemSource(List.of(
                shulkers,
                new LazyItemSource(() -> new ContainerSource(nearby.get().shulkers())),
                new LazyItemSource(() -> new ContainerSource(nearby.get().storage()))));
        ItemStack refill = RefillLogic.refill(lastSeen, sources);
        if (refill.isEmpty()) {
            return;
        }
        // Mão com o recipiente vazio (balde, tigela, garrafa): guarda no inventário para abrir espaço.
        ItemStack leftover = player.getItemInHand(hand);
        if (!leftover.isEmpty()) {
            ItemStack toStore = leftover.copy();
            if (!player.getInventory().add(toStore) || !toStore.isEmpty()) {
                // Sem lugar: desfaz, nada some (o recipiente continua na mão, o estoque volta à shulker).
                // Se o inventário guardou só parte do stack, a mão fica só com o que sobrou (senão duplicaria).
                leftover.setCount(toStore.getCount());
                ItemStack rest = sources.give(refill);
                if (!rest.isEmpty()) {
                    player.getInventory().placeItemBackInInventory(rest);
                }
                return;
            }
        }
        // O servidor sincroniza o slot alterado com o cliente no próximo envio de inventário do jogador.
        player.setItemInHand(hand, refill);
    }
}
