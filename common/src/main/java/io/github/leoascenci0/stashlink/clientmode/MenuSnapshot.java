package io.github.leoascenci0.stashlink.clientmode;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Tira a "foto" dos slots do menu aberto para a lógica pura. É feita de novo a cada tick: nunca assumimos que um
 * clique deu certo, olhamos o que o menu mostra agora (já com a correção do servidor, se houve).
 */
public final class MenuSnapshot {
    private MenuSnapshot() {
    }

    /**
     * Slot cujo {@code container} é o inventário do jogador é "do jogador" (e o índice dele no inventário vem de
     * {@code getContainerSlot}); qualquer outro é do container aberto. Não supomos a ordem dos slots do menu.
     */
    public static List<MenuEntry> of(AbstractContainerMenu menu, Inventory inventory) {
        List<MenuEntry> out = new ArrayList<>(menu.slots.size());
        for (Slot slot : menu.slots) {
            int inv = slot.container == inventory ? slot.getContainerSlot() : -1;
            out.add(new MenuEntry(slot.index, inv, slot.getItem()));
        }
        return out;
    }

    /** Os stacks (não vazios) dos slots do container, para o cache de conteúdo. */
    public static List<ItemStack> containerStacks(List<MenuEntry> entries) {
        List<ItemStack> out = new ArrayList<>();
        for (MenuEntry e : entries) {
            if (!e.isPlayerSlot() && !e.stack().isEmpty()) {
                out.add(e.stack());
            }
        }
        return out;
    }
}
