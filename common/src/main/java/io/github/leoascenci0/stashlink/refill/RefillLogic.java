package io.github.leoascenci0.stashlink.refill;

import io.github.leoascenci0.stashlink.source.ItemSource;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Regras puras do reabastecimento (sem jogador nem mundo), para poderem ser testadas sem abrir o jogo. */
public final class RefillLogic {
    private RefillLogic() {
    }

    /**
     * Busca na fonte um stack para repor o que acabou de esgotar na mão.
     *
     * <p>Leva no máximo um stack cheio ({@code getMaxStackSize}) e junta tudo num só stack, mesmo que o
     * conteúdo esteja espalhado em vários slots ou shulkers.
     *
     * <p>Ferramenta que quebrou: o modelo ignora o desgaste, senão nunca acharia uma igual (a da shulker está
     * nova). Encantamentos e nome continuam obrigatórios.
     *
     * @param lastSeen o stack que estava na mão antes de esgotar
     * @return o stack para colocar na mão, ou vazio se não há estoque
     */
    public static ItemStack refill(ItemStack lastSeen, ItemSource source) {
        if (lastSeen.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack model = lastSeen.copyWithCount(1);
        if (model.has(DataComponents.DAMAGE)) {
            model.set(DataComponents.DAMAGE, 0);
        }
        List<ItemStack> taken = source.take(model, lastSeen.getMaxStackSize());
        int total = ItemSource.sum(taken);
        if (total <= 0) {
            return ItemStack.EMPTY;
        }
        return taken.get(0).copyWithCount(total);
    }

    /**
     * Detecta o "swap" com a tecla F: o item que sumiu de uma mão apareceu na outra. Isso não é esgotamento e
     * não deve puxar nada.
     *
     * @param lastSeen      o que estava na mão que ficou vazia
     * @param otherBefore   a outra mão no tick anterior
     * @param otherNow      a outra mão agora
     */
    public static boolean movedToOtherHand(ItemStack lastSeen, ItemStack otherBefore, ItemStack otherNow) {
        if (otherNow.isEmpty() || !ItemStack.isSameItem(otherNow, lastSeen)) {
            return false;
        }
        int before = ItemStack.isSameItem(otherBefore, lastSeen) ? otherBefore.getCount() : 0;
        return otherNow.getCount() > before;
    }
}
