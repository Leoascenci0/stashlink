package io.github.leoascenci0.stashlink.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A "gaveta" de teste do Item 26: dois slots, cada um com <b>um</b> tipo de item e até {@link #CAPACITY} unidades (bem
 * mais que 64, como uma gaveta de verdade). <b>Não</b> é {@code Container}: o mod só a enxerga pela tomada de itens do
 * loader, que cada loader liga a este objeto (com suporte a transação, por meio de {@link #snapshot}/{@link #restore}).
 */
public final class ApiDrawerBlockEntity extends BlockEntity {
    public static final int SLOTS = 2;
    public static final long CAPACITY = 10_000;

    /** Cópia do conteúdo, para a transação do loader poder desfazer. */
    public record Snapshot(ItemStack[] models, long[] amounts) {
    }

    private final ItemStack[] models = {ItemStack.EMPTY, ItemStack.EMPTY};
    private final long[] amounts = new long[SLOTS];

    public ApiDrawerBlockEntity(BlockPos pos, BlockState state) {
        super(TestBlocks.API_DRAWER_TYPE, pos, state);
    }

    /** O item do slot (quantidade 1), ou vazio. */
    public ItemStack model(int slot) {
        return models[slot];
    }

    public long amount(int slot) {
        return amounts[slot];
    }

    /** O slot aceita este item? Vazio, ou já com o mesmo item e componentes. */
    public boolean accepts(int slot, ItemStack model) {
        return models[slot].isEmpty() || ItemStack.isSameItemSameComponents(models[slot], model);
    }

    /** Troca o conteúdo de um slot (adaptadores do loader e o próprio teste). Zero esvazia o slot. */
    public void set(int slot, ItemStack model, long amount) {
        models[slot] = amount <= 0 || model.isEmpty() ? ItemStack.EMPTY : model.copyWithCount(1);
        amounts[slot] = Math.max(0, amount);
        setChanged();
    }

    public void put(int slot, Item item, long amount) {
        set(slot, new ItemStack(item), amount);
    }

    /** Quanto deste item há na gaveta (os dois slots). */
    public long stock(Item item) {
        long total = 0;
        for (int i = 0; i < SLOTS; i++) {
            if (models[i].is(item)) {
                total += amounts[i];
            }
        }
        return total;
    }

    public Snapshot snapshot() {
        return new Snapshot(models.clone(), amounts.clone());
    }

    public void restore(Snapshot snapshot) {
        System.arraycopy(snapshot.models(), 0, models, 0, SLOTS);
        System.arraycopy(snapshot.amounts(), 0, amounts, 0, SLOTS);
    }
}
