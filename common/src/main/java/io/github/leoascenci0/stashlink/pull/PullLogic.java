package io.github.leoascenci0.stashlink.pull;

import io.github.leoascenci0.stashlink.source.ItemSource;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Regras puras do "puxar para a hotbar" (sem jogador nem mundo), para poderem ser testadas sem abrir o jogo. */
public final class PullLogic {
    public static final int NO_SLOT = -1;

    private PullLogic() {
    }

    /**
     * Escolhe o slot da hotbar que vai receber o item: o selecionado (se vazio ou se já tem o mesmo item com
     * espaço), senão outro slot com o mesmo item e espaço, senão o primeiro vazio.
     *
     * @return o índice, ou {@link #NO_SLOT} se a hotbar está cheia de outras coisas
     */
    public static int chooseSlot(List<ItemStack> hotbar, int selected, ItemStack model) {
        if (accepts(hotbar.get(selected), model)) {
            return selected;
        }
        for (int i = 0; i < hotbar.size(); i++) {
            if (!hotbar.get(i).isEmpty() && accepts(hotbar.get(i), model)) {
                return i;
            }
        }
        for (int i = 0; i < hotbar.size(); i++) {
            if (hotbar.get(i).isEmpty()) {
                return i;
            }
        }
        return NO_SLOT;
    }

    private static boolean accepts(ItemStack slot, ItemStack model) {
        return slot.isEmpty()
                || (ItemStack.isSameItemSameComponents(slot, model) && slot.getCount() < slot.getMaxStackSize());
    }

    /**
     * Tira até {@code wanted} itens iguais a {@code model} da fonte e coloca no slot escolhido da hotbar.
     * Nunca leva mais do que cabe no slot; o que sai da fonte é exatamente o que entra na hotbar.
     *
     * @param hotbar   os 9 slots da hotbar (a lista é alterada)
     * @param selected slot selecionado no momento
     * @return o slot que recebeu o item, ou {@link #NO_SLOT} se nada foi movido
     */
    public static int pullIntoHotbar(List<ItemStack> hotbar, int selected, ItemStack model, int wanted, ItemSource source) {
        if (model.isEmpty() || wanted <= 0) {
            return NO_SLOT;
        }
        ItemStack one = model.copyWithCount(1);
        int slot = chooseSlot(hotbar, selected, one);
        if (slot == NO_SLOT) {
            return NO_SLOT;
        }
        ItemStack current = hotbar.get(slot);
        int room = one.getMaxStackSize() - (current.isEmpty() ? 0 : current.getCount());
        List<ItemStack> taken = source.take(one, Math.min(Math.min(wanted, one.getMaxStackSize()), room));
        int total = ItemSource.sum(taken);
        if (total <= 0) {
            return NO_SLOT;
        }
        hotbar.set(slot, one.copyWithCount((current.isEmpty() ? 0 : current.getCount()) + total));
        return slot;
    }
}
