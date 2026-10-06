package io.github.leoascenci0.stashlink.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BenchPanelLayoutTest {
    private static final int IMAGE = 176;

    @Test
    void roomToSpareCentersTheBlock() {
        // 147 + 1 + 176 = 324; (500 - 324) / 2 = 88 de folga: estação em 88 + 148.
        assertEquals(88 + 148, BenchPanel.stationLeft(500, IMAGE, 100));
    }

    /** Relato do Eliel (2026-10-05): janela de 382 de largura deixava o painel por cima da fornalha. */
    @Test
    void tightWindowStillMovesTheStationOutOfThePanel() {
        assertEquals(32 + 148, BenchPanel.stationLeft(382, IMAGE, 103));
    }

    @Test
    void windowTooNarrowKeepsTheDefault() {
        assertEquals(103, BenchPanel.stationLeft(340, IMAGE, 103));
    }
}
