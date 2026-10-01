package io.github.leoascenci0.stashlink.client;

import io.github.leoascenci0.stashlink.compat.mc.ClientCompat;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * Tecla K (configurável em Opções &gt; Controles) que abre a tela de config do StashLink direto do jogo, sem
 * depender de Mod Menu. Registrar a tecla é "cola" de cada loader, que chama {@link #poll} a cada tick.
 */
public final class ConfigKey {
    /** GLFW_KEY_K = 75. */
    public static final KeyMapping KEY = new KeyMapping("key.stashlink.open_config", 75, QuickStackKey.CATEGORY);

    private ConfigKey() {
    }

    /** Chame a cada tick; abre a tela uma vez por aperto, se não houver outra tela aberta. */
    public static void poll(Minecraft mc) {
        while (KEY.consumeClick()) {
            if (!ClientCompat.hasScreenOpen(mc)) {
                ClientCompat.openScreen(mc, new StashLinkConfigScreen(null));
            }
        }
    }
}
