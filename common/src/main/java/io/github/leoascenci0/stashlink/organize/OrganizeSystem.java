package io.github.leoascenci0.stashlink.organize;

import io.github.leoascenci0.stashlink.Constants;
import io.github.leoascenci0.stashlink.quickstack.QuickStackReceive;
import io.github.leoascenci0.stashlink.slotlock.SlotLocks;
import io.github.leoascenci0.stashlink.source.ContainerInsert;
import io.github.leoascenci0.stashlink.source.ContainerSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Organizar o <b>sistema</b> de armazenamento (Item 20.2): consolida cada item no baú que "é a casa dele" e depois arruma
 * cada baú ({@link OrganizeLogic}). Tudo em duas fases, como a tecla N: primeiro um <b>plano</b>, depois <b>aplicar</b>.
 *
 * <p><b>O plano roda de verdade e desfaz.</b> Para a prévia usamos exatamente o mesmo código que vai mexer (inserção do
 * {@link ContainerInsert}, trava de slot, filtro "recebe com N"): o plano executa nos containers reais, anota o que
 * mudou em cada um e <b>restaura na hora</b>, no mesmo tick do servidor (ninguém enxerga o meio do caminho). Assim a
 * prévia nunca mostra algo que o aplicar não faria, e não existe uma segunda implementação para ficar diferente.
 *
 * <p><b>Aplicar</b> só grava se cada container ainda está exatamente como estava no plano (slot a slot); se alguém mexeu
 * num baú entre a prévia e o clique, nada é gravado. Como o conjunto "antes" e o conjunto "depois" têm a mesma soma de
 * cada item (conferida), e só gravamos sobre containers iguais ao "antes", nada duplica nem some. Desfazer é o mesmo
 * passo ao contrário (só vale se os containers ainda estão iguais ao "depois").
 *
 * <p><b>Casa do item (mesma regra da N):</b> baú que tem um slot reservado para ele (Item 13) vem primeiro; depois, o baú que
 * já tem mais dele (empate: o mais perto). A categoria do item não pode estar desligada pelo jogador (Item 17). Container com
 * "recebe com a N" desligado fica <b>fora de tudo</b>: nem recebe, nem é esvaziado, nem reordenado. Item que não tem casa
 * onde já está continua onde está. Slot reservado nunca é esvaziado.
 */
public final class OrganizeSystem {
    /** Teto de containers por plano (o resto fica de fora); o raio já limita, isto protege o tick. */
    public static final int MAX_CONTAINERS = 400;

    private OrganizeSystem() {
    }

    /** Uma movimentação do plano: {@code count} itens de {@code item} saem de {@code from} para {@code to}. */
    public record Move(ItemStack item, int count, Set<BlockPos> from, Set<BlockPos> to) {
    }

    /** O que um container tinha antes e terá depois (todos os slots; é o que Aplicar/Desfazer gravam). */
    public record Touched(Set<BlockPos> where, List<ItemStack> before, List<ItemStack> after) {
    }

    /** O plano: nada mexe enquanto ele é só um plano. {@code tidied} = quantos containers só serão reordenados. */
    public record Plan(List<Move> moves, List<Touched> touched, int tidied) {
        public boolean isEmpty() {
            return touched.isEmpty();
        }

        /** Soma de cada item antes e depois (têm de ser iguais). */
        boolean conserved() {
            OrganizeLogic.Totals before = new OrganizeLogic.Totals();
            OrganizeLogic.Totals after = new OrganizeLogic.Totals();
            for (Touched t : touched) {
                before.addAll(t.before());
                after.addAll(t.after());
            }
            return before.equals(after);
        }
    }

    private static final class Box {
        final ContainerSource.Entry entry;
        final Container c;
        final List<ItemStack> before;
        Boolean ok;
        boolean mutated;

        Box(ContainerSource.Entry entry) {
            this.entry = entry;
            this.c = entry.container();
            this.before = OrganizeLogic.snapshot(c);
        }

