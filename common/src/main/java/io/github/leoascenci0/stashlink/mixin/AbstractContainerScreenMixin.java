package io.github.leoascenci0.stashlink.mixin;

import io.github.leoascenci0.stashlink.client.LabelPanel;
import io.github.leoascenci0.stashlink.client.SlotLockClient;
import io.github.leoascenci0.stashlink.lootall.LootAllService;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Unique;
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
    protected int leftPos;
    @Shadow
    protected int topPos;
    @Shadow
    @Final
    protected int imageWidth;

    @Unique
    private LabelPanel stashlink$labelPanel;


    /** O lápis de rótulo ao lado do título, em baú/barril/shulker (só se o servidor tem o mod e se mirava um bloco). */
    @Inject(method = "init", at = @At("TAIL"))
    private void stashlink$addLabelPen(CallbackInfo ci) {
        AbstractContainerScreen<?> self = (AbstractContainerScreen<?>) (Object) this;
        stashlink$labelPanel = null;
        if (LootAllService.isSupportedMenu(self.getMenu())) {
            stashlink$labelPanel = LabelPanel.create(leftPos, topPos, imageWidth,
                    Minecraft.getInstance().font.width(self.getTitle()), self::setFocused);
            if (stashlink$labelPanel != null) {
                for (AbstractWidget widget : stashlink$labelPanel.widgets()) {
                    ((ScreenInvoker) (Object) this).stashlink$addRenderableWidget(widget);
                }
            }
        }
    }
/** Fechar a tela grava o texto que ficou no campo. */    @Inject(method = "removed", at = @At("HEAD"))    private void stashlink$saveLabelOnClose(CallbackInfo ci) {        if (stashlink$labelPanel != null) {            stashlink$labelPanel.onClose();        }    }

    /** Digitando no campo do rótulo, as teclas pertencem ao campo (E não fecha o baú). */
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void stashlink$typingInLabel(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (stashlink$labelPanel != null && stashlink$labelPanel.onKey(event)) {
            cir.setReturnValue(true);
        }
    }
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
