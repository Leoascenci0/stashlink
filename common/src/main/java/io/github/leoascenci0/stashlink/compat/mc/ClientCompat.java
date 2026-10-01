package io.github.leoascenci0.stashlink.compat.mc;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;

/**
 * API de cliente do Minecraft sujeita a mudar entre versões. Fora de {@code compat/}, ninguém chama
 * estas coisas direto (ver docs/UPDATING.md).
 */
public final class ClientCompat {
    private ClientCompat() {
    }

    /** Categoria própria na tela de controles. {@code KeyMapping.Category} existe desde 1.21.9. */
    public static KeyMapping.Category keyCategory(String path) {
        return KeyMapping.Category.register(McCompat.id(path));
    }

    /**
     * Há uma tela aberta? Em MC 26.3 o campo {@code Minecraft.screen} sumiu; agora é
     * {@code mc.gui.screen()}.
     */
    public static boolean hasScreenOpen(Minecraft mc) {
        return mc.gui.screen() != null;
    }

    /** Abre (ou fecha, com {@code null}) uma tela. Em MC 26.3 {@code Minecraft.setScreen} passou para {@code mc.gui.setScreen}. */
    public static void openScreen(Minecraft mc, net.minecraft.client.gui.screens.Screen screen) {
        mc.gui.setScreen(screen);
    }

    /** A tecla do evento é a deste KeyMapping? ({@link KeyEvent} existe desde 1.21.9.) */
    public static boolean keyMatches(KeyMapping key, KeyEvent event) {
        return key.matches(event);
    }
}