        /** A permissão (claims) só é perguntada quando algo vai mudar neste container, e uma vez só. */
        boolean allowed() {
            if (ok == null) {
                ok = !entry.where().isEmpty() && entry.allowed().getAsBoolean();
            }
            return ok;
        }
    }

    private record MoveKey(int model, int from, int to) {
    }

    /**
     * Monta o plano para estes containers (do mais perto ao mais longe). {@code excluded} diz quais itens nunca se
     * movem (categoria desligada, Item 17). Não deixa nenhum container alterado, nem se der erro.
     */
    public static Plan plan(List<ContainerSource.Entry> entries, Predicate<ItemStack> excluded) {
        List<Box> boxes = new ArrayList<>();
        for (ContainerSource.Entry entry : entries) {
            if (boxes.size() >= MAX_CONTAINERS) {
                break;
            }
            // Container com o botão "recebe com a N" desligado (Item 17) fica fora de tudo: nem recebe, nem é esvaziado, nem
            // reordenado. O jogador o marcou como "não mexa"; para arrumá-lo, o botão Organizar da tela dele.
            if (!QuickStackReceive.accepts(entry.container())) {
                continue;
            }
            boxes.add(new Box(entry));
        }
        try {
            List<ItemStack> models = new ArrayList<>();
            Map<MoveKey, Integer> moved = new LinkedHashMap<>();
            movePhase(boxes, excluded, models, moved);
            int tidied = tidyPhase(boxes);

            List<Touched> touched = new ArrayList<>();
            for (Box box : boxes) {
                if (box.mutated) {
                    touched.add(new Touched(box.entry.where(), box.before, OrganizeLogic.snapshot(box.c)));
                }
            }
            List<Move> moves = new ArrayList<>();
            for (Map.Entry<MoveKey, Integer> e : moved.entrySet()) {
                MoveKey key = e.getKey();
                moves.add(new Move(models.get(key.model()), e.getValue(), boxes.get(key.from()).entry.where(),
                        boxes.get(key.to()).entry.where()));
            }
            Plan plan = new Plan(moves, touched, tidied);
            if (!plan.conserved()) {
                // Cinto de segurança: a soma tem de bater. Se não bater, não oferece plano nenhum.
                Constants.LOG.error("Organizar: a soma de itens não bateu no plano; descartado");
                return new Plan(List.of(), List.of(), 0);
            }
            return plan;
        } finally {
            for (Box box : boxes) {
                if (box.mutated) {
                    OrganizeLogic.restore(box.c, box.before);
                }
            }
        }
    }

