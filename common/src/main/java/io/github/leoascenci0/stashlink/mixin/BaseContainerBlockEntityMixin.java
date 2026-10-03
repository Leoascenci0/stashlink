package io.github.leoascenci0.stashlink.mixin;

import io.github.leoascenci0.stashlink.slotlock.SlotLockHolder;
import io.github.leoascenci0.stashlink.slotlock.SlotLocks;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dá "memória" de slots travados a todo container de bloco (baú, barril, shulker, funil...). Grava e lê junto
 * com o resto dos dados do bloco, então sobrevive a reiniciar o servidor e some junto com o bloco quebrado
 * (a trava nunca fica "órfã" num lugar onde depois se coloque outro baú).
 */
@Mixin(BaseContainerBlockEntity.class)
public abstract class BaseContainerBlockEntityMixin implements SlotLockHolder {
    @Unique
    private final Map<Integer, Item> stashlink$locks = new HashMap<>();

    @Override
    public Map<Integer, Item> stashlink$locks() {
        return stashlink$locks;
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void stashlink$save(ValueOutput output, CallbackInfo ci) {
        if (!stashlink$locks.isEmpty()) {
            List<SlotLocks.Saved> list = new ArrayList<>();
            stashlink$locks.forEach((slot, item) -> list.add(new SlotLocks.Saved(slot, item)));
            output.store(SlotLocks.KEY, SlotLocks.CODEC, list);
        }
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void stashlink$load(ValueInput input, CallbackInfo ci) {
        stashlink$locks.clear();
        input.read(SlotLocks.KEY, SlotLocks.CODEC).ifPresent(list -> {
            for (SlotLocks.Saved saved : list) {
                stashlink$locks.put(saved.slot(), saved.item());
            }
        });
    }
}
