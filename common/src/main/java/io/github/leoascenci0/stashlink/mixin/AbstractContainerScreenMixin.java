package io.github.leoascenci0.stashlink.mixin;

import io.github.leoascenci0.stashlink.client.SlotLockClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Cliente: Alt + clique trava/destrava o slot, e a prévia é desenhada depois do slot. */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {
    @Shadow
    private Slot getHoveredSlot(double x, double y) {
        throw new AssertionError();
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void stashlink$altClickLocksSlot(MouseButtonEvent event, boolean doubleClick,
                                             CallbackInfoReturnable<Boolean> cir) {
        if (event.hasAltDown()
                && SlotLockClient.onAltClick(Minecraft.getInstance(), (AbstractContainerScreen<?>) (Object) this,
                getHoveredSlot(event.x(), event.y()))) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "extractSlot", at = @At("TAIL"))
    private void stashlink$drawLockPreview(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY,
                                           CallbackInfo ci) {
        SlotLockClient.drawGhost(graphics, slot);
    }
}
