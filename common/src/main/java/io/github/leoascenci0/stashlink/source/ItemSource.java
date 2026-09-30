package io.github.leoascenci0.stashlink.source;

import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Um lugar de onde o mod pode tirar itens (ex.: shulkers no inventário do jogador; no futuro, shulker ou baú
 * no chão — Item 6). Quem consome (reabastecimento, colocar bloco…) só conhece esta interface, então
 * adicionar uma fonte nova não exige mexer em quem consome.
 *
 * <p>"Qual item" é dado por um stack modelo: casa quem for o mesmo item <b>com os mesmos componentes</b>
 * (uma picareta encantada não é igual a uma sem encantamento). A quantidade do modelo é ignorada.
 *
 * <p>Sem dependência de API de loader: só tipos do Minecraft.
 */
public interface ItemSource {
    /** Quantos itens iguais ao modelo esta fonte tem no total (não altera nada). */
    int available(ItemStack item);

    /**
     * Tira até {@code n} itens iguais ao modelo. Devolve stacks novos (cada um respeitando o tamanho máximo
     * de stack); a soma é exatamente o que saiu da fonte — pode ser menos que {@code n}, ou vazio.
     */
    List<ItemStack> take(ItemStack item, int n);

    /** Soma das quantidades de uma lista de stacks. */
    static int sum(List<ItemStack> stacks) {
        int total = 0;
        for (ItemStack stack : stacks) {
            total += stack.getCount();
        }
        return total;
    }
}
