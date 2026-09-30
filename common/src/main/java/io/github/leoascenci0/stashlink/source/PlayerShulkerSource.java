package io.github.leoascenci0.stashlink.source;

import io.github.leoascenci0.stashlink.storage.ShulkerStorage;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Fonte: o conteúdo das shulker boxes que estão no inventário do jogador.
 *
 * <p>Recebe a lista de slots do inventário (a cola do loader passa a lista real do jogador), varre em ordem
 * de slot e usa apenas {@link ShulkerStorage} para mexer no conteúdo. Itens soltos no inventário não são
 * desta fonte: o jogo já os usa normalmente.
 */
public final class PlayerShulkerSource implements ItemSource {
    private final List<ItemStack> inventory;

    public PlayerShulkerSource(List<ItemStack> inventory) {
        this.inventory = inventory;
    }

    @Override
    public int available(ItemStack item) {
        Predicate<ItemStack> filter = matcher(item);
        int total = 0;
        for (ItemStack stack : inventory) {
            if (ShulkerStorage.isShulker(stack)) {
                total += ShulkerStorage.count(stack, filter);
            }
        }
        return total;
    }

    @Override
    public List<ItemStack> take(ItemStack item, int n) {
        Predicate<ItemStack> filter = matcher(item);
        List<ItemStack> taken = new ArrayList<>();
        int remaining = Math.max(n, 0);
        for (ItemStack stack : inventory) {
            if (remaining <= 0) {
                break;
            }
            if (ShulkerStorage.isShulker(stack)) {
                List<ItemStack> out = ShulkerStorage.extract(stack, filter, remaining);
                remaining -= ItemSource.sum(out);
                taken.addAll(out);
            }
        }
        return taken;
    }

    /**
     * Devolve um stack às shulkers do inventário (desfazer um {@link #take}). Retorna o que não coube.
     */
    @Override
    public ItemStack give(ItemStack stack) {
        ItemStack rest = stack.copy();
        for (ItemStack box : inventory) {
            if (rest.isEmpty()) {
                break;
            }
            if (ShulkerStorage.isShulker(box)) {
                rest = ShulkerStorage.insert(box, rest);
            }
        }
        return rest;
    }

    private static Predicate<ItemStack> matcher(ItemStack item) {
        ItemStack model = item.copyWithCount(1);
        return s -> ItemStack.isSameItemSameComponents(s, model);
    }
}
