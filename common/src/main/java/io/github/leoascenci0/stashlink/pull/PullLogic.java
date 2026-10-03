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
     * O slot que o mod escolheu antes e ainda é dele: {@code model} é o que ele pôs ali (quantidade 1) e
     * {@code count} quanto. Só vale enquanto o slot ainda tem esse mesmo item.
     */
    public record Owned(int slot, ItemStack model, int count) {
    }

    /**
     * @param slot     slot que recebeu o item, ou {@link #NO_SLOT} se nada foi puxado
     * @param taken    quantos itens saíram das fontes para o slot
     * @param returned quantos itens do slot antigo do mod foram devolvidos ao armazenamento/inventário
     * @param wasEmpty o slot estava vazio antes de receber (então é inteiro do mod)
     */
    public record Result(int slot, int taken, int returned, boolean wasEmpty) {
        static Result none(int returned) {
            return new Result(NO_SLOT, 0, returned, false);
        }
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

    /**
     * Como {@link #chooseSlot(List, int, ItemStack)}, mas sabendo qual slot é do mod: um slot que já tem o
     * mesmo item (com espaço) ainda ganha; depois vem o slot do mod (o item antigo dele será trocado), e só
     * então slot vazio. Assim a hotbar não enche de itens puxados.
     */
    static int chooseSlot(List<ItemStack> hotbar, int selected, ItemStack model, Owned owned) {
        if (!ownedUsable(hotbar, owned)) {
            return chooseSlot(hotbar, selected, model);
        }
        if (!hotbar.get(selected).isEmpty() && accepts(hotbar.get(selected), model)) {
            return selected;
        }
        for (int i = 0; i < hotbar.size(); i++) {
            if (!hotbar.get(i).isEmpty() && accepts(hotbar.get(i), model)) {
                return i;
            }
        }
        // O slot do mod só serve se tem OUTRO item (se tivesse o mesmo, mas cheio, trocar não adiantaria).
        if (!ItemStack.isSameItemSameComponents(hotbar.get(owned.slot()), model)) {
            return owned.slot();
        }
        if (hotbar.get(selected).isEmpty()) {
            return selected;
        }
        for (int i = 0; i < hotbar.size(); i++) {
            if (hotbar.get(i).isEmpty()) {
                return i;
            }
        }
        return NO_SLOT;
    }

    private static boolean ownedUsable(List<ItemStack> hotbar, Owned owned) {
        return owned != null && owned.count() > 0 && owned.slot() >= 0 && owned.slot() < hotbar.size()
                && !hotbar.get(owned.slot()).isEmpty()
                && ItemStack.isSameItemSameComponents(hotbar.get(owned.slot()), owned.model());
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
        return pull(hotbar, selected, model, wanted, source, null, null).slot();
    }

    /**
     * Como {@link #pullIntoHotbar}, mas trocando no lugar: se o slot do mod ({@code owned}) tem outro item, ele
     * é devolvido por {@code returnTo} e o novo ocupa o mesmo slot.
     *
     * <p>Garantias: (1) só mexe no slot do mod e só no que o mod pôs ({@code owned.count()}), nunca em item do
     * jogador; (2) o que sai do slot é <b>exatamente</b> o que {@code returnTo} aceitou — se nada aceitar, o
     * item antigo continua no slot e o novo vai para outro slot, como sem a troca; (3) a troca só começa se a
     * fonte tem o item pedido, para um pedido impossível não esvaziar a mão à toa.
     */
    public static Result pull(List<ItemStack> hotbar, int selected, ItemStack model, int wanted, ItemSource source,
                              Owned owned, ItemSource returnTo) {
        if (model.isEmpty() || wanted <= 0) {
            return Result.none(0);
        }
        ItemStack one = model.copyWithCount(1);
        int slot = chooseSlot(hotbar, selected, one, returnTo == null ? null : owned);
        if (slot == NO_SLOT) {
            return Result.none(0);
        }
        int returned = 0;
        if (returnTo != null && ownedUsable(hotbar, owned) && slot == owned.slot() && !accepts(hotbar.get(slot), one)) {
            if (source.available(one) <= 0) {
                return Result.none(0);
            }
            returned = giveBack(hotbar, owned, returnTo);
            if (!hotbar.get(slot).isEmpty()) {
                // Sobrou item antigo (origem e inventário cheios): deixa lá, quem sobra no slot passa a ser do
                // jogador, e o novo item vai para outro slot.
                slot = chooseSlot(hotbar, selected, one);
                if (slot == NO_SLOT) {
                    return Result.none(returned);
                }
            }
        }
        ItemStack current = hotbar.get(slot);
        boolean wasEmpty = current.isEmpty();
        int room = one.getMaxStackSize() - (wasEmpty ? 0 : current.getCount());
        List<ItemStack> taken = source.take(one, Math.min(Math.min(wanted, one.getMaxStackSize()), room));
        int total = ItemSource.sum(taken);
        if (total <= 0) {
            return Result.none(returned);
        }
        hotbar.set(slot, one.copyWithCount((wasEmpty ? 0 : current.getCount()) + total));
        return new Result(slot, total, returned, wasEmpty);
    }

    /** Devolve ao {@code returnTo} o que o mod pôs no slot (o que o jogador gastou já não está lá). */
    private static int giveBack(List<ItemStack> hotbar, Owned owned, ItemSource returnTo) {
        ItemStack current = hotbar.get(owned.slot());
        int amount = Math.min(current.getCount(), owned.count());
        if (amount <= 0) {
            return 0;
        }
        ItemStack rest = returnTo.give(current.copyWithCount(amount));
        int returned = amount - rest.getCount();
        if (returned > 0) {
            hotbar.set(owned.slot(), returned >= current.getCount() ? ItemStack.EMPTY
                    : current.copyWithCount(current.getCount() - returned));
        }
        return returned;
    }
}
