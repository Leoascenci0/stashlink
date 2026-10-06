package io.github.leoascenci0.stashlink.platform;

import io.github.leoascenci0.stashlink.source.ModStorage;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.function.ObjLongConsumer;

/**
 * Item 26 no NeoForge: um bloco de outro mod visto pela capability de itens ({@code ResourceHandler<ItemResource>},
 * a API de transferência que substituiu o {@code IItemHandler}). Só "cola": cada operação abre uma transação;
 * simular = desistir no fim (fechar sem confirmar), de verdade = confirmar.
 */
final class NeoForgeModStorage implements ModStorage {
    private final ResourceHandler<ItemResource> handler;

    NeoForgeModStorage(ResourceHandler<ItemResource> handler) {
        this.handler = handler;
    }

    @Override
    public void forEach(ObjLongConsumer<ItemStack> sink) {
        int size = handler.size();
        for (int i = 0; i < size; i++) {
            ItemResource resource = handler.getResource(i);
            long amount = handler.getAmountAsLong(i);
            if (!resource.isEmpty() && amount > 0) {
                sink.accept(resource.toStack(1), amount);
            }
        }
    }

    @Override
    public long count(ItemStack model) {
        ItemResource wanted = ItemResource.of(model);
        long total = 0;
        int size = handler.size();
        for (int i = 0; i < size; i++) {
            if (wanted.equals(handler.getResource(i))) {
                long r = total + handler.getAmountAsLong(i);
                total = r < 0 ? Long.MAX_VALUE : r;
            }
        }
        return total;
    }

    @Override
    public long extract(ItemStack model, long amount, boolean simulate) {
        // Transação já aberta (chamado de dentro de outra operação de outro mod): melhor não mexer.
        if (amount <= 0 || model.isEmpty() || Transaction.getLifecycle() != Transaction.Lifecycle.NONE) {
            return 0;
        }
        try (Transaction tx = Transaction.openRoot()) {
            int got = handler.extract(ItemResource.of(model), (int) Math.min(amount, Integer.MAX_VALUE), tx);
            if (!simulate) {
                tx.commit();
            }
            return got;
        }
    }

    @Override
    public long insert(ItemStack model, long amount, boolean simulate) {
        if (amount <= 0 || model.isEmpty() || Transaction.getLifecycle() != Transaction.Lifecycle.NONE) {
            return 0;
        }
        try (Transaction tx = Transaction.openRoot()) {
            int in = handler.insert(ItemResource.of(model), (int) Math.min(amount, Integer.MAX_VALUE), tx);
            if (!simulate) {
                tx.commit();
            }
            return in;
        }
    }

    @Override
    public Object identity() {
        return handler;
    }
}
