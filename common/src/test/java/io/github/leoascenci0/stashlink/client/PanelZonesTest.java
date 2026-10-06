package io.github.leoascenci0.stashlink.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Item 24: a zona que JEI/REI recebem cobre o painel e as abas, sem sobrar nem faltar. */
class PanelZonesTest {
    @Test
    void zoneWithoutTabsIsJustThePanel() {
        PanelZones.Zone zone = BenchPanel.zoneAt(10, 20, 0);
        assertEquals(new PanelZones.Zone(10, 20, 147, 166), zone);
    }

    @Test
    void tabsExtendTheZoneToTheLeft() {
        PanelZones.Zone zone = BenchPanel.zoneAt(40, 20, 4);
        assertEquals(10, zone.x());
        assertEquals(40 + 147 - 10, zone.width());
        assertEquals(166, zone.height());
    }

    @Test
    void manyTabsCanBeTallerThanThePanel() {
        PanelZones.Zone zone = BenchPanel.zoneAt(40, 20, 7);
        assertEquals(3 + 27 * 7, zone.height());
        assertTrue(zone.height() > 166);
    }
}
