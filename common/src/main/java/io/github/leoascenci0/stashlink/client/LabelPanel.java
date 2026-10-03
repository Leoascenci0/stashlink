package io.github.leoascenci0.stashlink.client;

import io.github.leoascenci0.stashlink.label.LabelText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * O "lápis" na tela do baú. Fechado: o nome do baú aparece como texto comum ao lado do título ("Baú"), sem
 * fundo, e o botão mostra ✎. Clicar no ✎ abre o campo de digitação e o botão vira ✔; clicar no ✔ (ou Enter)
 * grava e fecha o campo. Fechar a tela também grava. O servidor limpa e valida o texto.
 */
public final class LabelPanel {
    private static final Component PEN = Component.literal("✎");
    private static final Component CHECK = Component.literal("✔");

    private final BlockPos pos;
    private final EditBox field;
    private final Button pen;
    private final Consumer<GuiEventListener> focus;
    private final int titleWidth;
    private final int textX;
    private final int textWidth;
    private boolean editing;
    /** O que o servidor tem gravado (também é o que se mostra com o campo fechado). */
    private String saved = "";

    private LabelPanel(BlockPos pos, int left, int top, int width, int titleWidth, Consumer<GuiEventListener> focus) {
        Minecraft mc = Minecraft.getInstance();
        this.pos = pos;
        this.focus = focus;
        this.titleWidth = titleWidth;
        int penX = left + width - 22;                       // o botão "N" (ReceivePanel) fica à esquerda do lápis
        int fieldX = left + 8 + titleWidth + 6;
        this.textX = 8 + titleWidth + 6;
        this.textWidth = Math.max(40, penX - ReceivePanel.RESERVED - 3 - fieldX);
        field = new EditBox(mc.font, fieldX, top + 3, textWidth, 14,
                Component.translatableWithFallback("stashlink.label.name", "Name"));
        field.setMaxLength(LabelText.MAX_NAME);
        field.setHint(Component.translatableWithFallback("stashlink.label.hint_short", "What is in here?"));
        field.setVisible(false);
        pen = Button.builder(PEN, b -> onPen())
                .bounds(penX, top + 3, 14, 14)
                .tooltip(Tooltip.create(Component.translatableWithFallback("stashlink.label.pen", "Name this container")))
                .build();
    }

    /** Cria o painel para a tela de container aberta, se for possível rotular (servidor com o mod e bloco mirado). */
    public static LabelPanel create(int left, int top, int width, int titleWidth, Consumer<GuiEventListener> focus) {
        BlockPos looked = LabelClient.lookedAtContainer();
        if (looked == null) {
            return null;
        }
        LabelPanel panel = new LabelPanel(looked, left, top, width, titleWidth, focus);
        LabelClient.fetch(panel, true);
        return panel;
    }

    public List<AbstractWidget> widgets() {
        return List.of(field, pen);
    }

    public BlockPos pos() {
        return pos;
    }

    private void onPen() {
        if (editing) {
            finish();
        } else {
            editing = true;
            field.setValue(saved);
            field.setVisible(true);
            pen.setMessage(CHECK);
            focus.accept(field);
        }
    }

    /** Grava e volta ao texto simples. */
    private void finish() {
        save();
        editing = false;
        field.setVisible(false);
        pen.setMessage(PEN);
        focus.accept(null);
    }

    /** O servidor mandou o texto atual. Não atropela o que a pessoa já está digitando. */
    public void fill(String name) {
        saved = name;
        if (!editing) {
            field.setValue(name);
        }
    }

    private void save() {
        if (!editing) {
            return;
        }
        String value = LabelText.sanitize(field.getValue(), LabelText.MAX_NAME);
        if (!value.equals(saved)) {
            LabelClient.save(pos, value, "");
            saved = value;
        }
    }

    /** A tela está fechando: grava o que ficou digitado. */
    public void onClose() {
        save();
    }

    /** Com o campo fechado, desenha o nome como o título: mesma cor, sem sombra e sem fundo. Coordenadas relativas à tela. */
    public void drawName(GuiGraphicsExtractor graphics) {
        if (editing || saved.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        var clipped = mc.font.substrByWidth(LabelText.toComponent(saved), textWidth);
        graphics.text(mc.font, Language.getInstance().getVisualOrder(clipped), textX, 6, 0xFF404040, false);
    }

    /**
     * Teclas enquanto se digita: Enter grava e fecha o campo; Esc segue o caminho normal (fecha a tela, e fechar
     * grava); o resto vai para o campo e <b>nunca</b> para o jogo (senão E fecharia o baú).
     */
    public boolean onKey(KeyEvent event) {
        if (!editing || !field.isFocused() || event.isEscape()) {
            return false;
        }
        if (event.isConfirmation()) {
            finish();
            return true;
        }
        return field.keyPressed(event) || field.canConsumeInput();
    }
}
