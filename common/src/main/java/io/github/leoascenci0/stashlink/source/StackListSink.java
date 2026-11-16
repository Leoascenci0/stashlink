package io.github.leoascenci0.stashlink.source;

import io.github.leoascenci0.stashlink.config.PlayerPrefsStore;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.IntPredicate;

/**
 * "Fonte" que só <b>recebe</b>: guarda itens numa lista de slots (a mochila do jogador). É o plano B da
 * devolução: se a origem está cheia ou longe, o item vai para o inventário, nunca para o chão.
 *
 * <p>Slot travado pelo jogador (Item 32) nunca recebe nada daqui, nem para completar pilha nem por estar vazio: o que
 * não couber nos outros slots volta como "sobra" e quem chamou segue a regra de reserva dele (o item fica onde estava).
 */
public final class StackListSink implements ItemSource {
    private final List<ItemStack> slots;
    private final IntPredicate locked;

    /** Sem travas: só para listas que não são do inventário do jogador (testes e contas internas). */
    public StackListSink(List<ItemStack> slots) {
        this(slots, slot -> false);
    }

    /** {@code locked} recebe o índice dentro de {@code slots} e diz se aquele slot está travado. */
    public StackListSink(List<ItemStack> slots, IntPredicate locked) {
        this.slots = slots;
        this.locked = locked;
    }

    /**
     * A mochila e a hotbar do jogador (36 slots, sem armadura nem mão secundária) com as travas dele: a lista do jogo
     * e a trava usam o mesmo índice. É o único jeito de guardar no inventário do jogador sem esquecer as travas.
     */
    public static StackListSink backpackOf(ServerPlayer player) {
        return new StackListSink(player.getInventory().getNonEquipmentItems(),
                slot -> PlayerPrefsStore.isSlotLocked(player, slot));
    }

    @Override
    public int available(ItemStack item) {
        return 0;
    }

    @Override
    public List<ItemStack> take(ItemStack item, int n) {
        return List.of();
    }

    /** Completa stacks iguais e depois usa slots vazios (menos os travados). Devolve o que não coube; não altera o stack recebido. */
    @Override
    public ItemStack give(ItemStack stack) {
        ItemStack rest = stack.copy();
        for (int pass = 0; pass < 2 && !rest.isEmpty(); pass++) {
            for (int i = 0; i < slots.size() && !rest.isEmpty(); i++) {
                if (locked.test(i)) {
                    continue;
                }
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
