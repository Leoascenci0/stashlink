package io.github.leoascenci0.stashlink.source;

import io.github.leoascenci0.stashlink.Constants;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntPredicate;
import java.util.function.Predicate;

/**
 * Fonte: baús e gavetas de outros mods ({@link ModStorage}, Item 26). Mesmas regras de {@link ContainerSource}: a
 * permissão (claims, outro jogador olhando) só é perguntada a quem <b>tem</b> o item e fica lembrada; {@link #give} só
 * devolve a quem esta instância tirou algo (desfazer volta à origem). Uma instância vale para uma operação.
 *
 * <p><b>Anti-dupe:</b> cada movimento é simulado antes e só conta o que o bloco disse que de fato entrou ou saiu. Bloco
 * que der erro (mod com defeito) conta como vazio para esta operação, sem levar junto o que outros blocos já deram.
 */
public final class ModStorageSource implements ItemSource {
    /** Um bloco de outro mod, a regra de "o jogador pode mexer aqui?" e as posições que o formam (a identidade). */
    public record Entry(ModStorage storage, BooleanSupplier allowed, Set<BlockPos> where) {
    }

    private static final class Node {
        final ModStorage storage;
        final BooleanSupplier check;
        final Set<BlockPos> where;
        Boolean ok;
        boolean touched;
        boolean broken;

        Node(Entry entry) {
            this.storage = entry.storage();
            this.check = entry.allowed();
            this.where = entry.where();
        }

        boolean allowed() {
            if (ok == null) {
                ok = check.getAsBoolean();
            }
            return ok;
        }
    }

    private final List<Node> nodes = new ArrayList<>();

    /** Blocos repetidos (mesmo objeto do loader em duas posições, ver {@link ModStorage#identity}) entram uma vez só. */
    public ModStorageSource(List<Entry> entriesInPriorityOrder) {
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Entry entry : entriesInPriorityOrder) {
            if (seen.add(entry.storage().identity())) {
                nodes.add(new Node(entry));
            }
        }
    }

    /** Como {@link ContainerSource#returningTo}: só os blocos que ocupam alguma posição de {@code origin}. */
    public static ModStorageSource returningTo(List<Entry> entries, Set<BlockPos> origin) {
        List<Entry> matching = new ArrayList<>();
        for (Entry entry : entries) {
            if (!Collections.disjoint(entry.where(), origin)) {
                matching.add(entry);
            }
        }
        ModStorageSource source = new ModStorageSource(matching);
        for (Node node : source.nodes) {
            node.touched = true;
        }
        return source;
    }

    /** Posições dos blocos de onde esta instância já tirou itens. */
    public Set<BlockPos> touchedPositions() {
        Set<BlockPos> out = new HashSet<>();
        for (Node node : nodes) {
            if (node.touched) {
                out.addAll(node.where);
            }
        }
        return out;
    }

    /** Soma que trava no máximo em vez de "dar a volta" (gaveta criativa guarda bilhões). */
    public static int saturated(long n) {
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, n));
    }

    private static void broke(Node node, RuntimeException e) {
        if (!node.broken) {
            Constants.LOG.warn("Armazenamento de outro mod em {} falhou; ignorado nesta operação", node.where, e);
        }
        node.broken = true;
    }

    private static long count(Node node, ItemStack model) {
        if (node.broken) {
            return 0;
        }
        try {
            return node.storage.count(model);
        } catch (RuntimeException e) {
            broke(node, e);
            return 0;
        }
    }

    @Override
    public void forEachStack(Consumer<ItemStack> sink) {
        for (Node node : nodes) {
            List<ItemStack> here = new ArrayList<>();
            try {
                node.storage.forEach((model, amount) -> {
                    if (!model.isEmpty() && amount > 0) {
                        // A quantidade pode passar do stack máximo (gaveta): só serve para contar, nunca vai para um slot.
                        here.add(model.copyWithCount(saturated(amount)));
                    }
                });
            } catch (RuntimeException e) {
                broke(node, e);
                continue;
            }
            if (!here.isEmpty() && node.allowed()) {
                here.forEach(sink);
            }
        }
    }

    @Override
    public int available(ItemStack item) {
        ItemStack model = item.copyWithCount(1);
        long total = 0;
        for (Node node : nodes) {
            long count = count(node, model);
            if (count > 0 && node.allowed()) {
                total += count;
            }
        }
        return saturated(total);
    }

    @Override
    public List<ItemStack> take(ItemStack item, int n) {
        ItemStack model = item.copyWithCount(1);
        int max = Math.max(1, model.getMaxStackSize());
        List<ItemStack> taken = new ArrayList<>();
        long remaining = Math.max(n, 0);
        for (Node node : nodes) {
            if (remaining <= 0) {
                break;
            }
            if (count(node, model) <= 0 || !node.allowed()) {
                continue;
            }
            long got;
            try {
                long can = node.storage.extract(model, remaining, true);
                got = can > 0 ? node.storage.extract(model, Math.min(can, remaining), false) : 0;
            } catch (RuntimeException e) {
                // A transação do loader desfaz sozinha o que não foi confirmado: nada saiu deste bloco.
                broke(node, e);
                continue;
            }
            if (got <= 0) {
                continue;
            }
            node.touched = true;
            remaining -= got;
            while (got > 0) {
                int part = (int) Math.min(got, max);
                taken.add(model.copyWithCount(part));
                got -= part;
            }
        }
        return taken;
    }

    @Override
    public ItemStack give(ItemStack stack) {
        ItemStack rest = stack.copy();
        for (Node node : nodes) {
            if (rest.isEmpty()) {
                break;
            }
            if (!node.touched || node.broken || !node.allowed()) {
                continue;
            }
            rest.shrink(saturated(insert(node, rest)));
        }
        return rest;
    }

    /** Guarda {@code stack} no bloco (simula antes) e devolve quanto entrou; nunca altera o stack. */
    static long insert(Node node, ItemStack stack) {
        ItemStack model = stack.copyWithCount(1);
        try {
            long fits = node.storage.insert(model, stack.getCount(), true);
            return fits > 0 ? Math.min(stack.getCount(), node.storage.insert(model, Math.min(fits, stack.getCount()), false)) : 0;
        } catch (RuntimeException e) {
            broke(node, e);
            return 0;
        }
    }

    /**
     * A tecla N num bloco de outro mod: para cada slot do jogador cujo item o bloco <b>já contém</b>, guarda o que couber
     * (simulado antes) e tira do jogador só o que entrou. A permissão só é perguntada se houver algo a guardar.
     *
     * @return quantos itens entraram
     */
    public static int stashInto(Entry entry, List<ItemStack> slots, IntPredicate skip, Predicate<ItemStack> excluded) {
        Node node = new Node(entry);
        int moved = 0;
        boolean asked = false;
        for (int i = 0; i < slots.size() && !node.broken; i++) {
            ItemStack stack = slots.get(i);
            if (skip.test(i) || stack.isEmpty() || excluded.test(stack) || count(node, stack.copyWithCount(1)) <= 0) {
                continue;
            }
            if (!asked) {
                asked = true;
                if (!node.allowed()) {
                    return 0;
                }
            }
            int in = saturated(insert(node, stack));
            stack.shrink(in);
            if (stack.isEmpty()) {
                slots.set(i, ItemStack.EMPTY);
            }
            moved += in;
        }
        return moved;
    }
}
