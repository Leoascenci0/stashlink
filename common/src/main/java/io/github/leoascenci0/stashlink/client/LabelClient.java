package io.github.leoascenci0.stashlink.client;

import io.github.leoascenci0.stashlink.compat.mc.ClientCompat;
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

    public static void setSenders(Consumer<LabelEditRequest> edit, Consumer<SetLabelRequest> set) {
        editSender = edit;
        setSender = set;
    }

    public static void setServerHasMod(BooleanSupplier value) {
        serverHasMod = value;
    }

    /** Chame a cada tick: aperto da tecla olhando para um bloco, jogando, com o mod no servidor. */
    public static void poll(Minecraft mc) {
        while (KEY.consumeClick()) {
            if (mc.player == null || mc.level == null || ClientCompat.hasScreenOpen(mc) || mc.player.isSpectator()) {
                continue;
            }
            BlockPos looked = ClientCompat.lookedAtBlock(mc);
            if (!serverHasMod.getAsBoolean()) {
                ClientCompat.overlay(mc, net.minecraft.network.chat.Component.translatableWithFallback(
                        "stashlink.label.no_server_mod", "This server does not have StashLink: labels are unavailable"));
            } else if (looked != null) {
                editSender.accept(new LabelEditRequest(looked));
            }
        }
    }

    /** O servidor mandou abrir o editor (thread do cliente). */
    public static void openEditor(LabelEditorData data) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && !ClientCompat.hasScreenOpen(mc)) {
            ClientCompat.openScreen(mc, new LabelEditScreen(data.pos(), data.name(), data.note(),
                    (name, note) -> setSender.accept(new SetLabelRequest(data.pos(), name, note))));
        }
    }
}
