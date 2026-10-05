package io.github.leoascenci0.stashlink.bench;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntPredicate;

/**
 * O "mapa" das poções como contas puras (sem nada do jogo, para dar para testar): cada poção é um número (nó), e cada
 * receita do suporte é uma aresta "poção {@code from} + ingrediente {@code ingredient} → poção {@code to}". Duas
 * perguntas:
 * <ul>
 *   <li>{@link #reachable}: que poções existem a partir das garrafas de água (o que o painel pode listar);</li>
 *   <li>{@link #firstSteps}: para cada poção, qual é o <b>primeiro passo</b> do caminho mais curto partindo das
 *   garrafas que o jogador tem e usando só ingredientes que ele tem. Um passo por clique: depois de o suporte
 *   terminar, o próximo clique recalcula a partir do que ficou nele.</li>
 * </ul>
 */
public final class BrewPlanner {
    /** Uma receita do suporte de poções. */
    public record Edge(int from, int ingredient, int to) {
    }

    private BrewPlanner() {
    }

    private static Map<Integer, List<Edge>> byFrom(Collection<Edge> edges) {
        Map<Integer, List<Edge>> out = new HashMap<>();
        for (Edge e : edges) {
            out.computeIfAbsent(e.from(), k -> new ArrayList<>()).add(e);
        }
        return out;
    }

    /** Todas as poções que se alcançam a partir de {@code roots} (sem as próprias raízes, a não ser que se volte a elas). */
    public static Set<Integer> reachable(Collection<Edge> edges, Collection<Integer> roots) {
        Map<Integer, List<Edge>> next = byFrom(edges);
        Set<Integer> seen = new LinkedHashSet<>();
        Set<Integer> expanded = new HashSet<>(roots);
        Deque<Integer> queue = new ArrayDeque<>(roots);
        while (!queue.isEmpty()) {
            int u = queue.poll();
            for (Edge e : next.getOrDefault(u, List.of())) {
                seen.add(e.to());
                if (expanded.add(e.to())) {
                    queue.add(e.to());
                }
            }
        }
        return seen;
    }

    /**
     * Busca em largura a partir de todas as garrafas do jogador ao mesmo tempo ({@code sources}, na ordem de
     * preferência: as que já estão no suporte primeiro), andando só por arestas cujo ingrediente ele tem. Devolve, para
     * cada poção alcançável, a aresta do primeiro passo. Uma poção que o jogador já tem também ganha passo (fazer mais
     * dela), contanto que se chegue a ela por ao menos uma receita.
     */
    public static Map<Integer, Edge> firstSteps(Collection<Edge> edges, List<Integer> sources, IntPredicate haveIngredient) {
        Map<Integer, List<Edge>> next = byFrom(edges);
        Set<Integer> sourceSet = new HashSet<>(sources);
        Map<Integer, Edge> first = new HashMap<>();
        Set<Integer> queued = new LinkedHashSet<>(sources);
        Deque<Integer> queue = new ArrayDeque<>(queued);
        while (!queue.isEmpty()) {
            int u = queue.poll();
            for (Edge e : next.getOrDefault(u, List.of())) {
                if (!haveIngredient.test(e.ingredient())) {
                    continue;
                }
                if (!first.containsKey(e.to())) {
                    // Saindo de uma garrafa que o jogador tem, o primeiro passo é esta aresta; senão, o de quem chegou aqui.
                    first.put(e.to(), sourceSet.contains(u) ? e : first.get(u));
                }
                if (queued.add(e.to())) {
                    queue.add(e.to());
                }
            }
        }
        return first;
    }
}
