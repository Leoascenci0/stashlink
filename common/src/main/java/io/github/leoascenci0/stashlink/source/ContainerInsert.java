package io.github.leoascenci0.stashlink.source;

import io.github.leoascenci0.stashlink.slotlock.SlotLocks;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;

/**
 * Guardar itens num {@link Container} qualquer, em dois passos: primeiro <b>simular</b> ({@link #capacity}: quanto
 * cabe, sem mexer em nada) e depois <b>aplicar</b> ({@link #insert}). Quem chama só aplica o que a simulação
 * disse que cabe, então nunca sobra item "no ar" (perda) nem se cria item (dupe).
 */
public final class ContainerInsert {
    private ContainerInsert() {
    }

    /** O slot aceita o item? O container concorda e o slot não está reservado para outro item (Item 13). */
    private static boolean accepts(Container c, int slot, ItemStack stack) {
        // Shulker colocada nunca recebe shulker (nem nada que não cabe em item de container): o jogo não deixa pela
        // GUI, mas o canPlaceItem do bloco não confere; sem isto, uma reserva de slot levava shulker para dentro.
        if (c instanceof ShulkerBoxBlockEntity && !stack.getItem().canFitInsideContainerItems()) {
            return false;
        }
        return c.canPlaceItem(slot, stack) && SlotLocks.mayPlace(c, slot, stack);
    }

    /** Quantos itens iguais a {@code stack} ainda cabem no container. Não altera nada. */
    public static int capacity(Container c, ItemStack stack) {
        int room = 0;
        for (int slot = 0; slot < c.getContainerSize(); slot++) {
            if (!accepts(c, slot, stack)) {
                continue;
            }
            ItemStack cur = c.getItem(slot);
            int limit = Math.min(c.getMaxStackSize(stack), stack.getMaxStackSize());
            if (cur.isEmpty()) {
                room += limit;
            } else if (ItemStack.isSameItemSameComponents(cur, stack)) {
                room += Math.max(0, limit - cur.getCount());
            }
        }
        return room;
    }

    /**
     * Guarda o máximo possível e devolve o que <b>não</b> coube. Ordem: (0) slots reservados para este item,
     * (1) completar stacks iguais, (2) slots vazios. Nunca altera o stack recebido.
     */
    public static ItemStack insert(Container c, ItemStack in) {
        ItemStack rest = in.copy();
        boolean changed = false;
        for (int pass = 0; pass < 3 && !rest.isEmpty(); pass++) {
            for (int slot = 0; slot < c.getContainerSize() && !rest.isEmpty(); slot++) {
                ItemStack cur = c.getItem(slot);
                if (!accepts(c, slot, rest)) {
                    continue;
                }
                int limit = Math.min(c.getMaxStackSize(rest), rest.getMaxStackSize());
                boolean sameStack = !cur.isEmpty() && ItemStack.isSameItemSameComponents(cur, rest);
                if (pass == 0 && !SlotLocks.reservedFor(c, slot, rest)) {
                    continue;
                }
                if (pass <= 1 && sameStack) {
                    int move = Math.min(rest.getCount(), limit - cur.getCount());
                    if (move > 0) {
                        cur.grow(move);
                        rest.shrink(move);
                        changed = true;
                    }
                } else if (cur.isEmpty() && (pass == 0 || pass == 2)) {
                    int move = Math.min(rest.getCount(), limit);
                    c.setItem(slot, rest.copyWithCount(move));
                    rest.shrink(move);
                    changed = true;
                }
            }
        }
        if (changed) {
            c.setChanged();
        }
        return rest;
    }

    /** {@code outer} é {@code inner} ou (baú duplo) contém {@code inner} como uma das metades. */
    public static boolean isOrContains(Container outer, Container inner) {
        return outer == inner || (outer instanceof CompoundContainer compound && compound.contains(inner))
                || (inner instanceof CompoundContainer compound && compound.contains(outer));
    }

    /** Quantos itens iguais ao modelo (mesmo item e componentes) o container tem. */
    public static int count(Container c, ItemStack model) {
        int total = 0;
        for (int slot = 0; slot < c.getContainerSize(); slot++) {
            ItemStack stack = c.getItem(slot);
            if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, model)) {
                total += stack.getCount();
            }
        }
        return total;
    }
}