    private static void movePhase(List<Box> boxes, Predicate<ItemStack> excluded, List<ItemStack> models,
                                  Map<MoveKey, Integer> moved) {
        for (Box box : boxes) {
            for (int slot = 0; slot < box.c.getContainerSize(); slot++) {
                ItemStack stack = box.c.getItem(slot);
                if (!stack.isEmpty() && !contains(models, stack)) {
                    models.add(stack.copyWithCount(1));
                }
            }
        }
        int n = boxes.size();
        for (int m = 0; m < models.size(); m++) {
            ItemStack model = models.get(m);
            if (excluded.test(model)) {
                continue;
            }
            int[] held = new int[n];
            boolean[] reserves = new boolean[n];
            List<Integer> order = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                Container c = boxes.get(i).c;
                held[i] = ContainerInsert.count(c, model);
                reserves[i] = SlotLocks.reserves(c, model);
                if (QuickStackReceive.accepts(c) && (reserves[i] || held[i] > 0)) {
                    order.add(i);
                }
            }
            if (order.isEmpty()) {
                continue;
            }
            // Casa primeiro: quem reserva o item, depois quem tem mais (empate: o mais perto = índice menor).
            order.sort((a, b) -> {
                if (reserves[a] != reserves[b]) {
                    return reserves[a] ? -1 : 1;
                }
                if (held[a] != held[b]) {
                    return Integer.compare(held[b], held[a]);
                }
                return Integer.compare(a, b);
            });
            int[] rank = new int[n];
            java.util.Arrays.fill(rank, Integer.MAX_VALUE);
            for (int k = 0; k < order.size(); k++) {
                rank[order.get(k)] = k;
            }
            for (int i = 0; i < n; i++) {
                if (held[i] > 0 && rank[i] > 0) {
                    drain(boxes, i, model, m, order, Math.min(rank[i], order.size()), moved);
                }
            }
        }
    }

    /** Esvazia, do container {@code from}, o que der do item para os containers de {@code order[0..limit)}. */
    private static void drain(List<Box> boxes, int from, ItemStack model, int modelIndex, List<Integer> order, int limit,
                              Map<MoveKey, Integer> moved) {
        Box src = boxes.get(from);
        for (int slot = 0; slot < src.c.getContainerSize(); slot++) {
            ItemStack stack = src.c.getItem(slot);
            if (stack.isEmpty() || !ItemStack.isSameItemSameComponents(stack, model) || OrganizeLogic.frozen(src.c, slot)) {
                continue;                                   // slot reservado nunca é esvaziado (só recebe o item da reserva)
            }
            for (int k = 0; k < limit && !src.c.getItem(slot).isEmpty(); k++) {
                int to = order.get(k);
                if (to == from) {
                    continue;
                }
                Box dst = boxes.get(to);
                ItemStack current = src.c.getItem(slot);
                int fits = Math.min(current.getCount(), ContainerInsert.capacity(dst.c, current));
                if (fits <= 0) {
                    continue;
                }
                if (!src.allowed()) {
                    return;                                 // sem permissão para tirar deste container: pula ele todo
                }
                if (!dst.allowed()) {
                    continue;
                }
                src.mutated = true;
                dst.mutated = true;
                ItemStack rest = ContainerInsert.insert(dst.c, current.copyWithCount(fits));
                int inserted = fits - rest.getCount();
                if (inserted <= 0) {
                    continue;
                }
                src.c.removeItem(slot, inserted);
                moved.merge(new MoveKey(modelIndex, from, to), inserted, Integer::sum);
            }
        }
    }

    private static boolean contains(List<ItemStack> models, ItemStack stack) {
        for (ItemStack model : models) {
            if (ItemStack.isSameItemSameComponents(model, stack)) {
                return true;
            }
        }
        return false;
    }

    private static int tidyPhase(List<Box> boxes) {
        int tidied = 0;
        for (Box box : boxes) {
            List<ItemStack> layout = OrganizeLogic.layout(box.c);
            if (layout == null || !box.allowed()) {
                continue;
            }
            box.mutated = true;
            OrganizeLogic.restore(box.c, layout);
            tidied++;
        }
        return tidied;
    }

    // ------------------------------------------------------------------------------------------ aplicar / desfazer

    /** Aplica o plano se todos os containers ainda estão como estavam. {@code false} = nada foi gravado. */
    public static boolean apply(Plan plan, List<ContainerSource.Entry> entries) {
        return write(plan, entries, true);
    }

    /** Desfaz um plano aplicado, se todos os containers ainda estão como o plano os deixou. {@code false} = nada foi gravado. */
    public static boolean undo(Plan plan, List<ContainerSource.Entry> entries) {
        return write(plan, entries, false);
    }

    private static boolean write(Plan plan, List<ContainerSource.Entry> entries, boolean forward) {
        if (plan.isEmpty() || !plan.conserved()) {
            return false;
        }
        List<Container> targets = new ArrayList<>();
        for (Touched touched : plan.touched()) {
            ContainerSource.Entry found = null;
            for (ContainerSource.Entry entry : entries) {
                if (entry.where().equals(touched.where())) {
                    found = entry;
                    break;
                }
            }
            List<ItemStack> expected = forward ? touched.before() : touched.after();
            if (found == null || !found.allowed().getAsBoolean() || !OrganizeLogic.sameContents(found.container(), expected)) {
                return false;
            }
            targets.add(found.container());
        }
        for (int i = 0; i < targets.size(); i++) {
            Touched touched = plan.touched().get(i);
            OrganizeLogic.restore(targets.get(i), forward ? touched.after() : touched.before());
        }
        return true;
    }
}
