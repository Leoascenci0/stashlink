package io.github.leoascenci0.stashlink.source;

import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Supplier;

/**
 * Fonte que só é montada na primeira vez que alguém a usa. Serve para a varredura de containers no raio: se
 * as shulkers do inventário já bastam, a varredura nem acontece.
 */
public final class LazyItemSource implements ItemSource {
    private final Supplier<ItemSource> factory;
    private ItemSource delegate;

    public LazyItemSource(Supplier<ItemSource> factory) {
        this.factory = factory;
    }

    private ItemSource get() {
        if (delegate == null) {
            delegate = factory.get();
        }
        return delegate;
    }

    @Override
    public int available(ItemStack item) {
        return get().available(item);
    }

    @Override
    public List<ItemStack> take(ItemStack item, int n) {
        return get().take(item, n);
    }

    @Override
    public ItemStack give(ItemStack stack) {
        // Se nunca foi usada, nada saiu dela: não há o que devolver e não vale montar a varredura só por isso.
        return delegate == null ? stack : delegate.give(stack);
    }
}
