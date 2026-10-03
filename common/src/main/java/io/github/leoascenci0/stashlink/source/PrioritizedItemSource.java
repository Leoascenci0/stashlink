package io.github.leoascenci0.stashlink.source;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Junta várias fontes numa só, respeitando a ordem de prioridade (a primeira da lista é a preferida).
 * A ordem vem de quem monta a lista (a configuração do jogador, no futuro).
 *
 * <ul>
 *   <li>{@code available} = soma de todas as fontes.</li>
 *   <li>{@code take} = tira da primeira fonte o que ela tiver, completa com a segunda, e assim por diante,
 *       até chegar em {@code n} ou acabarem as fontes.</li>
 * </ul>
 *
 * <p><b>Consistência:</b> uma fonte que der erro é tratada como "sem nada" — o que já saiu de fontes
 * anteriores é devolvido a quem chamou (nunca some item) e as fontes seguintes ainda são tentadas.
 * Cada fonte é responsável por ser atômica dentro de si (como {@code ShulkerStorage}).
 */
public final class PrioritizedItemSource implements ItemSource {
    private final List<ItemSource> sources;

    public PrioritizedItemSource(List<ItemSource> sourcesInPriorityOrder) {
        this.sources = List.copyOf(sourcesInPriorityOrder);
    }

    @Override
    public int available(ItemStack item) {
        int total = 0;
        for (ItemSource source : sources) {
            try {
                total += source.available(item);
            } catch (RuntimeException e) {
                // fonte com problema conta como vazia
            }
        }
        return total;
    }

    @Override
    public List<ItemStack> take(ItemStack item, int n) {
        List<ItemStack> taken = new ArrayList<>();
        int remaining = Math.max(n, 0);
        for (ItemSource source : sources) {
            if (remaining <= 0) {
                break;
            }
            try {
                List<ItemStack> out = source.take(item, remaining);
                remaining -= ItemSource.sum(out);
                taken.addAll(out);
            } catch (RuntimeException e) {
                // fonte com problema: segue para a próxima, sem perder o que já foi tirado
            }
        }
        return taken;
    }

    @Override
    public void forEachStack(java.util.function.Consumer<ItemStack> sink) {
        for (ItemSource source : sources) {
            try {
                source.forEachStack(sink);
            } catch (RuntimeException e) {
                // fonte com problema conta como vazia
            }
        }
    }

    /** Devolve o stack às fontes, na ordem de prioridade; retorna o que nenhuma quis. */
    @Override
    public ItemStack give(ItemStack stack) {
        ItemStack rest = stack.copy();
        for (ItemSource source : sources) {
            if (rest.isEmpty()) {
                break;
            }
            try {
                rest = source.give(rest);
            } catch (RuntimeException e) {
                // fonte com problema não aceita nada; o resto vai para a próxima
            }
        }
        return rest;
    }
}
