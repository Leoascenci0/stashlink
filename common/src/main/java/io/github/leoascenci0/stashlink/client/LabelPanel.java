package io.github.leoascenci0.stashlink.client;

import io.github.leoascenci0.stashlink.label.LabelText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * O "lápis" na tela do baú: um botão ✎ no canto da linha do título e, ao lado do título, um campo com o nome
 * do baú (o texto que aparece no holograma). Já abre preenchido; Enter, o lápis ou fechar a tela gravam.
 * O servidor limpa e valida o texto.
 */
public final class LabelPanel {
    private final BlockPos pos;
    private final EditBox field;
    private final Button pen;
    private final Consumer<GuiEventListener> focus;
    /** O que o servidor tem gravado (para só enviar quando mudou). */
    private String saved = "";

    private LabelPanel(BlockPos pos, int left, int top, int width, int titleWidth, Consumer<GuiEventListener> focus) {
        Minecraft mc = Minecraft.getInstance();
        this.pos = pos;
        this.focus = focus;
        int penX = left + width - 22;
        int fieldX = left + 8 + titleWidth + 6;
        field = new EditBox(mc.font, fieldX, top + 3, Math.max(40, penX - 3 - fieldX), 14,
                Component.translatableWithFallback("stashlink.label.name", "Name"));
        field.setMaxLength(LabelText.MAX_NAME);
        field.setHint(Component.translatableWithFallback("stashlink.label.hint_short", "What is in here?"));
        pen = Button.builder(Component.literal("✎"), b -> onPen())
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
        if (field.isFocused()) {
            save();
            focus.accept(null);
        } else {
            focus.accept(field);
        }
    }

    /** O servidor mandou o texto atual. Não atropela o que a pessoa já está digitando. */
    public void fill(String name) {
        saved = name;
        if (!field.isFocused()) {
            field.setValue(name);
        }
    }

    private void save() {
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

    /**
     * Teclas enquanto se digita: Enter grava; Esc segue o caminho normal (fecha a tela, e fechar grava); o resto
     * vai para o campo e <b>nunca</b> para o jogo (senão E fecharia o baú e os números trocariam itens).
     */
    public boolean onKey(KeyEvent event) {
        if (!field.isFocused() || event.isEscape()) {
            return false;
        }
        if (event.isConfirmation()) {
            save();
            focus.accept(null);
            return true;
        }
        return field.keyPressed(event) || field.canConsumeInput();
    }
}
