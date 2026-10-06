package io.github.leoascenci0.stashlink.client;

import net.minecraft.client.gui.screens.Screen;

import java.util.List;
import java.util.function.Supplier;

/**
 * Zonas da tela que o StashLink ocupa FORA do fundo da tela do jogo (hoje: o painel "Armazenamento" e as abas dele).
 * Mods de lista de itens (JEI, REI) leem isto para não desenhar a lista por baixo do painel. Fica em {@code common}
 * porque é só geometria; quem conversa com cada mod são os plugins do loader (ex.: {@code compat/jei} no Fabric).
 * O botão "N" e o lápis do rótulo ficam dentro do fundo do baú, que esses mods já tratam como ocupado.
 */
public final class PanelZones {
    /** Retângulo em pixels da tela (mesmo sistema de coordenadas do mouse). */
    public record Zone(int x, int y, int width, int height) {
    }

    /** Só existe uma tela de container aberta por vez: uma "vaga" única basta e é limpa ao fechar. */
    private static Screen screen;
    private static Supplier<List<Zone>> source;

    private PanelZones() {
    }

    public static void register(Screen owner, Supplier<List<Zone>> zones) {
        screen = owner;
        source = zones;
    }

    public static void clear(Screen owner) {
        if (screen == owner) {
            screen = null;
            source = null;
        }
    }

    /** As zonas ocupadas pelo StashLink nesta tela; vazio se não for a tela registrada. */
    public static List<Zone> of(Screen owner) {
        Supplier<List<Zone>> current = source;
        return owner != null && owner == screen && current != null ? current.get() : List.of();
    }
}
