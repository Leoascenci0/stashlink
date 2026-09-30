package io.github.leoascenci0.stashlink.source;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
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
    /** Um container e a regra de "o jogador pode mexer aqui?". */
    public record Entry(Container container, BooleanSupplier allowed) {
    }

    private static final class Node {
        final Container container;
        final BooleanSupplier check;
        Boolean ok;
        boolean touched;

        Node(Entry entry) {
            this.container = entry.container();
            this.check = entry.allowed();
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
            if (node.touched) {
                rest = ContainerInsert.insert(node.container, rest);
            }
        }
        return rest;
    }

}
