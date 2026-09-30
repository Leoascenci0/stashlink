package io.github.leoascenci0.stashlink.quickstack;

import io.github.leoascenci0.stashlink.source.ContainerInsert;
import io.github.leoascenci0.stashlink.source.ContainerSource;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.IntPredicate;

/**
 * A regra da tecla N, sem nada de rede nem de jogador (por isso dá para testar de forma exaustiva):
 * para cada container, do mais perto ao mais longe, move dos slots do jogador tudo o que <b>o container já
 * contém</b> (mesmo item <b>e</b> mesmos componentes — uma picareta encantada não vai para um baú que só tem
 * picaretas sem encantamento).
 *
 * <p>Sem perda nem duplicação: primeiro pergunta ao container quanto cabe ({@link ContainerInsert#capacity}),
 * depois move exatamente essa quantidade e só então tira o mesmo tanto do jogador.
 */
public final class QuickStackLogic {
    private QuickStackLogic() {
    }

    /** Resumo do que foi feito: quantos itens foram movidos e para quantos containers. */
    public record Result(int itemsMoved, int containersUsed) {
    }

    /**
     * @param slots   slots do jogador (lista modificável; é alterada)
     * @param skip    {@code true} para slots que não podem ser esvaziados (hotbar, travados)
     * @param targets containers em ordem de prioridade; a permissão de cada um só é consultada se ele tiver
     *                algo que casa com o inventário
     */
    public static Result stack(List<ItemStack> slots, IntPredicate skip, List<ContainerSource.Entry> targets) {
        int moved = 0;
        int used = 0;
        for (ContainerSource.Entry target : targets) {
            if (!hasMatch(slots, skip, target) || !target.allowed().getAsBoolean()) {
                continue;
            }
            int movedHere = 0;
            for (int i = 0; i < slots.size(); i++) {
                ItemStack stack = slots.get(i);
                if (skip.test(i) || stack.isEmpty() || ContainerInsert.count(target.container(), stack) <= 0) {
                    continue;
                }
                int fits = Math.min(stack.getCount(), ContainerInsert.capacity(target.container(), stack));
                if (fits <= 0) {
                    continue;
                }
                ItemStack rest = ContainerInsert.insert(target.container(), stack.copyWithCount(fits));
                // "rest" deveria ser vazio (a simulação garantiu); se não for, só sai do jogador o que entrou.
                int inserted = fits - rest.getCount();
                stack.shrink(inserted);
                if (stack.isEmpty()) {
                    slots.set(i, ItemStack.EMPTY);
                }
                movedHere += inserted;
            }
            if (movedHere > 0) {
                moved += movedHere;
                used++;
            }
        }
        return new Result(moved, used);
    }

    private static boolean hasMatch(List<ItemStack> slots, IntPredicate skip, ContainerSource.Entry target) {
        for (int i = 0; i < slots.size(); i++) {
            ItemStack stack = slots.get(i);
            if (!skip.test(i) && !stack.isEmpty() && ContainerInsert.count(target.container(), stack) > 0) {
                return true;
            }
        }
        return false;
    }
}
