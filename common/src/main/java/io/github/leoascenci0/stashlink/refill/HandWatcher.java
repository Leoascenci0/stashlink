package io.github.leoascenci0.stashlink.refill;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Vigia uma mão do jogador, tick a tick, e avisa quando o stack que estava nela sumiu.
 *
 * <p>Por que assim: não existe um "evento de stack esgotado" igual em Fabric e NeoForge (colocar bloco, comer,
 * arremessar e quebrar ferramenta acabam em código diferente). Olhar a mão uma vez por tick funciona para todos
 * esses casos e mantém a lógica em {@code common}. O custo é uma comparação por mão por jogador.
 *
 * <p>O vigia só dispara quando: a mão estava com item no tick anterior, o slot selecionado é o mesmo e agora está
 * vazia (ou com o recipiente vazio do item, ver {@link #isLeftover}). Trocar de slot da hotbar nunca dispara.
 */
public final class HandWatcher {
    private int lastKey = Integer.MIN_VALUE;
    private ItemStack last = ItemStack.EMPTY;

    /** O que havia na mão no tick anterior (vazio se nada). Não altere o stack devolvido. */
    public ItemStack last() {
        return last;
    }

    /**
     * @param key     identifica "qual mão/slot" (slot da hotbar para a mão principal); mudou = não é esgotamento
     * @param current o que está na mão agora
     * @param active  false quando o jogo está numa situação em que não se deve reabastecer (GUI aberta, criativo,
     *                item solto com Q...); o vigia apenas atualiza o que viu
     * @return o stack que estava na mão e acabou (só para servir de modelo), ou vazio se nada esgotou
     */
    public ItemStack observe(int key, ItemStack current, boolean active) {
        ItemStack before = last;
        boolean sameSlot = key == lastKey;
        lastKey = key;
        last = current.isEmpty() ? ItemStack.EMPTY : current.copy();
        if (active && sameSlot && !before.isEmpty() && (current.isEmpty() || isLeftover(before, current))) {
            return before;
        }
        return ItemStack.EMPTY;
    }

    /**
     * O último item virou o "recipiente vazio" dele: balde de água → balde, sopa → tigela, poção → garrafa.
     * Só vale se havia 1 só e sobrou 1 só, para o recipiente poder ser guardado no inventário sem sobra.
     */
    static boolean isLeftover(ItemStack before, ItemStack current) {
        return before.getCount() == 1
                && current.getCount() == 1
                && !ItemStack.isSameItem(before, current)
                && (current.is(Items.BUCKET) || current.is(Items.BOWL) || current.is(Items.GLASS_BOTTLE));
    }
}
