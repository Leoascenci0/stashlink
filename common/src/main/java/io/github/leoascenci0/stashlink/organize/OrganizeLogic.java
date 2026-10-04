package io.github.leoascenci0.stashlink.organize;

import io.github.leoascenci0.stashlink.compat.mc.OrganizeCompat;
import io.github.leoascenci0.stashlink.quickstack.ItemCategory;
import io.github.leoascenci0.stashlink.slotlock.SlotLocks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Arrumar <b>um</b> container (Item 20.1): junta os stacks parciais e ordena como o inventário criativo. Sem rede nem
 * jogador, só {@link Container}: dá para testar de forma exaustiva.
 *
 * <p>Garantias: (1) a soma de cada item (mesmo item <i>e</i> componentes) não muda — conferida antes de gravar; (2) um
 * slot reservado para um item (Item 13) <b>nunca perde</b> o que tem nem recebe outro item, mas é completado com o item dele antes de o resto ser espalhado; (3) se algum slot não aceitar o que
 * o plano pôs nele ({@code canPlaceItem}), nada é gravado; (4) é idempotente: arrumar um container já arrumado não muda nada.
 */
public final class OrganizeLogic {
    private OrganizeLogic() {
    }

    /** O slot está reservado para algum item? Então a arrumação o deixa como está. */
    public static boolean frozen(Container c, int slot) {
        return SlotLocks.lockedItem(c, slot) != null;
    }

    /**
     * Ordem de exibição: a do <b>inventário criativo</b> do jogo (Blocos, Coloridos, Natural, Funcional, Redstone,
     * Ferramentas, Combate, Comida, Ingredientes... e os itens de mods nas abas deles), que o jogador já conhece. O que não
     * está em nenhuma aba vem depois, por categoria e pelo id do item (igual em qualquer idioma). Empate (mesmo item
     * com componentes diferentes) mantém a ordem em que já estavam, o que torna a arrumação idempotente.
     */
    static final Comparator<ItemStack> ORDER = Comparator
            .<ItemStack>comparingInt(stack -> OrganizeCompat.rank(stack.getItem()))
            .thenComparingInt(stack -> {
                ItemCategory category = ItemCategory.of(stack);
                return category == null ? 0 : category.ordinal() + 1;
            })
            .thenComparing(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath());

    /** Cópia do que há em cada slot (para desfazer e para conferir "nada mudou"). */
    public static List<ItemStack> snapshot(Container c) {
        List<ItemStack> out = new ArrayList<>(c.getContainerSize());
        for (int slot = 0; slot < c.getContainerSize(); slot++) {
            out.add(c.getItem(slot).copy());
        }
        return out;
    }

    /** O container tem exatamente este conteúdo, slot a slot? */
    public static boolean sameContents(Container c, List<ItemStack> snapshot) {
        if (snapshot.size() != c.getContainerSize()) {
            return false;
        }
        for (int slot = 0; slot < snapshot.size(); slot++) {
            if (!same(c.getItem(slot), snapshot.get(slot))) {
                return false;
            }
        }
        return true;
    }

    /** Põe de volta o conteúdo de um {@link #snapshot}. */
    public static void restore(Container c, List<ItemStack> snapshot) {
        for (int slot = 0; slot < snapshot.size() && slot < c.getContainerSize(); slot++) {
            if (!same(c.getItem(slot), snapshot.get(slot))) {
                c.setItem(slot, snapshot.get(slot).copy());
            }
        }
        c.setChanged();
    }

    static boolean same(ItemStack a, ItemStack b) {
        return ItemStack.matches(a, b);
    }

