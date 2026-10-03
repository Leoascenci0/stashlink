package io.github.leoascenci0.stashlink.mixin;

import io.github.leoascenci0.stashlink.slotlock.SlotLocks;
import net.minecraft.world.inventory.ShulkerBoxSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Slot reservado só aceita o seu item. {@code Slot.mayPlace} é o que o menu consulta em clique, shift-clique,
 * arrastar e troca por número, nos dois lados (o cliente já recusa o que o servidor recusaria, sem "piscar").
 * {@code ShulkerBoxSlot} sobrescreve o método sem chamar o da classe pai, então precisa do seu gancho.
 */
@Mixin({Slot.class, ShulkerBoxSlot.class})
public abstract class SlotMixin {
    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void stashlink$respectLock(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        Slot self = (Slot) (Object) this;
        if (!SlotLocks.mayPlace(self.container, self.getContainerSlot(), stack)) {
            cir.setReturnValue(false);
        }
    }
}
