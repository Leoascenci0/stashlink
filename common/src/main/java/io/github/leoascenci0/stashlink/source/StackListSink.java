package io.github.leoascenci0.stashlink.source;

import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * "Fonte" que só <b>recebe</b>: guarda itens numa lista de slots (a mochila do jogador). É o plano B da
 * devolução: se a origem está cheia ou longe, o item vai para o inventário, nunca para o chão.
 */
public final class StackListSink implements ItemSource {
    private final List<ItemStack> slots;

    public StackListSink(List<ItemStack> slots) {
        this.slots = slots;
    }

    @Override
    public int available(ItemStack item) {
        return 0;
    }

    @Override
    public List<ItemStack> take(ItemStack item, int n) {
        return List.of();
    }

    /** Completa stacks iguais e depois usa slots vazios. Devolve o que não coube; não altera o stack recebido. */
    @Override
    public ItemStack give(ItemStack stack) {
        ItemStack rest = stack.copy();
        for (int pass = 0; pass < 2 && !rest.isEmpty(); pass++) {
            for (int i = 0; i < slots.size() && !rest.isEmpty(); i++) {
                ItemStack cur = slots.get(i);
                if (pass == 0 && !cur.isEmpty() && ItemStack.isSameItemSameComponents(cur, rest)) {
                    int move = Math.min(rest.getCount(), cur.getMaxStackSize() - cur.getCount());
                    if (move > 0) {
                        cur.grow(move);
                        rest.shrink(move);
                    }
                } else if (pass == 1 && cur.isEmpty()) {
                    int move = Math.min(rest.getCount(), rest.getMaxStackSize());
                    slots.set(i, rest.copyWithCount(move));
                    rest.shrink(move);
                }
            }
        }
        return rest;
    }
}
