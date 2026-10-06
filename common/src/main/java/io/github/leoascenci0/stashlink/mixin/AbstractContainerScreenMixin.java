package io.github.leoascenci0.stashlink.mixin;

import io.github.leoascenci0.stashlink.client.BenchPanel;
import io.github.leoascenci0.stashlink.client.BenchBeaconButtons;
import io.github.leoascenci0.stashlink.client.PanelZones;
import io.github.leoascenci0.stashlink.compat.mc.BenchCompat;
import io.github.leoascenci0.stashlink.client.LabelPanel;
import io.github.leoascenci0.stashlink.client.ReceivePanel;
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

    /** Botão "N" (recebe com a tecla N), Item 17. */
    @Unique
    private ReceivePanel stashlink$receivePanel;

    /** Painel "Armazenamento" das estações (Item 16); só existe em tela de estação. */
    @Unique
    private BenchPanel stashlink$benchPanel;


    /** Ícones de pagamento clicáveis do sinalizador (Item 16.3); só existe no sinalizador. */
    @Unique
    private BenchBeaconButtons stashlink$beaconButtons;


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

    /** O botão "N" (recebe itens com a tecla N) na linha do título, em baú/barril/shulker. */
    @Inject(method = "init", at = @At("TAIL"))
    private void stashlink$addReceiveButton(CallbackInfo ci) {
        AbstractContainerScreen<?> self = (AbstractContainerScreen<?>) (Object) this;
        stashlink$receivePanel = null;
        if (LootAllService.isSupportedMenu(self.getMenu())) {
            stashlink$receivePanel = ReceivePanel.create(self.getMenu(), Minecraft.getInstance().player.getInventory(),
                    leftPos, topPos, imageWidth);
            if (stashlink$receivePanel != null) {
                for (AbstractWidget widget : stashlink$receivePanel.widgets()) {
                    ((ScreenInvoker) (Object) this).stashlink$addRenderableWidget(widget);
                }
            }
        }
    }

    /** O botão acompanha o que o servidor contou (a resposta chega depois de a tela abrir). */
    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void stashlink$refreshReceiveButton(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta,
                                                CallbackInfo ci) {
        if (stashlink$receivePanel != null) {
            stashlink$receivePanel.refresh();
        }
    }

    /** O painel "Armazenamento" ao lado da estação (bancada, fornalha, ferreiro...). Só aparece se o servidor mandou a lista. */
    @Inject(method = "init", at = @At("TAIL"))
    private void stashlink$addBenchPanel(CallbackInfo ci) {
        AbstractContainerScreen<?> self = (AbstractContainerScreen<?>) (Object) this;
        stashlink$benchPanel = BenchPanel.create(self.getMenu(), leftPos, topPos, imageWidth, self);
        if (stashlink$benchPanel != null) {
            // As duas telas viram um bloco só, centralizado (como o livro de receitas da fornalha).
            if (io.github.leoascenci0.stashlink.client.BenchClient.expectedFor(self.getMenu())) {
                leftPos = BenchPanel.stationLeft(self.width, imageWidth, leftPos);
                // Encantamento e suporte de poções calculam o próprio x como (width - imageWidth) / 2 e ignoram leftPos
                // (fundo, clique, desenho): a largura que eles enxergam passa a ser a que dá exatamente leftPos.
                if (BenchCompat.ignoresLeftPos(self.getMenu())) {
                    self.width = BenchCompat.widthForLeftPos(leftPos, imageWidth);
                }
                stashlink$benchPanel.layout(leftPos, topPos, imageWidth);
            }
            for (AbstractWidget widget : stashlink$benchPanel.widgets()) {
                ((ScreenInvoker) (Object) this).stashlink$addRenderableWidget(widget);
            }
            PanelZones.register(self, stashlink$benchPanel::zones);
        } else {
            PanelZones.clear(self);
        }
    }

    /** Fechar a tela libera a zona (JEI/REI deixam de reservar o espaço). */
    @Inject(method = "removed", at = @At("HEAD"))
    private void stashlink$clearPanelZones(CallbackInfo ci) {
        PanelZones.clear((AbstractContainerScreen<?>) (Object) this);
    }

    /** Os ícones de pagamento clicáveis do sinalizador. */
    @Inject(method = "init", at = @At("TAIL"))
    private void stashlink$addBeaconButtons(CallbackInfo ci) {
        AbstractContainerScreen<?> self = (AbstractContainerScreen<?>) (Object) this;
        stashlink$beaconButtons = BenchBeaconButtons.create(self.getMenu());
    }

    /** Desenha os botões do sinalizador. */
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void stashlink$drawBeaconButtons(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta,
                                          CallbackInfo ci) {
        if (stashlink$beaconButtons != null) {
            stashlink$beaconButtons.layout(leftPos, topPos);
            stashlink$beaconButtons.draw(graphics, mouseX, mouseY);
        }
    }

    /** Clique num ícone do sinalizador: pede ao servidor; o clique não chega ao jogo. */
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void stashlink$clickBeaconButtons(MouseButtonEvent event, boolean doubleClick,
                                           CallbackInfoReturnable<Boolean> cir) {
        if (stashlink$beaconButtons != null) {
            stashlink$beaconButtons.layout(leftPos, topPos);
            if (stashlink$beaconButtons.mouseClicked(event)) {
                cir.setReturnValue(true);
            }
        }
    }

    /**
     * O campo de nome da bigorna é criado com o centro da janela e não com {@code leftPos} (peculiaridade do 26.3), então
     * não acompanha a estação quando o painel a desloca: aqui ele volta para dentro do fundo dela, a cada quadro.
     */
    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void stashlink$keepAnvilNameFieldInPlace(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta,
                                                     CallbackInfo ci) {
        AbstractContainerScreen<?> self = (AbstractContainerScreen<?>) (Object) this;
        if (stashlink$benchPanel == null || !BenchCompat.isAnvil(self.getMenu())) {
            return;
        }
        for (net.minecraft.client.gui.components.events.GuiEventListener child : self.children()) {
            if (child instanceof net.minecraft.client.gui.components.EditBox box && box.getWidth() == BenchCompat.ANVIL_NAME_FIELD_WIDTH
                    && box.getX() != leftPos + BenchCompat.ANVIL_NAME_FIELD_DX) {
                box.setX(leftPos + BenchCompat.ANVIL_NAME_FIELD_DX);
            }
        }
    }

    /** Desenha o painel por cima de tudo (depois dos slots), com a dica do item sob o mouse. */
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void stashlink$drawBenchPanel(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta,
                                          CallbackInfo ci) {
        stashlink$drawPanel(graphics, mouseX, mouseY, delta);
    }

    /**
     * Telas com livro de receitas (as fornalhas): no 26.3 o {@code extractRenderState} delas não chama o desta classe,
     * só o {@code extractContents}. O painel é desenhado aqui nelas (e só nelas, para nunca desenhar duas vezes), e o
     * botão do livro (20x18, o único botão de imagem desse tamanho) some, já que o livro fica fechado.
     */
    @Inject(method = "extractContents", at = @At("TAIL"))
    private void stashlink$drawBenchPanelOverRecipeBookScreens(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                                               float delta, CallbackInfo ci) {
        AbstractContainerScreen<?> self = (AbstractContainerScreen<?>) (Object) this;
        if (!(self instanceof net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen<?>)) {
            return;
        }
        if (stashlink$benchPanel != null && BenchCompat.hidesRecipeBook(self.getMenu())
                && io.github.leoascenci0.stashlink.client.BenchClient.expectedFor(self.getMenu())) {
            for (GuiEventListener child : self.children()) {
                if (child instanceof net.minecraft.client.gui.components.ImageButton button
                        && button.getWidth() == BenchCompat.RECIPE_BUTTON_WIDTH
                        && button.getHeight() == BenchCompat.RECIPE_BUTTON_HEIGHT) {
                    button.visible = false;
                    button.active = false;
                }
            }
        }
        stashlink$drawPanel(graphics, mouseX, mouseY, delta);
    }

    @Unique
    private void stashlink$drawPanel(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (stashlink$benchPanel != null) {
            stashlink$benchPanel.layout(leftPos, topPos, imageWidth);
            stashlink$benchPanel.draw(graphics, mouseX, mouseY, delta);
        }
    }

    /** Roda do mouse sobre o painel rola a lista (e não troca o item da hotbar). */
    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void stashlink$scrollBenchPanel(double x, double y, double scrollX, double scrollY,
                                            CallbackInfoReturnable<Boolean> cir) {
        if (stashlink$benchPanel != null) {
            stashlink$benchPanel.layout(leftPos, topPos, imageWidth);
        }
        if (stashlink$benchPanel != null && stashlink$benchPanel.mouseScrolled(x, y, scrollY)) {
            cir.setReturnValue(true);
        }
    }

    /** Clique no painel: pede o item ao servidor; nunca chega ao jogo (que trataria como clique fora e soltaria o cursor). */
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void stashlink$clickBenchPanel(MouseButtonEvent event, boolean doubleClick,
                                           CallbackInfoReturnable<Boolean> cir) {
        if (stashlink$benchPanel != null) {
            stashlink$benchPanel.layout(leftPos, topPos, imageWidth);
        }
        if (stashlink$benchPanel != null && stashlink$benchPanel.mouseClicked(event)) {
            cir.setReturnValue(true);
        }
    }

    /** O nome do baú, como texto comum ao lado do título. */
    @Inject(method = "extractLabels", at = @At("TAIL"))
    private void stashlink$drawLabelName(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
        if (stashlink$labelPanel != null) {
            stashlink$labelPanel.drawName(graphics);
        }
    }

    /** Fechar a tela grava o texto que ficou no campo. */
    @Inject(method = "removed", at = @At("HEAD"))
    private void stashlink$saveLabelOnClose(CallbackInfo ci) {
        if (stashlink$labelPanel != null) {
            stashlink$labelPanel.onClose();
        }
    }

    /** Digitando no campo do rótulo, as teclas pertencem ao campo (E não fecha o baú). */
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void stashlink$typingInLabel(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (stashlink$labelPanel != null && stashlink$labelPanel.onKey(event)) {
            cir.setReturnValue(true);
        }
        if (stashlink$benchPanel != null && stashlink$benchPanel.onKey(event)) {
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
