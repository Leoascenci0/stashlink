package io.github.leoascenci0.stashlink.client;

import io.github.leoascenci0.stashlink.compat.mc.ClientCompat;
import io.github.leoascenci0.stashlink.label.LabelText;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.function.BiConsumer;

/**
 * Editor da tecla J (fora do baú): um campo com o nome do container. Aceita símbolos como ❤ ⭐ ⚡ e atalhos
 * {@code :apple:} (ícone do item). O servidor limpa e corta o texto de novo.
 */
public class LabelEditScreen extends Screen {
    private static final int WIDTH = 240;

    private final String name;
    private final BiConsumer<String, String> onSave;
    private EditBox nameBox;

    public LabelEditScreen(BlockPos pos, String name, String note, BiConsumer<String, String> onSave) {
        super(Component.translatableWithFallback("stashlink.label.title", "Container label"));
        this.name = name;
        this.onSave = onSave;
    }

    @Override
    protected void init() {
        int x = this.width / 2 - WIDTH / 2;
        int y = this.height / 4 + 20;
        nameBox = addRenderableWidget(new EditBox(this.font, x, y, WIDTH, 20,
                Component.translatableWithFallback("stashlink.label.name", "Name")));
        nameBox.setMaxLength(LabelText.MAX_NAME);
        nameBox.setValue(name);
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> {
            onSave.accept(nameBox.getValue(), "");
            onClose();
        }).bounds(x, y + 36, WIDTH / 2 - 2, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
                .bounds(x + WIDTH / 2 + 2, y + 36, WIDTH / 2 - 2, 20).build());
        setInitialFocus(nameBox);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int x = this.width / 2 - WIDTH / 2;
        int y = this.height / 4 + 20;
        graphics.centeredText(this.font, this.title, this.width / 2, y - 24, 0xFFFFFFFF);
        graphics.text(this.font, Component.translatableWithFallback("stashlink.label.name", "Name"), x, y - 11, 0xFFAAAAAA);
        graphics.text(this.font, Component.translatableWithFallback("stashlink.label.hint",
                "Symbols and :apple: :heart: :diamond_pickaxe: work. Leave empty to remove."),
                x, y + 62, 0xFF777777);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            ClientCompat.openScreen(minecraft, null);
        }
    }
}
