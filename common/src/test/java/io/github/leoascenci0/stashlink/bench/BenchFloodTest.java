package io.github.leoascenci0.stashlink.bench;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BenchFloodTest {
    @Test
    void capsRequestsPerTickAndRecoversNextTick() {
        BenchFlood flood = new BenchFlood(4, 1000, 20);
        Object player = new Object();
        for (int i = 0; i < 4; i++) {
            assertTrue(flood.allow(player, 100), "pedido " + i + " cabe no tick");
        }
        assertFalse(flood.allow(player, 100), "o 5o no mesmo tick é barrado");
        assertTrue(flood.allow(player, 101), "no tick seguinte volta a aceitar");
    }

    @Test
    void capsRequestsPerWindowAndRecoversAfterIt() {
        BenchFlood flood = new BenchFlood(4, 30, 20);
        Object player = new Object();
        int accepted = 0;
        for (long tick = 0; tick < 20; tick++) {             // 4 por tick durante 20 ticks = 80 tentativas
            for (int i = 0; i < 4; i++) {
                if (flood.allow(player, tick)) {
                    accepted++;
                }
            }
        }
        assertTrue(accepted == 30, "só 30 por janela de 1 s, passaram " + accepted);
        assertTrue(flood.allow(player, 20), "janela nova: aceita de novo");
    }

    @Test
    void eachPlayerHasHisOwnBudget() {
        BenchFlood flood = new BenchFlood(1, 5, 20);
        Object a = new Object();
        Object b = new Object();
        assertTrue(flood.allow(a, 1));
        assertFalse(flood.allow(a, 1));
        assertTrue(flood.allow(b, 1), "o outro jogador não é afetado");
    }
}
