package io.github.leoascenci0.stashlink.pull;

import io.github.leoascenci0.stashlink.source.Origin;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * O slot da hotbar que o mod escolheu para um item puxado e que ainda é "dele": o que há ali pode voltar ao
 * armazenamento quando o Litematica pedir outro bloco. Só o que o mod pôs conta ({@code count}); item que o
 * jogador colocou no slot, ou um slot que ele mexeu, nunca é tocado.
 */
final class PulledSlot {
    final int slot;
    /** Item e componentes do que o mod pôs (quantidade 1). */
    final ItemStack model;
    /** Quantos itens o mod pôs e ainda não devolveu (o jogador pode ter gasto parte). */
    int count;
    Origin origin;

    PulledSlot(int slot, ItemStack model, int count, Origin origin) {
        this.slot = slot;
        this.model = model.copyWithCount(1);
        this.count = count;
        this.origin = origin;
    }

    PullLogic.Owned asOwned() {
        return new PullLogic.Owned(slot, model, count);
    }

    /**
     * Ainda é do mod? Só se o slot continua com o item que o mod pôs (o jogador não trocou nem esvaziou).
     */
    boolean stillOwned(List<ItemStack> hotbar) {
        ItemStack now = hotbar.get(slot);
        return !now.isEmpty() && ItemStack.isSameItemSameComponents(now, model);
    }

    /**
     * Qual slot é do mod depois de um pedido. {@code mine} é o registro de antes (ou null) e {@code result} o
     * que o pedido fez; devolve o registro novo, ou null se o mod já não tem slot.
     */
    static PulledSlot next(PulledSlot mine, List<ItemStack> hotbar, PullLogic.Result result, ItemStack model,
                           Origin origin) {
        if (mine != null && result.returned() > 0) {
            mine.count -= result.returned();
        }
        if (result.slot() == PullLogic.NO_SLOT) {
            return mine != null && mine.count > 0 ? mine : null;
        }
        if (result.wasEmpty()) {
            // Slot que estava vazio: é inteiro do mod (e substitui o registro anterior).
            return new PulledSlot(result.slot(), model, result.taken(), origin);
        }
        if (mine != null && mine.slot == result.slot()) {
            // Completou o stack do mod: soma o que ele ainda tinha lá com o que acabou de pôr.
            int before = hotbar.get(result.slot()).getCount() - result.taken();
            mine.count = Math.min(mine.count, before) + result.taken();
            mine.origin = mine.origin.merge(origin);
            return mine;
        }
        // Completou um stack que é do jogador. O registro antigo só sobrevive se o slot dele não foi mexido.
        return mine != null && result.returned() == 0 ? mine : null;
    }
}
