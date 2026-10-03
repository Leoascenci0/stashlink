package io.github.leoascenci0.stashlink.client;

import io.github.leoascenci0.stashlink.label.LabelText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * O "lápis" na tela do baú: um botão ✎ ao lado do título que abre, logo acima da tela, dois campos (nome e
 * resumo). Enter ou o lápis de novo salvam; Esc fecha a tela como sempre. O servidor limpa e valida o texto.
 */
public final class LabelPanel {
    private final BlockPos pos;
    private final EditBox name;
    private final EditBox note;
    private final Button pen;
    private final java.util.function.Consumer<net.minecraft.client.gui.components.events.GuiEventListener> focus;
    private boolean open;

    private LabelPanel(BlockPos pos, int left, int top, int width,
                       java.util.function.Consumer<net.minecraft.client.gui.components.events.GuiEventListener> focus) {
        this.focus = focus;
        Minecraft mc = Minecraft.getInstance();
        this.pos = pos;
        int boxY = Math.max(2, top - 24);
        int nameWidth = Math.min(110, width / 2 - 2);
        name = new EditBox(mc.font, left, boxY, nameWidth, 20,
                Component.translatableWithFallback("stashlink.label.name", "Name"));
        name.setMaxLength(LabelText.MAX_NAME);
        name.setHint(Component.translatableWithFallback("stashlink.label.name", "Name"));
        note = new EditBox(mc.font, left + nameWidth + 4, boxY, width - nameWidth - 4, 20,
                Component.translatableWithFallback("stashlink.label.note", "What is inside"));
        note.setMaxLength(LabelText.MAX_NOTE);
        note.setHint(Component.translatableWithFallback("stashlink.label.note", "What is inside"));
        name.setVisible(false);
        note.setVisible(false);
        pen = Button.builder(Component.literal("✎"), b -> toggle())
                .bounds(left + width - 20, top + 3, 14, 14)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                        Component.translatableWithFallback("stashlink.label.pen", "Name this container")))
                .build();
    }

    /** Cria o painel para a tela de container aberta, se for possível rotular (servidor com o mod e bloco mirado). */
    public static LabelPanel create(int left, int top, int width,
                                    java.util.function.Consumer<net.minecraft.client.gui.components.events.GuiEventListener> focus) {
        BlockPos looked = LabelClient.lookedAtContainer();
        return looked == null ? null : new LabelPanel(looked, left, top, width, focus);
    }

    public List<AbstractWidget> widgets() {
        return List.of(pen, name, note);
    }

    public BlockPos pos() {
        return pos;
    }

    private void toggle() {
        if (open) {
            save();
        } else {
            open = true;
            name.setVisible(true);
            note.setVisible(true);
            name.setValue("");
            note.setValue("");
            LabelClient.fetch(this);
        }
    }

    /** O servidor mandou o texto atual. */
    public void fill(String nameText, String noteText) {
        name.setValue(nameText);
        note.setValue(noteText);
        focus.accept(name);
    }

    private void save() {
        LabelClient.save(pos, name.getValue(), note.getValue());
        open = false;
        name.setVisible(false);
        note.setVisible(false);
        focus.accept(null);
    }

    /**
     * Teclas enquanto se digita: Enter salva; Esc segue o caminho normal (fecha a tela); o resto vai para o
     * campo e <b>nunca</b> para o jogo (senão E fecharia o baú e os números trocariam itens). Devolve se tratou.
     */
    public boolean onKey(KeyEvent event) {
        if (!open) {
            return false;
        }
        EditBox box = note.isFocused() ? note : name.isFocused() ? name : null;
        if (box == null) {
            return false;
        }
        if (event.isEscape()) {
            return false;
        }
        if (event.isConfirmation()) {
            save();
            return true;
        }
        return box.keyPressed(event) || box.canConsumeInput();
    }
}
