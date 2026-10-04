package io.github.leoascenci0.stashlink.bench;

import io.github.leoascenci0.stashlink.compat.mc.BenchCompat;
import io.github.leoascenci0.stashlink.network.BenchPoolSync;

import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * A ordem única das listas do painel (todas as estações e todas as abas): primeiro o que está disponível, depois o
 * que falta material (vermelho); dentro de cada grupo, pelo nome; e, a nomes iguais, pelo id do item, pelo id da
 * entrada, pela quantidade (maior primeiro) e pelos componentes. É total e estável: a mesma lista, em qualquer ordem
 * de entrada, sai sempre igual.
 *
 * <p>O servidor não conhece o idioma do jogador, então ordena (e corta no teto de {@link BenchPoolSync#MAX_ENTRIES})
 * pelo id do registro, que é igual em qualquer máquina; o cliente reordena o que recebeu pelo <b>nome localizado</b>.
 * É o mesmo comparador nos dois lados, só muda a função de nome.
 */
public final class BenchOrder {
    private BenchOrder() {
    }

    /** O comparador, com {@code name} dando o texto de ordenação de cada entrada (já em minúsculas, se quiser ignorar caixa). */
    public static Comparator<BenchPoolSync.Entry> comparator(Function<BenchPoolSync.Entry, String> name) {
        return Comparator.<BenchPoolSync.Entry, Boolean>comparing(BenchPoolSync.Entry::missing)
                .thenComparing(name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(entry -> BenchCompat.itemId(entry.item()))
                .thenComparingInt(BenchPoolSync.Entry::id)
                .thenComparing(Comparator.comparingInt(BenchPoolSync.Entry::count).reversed())
                .thenComparingInt(entry -> BenchCompat.componentsHash(entry.item()));
    }

    /** A ordem do servidor: o nome é o id do registro. */
    public static Comparator<BenchPoolSync.Entry> forServer() {
        return comparator(entry -> BenchCompat.itemId(entry.item()));
    }

    /** Ordena e só então corta no teto: o que fica de fora é sempre o mesmo, não depende da ordem de varredura. */
    public static List<BenchPoolSync.Entry> sortedAndCapped(List<BenchPoolSync.Entry> entries) {
        List<BenchPoolSync.Entry> sorted = new java.util.ArrayList<>(entries);
        sorted.sort(forServer());
        return sorted.size() > BenchPoolSync.MAX_ENTRIES
                ? List.copyOf(sorted.subList(0, BenchPoolSync.MAX_ENTRIES)) : sorted;
    }
}
