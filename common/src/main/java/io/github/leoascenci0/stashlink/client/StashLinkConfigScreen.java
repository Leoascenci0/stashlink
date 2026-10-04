package io.github.leoascenci0.stashlink.client;

import io.github.leoascenci0.stashlink.compat.mc.ClientCompat;
import io.github.leoascenci0.stashlink.config.ClientPolicy;
import io.github.leoascenci0.stashlink.compat.mc.LabelCompat;
import io.github.leoascenci0.stashlink.config.Feature;
import io.github.leoascenci0.stashlink.quickstack.ItemCategory;
import io.github.leoascenci0.stashlink.config.PlayerPrefs;
import io.github.leoascenci0.stashlink.config.StashLinkConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.LockIconButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Tela de configuração (Mod Menu no Fabric, botão "Config" da lista de mods no NeoForge). Usa só widgets do
 * jogo, então é a mesma nos dois loaders e não exige Cloth Config.
 *
 * <p>Duas abas. <b>Funções</b>: cada função do mod tem um botão liga/desliga (a sua escolha) e um cadeado ao lado,
 * igual ao seletor de dificuldade do jogo; trancada, a função não funciona naquele servidor e o botão fica
 * desligado. O cadeado é do servidor (dono do mundo ou operador, conferido pelo servidor); o liga/desliga é
 * pessoal. <b>Ajustes</b>: raio, baús e slots travados.
 *
 * <p>Em mundo local edita a config do jogo (que roda a lógica). Num servidor/Realms o cliente não alcança a
 * config do servidor, então a tela edita as <b>preferências pessoais</b> ({@link ClientPrefs}), que o servidor
 * recebe e limita. Assim funciona mesmo sem comandos nem acesso a arquivos.
 */
public class StashLinkConfigScreen extends Screen {
    private static final int WIDTH = 200;
    private static final int ROW = 24;
    /** Linha de função: botão + 4 px + cadeado de 20 px = WIDTH, igual ao seletor de dificuldade do jogo. */
    private static final int LOCK_SIZE = 20;
    private static final int FEATURE_ROW = 22;

    private static final int PAGE_FEATURES = 0;
    private static final int PAGE_SETTINGS = 1;
    private static final int PAGE_CATEGORIES = 2;

    private final Screen parent;
    private final boolean local;
    /** Sem mundo aberto (tela de mods no menu inicial): não há servidor, os cadeados editam o arquivo local. */
    private final boolean noWorld;
    private int page = PAGE_FEATURES;
    /** Última versão da política do servidor vista: se mudar (resposta do servidor), a tela se redesenha. */
    private int seenPolicyVersion;
    private boolean requestedPolicy;
    /** Posições (y) calculadas em init(), usadas ao desenhar os textos. */
    private boolean clientModeActive;
    private int infoY;
    private int slotsLabelY;
    /** Rolagem da lista (px). Widgets da lista, y original de cada um e a janela visível entre abas e rodapé. */
    private int scroll;
    private int maxScroll;
    private int viewTop;
    private int viewBottom;
    private final List<AbstractWidget> scrollables = new ArrayList<>();
    private final List<Integer> scrollBaseY = new ArrayList<>();

    public StashLinkConfigScreen(Screen parent) {
        super(Component.translatableWithFallback("stashlink.config.title", "StashLink settings"));
        this.parent = parent;
        // Sem servidor local mas com mundo aberto = conectado a servidor remoto.
        Minecraft mc = Minecraft.getInstance();
        this.noWorld = mc.level == null;
        this.local = noWorld || mc.getSingleplayerServer() != null;
    }

