package io.github.leoascenci0.stashlink.client;

import io.github.leoascenci0.stashlink.compat.mc.ClientCompat;
import io.github.leoascenci0.stashlink.config.Feature;
import io.github.leoascenci0.stashlink.lootall.LootAllService;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;

/**
 * A tecla W (configurável em Opções &gt; Controles) dentro da tela de um container. Diferente da N, o gatilho
 * é um evento de tecla <i>da tela</i> (cada loader tem o seu), e não o tick sem tela — a cola chama
 * {@link #onKeyPressed}.
 */
public final class LootAllKey {
    /** GLFW_KEY_W = 87. */
    public static final KeyMapping KEY = new KeyMapping("key.stashlink.loot_all", 87, QuickStackKey.CATEGORY);

    /** Freio no cliente: segurar a tecla repete o evento; o servidor também limita, mas nem precisa receber. */
    private static final int MIN_TICKS_BETWEEN_SENDS = 5;
    private static long lastSendTick = Long.MIN_VALUE;

    private LootAllKey() {
    }

    /**
     * Chame a cada tecla apertada dentro de uma tela. Roda {@code send} se for a nossa tecla numa tela de container
     * suportada. Devolve {@code true} se a tecla foi tratada aqui (o loader deve engoli-la).
     */
    public static boolean onKeyPressed(Minecraft mc, Screen screen, KeyEvent event, Runnable send) {
        if (!(screen instanceof AbstractContainerScreen<?> containerScreen)
                || !LootAllService.isSupportedMenu(containerScreen.getMenu())
                // Digitando num campo de texto a tecla é letra, não comando (as telas suportadas não têm um,
                // mas se algum mod acrescentar, não roubamos a digitação).
                || screen.getFocused() instanceof EditBox
                || !ClientCompat.keyMatches(KEY, event)) {
            return false;
        }
        if (mc.player == null || mc.level == null || mc.player.isSpectator()) {
            return false;
        }
        long now = mc.level.getGameTime();
        if (lastSendTick == Long.MIN_VALUE || now < lastSendTick || now - lastSendTick >= MIN_TICKS_BETWEEN_SENDS) {
            lastSendTick = now;
            if (ClientFeatures.allow(mc, Feature.LOOT_ALL)) {
                send.run();
            }
        }
        return true;
    }
}
