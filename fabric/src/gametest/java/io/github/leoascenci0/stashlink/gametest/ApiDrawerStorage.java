package io.github.leoascenci0.stashlink.gametest;

import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * A tomada de itens (Transfer API) da gaveta de teste do Item 26, como um mod de gavetas faria: cada slot guarda um
 * tipo de item e até {@link ApiDrawerBlockEntity#CAPACITY}; o que não for confirmado na transação é desfeito.
 */
final class ApiDrawerStorage extends SnapshotParticipant<ApiDrawerBlockEntity.Snapshot> implements Storage<ItemVariant> {
    private final ApiDrawerBlockEntity be;

    ApiDrawerStorage(ApiDrawerBlockEntity be) {
        this.be = be;
    }

    private boolean holds(int slot, ItemVariant resource) {
        return !be.model(slot).isEmpty() && resource.matches(be.model(slot));
    }

    @Override
    public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
        StoragePreconditions.notBlankNotNegative(resource, maxAmount);
        ItemStack model = resource.toStack(1);
        long done = 0;
        // Primeiro os slots que já têm o item, depois os vazios.
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < ApiDrawerBlockEntity.SLOTS && done < maxAmount; i++) {
                boolean fits = pass == 0 ? holds(i, resource) : be.model(i).isEmpty();
                long n = fits ? Math.min(ApiDrawerBlockEntity.CAPACITY - be.amount(i), maxAmount - done) : 0;
                if (n > 0) {
                    updateSnapshots(transaction);
                    be.set(i, model, be.amount(i) + n);
                    done += n;
                }
            }
        }
        return done;
    }

    @Override
    public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
        StoragePreconditions.notBlankNotNegative(resource, maxAmount);
        long done = 0;
        for (int i = 0; i < ApiDrawerBlockEntity.SLOTS && done < maxAmount; i++) {
            done += extractFrom(i, resource, maxAmount - done, transaction);
        }
        return done;
    }

    private long extractFrom(int slot, ItemVariant resource, long maxAmount, TransactionContext transaction) {
        if (!holds(slot, resource)) {
            return 0;
        }
        long n = Math.min(be.amount(slot), maxAmount);
        if (n > 0) {
            updateSnapshots(transaction);
            be.set(slot, be.model(slot), be.amount(slot) - n);
        }
        return n;
    }

    @Override
    public Iterator<StorageView<ItemVariant>> iterator() {
        List<StorageView<ItemVariant>> views = new ArrayList<>();
        for (int i = 0; i < ApiDrawerBlockEntity.SLOTS; i++) {
            int slot = i;
            views.add(new StorageView<>() {
                @Override
                public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
                    StoragePreconditions.notBlankNotNegative(resource, maxAmount);
                    return extractFrom(slot, resource, maxAmount, transaction);
                }

                @Override
                public boolean isResourceBlank() {
                    return be.model(slot).isEmpty();
                }

                @Override
                public ItemVariant getResource() {
                    return be.model(slot).isEmpty() ? ItemVariant.blank() : ItemVariant.of(be.model(slot));
                }

                @Override
                public long getAmount() {
                    return be.amount(slot);
                }

                @Override
                public long getCapacity() {
                    return ApiDrawerBlockEntity.CAPACITY;
                }
            });
        }
        return views.iterator();
    }

    @Override
    protected ApiDrawerBlockEntity.Snapshot createSnapshot() {
        return be.snapshot();
    }

    @Override
    protected void readSnapshot(ApiDrawerBlockEntity.Snapshot snapshot) {
        be.restore(snapshot);
    }

    @Override
    protected void onFinalCommit() {
        be.setChanged();
    }
}
