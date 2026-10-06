package io.github.leoascenci0.stashlink.platform;

import io.github.leoascenci0.stashlink.source.ModStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.world.item.ItemStack;

import java.util.function.ObjLongConsumer;

/**
 * Item 26 no Fabric: um bloco de outro mod visto pela Transfer API ({@code Storage<ItemVariant>}). Só "cola": cada
 * operação abre uma transação; simular = desistir no fim, de verdade = confirmar.
 */
final class FabricModStorage implements ModStorage {
    private final Storage<ItemVariant> storage;

    FabricModStorage(Storage<ItemVariant> storage) {
        this.storage = storage;
    }

    @Override
    public void forEach(ObjLongConsumer<ItemStack> sink) {
        for (StorageView<ItemVariant> view : storage.nonEmptyViews()) {
            ItemVariant variant = view.getResource();
            if (!variant.isBlank() && view.getAmount() > 0) {
                sink.accept(variant.toStack(1), view.getAmount());
            }
        }
    }

    @Override
    public long count(ItemStack model) {
        ItemVariant variant = ItemVariant.of(model);
        long total = 0;
        for (StorageView<ItemVariant> view : storage.nonEmptyViews()) {
            if (variant.equals(view.getResource())) {
                total = saturatedAdd(total, view.getAmount());
            }
        }
        return total;
    }

    @Override
    public long extract(ItemStack model, long amount, boolean simulate) {
        // Transação já aberta (chamado de dentro de outra operação de outro mod): melhor não mexer.
        if (amount <= 0 || model.isEmpty() || !storage.supportsExtraction() || Transaction.isOpen()) {
            return 0;
        }
        try (Transaction tx = Transaction.openOuter()) {
            long got = storage.extract(ItemVariant.of(model), amount, tx);
            if (!simulate) {
                tx.commit();
            }
            return got;
        }
    }

    @Override
    public long insert(ItemStack model, long amount, boolean simulate) {
        if (amount <= 0 || model.isEmpty() || !storage.supportsInsertion() || Transaction.isOpen()) {
            return 0;
        }
        try (Transaction tx = Transaction.openOuter()) {
            long in = storage.insert(ItemVariant.of(model), amount, tx);
            if (!simulate) {
                tx.commit();
            }
            return in;
        }
    }

    @Override
    public Object identity() {
        return storage;
    }

    private static long saturatedAdd(long a, long b) {
        long r = a + b;
        return r < 0 ? Long.MAX_VALUE : r;
    }
}
