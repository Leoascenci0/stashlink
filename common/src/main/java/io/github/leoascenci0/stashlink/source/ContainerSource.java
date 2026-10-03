package io.github.leoascenci0.stashlink.source;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * Fonte: o conteúdo de containers "de verdade" (shulker colocada, baú, barril) — qualquer {@link Container}.
 *
 * <p>Cada container vem com uma checagem de permissão ({@code allowed}: claims, trava). Ela só é consultada
 * quando o container <b>tem</b> o item procurado (evita perguntar aos mods de proteção sobre 200 baús
 * irrelevantes) e o resultado é lembrado durante a vida deste objeto.
 *
 * <p>Uma instância vale para <b>uma operação</b> (um reabastecimento): {@link #give} só devolve aos containers
 * dos quais esta instância tirou algo, ou seja, "desfazer" volta para a origem.
 */
public final class ContainerSource implements ItemSource {
    /**
     * Um container e a regra de "o jogador pode mexer aqui?". {@code where} são as posições de bloco que o
     * formam (duas para baú duplo): é a "identidade" estável do container entre operações, já que o objeto
     * {@code Container} muda (o baú duplo vira outro {@code CompoundContainer} a cada varredura). Vazio =
     * desconhecido (nunca é reconhecido como origem).
     */
    public record Entry(Container container, BooleanSupplier allowed, Set<BlockPos> where) {
        public Entry(Container container, BooleanSupplier allowed) {
            this(container, allowed, Set.of());
        }
    }

    private static final class Node {
        final Container container;
        final BooleanSupplier check;
        final Set<BlockPos> where;
        Boolean ok;
        boolean touched;

        Node(Entry entry) {
            this.container = entry.container();
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

    public ContainerSource(List<Entry> entriesInPriorityOrder) {
        for (Entry entry : entriesInPriorityOrder) {
            nodes.add(new Node(entry));
        }
    }

    /**
     * Fonte para <b>devolver</b> itens a containers de origem conhecida (ver {@link Origin}): só entram os
     * containers de {@code entries} que ocupam alguma das posições de {@code origin}, já marcados como
     * "tocados". A permissão de cada um continua sendo consultada em {@link #give}.
     */
    public static ContainerSource returningTo(List<Entry> entries, Set<BlockPos> origin) {
        List<Entry> matching = new ArrayList<>();
        for (Entry entry : entries) {
            for (BlockPos pos : entry.where()) {
                if (origin.contains(pos)) {
                    matching.add(entry);
                    break;
                }
            }
        }
        ContainerSource source = new ContainerSource(matching);
        for (Node node : source.nodes) {
            node.touched = true;
        }
        return source;
    }

    /** Posições dos containers de onde esta instância já tirou itens (a "origem" do que foi puxado). */
    public Set<BlockPos> touchedPositions() {
        Set<BlockPos> out = new HashSet<>();
        for (Node node : nodes) {
            if (node.touched) {
                out.addAll(node.where);
            }
        }
        return out;
    }

    @Override
    public void forEachStack(java.util.function.Consumer<ItemStack> sink) {
        for (Node node : nodes) {
            Container c = node.container;
            boolean any = false;
            for (int slot = 0; slot < c.getContainerSize() && !any; slot++) {
                any = !c.getItem(slot).isEmpty();
            }
            if (!any || !node.allowed()) {
                continue;
            }
            for (int slot = 0; slot < c.getContainerSize(); slot++) {
                ItemStack stack = c.getItem(slot);
                if (!stack.isEmpty()) {
                    sink.accept(stack);
                }
            }
        }
    }

    @Override
    public int available(ItemStack item) {
        ItemStack model = item.copyWithCount(1);
        int total = 0;
        for (Node node : nodes) {
            int count = ContainerInsert.count(node.container, model);
            if (count > 0 && node.allowed()) {
                total += count;
            }
        }
        return total;
    }

    @Override
    public List<ItemStack> take(ItemStack item, int n) {
        ItemStack model = item.copyWithCount(1);
        List<ItemStack> taken = new ArrayList<>();
        int remaining = Math.max(n, 0);
        for (Node node : nodes) {
            if (remaining <= 0) {
                break;
            }
            if (ContainerInsert.count(node.container, model) <= 0 || !node.allowed()) {
                continue;
            }
            Container c = node.container;
            boolean changed = false;
            for (int slot = 0; slot < c.getContainerSize() && remaining > 0; slot++) {
                ItemStack stack = c.getItem(slot);
                if (stack.isEmpty() || !ItemStack.isSameItemSameComponents(stack, model)) {
                    continue;
                }
                int amount = Math.min(Math.min(stack.getCount(), remaining), model.getMaxStackSize());
                ItemStack out = c.removeItem(slot, amount);
                if (!out.isEmpty()) {
                    remaining -= out.getCount();
                    taken.add(out);
                    changed = true;
                }
            }
            if (changed) {
                node.touched = true;
                c.setChanged();
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
            if (node.touched && node.allowed()) {
                rest = ContainerInsert.insert(node.container, rest);
            }
        }
        return rest;
    }

}
