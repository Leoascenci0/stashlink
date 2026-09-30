package io.github.leoascenci0.stashlink.lootall;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.IntPredicate;

/**
 * A regra da tecla W, sem rede nem jogador (por isso dá para testar de forma exaustiva): move itens de um
 * container para os slots do jogador, só o que cabe.
 *
 * <p>Sem perda nem duplicação, em três passos por slot do container: (1) <b>simula</b> quanto cabe no
 * inventário; (2) <b>tira</b> exatamente essa quantidade do container; (3) <b>coloca</b> no inventário. Se o passo
 * 3 devolver sobra (não deveria, a simulação garantiu), a sobra volta para o slot de origem.
 *
 * <p>Onde entra: primeiro completa stacks iguais já existentes, depois usa slots vazios preferindo a mochila
 * (9-35) e deixando a hotbar (0-8) por último, para não bagunçar as ferramentas. Slots travados nunca recebem.
 */
public final class LootAllLogic {
    private static final int HOTBAR_SIZE = 9;

    private LootAllLogic() {
    }

    /** Quantos itens foram trazidos e quantos ficaram no container (não couberam ou não podem ser tirados). */
    public record Result(int itemsMoved, int itemsLeft) {
    }

    /**
     * @param source container de onde puxar
     * @param taker  quem está tirando (o inventário do jogador); só é passado a {@code canTakeItem}
     * @param slots  slots do jogador, 0-8 hotbar e 9-35 mochila (lista modificável; é alterada)
     * @param locked slots que não podem receber nada
     */
    public static Result pull(Container source, Container taker, List<ItemStack> slots, IntPredicate locked) {
        int moved = 0;
        for (int s = 0; s < source.getContainerSize(); s++) {
            ItemStack stack = source.getItem(s);
            if (stack.isEmpty() || !source.canTakeItem(taker, s, stack)) {
                continue;
            }
            int fits = Math.min(stack.getCount(), room(slots, stack, locked));
            if (fits <= 0) {
                continue;
            }
            ItemStack taken = source.removeItem(s, fits);
            ItemStack rest = insert(slots, taken, locked);
            if (!rest.isEmpty()) {
                giveBack(source, s, rest);
            }
            moved += taken.getCount() - rest.getCount();
        }
        int left = 0;
        for (int s = 0; s < source.getContainerSize(); s++) {
            left += source.getItem(s).getCount();
        }
        return new Result(moved, left);
    }

    /** Quantos itens iguais a {@code stack} ainda cabem nos slots permitidos. Não altera nada. */
    static int room(List<ItemStack> slots, ItemStack stack, IntPredicate locked) {
        int limit = stack.getMaxStackSize();
        int room = 0;
        for (int i = 0; i < slots.size(); i++) {
            if (locked.test(i)) {
                continue;
            }
            ItemStack cur = slots.get(i);
            if (cur.isEmpty()) {
                room += limit;
            } else if (ItemStack.isSameItemSameComponents(cur, stack)) {
                room += Math.max(0, limit - cur.getCount());
            }
        }
        return room;
    }

    /** Coloca o máximo possível e devolve o que não coube. Nunca altera o stack recebido. */
    static ItemStack insert(List<ItemStack> slots, ItemStack in, IntPredicate locked) {
        ItemStack rest = in.copy();
        int limit = rest.getMaxStackSize();
        // Passo 1: completar stacks iguais (em qualquer lugar).
        for (int i = 0; i < slots.size() && !rest.isEmpty(); i++) {
            ItemStack cur = slots.get(i);
            if (locked.test(i) || cur.isEmpty() || !ItemStack.isSameItemSameComponents(cur, rest)) {
                continue;
            }
            int move = Math.min(rest.getCount(), limit - cur.getCount());
            if (move > 0) {
                cur.grow(move);
                rest.shrink(move);
            }
        }
        // Passo 2: slots vazios — mochila primeiro, hotbar por último.
        for (int n = 0; n < slots.size() && !rest.isEmpty(); n++) {
            int i = (n + HOTBAR_SIZE) % slots.size();
            if (locked.test(i) || !slots.get(i).isEmpty()) {
                continue;
            }
            int move = Math.min(rest.getCount(), limit);
            slots.set(i, rest.copyWithCount(move));
            rest.shrink(move);
        }
        return rest;
    }

    private static void giveBack(Container source, int slot, ItemStack rest) {
        ItemStack cur = source.getItem(slot);
        if (cur.isEmpty()) {
            source.setItem(slot, rest);
        } else {
            cur.grow(rest.getCount());
        }
    }
}
