package io.github.leoascenci0.stashlink.client;

import io.github.leoascenci0.stashlink.compat.mc.ClientCompat;
import io.github.leoascenci0.stashlink.config.Feature;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * A tecla N (configurável em Opções &gt; Controles) — só o lado cliente e só a parte comum. Registrar a tecla e
 * enviar o pacote é "cola" de cada loader, que chama {@link #poll} a cada tick do cliente.
 */
public final class QuickStackKey {
    /** Categoria própria na tela de controles (o texto vem do arquivo de idioma). */
    public static final KeyMapping.Category CATEGORY =
            ClientCompat.keyCategory("main");

    /** GLFW_KEY_N = 78. */
    public static final KeyMapping KEY = new KeyMapping("key.stashlink.quick_stack", 78, CATEGORY);

    private QuickStackKey() {
    }

    /** Chame a cada tick; roda {@code send} uma vez por aperto da tecla, se estiver jogando (sem tela aberta). */
    public static void poll(Minecraft mc, Runnable send) {
        while (KEY.consumeClick()) {
            if (mc.player != null && mc.level != null && !ClientCompat.hasScreenOpen(mc) && !mc.player.isSpectator()) {
                if (ClientFeatures.allow(mc, Feature.QUICK_STACK)) {
                    send.run();
                }
            }
        }
    }
}
