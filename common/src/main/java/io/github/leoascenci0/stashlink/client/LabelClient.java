package io.github.leoascenci0.stashlink.client;

import io.github.leoascenci0.stashlink.compat.mc.ClientCompat;
import io.github.leoascenci0.stashlink.config.Feature;
import io.github.leoascenci0.stashlink.network.LabelEditRequest;
import io.github.leoascenci0.stashlink.network.LabelEditorData;
import io.github.leoascenci0.stashlink.network.SetLabelRequest;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Lado cliente dos rótulos de baú: a tecla (J, configurável) pede ao servidor o rótulo do bloco que se olha, o
 * servidor responde com o texto atual e abre o editor ({@link LabelEditScreen}); ao salvar, o pedido volta com
 * o texto novo. O holograma em si é uma entidade do servidor, sem código de cliente.
 */
public final class LabelClient {
    /** GLFW_KEY_J = 74. */
    public static final KeyMapping KEY = new KeyMapping("key.stashlink.edit_label", 74, QuickStackKey.CATEGORY);

    private static Consumer<LabelEditRequest> editSender = request -> { };
    private static Consumer<SetLabelRequest> setSender = request -> { };
    private static BooleanSupplier serverHasMod = () -> false;

    private LabelClient() {
    }

    private static LabelPanel awaiting;

    /** Há como rotular: o servidor tem o mod. */
    public static boolean available() {
        // Desligado por mim ou trancado no servidor: nada de lápis no baú.
        return serverHasMod.getAsBoolean() && ClientFeatures.enabled(Feature.LABEL);
    }

    /** O container para onde a mira aponta agora (a tela do baú acabou de abrir por clique nele), se der para rotular. */
    public static net.minecraft.core.BlockPos lookedAtContainer() {
        Minecraft mc = Minecraft.getInstance();
        return available() ? ClientCompat.lookedAtBlock(mc) : null;
    }

    /** O painel do lápis pede o texto atual; a resposta chega em {@link #openEditor}. */
    public static void fetch(LabelPanel panel, boolean silent) {
        awaiting = panel;
        request(Minecraft.getInstance(), panel.pos());
        if (silent) {
            pendingSince = Long.MIN_VALUE;                   // abrir a tela não deve reclamar se não houver resposta
        }
    }

    public static void save(net.minecraft.core.BlockPos pos, String name, String note) {
        setSender.accept(new SetLabelRequest(pos, name, note));
    }

    public static void setSenders(Consumer<LabelEditRequest> edit, Consumer<SetLabelRequest> set) {
        editSender = edit;
        setSender = set;
    }

    public static void setServerHasMod(BooleanSupplier value) {
        serverHasMod = value;
    }

    /** Tick em que um pedido saiu e ainda não houve resposta (para avisar em vez de falhar calado). */
    private static long pendingSince = Long.MIN_VALUE;
    private static final int REPLY_TIMEOUT_TICKS = 40;

    private static void say(Minecraft mc, String key, String fallback) {
        ClientCompat.overlay(mc, net.minecraft.network.chat.Component.translatableWithFallback(key, fallback));
    }

    private static void request(Minecraft mc, BlockPos pos) {
        pendingSince = mc.level.getGameTime();
        editSender.accept(new LabelEditRequest(pos));
    }

    /** Chame a cada tick: aperto da tecla olhando para um bloco, jogando, com o mod no servidor. */
    public static void poll(Minecraft mc) {
        if (pendingSince != Long.MIN_VALUE && mc.level != null && mc.level.getGameTime() - pendingSince > REPLY_TIMEOUT_TICKS) {
            pendingSince = Long.MIN_VALUE;
            awaiting = null;
            say(mc, "stashlink.label.no_reply", "No reply from the server: aim at a chest within reach");
        }
        while (KEY.consumeClick()) {
            if (mc.player == null || mc.level == null || ClientCompat.hasScreenOpen(mc) || mc.player.isSpectator()) {
                continue;
            }
            BlockPos looked = ClientCompat.lookedAtBlock(mc);
            if (serverHasMod.getAsBoolean() && !ClientFeatures.allow(mc, Feature.LABEL)) {
                continue;
            }
            if (!serverHasMod.getAsBoolean()) {
                say(mc, "stashlink.label.no_server_mod", "This server does not have StashLink: labels are unavailable");
            } else if (looked == null) {
                say(mc, "stashlink.label.aim", "Aim at a chest, barrel, shulker box or ender chest");
            } else {
                request(mc, looked);
            }
        }
    }

    /** O servidor mandou abrir o editor (thread do cliente). */
    public static void openEditor(LabelEditorData data) {
        pendingSince = Long.MIN_VALUE;
        Minecraft mc = Minecraft.getInstance();
        if (awaiting != null && awaiting.pos().equals(data.pos())) {
            awaiting.fill(data.name());
            awaiting = null;
        } else if (mc.player != null && !ClientCompat.hasScreenOpen(mc)) {
            ClientCompat.openScreen(mc, new LabelEditScreen(data.pos(), data.name(), data.note(),
                    (name, note) -> setSender.accept(new SetLabelRequest(data.pos(), name, note))));
        }
    }
}
