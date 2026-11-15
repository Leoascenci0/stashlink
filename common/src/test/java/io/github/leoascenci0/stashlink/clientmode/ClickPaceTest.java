package io.github.leoascenci0.stashlink.clientmode;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClickPaceTest {
    @Test
    void unknownIdFallsBackToNormal() {
        assertEquals(ClickPace.NORMAL, ClickPace.byId("nao_existe"));
        assertEquals(ClickPace.NORMAL, ClickPace.byId(null));
        for (ClickPace p : ClickPace.values()) {
            assertEquals(p, ClickPace.byId(p.id()));
        }
    }

    @Test
    void clickRatesDecreaseAndPauseNeverShrinks() {
        assertTrue(ClickPace.FAST.clicksPerTick() > ClickPace.NORMAL.clicksPerTick());
        assertTrue(ClickPace.NORMAL.clicksPerTick() > ClickPace.CAREFUL.clicksPerTick());
        assertTrue(ClickPace.FAST.hoverPerTick() > ClickPace.NORMAL.hoverPerTick());
        assertTrue(ClickPace.NORMAL.hoverPerTick() > ClickPace.CAREFUL.hoverPerTick());
        assertTrue(ClickPace.CAREFUL.pauseTicks() > ClickPace.NORMAL.pauseTicks());
        assertTrue(ClickPace.NORMAL.pauseTicks() >= ClickPace.FAST.pauseTicks());
    }

    @Test
    void nextCyclesThroughAll() {
        assertEquals(ClickPace.NORMAL, ClickPace.FAST.next());
        assertEquals(ClickPace.CAREFUL, ClickPace.NORMAL.next());
        assertEquals(ClickPace.FAST, ClickPace.CAREFUL.next());
    }
}
