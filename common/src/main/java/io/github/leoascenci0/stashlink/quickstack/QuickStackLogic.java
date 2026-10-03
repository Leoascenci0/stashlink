package io.github.leoascenci0.stashlink.quickstack;

import io.github.leoascenci0.stashlink.source.ContainerInsert;
import io.github.leoascenci0.stashlink.source.ContainerSource;
import io.github.leoascenci0.stashlink.slotlock.SlotLocks;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        Set<ContainerSource.Entry> used = new HashSet<>();
        // A permissão (claims) pode custar caro: pergunta no máximo uma vez por container, mesmo com duas rodadas.
        Map<ContainerSource.Entry, Boolean> allowed = new HashMap<>();
        // Rodada 1: só slots reservados (Item 13) — o item vai para o container onde há um slot dele, mesmo que
        // outro mais perto também o contenha. Rodada 2: a regra de sempre ("o container já contém o item").
        for (boolean reservedOnly : new boolean[]{true, false}) {
            for (ContainerSource.Entry target : targets) {
                if (!hasMatch(slots, skip, target, reservedOnly)
                        || !allowed.computeIfAbsent(target, t -> t.allowed().getAsBoolean())) {
                    continue;
                }
                int movedHere = 0;
                for (int i = 0; i < slots.size(); i++) {
                    ItemStack stack = slots.get(i);
                    if (skip.test(i) || stack.isEmpty() || !matches(target.container(), stack, reservedOnly)) {
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
                    used.add(target);
                }
            }
        }
        return new Result(moved, used.size());
    }

    /** O container deve receber este item? Reservado para ele, ou (fora da rodada de reservas) já o contém. */
    private static boolean matches(Container c, ItemStack stack, boolean reservedOnly) {
        return SlotLocks.reserves(c, stack) || (!reservedOnly && ContainerInsert.count(c, stack) > 0);
    }

    private static boolean hasMatch(List<ItemStack> slots, IntPredicate skip, ContainerSource.Entry target,
                                    boolean reservedOnly) {
        for (int i = 0; i < slots.size(); i++) {
            ItemStack stack = slots.get(i);
            if (!skip.test(i) && !stack.isEmpty() && matches(target.container(), stack, reservedOnly)) {
                return true;
            }
        }
        return false;
    }
}
