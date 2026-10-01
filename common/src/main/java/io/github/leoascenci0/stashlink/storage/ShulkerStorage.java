package io.github.leoascenci0.stashlink.storage;

import io.github.leoascenci0.stashlink.compat.mc.McCompat;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Única camada de acesso ao conteúdo de uma shulker box em forma de {@link ItemStack}.
 *
 * <p>Se o formato de item mudar em outra versão do Minecraft, só esta classe precisa ser ajustada
 * (risco "componentes de item mudam por versão" em docs/ARCHITECTURE.md).
 *
 * <p>Todas as operações que alteram algo são <b>atômicas</b>: calculam tudo numa cópia dos slots e só no
 * final gravam de volta na shulker. Não há estado intermediário em que um item exista em dois lugares
 * (ou em nenhum).
 */
public final class ShulkerStorage {
    /** Tamanho de uma shulker box vanilla (3 linhas x 9 colunas). */
    public static final int SLOTS = 27;

    private ShulkerStorage() {
    }

    /** {@code true} se o stack é uma shulker box de qualquer cor. */
    public static boolean isShulker(ItemStack stack) {
        return !stack.isEmpty()
                && stack.getItem() instanceof BlockItem blockItem
                && blockItem.getBlock() instanceof ShulkerBoxBlock;
    }

    /**
     * Lê o conteúdo: sempre uma lista de {@link #SLOTS} posições (slot vazio = {@link ItemStack#EMPTY}).
     * Os stacks devolvidos são <b>cópias</b>; alterá-los não altera a shulker.
     */
    public static List<ItemStack> read(ItemStack shulker) {
        requireShulker(shulker);
        return McCompat.readContainerComponent(shulker, SLOTS);
    }

    /** Grava o conteúdo (até {@link #SLOTS} posições). Conteúdo vazio volta ao componente padrão (EMPTY),
     * deixando a shulker idêntica a uma recém-craftada (senão ela não empilharia/casaria com as novas). */
    public static void write(ItemStack shulker, List<ItemStack> slots) {
        requireShulker(shulker);
        if (slots.size() > SLOTS) {
            throw new IllegalArgumentException("Shulker tem " + SLOTS + " slots, recebi " + slots.size());
        }
        McCompat.writeContainerComponent(shulker, slots, slots.stream().allMatch(ItemStack::isEmpty));
    }

    /** Soma de itens que casam com o filtro (não altera nada). */
    public static int count(ItemStack shulker, Predicate<ItemStack> filter) {
        int total = 0;
        for (ItemStack slot : read(shulker)) {
            if (!slot.isEmpty() && filter.test(slot)) {
                total += slot.getCount();
            }
        }
        return total;
    }

    /**
     * Tira até {@code max} itens que casam com o filtro, do primeiro slot para o último.
     * Devolve stacks novos (cada um respeitando o tamanho máximo de stack do item); a soma deles é
     * exatamente o que saiu da shulker.
     */
    public static List<ItemStack> extract(ItemStack shulker, Predicate<ItemStack> filter, int max) {
        List<ItemStack> slots = read(shulker);
        List<ItemStack> taken = new ArrayList<>();
        int remaining = Math.max(max, 0);
        for (int i = 0; i < slots.size() && remaining > 0; i++) {
            ItemStack slot = slots.get(i);
            if (slot.isEmpty() || !filter.test(slot)) {
                continue;
            }
            ItemStack out = slot.split(Math.min(remaining, slot.getCount()));
            remaining -= out.getCount();
            taken.add(out);
        }
        if (!taken.isEmpty()) {
            write(shulker, slots);
        }
        return taken;
    }

    /**
     * Tenta guardar o stack na shulker: primeiro completa stacks parciais do mesmo item (mesmos
     * componentes), depois usa slots vazios. Devolve o que <b>não coube</b> ({@link ItemStack#EMPTY}
     * se coube tudo). O stack recebido nunca é alterado.
     *
     * <p>Shulker dentro de shulker é recusado (vanilla proíbe), e o stack volta inteiro.
     */
    public static ItemStack insert(ItemStack shulker, ItemStack stack) {
        List<ItemStack> slots = read(shulker);
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (!stack.getItem().canFitInsideContainerItems()) {
            return stack.copy();
        }
        int remaining = stack.getCount();
        int maxStack = stack.getMaxStackSize();
        boolean changed = false;

        for (int i = 0; i < slots.size() && remaining > 0; i++) {
            ItemStack slot = slots.get(i);
            if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, stack) && slot.getCount() < maxStack) {
                int moved = Math.min(remaining, maxStack - slot.getCount());
                slot.grow(moved);
                remaining -= moved;
                changed = true;
            }
        }
        for (int i = 0; i < slots.size() && remaining > 0; i++) {
            if (slots.get(i).isEmpty()) {
                int moved = Math.min(remaining, maxStack);
                slots.set(i, stack.copyWithCount(moved));
                remaining -= moved;
                changed = true;
            }
        }
        if (changed) {
            write(shulker, slots);
        }
        return remaining == 0 ? ItemStack.EMPTY : stack.copyWithCount(remaining);
    }

    private static void requireShulker(ItemStack stack) {
        if (!isShulker(stack)) {
            throw new IllegalArgumentException("Não é uma shulker box: " + stack);
        }
    }
}
