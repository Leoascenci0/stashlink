package io.github.leoascenci0.stashlink.bench;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Freio de rajada de pedidos (anti-flood) por jogador: no máximo {@code perTick} pedidos no mesmo tick e
 * {@code perWindow} dentro de uma janela de {@code windowTicks} ticks. Cada pedido do livro de receitas varre os baús
 * do raio, então um cliente que manda centenas de pacotes por tick pesaria no servidor inteiro. Quem clica de verdade
 * nunca chega perto (20 cliques por segundo já é humanamente impossível). Só a thread do servidor usa.
 */
final class BenchFlood {
    /** O livro de receitas: 4 por tick e 30 a cada segundo. */
    static final BenchFlood BOOK = new BenchFlood(4, 30, 20);

    private static final class Window {
        long tick = Long.MIN_VALUE;
        int inTick;
        boolean started;
        long windowStart;
        int inWindow;
    }

    private final int perTick;
    private final int perWindow;
    private final int windowTicks;
    /** Por identidade do objeto do jogador: relogar cria outro, e o antigo é coletado sozinho. */
    private final Map<Object, Window> windows = new WeakHashMap<>();

    BenchFlood(int perTick, int perWindow, int windowTicks) {
        this.perTick = perTick;
        this.perWindow = perWindow;
        this.windowTicks = windowTicks;
    }

    /** Este pedido pode ser atendido agora? Conta o pedido só quando aceita. */
    boolean allow(Object player, long now) {
        Window w = windows.computeIfAbsent(player, k -> new Window());
        if (w.tick != now) {
            w.tick = now;
            w.inTick = 0;
        }
        if (!w.started || now - w.windowStart >= windowTicks || now < w.windowStart) {
            w.started = true;
            w.windowStart = now;
            w.inWindow = 0;
        }
        if (w.inTick >= perTick || w.inWindow >= perWindow) {
            return false;
        }
        w.inTick++;
        w.inWindow++;
        return true;
    }
}
