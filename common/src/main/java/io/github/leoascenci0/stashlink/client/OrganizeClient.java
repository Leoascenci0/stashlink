package io.github.leoascenci0.stashlink.client;

import io.github.leoascenci0.stashlink.compat.mc.ClientCompat;
import io.github.leoascenci0.stashlink.config.Feature;
import io.github.leoascenci0.stashlink.network.OrganizeRequest;
import io.github.leoascenci0.stashlink.network.OrganizeSync;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Lado cliente do Item 20: a tecla (O, configurável) que abre a tela do sistema, o envio dos pedidos e o último estado
 * que o servidor contou (prévia, resultado da busca, se há o que aplicar/desfazer). O cliente nunca mexe em item: só pede.
 */
public final class OrganizeClient {
    /** GLFW_KEY_O = 79. */
    public static final KeyMapping KEY = new KeyMapping("key.stashlink.organize", 79, QuickStackKey.CATEGORY);

    private static Consumer<CustomPacketPayload> sender = request -> { };
    private static BooleanSupplier serverHasMod = () -> false;

    private static List<OrganizeSync.Row> previewRows = List.of();
    private static List<OrganizeSync.Row> searchRows = List.of();
    private static boolean canApply;
    private static boolean canUndo;
    private static int tidied;
    private static int message = OrganizeSync.MSG_NONE;
    private static boolean previewed;

    /** A tela aberta agora, se houver (a própria tela se registra ao abrir e se solta ao fechar). */
    private static OrganizeScreen open;

    private OrganizeClient() {
    }

    public static void setSender(Consumer<CustomPacketPayload> value) {
        sender = value;
    }

    public static void setServerHasMod(BooleanSupplier value) {
        serverHasMod = value;
    }

    /** O mod está no servidor e a função não está desligada nem trancada: dá para organizar. */
    public static boolean available() {
        return serverHasMod.getAsBoolean() && ClientFeatures.enabled(Feature.ORGANIZE);
    }

    public static void send(OrganizeRequest request) {
        sender.accept(request);
    }

    /** Pede ao servidor para organizar o baú aberto ({@code containerId} é o do menu na tela). */
    public static void organizeChest(int containerId) {
        send(new OrganizeRequest(OrganizeRequest.CHEST, containerId, "", List.of(), net.minecraft.core.BlockPos.ZERO));
    }

    /** Abre a tela do sistema (busca e organizar tudo). */
    public static void openScreen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.player.isSpectator()) {
            return;
        }
        if (!serverHasMod.getAsBoolean()) {
            say(mc, "stashlink.organize.no_server_mod", "This server does not have StashLink: organizing is unavailable");
            return;
        }
        if (ClientFeatures.allow(mc, Feature.ORGANIZE)) {
            ClientCompat.openScreen(mc, new OrganizeScreen());
        }
    }

    /** Chame a cada tick: aperto da tecla jogando (sem tela aberta). */
    public static void poll(Minecraft mc) {
        while (KEY.consumeClick()) {
            if (mc.player != null && mc.level != null && !ClientCompat.hasScreenOpen(mc) && !mc.player.isSpectator()) {
                openScreen();
            }
        }
    }

    private static void say(Minecraft mc, String key, String fallback) {
        ClientCompat.overlay(mc, Component.translatableWithFallback(key, fallback));
    }

    /** Chegou uma resposta do servidor (thread do cliente). */
    public static void apply(OrganizeSync sync) {
        switch (sync.kind()) {
            case OrganizeSync.PREVIEW -> {
                previewRows = sync.rows();
                previewed = true;
                canApply = sync.canApply();
                tidied = sync.tidied();
            }
            case OrganizeSync.SEARCH -> searchRows = sync.rows();
            default -> {
                // STATE: depois de aplicar/desfazer, a prévia antiga deixou de valer.
                if (sync.message() == OrganizeSync.MSG_APPLIED || sync.message() == OrganizeSync.MSG_STALE
                        || sync.message() == OrganizeSync.MSG_UNDONE) {
                    previewRows = List.of();
                    previewed = false;
                    tidied = 0;
                }
                canApply = sync.canApply();
            }
        }
        canUndo = sync.canUndo();
        message = sync.message();
        if (open != null) {
            open.refresh();
        }
    }

    public static List<OrganizeSync.Row> previewRows() {
        return previewRows;
    }

    public static List<OrganizeSync.Row> searchRows() {
        return searchRows;
    }

    public static boolean canApply() {
        return canApply;
    }

    public static boolean canUndo() {
        return canUndo;
    }

    public static boolean previewed() {
        return previewed;
    }

    public static int tidied() {
        return tidied;
    }

    public static int message() {
        return message;
    }

    /** A tela abriu ({@code screen}) ou fechou ({@code null}). Ao abrir, esquece prévias e buscas antigas (o mundo pode ter mudado). */
    static void opened(OrganizeScreen screen) {
        open = screen;
        if (screen == null) {
            return;
        }
        previewRows = List.of();
        searchRows = List.of();
        canApply = false;
        previewed = false;
        tidied = 0;
        message = OrganizeSync.MSG_NONE;
    }
}