    /**
     * O novo conteúdo do container depois de arrumado, ou {@code null} se não há o que mudar (ou se não dá para
     * arrumar com segurança). Não altera nada.
     */
    public static List<ItemStack> layout(Container c) {
        int size = c.getContainerSize();
        List<ItemStack> before = snapshot(c);
        List<Integer> free = new ArrayList<>();
        List<ItemStack> pool = new ArrayList<>();
        for (int slot = 0; slot < size; slot++) {
            if (frozen(c, slot)) {
                continue;
            }
            free.add(slot);
            ItemStack stack = before.get(slot);
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack into = null;
            for (ItemStack entry : pool) {
                if (ItemStack.isSameItemSameComponents(entry, stack)) {
                    into = entry;
                    break;
                }
            }
            if (into == null) {
                pool.add(stack.copy());
            } else {
                into.grow(stack.getCount());
            }
        }
        // Slot reservado (Item 13) é a casa do item: antes de espalhar o resto, completa-o com o item dele (o mesmo que a N
        // faz). Ele nunca perde o que tem nem recebe outro item.
        java.util.Map<Integer, ItemStack> reserved = new java.util.HashMap<>();
        for (int slot = 0; slot < size; slot++) {
            net.minecraft.world.item.Item locked = SlotLocks.lockedItem(c, slot);
            if (locked == null) {
                continue;
            }
            ItemStack current = before.get(slot);
            if (!current.isEmpty() && !current.is(locked)) {
                continue;
            }
            ItemStack source = null;
            for (ItemStack entry : pool) {
                if (entry.is(locked) && (current.isEmpty() || ItemStack.isSameItemSameComponents(entry, current))) {
                    source = entry;
                    break;
                }
            }
            if (source == null) {
                continue;
            }
            ItemStack model = current.isEmpty() ? source : current;
            int limit = Math.max(1, Math.min(c.getMaxStackSize(model), model.getMaxStackSize()));
            int put = Math.min(limit - current.getCount(), source.getCount());
            if (put <= 0) {
                continue;
            }
            ItemStack placed = model.copyWithCount(current.getCount() + put);
            if (!c.canPlaceItem(slot, placed)) {
                continue;
            }
            reserved.put(slot, placed);
            source.shrink(put);
        }
        pool.removeIf(ItemStack::isEmpty);

        // O que não coube na pilha reservada (ex.: ela já está em 64) fica logo depois dela, nos primeiros slots livres
        // seguintes, em vez de ir parar no meio dos outros itens. Só se couber inteiro; senão segue para a ordem normal.
        List<Integer> open = new ArrayList<>(free);
        java.util.Map<Integer, ItemStack> beside = new java.util.HashMap<>();
        for (int slot = 0; slot < size; slot++) {
            net.minecraft.world.item.Item locked = SlotLocks.lockedItem(c, slot);
            if (locked == null) {
                continue;
            }
            for (java.util.Iterator<ItemStack> it = pool.iterator(); it.hasNext(); ) {
                ItemStack entry = it.next();
                if (!entry.is(locked)) {
                    continue;
                }
                int limit = Math.max(1, Math.min(c.getMaxStackSize(entry), entry.getMaxStackSize()));
                int stacks = (entry.getCount() + limit - 1) / limit;
                List<Integer> slots = new ArrayList<>();
                for (int candidate : open) {
                    if (candidate > slot && slots.size() < stacks) {
                        slots.add(candidate);
                    }
                }
                if (slots.size() < stacks) {
                    continue;
                }
                java.util.Map<Integer, ItemStack> put = new java.util.HashMap<>();
                int remaining = entry.getCount();
                boolean accepted = true;
                for (int target : slots) {
                    ItemStack placed = entry.copyWithCount(Math.min(remaining, limit));
                    if (!c.canPlaceItem(target, placed)) {
                        accepted = false;
                        break;
                    }
                    put.put(target, placed);
                    remaining -= placed.getCount();
                }
                if (accepted) {
                    beside.putAll(put);
                    open.removeAll(slots);
                    it.remove();
                }
            }
        }
        pool.sort(ORDER);                                   // estável: empate fica na ordem dos slots

        List<ItemStack> after = new ArrayList<>(before);
        reserved.forEach(after::set);
        beside.forEach(after::set);
        int next = 0;
        for (ItemStack entry : pool) {
            int limit = Math.max(1, Math.min(c.getMaxStackSize(entry), entry.getMaxStackSize()));
            int remaining = entry.getCount();
            while (remaining > 0) {
                if (next >= open.size()) {
                    return null;                            // não deveria acontecer (juntar só reduz stacks)
                }
                int slot = open.get(next++);
                int put = Math.min(remaining, limit);
                ItemStack placed = entry.copyWithCount(put);
                if (!c.canPlaceItem(slot, placed)) {
                    return null;                            // o container recusa: não mexe em nada
                }
                after.set(slot, placed);
                remaining -= put;
            }
        }
        for (; next < open.size(); next++) {
            after.set(open.get(next), ItemStack.EMPTY);
        }
        if (!sameTotals(before, after)) {
            return null;                                    // cinto de segurança: a soma tem de bater
        }
        for (int slot = 0; slot < size; slot++) {
            if (!same(before.get(slot), after.get(slot))) {
                return after;
            }
        }
        return null;
    }

    /** Arruma o container. {@code true} se mudou alguma coisa. */
    public static boolean tidy(Container c) {
        List<ItemStack> after = layout(c);
        if (after == null) {
            return false;
        }
        restore(c, after);
        return true;
    }

    /** A soma de cada item (item + componentes) é a mesma nas duas listas? */
    public static boolean sameTotals(List<ItemStack> a, List<ItemStack> b) {
        return totals(a).equals(totals(b));
    }

    /** Soma por item+componentes, numa lista comparável (modelo com contagem 1 -> total). */
    static Totals totals(List<ItemStack> stacks) {
        Totals totals = new Totals();
        for (ItemStack stack : stacks) {
            totals.add(stack);
        }
        return totals;
    }

    /** Totais por tipo de item. {@code equals} compara o conteúdo (item+componentes -> soma). */
    public static final class Totals {
        private final List<ItemStack> models = new ArrayList<>();
        private final List<Long> counts = new ArrayList<>();

        public void add(ItemStack stack) {
            if (stack.isEmpty()) {
                return;
            }
            for (int i = 0; i < models.size(); i++) {
                if (ItemStack.isSameItemSameComponents(models.get(i), stack)) {
                    counts.set(i, counts.get(i) + stack.getCount());
                    return;
                }
            }
            models.add(stack.copyWithCount(1));
            counts.add((long) stack.getCount());
        }

        public void addAll(List<ItemStack> stacks) {
            for (ItemStack stack : stacks) {
                add(stack);
            }
        }

        public long total(ItemStack model) {
            for (int i = 0; i < models.size(); i++) {
                if (ItemStack.isSameItemSameComponents(models.get(i), model)) {
                    return counts.get(i);
                }
            }
            return 0;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Totals other) || other.models.size() != models.size()) {
                return false;
            }
            for (int i = 0; i < models.size(); i++) {
                if (other.total(models.get(i)) != counts.get(i)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int hashCode() {
            return models.size();
        }
    }
}
