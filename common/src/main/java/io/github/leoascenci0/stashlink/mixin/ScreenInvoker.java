package io.github.leoascenci0.stashlink.mixin;

import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Deixa o mod chamar {@code Screen.addRenderableWidget} (protegido). Tem de ser um mixin em {@code Screen}: o
 * {@code @Shadow} de método herdado não funciona num mixin de {@code AbstractContainerScreen} (o jogo não abria).
 */
@Mixin(Screen.class)
public interface ScreenInvoker {
    @Invoker("addRenderableWidget")
    <T extends GuiEventListener & Renderable & NarratableEntry> T stashlink$addRenderableWidget(T widget);
}
