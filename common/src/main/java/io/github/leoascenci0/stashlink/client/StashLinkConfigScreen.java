package io.github.leoascenci0.stashlink.client;

import io.github.leoascenci0.stashlink.config.StashLinkConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.Set;
import java.util.TreeSet;

/**
 * Tela de configuração (Mod Menu no Fabric, botão "Config" da lista de mods no NeoForge). Usa só widgets do
 * jogo, então é a mesma nos dois loaders e não exige Cloth Config.
 *
 * <p>Edita a config do lado que roda a lógica. Num servidor remoto esse lado é o servidor, que o cliente não
 * alcança: aí a tela fica somente leitura e manda usar {@code /stashlink} ou o arquivo do servidor.
 */
public class StashLinkConfigScreen extends Screen {
    private static final int WIDTH = 200;
    private static final int ROW = 24;

    private final Screen parent;
    private final boolean editable;

    public StashLinkConfigScreen(Screen parent) {
        super(Component.translatableWithFallback("stashlink.config.title", "StashLink settings"));
        this.parent = parent;
        // Sem servidor local mas com mundo aberto = conectado a servidor remoto.
        this.editable = minecraft == null || minecraft.level == null || minecraft.getSingleplayerServer() != null;
    }

    @Override
    protected void init() {
        int x = this.width / 2 - WIDTH / 2;
        int y = this.height / 6 + 24;

        addRenderableWidget(new RadiusSlider(x, y)).active = editable;

        y += ROW;
        Button chests = addRenderableWidget(Button.builder(chestsLabel(), b -> {
            StashLinkConfig.includeChests = !StashLinkConfig.includeChests;
            b.setMessage(chestsLabel());
        }).bounds(x, y, WIDTH, 20).build());
        chests.active = editable;

        y += ROW + 12;
        EditBox slots = addRenderableWidget(new EditBox(this.font, x, y, WIDTH, 20,
                Component.translatableWithFallback("stashlink.config.locked_slots", "Locked slots")));
        slots.setMaxLength(120);
        slots.setValue(joinSlots(StashLinkConfig.lockedSlots));
        slots.setResponder(text -> StashLinkConfig.lockedSlots = parseSlots(text));
        slots.active = editable;

        y += ROW + 12;
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(x, y, WIDTH, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int cx = this.width / 2;
        int top = this.height / 6;
        graphics.centeredText(this.font, this.title, cx, top - 6, 0xFFFFFFFF);
        if (!editable) {
            graphics.centeredText(this.font, Component.translatableWithFallback("stashlink.config.remote",
                    "Connected to a server: use /stashlink or the server's stashlink.json"), cx, top + 8, 0xFFFF5555);
        }
        // Rótulo do campo de slots (o EditBox começa em top + 24 + 2*ROW + 12).
        graphics.text(this.font, Component.translatableWithFallback("stashlink.config.locked_slots_hint",
                "Locked slots (0-35, comma-separated; 0-8 = hotbar)"),
                cx - WIDTH / 2, top + 24 + 2 * ROW + 12 - 11, 0xFFAAAAAA);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    /** Gravar ao sair (e não a cada clique) evita reescrever o arquivo dezenas de vezes ao arrastar o slider. */
    @Override
    public void removed() {
        if (editable) {
            StashLinkConfig.save();
        }
    }

    private static Component chestsLabel() {
        return Component.translatableWithFallback("stashlink.config.include_chests",
                "Use chests and barrels as source: %s", StashLinkConfig.includeChests ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF);
    }

    static String joinSlots(Set<Integer> slots) {
        StringBuilder sb = new StringBuilder();
        for (int s : slots) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(s);
        }
        return sb.toString();
    }

    /** Lê "9, 10 11" -> {9,10,11}. Lixo e números fora de 0-35 são ignorados. */
    static Set<Integer> parseSlots(String text) {
        Set<Integer> out = new TreeSet<>();
        for (String part : text.split("[,;\\s]+")) {
            try {
                int s = Integer.parseInt(part);
                if (s >= 0 && s < StashLinkConfig.INVENTORY_SLOTS) {
                    out.add(s);
                }
            } catch (NumberFormatException ignored) {
                // pedaço vazio ou não numérico
            }
        }
        return out;
    }

    /** Slider de 0 até o teto do servidor, em passos de 1 bloco. */
    private static final class RadiusSlider extends AbstractSliderButton {
        RadiusSlider(int x, int y) {
            super(x, y, WIDTH, 20, Component.empty(), toSlider(StashLinkConfig.sourceRadius));
            updateMessage();
        }

        private static double toSlider(int radius) {
            int cap = StashLinkConfig.radiusCap();
            return cap == 0 ? 0 : Math.max(0, Math.min(1, radius / (double) cap));
        }

        private int radius() {
            return (int) Math.round(this.value * StashLinkConfig.radiusCap());
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatableWithFallback("stashlink.config.radius",
                    "Source radius: %s blocks", radius()));
        }

        @Override
        protected void applyValue() {
            StashLinkConfig.trySetRadius(radius());
        }
    }
}
