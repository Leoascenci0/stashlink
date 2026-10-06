package io.github.leoascenci0.stashlink.gametest;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * A tomada de itens (capability) da gaveta de teste do Item 26 no NeoForge, como um mod de gavetas faria: cada slot
 * guarda um tipo de item e até {@link ApiDrawerBlockEntity#CAPACITY}; o que não for confirmado na transação é desfeito.
 */
final class ApiDrawerHandler extends SnapshotJournal<ApiDrawerBlockEntity.Snapshot> implements ResourceHandler<ItemResource> {
    private final ApiDrawerBlockEntity be;

    ApiDrawerHandler(ApiDrawerBlockEntity be) {
        this.be = be;
    }

    @Override
    public int size() {
        return ApiDrawerBlockEntity.SLOTS;
    }

    @Override
    public ItemResource getResource(int index) {
        return ItemResource.of(be.model(index));
    }

    @Override
    public long getAmountAsLong(int index) {
        return be.amount(index);
    }

    @Override
    public long getCapacityAsLong(int index, ItemResource resource) {
        return ApiDrawerBlockEntity.CAPACITY;
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        return true;
    }

    @Override
    public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        ItemStack model = resource.toStack(1);
        if (!be.accepts(index, model)) {
            return 0;
        }
        int n = (int) Math.min(ApiDrawerBlockEntity.CAPACITY - be.amount(index), amount);
        if (n > 0) {
            updateSnapshots(transaction);
            be.set(index, model, be.amount(index) + n);
        }
        return Math.max(n, 0);
    }

    @Override
    public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        if (be.model(index).isEmpty() || !resource.matches(be.model(index))) {
            return 0;
        }
        int n = (int) Math.min(be.amount(index), amount);
        if (n > 0) {
            updateSnapshots(transaction);
            be.set(index, be.model(index), be.amount(index) - n);
        }
        return n;
    }

    @Override
    protected ApiDrawerBlockEntity.Snapshot createSnapshot() {
        return be.snapshot();
    }

    @Override
    protected void revertToSnapshot(ApiDrawerBlockEntity.Snapshot snapshot) {
        be.restore(snapshot);
    }

    @Override
    protected void onRootCommit(ApiDrawerBlockEntity.Snapshot originalState) {
        be.setChanged();
    }
}
