package io.github.leoascenci0.stashlink.mixin;

import io.github.leoascenci0.stashlink.compat.mc.LabelCompat;
import io.github.leoascenci0.stashlink.label.HologramService;
import io.github.leoascenci0.stashlink.label.Label;
import io.github.leoascenci0.stashlink.label.LabelHolder;
import io.github.leoascenci0.stashlink.quickstack.QuickStackReceive;
import io.github.leoascenci0.stashlink.quickstack.ReceiveHolder;
import io.github.leoascenci0.stashlink.slotlock.SlotLockHolder;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
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
public abstract class BaseContainerBlockEntityMixin implements SlotLockHolder, LabelHolder, ReceiveHolder {
    @Unique
    private final Map<Integer, Item> stashlink$locks = new HashMap<>();

    @Override
    public Map<Integer, Item> stashlink$locks() {
        return stashlink$locks;
    }

    @Unique
    private Label stashlink$label = Label.EMPTY;

    @Override
    public Label stashlink$label() {
        return stashlink$label;
    }

    @Override
    public void stashlink$setLabel(Label label) {
        stashlink$label = label;
    }

    /** Botão "recebe itens com a tecla N" (Item 17). Padrão ligado; só o estado DESLIGADO vai para o disco. */
    @Unique
    private boolean stashlink$receivesQuickStack = true;

    @Override
    public boolean stashlink$receivesQuickStack() {
        return stashlink$receivesQuickStack;
    }

    @Override
    public void stashlink$setReceivesQuickStack(boolean receives) {
        stashlink$receivesQuickStack = receives;
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void stashlink$saveLabel(ValueOutput output, CallbackInfo ci) {
        if (!stashlink$label.isEmpty()) {
            output.store(Label.KEY, Label.CODEC, stashlink$label);
        }
        if (!stashlink$receivesQuickStack) {
            output.putBoolean(QuickStackReceive.KEY, true);
        }
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void stashlink$loadReceives(ValueInput input, CallbackInfo ci) {
        stashlink$receivesQuickStack = !input.getBooleanOr(QuickStackReceive.KEY, false);
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void stashlink$loadLabel(ValueInput input, CallbackInfo ci) {
        stashlink$label = input.read(Label.KEY, Label.CODEC).map(Label::sanitized).orElse(Label.EMPTY);
        if (!stashlink$label.isEmpty()) {
            HologramService.track((BlockEntity) (Object) this);
        }
    }

    /**
     * Só a shulker leva o rótulo e o botão da N no item que solta (baú e barril os perdem ao quebrar, por decisão do
     * Eliel). Os dois vão juntos no mesmo CUSTOM_DATA.
     */
    @Inject(method = "collectImplicitComponents", at = @At("TAIL"))
    private void stashlink$labelToItem(DataComponentMap.Builder builder, CallbackInfo ci) {
        if ((Object) this instanceof ShulkerBoxBlockEntity) {
            LabelCompat.writeToItem(builder, stashlink$label, !stashlink$receivesQuickStack);
        }
    }

    @Inject(method = "applyImplicitComponents", at = @At("TAIL"))
    private void stashlink$labelFromItem(DataComponentGetter components, CallbackInfo ci) {
        if ((Object) this instanceof ShulkerBoxBlockEntity) {
            Label label = LabelCompat.readFromItem(components).sanitized();
            if (!label.isEmpty()) {
                stashlink$label = label;
                HologramService.track((BlockEntity) (Object) this);
            }
            if (LabelCompat.readNoQuickStackFromItem(components)) {
                stashlink$receivesQuickStack = false;
            }
        }
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