    @Override
    protected void init() {
        seenPolicyVersion = ClientPolicy.version();
        if (!noWorld && !requestedPolicy) {
            // Pergunta ao servidor a política atual (cadeados e se posso mexer neles); a resposta redesenha a tela.
            requestedPolicy = true;
            ClientPrefs.sync();
        }
        int x = this.width / 2 - WIDTH / 2;
        int top = this.height / 6;

        // Abas: as funções (liga/desliga + cadeado) e os ajustes finos (raio, baús, slots).
        int tabW = 50;                              // Funções e Ajustes são curtas; "Itens bloqueados" ganha o resto
        int lastW = WIDTH - 2 * (tabW + 4);
        Button features = addRenderableWidget(Button.builder(
                Component.translatableWithFallback("stashlink.config.tab_features", "Features"),
                b -> switchPage(PAGE_FEATURES)).bounds(x, top + 20, tabW, 20).build());
        features.active = page != PAGE_FEATURES;
        Button settings = addRenderableWidget(Button.builder(
                Component.translatableWithFallback("stashlink.config.tab_settings", "Settings"),
                b -> switchPage(PAGE_SETTINGS)).bounds(x + tabW + 4, top + 20, tabW, 20).build());
        settings.active = page != PAGE_SETTINGS;
        Button categories = addRenderableWidget(Button.builder(
                Component.translatableWithFallback("stashlink.config.tab_categories", "Key N"),
                b -> switchPage(PAGE_CATEGORIES)).bounds(x + 2 * (tabW + 4), top + 20, lastW, 20).build());
        categories.active = page != PAGE_CATEGORIES;

        // Na aba "Tecla N" o texto explicativo fica logo abaixo das abas (a lista rola, o rodapé é fixo).
        int y = page == PAGE_CATEGORIES ? top + 60 : top + 48;
        if (page == PAGE_CATEGORIES) {
            infoY = top + 44;
        }
        viewTop = y;
        int firstScrollable = this.children().size();   // tudo que entra depois das 3 abas rola
        if (page == PAGE_FEATURES) {
            y = initFeatures(x, y, false);
        } else if (page == PAGE_CATEGORIES) {
            y = initFeatures(x, y, true);
        } else {
            y = initSettings(x, y);
        }

        // Cabe na tela: Concluído logo abaixo da lista. Não cabe: Concluído fixo no rodapé e a lista rola.
        int doneY = y + 8;
        if (doneY + 20 > this.height - 6) {
            doneY = this.height - 28;
            viewBottom = doneY - 4;
            maxScroll = Math.max(0, y - viewBottom);
        } else {
            viewBottom = this.height;
            maxScroll = 0;
        }
        scrollables.clear();
        scrollBaseY.clear();
        for (int i = firstScrollable; i < this.children().size(); i++) {
            if (this.children().get(i) instanceof AbstractWidget widget) {
                scrollables.add(widget);
                scrollBaseY.add(widget.getY());
            }
        }
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(x, doneY, WIDTH, 20).build());
        applyScroll();
    }

    /** Reposiciona a lista conforme a rolagem; o que ficaria cortado pelo topo ou pelo rodapé some. */
    private void applyScroll() {
        scroll = Math.max(0, Math.min(maxScroll, scroll));
        for (int i = 0; i < scrollables.size(); i++) {
            AbstractWidget widget = scrollables.get(i);
            int y = scrollBaseY.get(i) - scroll;
            widget.setY(y);
            widget.visible = y >= viewTop && y + widget.getHeight() <= viewBottom;
        }
    }

    /** Roda do mouse rola a lista de funções (uma linha por "clique" da roda). */
    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (maxScroll > 0 && scrollY != 0) {
            scroll -= (int) Math.signum(scrollY) * FEATURE_ROW;
            applyScroll();
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    private void switchPage(int newPage) {
        page = newPage;
        scroll = 0;
        rebuildWidgets();
    }

    @Override
    public void tick() {
        super.tick();
        if (seenPolicyVersion != ClientPolicy.version()) {
            rebuildWidgets();
        }
    }

    // ------------------------------------------------------------------ aba "Funções"

    /** Uma linha por função: botão liga/desliga (pessoal) + cadeado (do servidor), como a dificuldade do jogo. */
    private int initFeatures(int x, int y, boolean categories) {
        for (Feature feature : Feature.values()) {
            if (feature.isSetting()) {
                continue;   // ajustes (raios, usar baús) ficam na aba Ajustes, só com cadeado
            }
            if (feature.isCategory() != categories) {
                continue;   // categorias da N (Item 17) têm aba própria
            }
            boolean locked = isLocked(feature);

            Button toggle = Button.builder(featureLabel(feature, locked), b -> {
                ClientPrefs.setFeatureOn(feature, !ClientPrefs.isFeatureOn(feature));
                b.setMessage(featureLabel(feature, false));
            }).bounds(x, y, WIDTH - LOCK_SIZE - 4, 20).build();
            Component tip = featureTip(feature);
            toggle.setTooltip(Tooltip.create(locked ? tip.copy().append("\n").append(
                    Component.translatableWithFallback("stashlink.feature.locked_here",
                            "This feature is locked on this server")) : tip));
            // Trancada: o botão fica desligado, como a dificuldade do jogo quando está travada.
            toggle.active = !locked;
            addRenderableWidget(toggle);

            LockIconButton lock = new LockIconButton(x + WIDTH - LOCK_SIZE, y, b -> toggleLock(feature));
            lock.setLocked(locked);
            lock.active = canEditLocks();
            lock.setTooltip(Tooltip.create(lockTip(locked)));
            addRenderableWidget(lock);
            y += FEATURE_ROW;
        }
        return y;
    }

    private boolean isLocked(Feature feature) {
        return noWorld ? StashLinkConfig.isFeatureLocked(feature) : ClientFeatures.lockedByServer(feature);
    }

    /** Sem mundo: o arquivo local. Com o mod no servidor: só dono/operador (o servidor confere de novo). */
    private boolean canEditLocks() {
        return noWorld || (ClientMode.serverHasMod() && ClientPolicy.canEdit());
    }

    private void toggleLock(Feature feature) {
        boolean wanted = !isLocked(feature);
        if (noWorld) {
            StashLinkConfig.setFeatureLocked(feature, wanted);
            rebuildWidgets();
        } else {
            // O servidor decide; a resposta (política nova) redesenha a tela.
            ClientFeatures.requestLock(feature, wanted);
        }
    }

    private Component featureLabel(Feature feature, boolean locked) {
        Component state = locked
                ? Component.translatableWithFallback("stashlink.feature.state_locked", "Locked")
                : (ClientPrefs.isFeatureOn(feature) ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF);
        return Component.empty().append(featureName(feature)).append(": ").append(state);
    }

    private static Component featureName(Feature feature) {
        ItemCategory category = ItemCategory.forFeature(feature);
        if (category != null) {
            // "Emoji" da categoria: ícone do item (a fonte do jogo não tem emoji colorido).
            return Component.empty().append(LabelCompat.sprite(true, category.icon())).append(" ")
                    .append(Component.translatable("stashlink.feature." + feature.id()));
        }
        return Component.translatable("stashlink.feature." + feature.id());
    }

    private static Component featureTip(Feature feature) {
        return Component.translatable("stashlink.feature." + feature.id() + ".tip");
    }

    private Component lockTip(boolean locked) {
        if (!canEditLocks()) {
            return ClientMode.serverHasMod()
                    ? Component.translatableWithFallback("stashlink.feature.lock_ops_only",
                            "Only the server owner or an operator can lock features")
                    : Component.translatableWithFallback("stashlink.feature.lock_needs_mod",
                            "Locking needs StashLink on the server");
        }
        return locked
                ? Component.translatableWithFallback("stashlink.feature.unlock_tip",
                        "Locked: this feature does not work here. Click to unlock")
                : Component.translatableWithFallback("stashlink.feature.lock_tip",
                        "Click to lock: this feature will stop working for everyone here");
    }

    // ------------------------------------------------------------------ aba "Ajustes"

    private int initSettings(int x, int y) {
        // No modo cliente o servidor não conhece o mod: raio e "usar baús" do servidor não se aplicam.
        clientModeActive = !local && ClientMode.active();

        if (!local) {
            // Botão do modo cliente: ao mudar, remonta a tela (o que aparece depende de o modo estar ativo).
            addRenderableWidget(Button.builder(clientModeLabel(), b -> {
                ClientPrefs.clientModeEnabled = !ClientPrefs.clientModeEnabled;
                rebuildWidgets();
            }).bounds(x, y, WIDTH, 20).build());
            y += ROW;
            if (clientModeActive) {
                infoY = y;
                y += 12;
            }
        }

        if (!clientModeActive) {
            // Cada ajuste tem um cadeado ao lado, como as funções: trancado, o valor do servidor vale para todos.
            RadiusSlider chestSlider = new RadiusSlider(x, y, local, false, isLocked(Feature.RADIUS));
            chestSlider.setTooltip(Tooltip.create(Component.translatableWithFallback("stashlink.config.radius.tip",
                    "How far chests, barrels and workbenches reach (max 16)")));
            addRenderableWidget(chestSlider);
            addSettingLock(Feature.RADIUS, x, y);
            y += ROW;
            RadiusSlider shulkerSlider = new RadiusSlider(x, y, local, true, isLocked(Feature.SHULKER_RADIUS));
            shulkerSlider.setTooltip(Tooltip.create(Component.translatableWithFallback("stashlink.config.shulker_radius.tip",
                    "How far placed shulker boxes reach (max 64)")));
            addRenderableWidget(shulkerSlider);
            addSettingLock(Feature.SHULKER_RADIUS, x, y);
            y += ROW;
            boolean chestsLocked = isLocked(Feature.CHESTS);
            Button chests = Button.builder(chestsLabel(chestsLocked), b -> {
                if (local) {
                    StashLinkConfig.includeChests = !StashLinkConfig.includeChests;
                    ClientPrefs.chests = StashLinkConfig.includeChests ? 1 : 0;   // a pessoal vale mais que o padrão
                } else {
                    // Padrão do servidor -> sim -> não -> padrão do servidor.
                    ClientPrefs.chests = ClientPrefs.chests == PlayerPrefs.UNSET ? 1
                            : (ClientPrefs.chests == 1 ? 0 : PlayerPrefs.UNSET);
                }
                b.setMessage(chestsLabel(false));
            }).bounds(x, y, WIDTH - LOCK_SIZE - 4, 20).build();
            chests.active = !chestsLocked;
            addRenderableWidget(chests);
            addSettingLock(Feature.CHESTS, x, y);
            y += ROW;
        }

        y += 12;
        slotsLabelY = y - 11;
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

        return y + ROW;
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
        if (page == PAGE_CATEGORIES) {
            graphics.centeredText(this.font, Component.translatableWithFallback("stashlink.config.categories_info",
                    "Off = quick store never takes that type"),
                    cx, infoY, 0xFFAAAAAA);
        }
        if (page == PAGE_SETTINGS) {
            int infoDraw = infoY - scroll;
            if (clientModeActive && infoDraw >= viewTop && infoDraw + 9 <= viewBottom) {
                graphics.centeredText(this.font, Component.translatableWithFallback("stashlink.config.client_mode_active",
                        "Client mode active: the server does not have StashLink, the mod acts only on your client"),
                        cx, infoDraw, 0xFF55FF55);
            }
            // Rótulo do campo de slots (fica 11 px acima do EditBox; a posição depende de quais widgets aparecem).
            int labelDraw = slotsLabelY - scroll;
            if (labelDraw >= viewTop && labelDraw + 9 <= viewBottom) {
                graphics.text(this.font, Component.translatableWithFallback("stashlink.config.locked_slots_hint",
                        "Locked slots (0-35, comma-separated; 0-8 = hotbar)"),
                        cx - WIDTH / 2, labelDraw, 0xFFAAAAAA);
            }
        }
        if (maxScroll > 0) {
            // Barrinha de rolagem à direita da lista: o tamanho mostra quanto da lista cabe, a posição onde estou.
            int barX = cx + WIDTH / 2 + 6;
            int trackH = viewBottom - viewTop;
            int thumbH = Math.max(12, trackH * trackH / (trackH + maxScroll));
            int thumbY = viewTop + (trackH - thumbH) * scroll / maxScroll;
            graphics.fill(barX, viewTop, barX + 4, viewBottom, 0x66000000);
            graphics.fill(barX, thumbY, barX + 4, thumbY + thumbH, 0xFFAAAAAA);
        }
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
        ClientPrefs.save();
        if (local) {
            StashLinkConfig.save();
        }
        if (!noWorld) {
            ClientPrefs.sync();
        }
    }

    private Component clientModeLabel() {
        return Component.translatableWithFallback("stashlink.config.client_mode", "Client mode: %s",
                ClientPrefs.clientModeEnabled ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF);
    }

    /** Cadeado de um ajuste, no fim da linha (igual ao das funções). */
    private void addSettingLock(Feature setting, int x, int y) {
        boolean locked = isLocked(setting);
        LockIconButton lock = new LockIconButton(x + WIDTH - LOCK_SIZE, y, b -> toggleLock(setting));
        lock.setLocked(locked);
        lock.active = canEditLocks();
        lock.setTooltip(Tooltip.create(lockTip(locked)));
        addRenderableWidget(lock);
    }

    private Component chestsLabel(boolean locked) {
        Component value;
        if (locked && !local) {
            value = Component.translatableWithFallback("stashlink.feature.state_locked", "Locked");
        } else if (local) {
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
        /** {@code true}: raio das shulkers colocadas (vai até 64); {@code false}: baús, barris e bancadas (até 16). */
        private final boolean shulker;
        /** Trancado pelo servidor (num servidor): o valor dele vale e o slider fica desligado. */
        private final boolean serverLocked;

        RadiusSlider(int x, int y, boolean local, boolean shulker, boolean serverLocked) {
            super(x, y, WIDTH - LOCK_SIZE - 4, 20, Component.empty(),
                    toSlider(cap(local, shulker), initial(local, shulker, serverLocked)));
            this.local = local;
            this.shulker = shulker;
            this.serverLocked = serverLocked;
            this.active = !this.serverLocked;
            updateMessage();
        }

        private static int cap(boolean local, boolean shulker) {
            if (shulker) {
                return local ? StashLinkConfig.shulkerCap() : StashLinkConfig.HARD_MAX_SHULKER_RADIUS;
            }
            return local ? StashLinkConfig.radiusCap() : StashLinkConfig.HARD_MAX_RADIUS;
        }

        /** Num servidor, sem preferência ainda, mostra o padrão do código; a preferência só vira "escolhida" ao mexer. */
        private static int initial(boolean local, boolean shulker, boolean locked) {
            if (locked && local) {   // trancado no meu mundo: mostra o valor que está valendo (o do servidor)
                return shulker ? StashLinkConfig.shulkerRadius : StashLinkConfig.sourceRadius;
            }
            if (shulker) {
                return ClientPrefs.shulkerRadius == PlayerPrefs.UNSET ? StashLinkConfig.shulkerRadius
                        : ClientPrefs.shulkerRadius;
            }
            return ClientPrefs.radius == PlayerPrefs.UNSET ? StashLinkConfig.sourceRadius : ClientPrefs.radius;
        }

        private static double toSlider(int cap, int radius) {
            return cap == 0 ? 0 : Math.max(0, Math.min(1, radius / (double) cap));
        }

        private int radius() {
            return (int) Math.round(this.value * cap(local, shulker));
        }

        @Override
        protected void updateMessage() {
            Component name = Component.translatableWithFallback(
                    shulker ? "stashlink.config.shulker_radius.name" : "stashlink.config.radius.name",
                    shulker ? "Shulkers" : "Chests and workbenches");
            Component locked = Component.translatableWithFallback("stashlink.feature.state_locked", "Locked");
            // Num servidor o valor trancado é desconhecido (só "Trancada"); no meu mundo mostro o valor e a palavra.
            Component value = serverLocked && !local ? locked
                    : Component.translatableWithFallback("stashlink.config.blocks", "%s blocks", radius());
            Component text = Component.empty().append(name).append(": ").append(value);
            setMessage(serverLocked && local ? text.copy().append(" (").append(locked).append(")") : text);
        }

        @Override
        protected void applyValue() {
            // Em mundo próprio muda também a preferência pessoal: ela vale mais que o padrão do servidor, e se ficasse
            // com um valor antigo o slider pareceria não fazer nada.
            if (shulker) {
                if (local) {
                    StashLinkConfig.shulkerRadius = Math.min(radius(), StashLinkConfig.shulkerCap());
                }
                ClientPrefs.shulkerRadius = radius();
            } else {
                if (local) {
                    StashLinkConfig.trySetRadius(radius());
                }
                ClientPrefs.radius = radius();
            }
        }
    }
}
