package io.github.leoascenci0.stashlink.source;

import io.github.leoascenci0.stashlink.compat.mc.McCompat;
import io.github.leoascenci0.stashlink.quickstack.QuickStackService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Monta a lista de fontes de um jogador, na ordem de prioridade do mod. Compartilhado por quem precisa de
 * itens "de reserva": o reabastecimento da mão (Item 4) e o pedido do Litematica (Item 7).
 */
public final class PlayerSources {
    private PlayerSources() {
    }

    public static ItemSource of(ServerPlayer player) {
        return operation(player).source();
    }

    /** Uma operação de puxar itens: as fontes e, depois, de onde saiu o que foi puxado (para devolver depois). */
    public static Operation operation(ServerPlayer player) {
        return new Operation(player, false);
    }

    /**
     * Como {@link #operation(ServerPlayer)}, mas ignorando containers que <b>outro</b> jogador está olhando (a mesma
     * regra da tecla N). Usado por tudo que tira itens de baús sem o jogador abri-los: botão do meio (Item 19),
     * pedido do Litematica (Item 7) e reabastecimento da mão (Item 4).
     */
    public static Operation operationSkippingOpened(ServerPlayer player) {
        return new Operation(player, true);
    }

    /** Valor calculado uma vez, só quando alguém pede; {@link #made()} diz se já foi pedido. */
    private static final class Memo<T> implements Supplier<T> {
        private final Supplier<T> factory;
        private T value;
        private boolean made;

        Memo(Supplier<T> factory) {
            this.factory = factory;
        }

        @Override
        public T get() {
            if (!made) {
                value = factory.get();
                made = true;
            }
            return value;
        }

        boolean made() {
            return made;
        }
    }

    public static final class Operation {
        private final ServerPlayer player;
        private final boolean skipOpenedByOthers;
        private final PlayerShulkerSource shulkers;
        private final Memo<NearbyContainers.Found> nearby;
        private final Memo<ContainerSource> placed;
        private final Memo<ContainerSource> storage;
        private final ItemSource source;

        private Operation(ServerPlayer player, boolean skipOpenedByOthers) {
            this.player = player;
            this.skipOpenedByOthers = skipOpenedByOthers;
            // As shulkers estão nos slots normais do inventário; as mãos ficam fora desta lista.
            this.shulkers = new PlayerShulkerSource(player.getInventory().getNonEquipmentItems());
            // Prioridade: shulkers no inventário, shulkers colocadas no raio, baús/barris no raio. As duas últimas
            // são preguiçosas: a varredura só acontece se as anteriores não bastarem.
            this.nearby = new Memo<>(() -> NearbyContainers.find(player));
            this.placed = new Memo<>(() -> new ContainerSource(guard(nearby.get().shulkers())));
            this.storage = new Memo<>(() -> new ContainerSource(guard(nearby.get().storage())));
            this.source = new PrioritizedItemSource(List.of(
                    shulkers,
                    new LazyItemSource(placed::get),
                    new LazyItemSource(storage::get)));
        }

        public ItemSource source() {
            return source;
        }

        /** Com {@code skipOpenedByOthers}, esconde das fontes o que outro jogador está olhando; senão devolve igual. */
        private List<ContainerSource.Entry> guard(List<ContainerSource.Entry> entries) {
            if (!skipOpenedByOthers) {
                return entries;
            }
            List<ContainerSource.Entry> out = new ArrayList<>();
            for (ContainerSource.Entry entry : entries) {
                out.add(new ContainerSource.Entry(entry.container(),
                        () -> !QuickStackService.openedByAnother(player, entry.container()) && entry.allowed().getAsBoolean(),
                        entry.where()));
            }
            return out;
        }

        /** De onde saiu o que esta operação já puxou. Vazia se nada saiu ainda. */
        public Origin origin() {
            Set<BlockPos> positions = new HashSet<>();
            if (placed.made()) {
                positions.addAll(placed.get().touchedPositions());
            }
            if (storage.made()) {
                positions.addAll(storage.get().touchedPositions());
            }
            return new Origin(shulkers.touched(), McCompat.dimensionOf(player), positions);
        }

        /**
         * Para onde devolver itens que vieram de {@code origin}: primeiro a origem (shulkers do inventário e
         * containers que <b>ainda</b> estão no raio, liberados e sem outro jogador com a GUI aberta), e por
         * último {@code overflow} (o inventário). O que nenhum aceitar volta como "sobra" de {@code give}.
         */
        public ItemSource returnTarget(Origin origin, ItemSource overflow) {
            List<ItemSource> targets = new ArrayList<>();
            if (origin.inventoryShulkers()) {
                targets.add(shulkers);
            }
            if (!origin.positions().isEmpty() && origin.dimension().equals(McCompat.dimensionOf(player))) {
                NearbyContainers.Found found = nearby.get();
                List<ContainerSource.Entry> candidates = new ArrayList<>(found.shulkers());
                candidates.addAll(found.storage());
                List<ContainerSource.Entry> usable = new ArrayList<>();
                for (ContainerSource.Entry entry : candidates) {
                    // Mesma regra da tecla N: não guardar em container que outro jogador está olhando.
                    usable.add(new ContainerSource.Entry(entry.container(),
                            () -> !QuickStackService.openedByAnother(player, entry.container())
                                    && entry.allowed().getAsBoolean(),
                            entry.where()));
                }
                targets.add(ContainerSource.returningTo(usable, origin.positions()));
            }
            targets.add(overflow);
            return new PrioritizedItemSource(targets);
        }
    }
}
