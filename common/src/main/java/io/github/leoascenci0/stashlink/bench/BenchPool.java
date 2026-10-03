package io.github.leoascenci0.stashlink.bench;

import io.github.leoascenci0.stashlink.compat.mc.BenchCompat;
import io.github.leoascenci0.stashlink.quickstack.QuickStackService;
import io.github.leoascenci0.stashlink.source.ContainerSource;
import io.github.leoascenci0.stashlink.source.ItemSource;
import io.github.leoascenci0.stashlink.source.LazyItemSource;
import io.github.leoascenci0.stashlink.source.NearbyContainers;
import io.github.leoascenci0.stashlink.source.PlayerShulkerSource;
import io.github.leoascenci0.stashlink.source.PrioritizedItemSource;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * O "armazenamento" que as bancadas (Item 16) enxergam como se fosse a mochila: shulkers no inventário, shulkers
 * colocadas, baús e barris dentro do raio do jogador ({@code PlayerPrefsStore.radius}, nunca um número fixo).
 * Mesmas regras de todo o mod: só container que o jogador poderia abrir (claims, trancado, baú de loot) e <b>nunca
 * um que outro jogador está olhando</b>. Fornalha, suporte de poções, funil, dispenser e dropper nunca entram:
 * {@link NearbyContainers} só aceita baú, barril e shulker.
 *
 * <p>Uma instância vale para uma operação (um clique): a varredura é preguiçosa e as permissões ficam lembradas.
 */
public final class BenchPool {
    /** Um tipo de item (com componentes) e quantos há no armazenamento. {@code item} tem sempre quantidade 1. */
    public record Stack(ItemStack item, int count) {
    }

    private final ItemSource source;

    private BenchPool(ServerPlayer player) {
        PlayerShulkerSource shulkers = new PlayerShulkerSource(player.getInventory().getNonEquipmentItems());
        // Baús entram sempre que a função está ligada: o liga/desliga da função é o consentimento (decisão do Eliel).
        Supplier<NearbyContainers.Found> nearby = memo(() -> NearbyContainers.find(player, true));
        this.source = new PrioritizedItemSource(List.of(
                shulkers,
                new LazyItemSource(() -> new ContainerSource(guard(player, nearby.get().shulkers()))),
                new LazyItemSource(() -> new ContainerSource(guard(player, nearby.get().storage())))));
    }

    public static BenchPool of(ServerPlayer player) {
        return new BenchPool(player);
    }

    /** Para tirar item (e devolver ao que foi tocado): o mesmo objeto serve a esta operação inteira. */
    public ItemSource source() {
        return source;
    }

    /** Container aberto por outro jogador fica de fora (mesma regra da tecla N). */
    private static List<ContainerSource.Entry> guard(ServerPlayer player, List<ContainerSource.Entry> entries) {
        List<ContainerSource.Entry> out = new ArrayList<>(entries.size());
        for (ContainerSource.Entry entry : entries) {
            out.add(new ContainerSource.Entry(entry.container(),
                    () -> !QuickStackService.openedByAnother(player, entry.container())
                            && entry.allowed().getAsBoolean(),
                    entry.where()));
        }
        return out;
    }

    /** Tudo o que há, somado por tipo de item (com componentes), em ordem estável: nome do item e depois quantidade. */
    public List<Stack> contents() {
        Map<BenchCompat.Key, Stack> sums = new LinkedHashMap<>();
        source.forEachStack(stack -> sums.merge(BenchCompat.keyOf(stack),
                new Stack(stack.copyWithCount(1), stack.getCount()),
                (a, b) -> new Stack(a.item(), a.count() + b.count())));
        List<Stack> list = new ArrayList<>(sums.values());
        list.sort(Comparator.<Stack, String>comparing(s -> BuiltInRegistries.ITEM.getKey(s.item().getItem()).toString())
                .thenComparing(Comparator.comparingInt(Stack::count).reversed()));
        return list;
    }

    /** Só os itens comuns (os que o livro de receitas aceita), por quantidade. */
    public Map<Item, Integer> plainCounts() {
        Map<Item, Integer> out = new LinkedHashMap<>();
        for (Stack s : contents()) {
            if (BenchCompat.usableForCrafting(s.item())) {
                out.merge(s.item().getItem(), s.count(), Integer::sum);
            }
        }
        return out;
    }

    private static <T> Supplier<T> memo(Supplier<T> factory) {
        Object[] box = new Object[1];
        boolean[] made = new boolean[1];
        return () -> {
            if (!made[0]) {
                box[0] = factory.get();
                made[0] = true;
            }
            @SuppressWarnings("unchecked") T value = (T) box[0];
            return value;
        };
    }
}
