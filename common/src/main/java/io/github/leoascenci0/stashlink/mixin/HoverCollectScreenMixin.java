package io.github.leoascenci0.stashlink.mixin;

import io.github.leoascenci0.stashlink.client.HoverCollectClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import io.github.leoascenci0.stashlink.compat.mc.ClientCompat;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Cliente (Item 21): Shift + passar o mouse coleta o item do slot. Mixin à parte do da tela para não se misturar. */
@Mixin(AbstractContainerScreen.class)
public abstract class HoverCollectScreenMixin {
    @Shadow
    private Slot getHoveredSlot(double x, double y) {
        throw new AssertionError();
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void stashlink$hoverCollect(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta,
                                        CallbackInfo ci) {
        HoverCollectClient.onHover(Minecraft.getInstance(), (AbstractContainerScreen<?>) (Object) this,
                getHoveredSlot(mouseX, mouseY));
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"))
    private void stashlink$leftPressed(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (ClientCompat.isLeftButton(event)) {
            HoverCollectClient.setLeftDown(true);
        }
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"))
    private void stashlink$leftDragged(MouseButtonEvent event, double dx, double dy, CallbackInfoReturnable<Boolean> cir) {
        if (ClientCompat.isLeftButton(event)) {
            HoverCollectClient.dragSeen();
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"))
    private void stashlink$leftReleased(MouseButtonEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (ClientCompat.isLeftButton(event)) {
            HoverCollectClient.setLeftDown(false);
        }
    }

    /** Tela nova: nenhum botão conta como apertado (soltar fora da tela não chega aqui). */
    @Inject(method = "init", at = @At("TAIL"))
    private void stashlink$resetButton(CallbackInfo ci) {
        HoverCollectClient.setLeftDown(false);
    }
}
