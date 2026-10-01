package io.github.leoascenci0.stashlink.client;

import io.github.leoascenci0.stashlink.compat.mc.ClientCompat;
import io.github.leoascenci0.stashlink.config.PlayerPrefs;
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
 * <p>Em mundo local edita a config do jogo (que roda a lógica). Num servidor/Realms o cliente não alcança a
 * config do servidor, então a tela edita as <b>preferências pessoais</b> ({@link ClientPrefs}), que o servidor
 * recebe e limita. Assim funciona mesmo sem comandos nem acesso a arquivos.
 */
public class StashLinkConfigScreen extends Screen {
    private static final int WIDTH = 200;
    private static final int ROW = 24;

    private final Screen parent;
    private final boolean local;

    public StashLinkConfigScreen(Screen parent) {
        super(Component.translatableWithFallback("stashlink.config.title", "StashLink settings"));
        this.parent = parent;
        // Sem servidor local mas com mundo aberto = conectado a servidor remoto.
        this.local = minecraft == null || minecraft.level == null || minecraft.getSingleplayerServer() != null;
    }

    @Override
    protected void init() {
        int x = this.width / 2 - WIDTH / 2;
        int y = this.height / 6 + 24;

        addRenderableWidget(new RadiusSlider(x, y, local));

        y += ROW;
        addRenderableWidget(Button.builder(chestsLabel(), b -> {
            if (local) {
                StashLinkConfig.includeChests = !StashLinkConfig.includeChests;
            } else {
                // Padrão do servidor -> sim -> não -> padrão do servidor.
                ClientPrefs.chests = ClientPrefs.chests == PlayerPrefs.UNSET ? 1
                        : (ClientPrefs.chests == 1 ? 0 : PlayerPrefs.UNSET);
            }
            b.setMessage(chestsLabel());
        }).bounds(x, y, WIDTH, 20).build());

        y += ROW + 12;
        EditBox slots = addRenderableWidget(new EditBox(this.font, x, y, WIDTH, 20,
                Component.translatableWithFallback("stashlink.config.locked_slots", "Locked slots")));
        slots.setMaxLength(120);
        slots.setValue(joinSlots(local ? StashLinkConfig.lockedSlots : ClientPrefs.lockedSlots));
        slots.setResponder(text -> {
            if (local) {
                StashLinkConfig.lockedSlots = parseSlots(text);
            } else {
                ClientPrefs.lockedSlots = new TreeSet<>(parseSlots(text));
            }
        });

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
        if (!local) {
            graphics.centeredText(this.font, Component.translatableWithFallback("stashlink.config.remote",
                    "On a server: these are your personal settings (the server may limit them)"), cx, top + 8, 0xFFFFFF55);
        }
        // Rótulo do campo de slots (o EditBox começa em top + 24 + 2*ROW + 12).
        graphics.text(this.font, Component.translatableWithFallback("stashlink.config.locked_slots_hint",
                "Locked slots (0-35, comma-separated; 0-8 = hotbar)"),
                cx - WIDTH / 2, top + 24 + 2 * ROW + 12 - 11, 0xFFAAAAAA);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            ClientCompat.openScreen(minecraft, parent);
        }
    }

    /** Gravar ao sair (e não a cada clique) evita reescrever o arquivo dezenas de vezes ao arrastar o slider. */
    @Override
    public void removed() {
        if (local) {
            StashLinkConfig.save();
        } else {
            ClientPrefs.save();
            ClientPrefs.sync();
        }
    }

    private Component chestsLabel() {
        Component value;
        if (local) {
            value = StashLinkConfig.includeChests ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF;
        } else if (ClientPrefs.chests == PlayerPrefs.UNSET) {
            value = Component.translatableWithFallback("stashlink.config.server_default", "Server default");
        } else {
            value = ClientPrefs.chests == 1 ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF;
        }
        return Component.translatableWithFallback("stashlink.config.include_chests",
                "Use chests and barrels as source: %s", value);
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

    /** Slider de 0 até o teto (local: o do jogo; servidor: o do código, e o servidor limita), passos de 1 bloco. */
    private static final class RadiusSlider extends AbstractSliderButton {
        private final boolean local;

        RadiusSlider(int x, int y, boolean local) {
            super(x, y, WIDTH, 20, Component.empty(), toSlider(local, initial(local)));
            this.local = local;
            updateMessage();
        }

        private static int cap(boolean local) {
            return local ? StashLinkConfig.radiusCap() : StashLinkConfig.HARD_MAX_RADIUS;
        }

        /** Num servidor, sem preferência ainda, mostra o padrão do código; a preferência só vira "escolhida" ao mexer. */
        private static int initial(boolean local) {
            return local || ClientPrefs.radius == PlayerPrefs.UNSET ? StashLinkConfig.sourceRadius : ClientPrefs.radius;
        }

        private static double toSlider(boolean local, int radius) {
            int cap = cap(local);
            return cap == 0 ? 0 : Math.max(0, Math.min(1, radius / (double) cap));
        }

        private int radius() {
            return (int) Math.round(this.value * cap(local));
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatableWithFallback("stashlink.config.radius",
                    "Source radius: %s blocks", radius()));
        }

        @Override
        protected void applyValue() {
            if (local) {
                StashLinkConfig.trySetRadius(radius());
            } else {
                ClientPrefs.radius = radius();
            }
        }
    }
}
