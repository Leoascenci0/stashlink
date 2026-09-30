package io.github.leoascenci0.stashlink.source;

import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Monta a lista de fontes de um jogador, na ordem de prioridade do mod. Compartilhado por quem precisa de
 * itens "de reserva": o reabastecimento da mão (Item 4) e o pedido do Litematica (Item 7).
 */
public final class PlayerSources {
    private PlayerSources() {
    }

    public static ItemSource of(ServerPlayer player) {
        // As shulkers estão nos slots normais do inventário; as mãos ficam fora desta lista.
        PlayerShulkerSource shulkers = new PlayerShulkerSource(player.getInventory().getNonEquipmentItems());
        // Prioridade: shulkers no inventário, shulkers colocadas no raio, baús/barris no raio. As duas últimas
        // são preguiçosas: a varredura só acontece se as anteriores não bastarem.
        Supplier<NearbyContainers.Found> nearby = memoize(() -> NearbyContainers.find(player));
        return new PrioritizedItemSource(List.of(
                shulkers,
                new LazyItemSource(() -> new ContainerSource(nearby.get().shulkers())),
                new LazyItemSource(() -> new ContainerSource(nearby.get().storage()))));
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
}
