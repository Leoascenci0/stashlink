package io.github.leoascenci0.stashlink.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HoverCollectPassTest {
    @Test
    void clicksOncePerEntry() {
        HoverCollectPass pass = new HoverCollectPass();
        assertTrue(pass.shouldClick(5, 1, 4));
        assertFalse(pass.shouldClick(5, 2, 4)); // parado em cima não repete
        assertFalse(pass.shouldClick(-1, 3, 4)); // saiu
        assertTrue(pass.shouldClick(5, 4, 4)); // voltou: nova passagem
    }

    @Test
    void limitsClicksPerTick() {
        HoverCollectPass pass = new HoverCollectPass();
        int clicks = 0;
        for (int slot = 0; slot < 10; slot++) {
            if (pass.shouldClick(slot, 7, HoverCollectPass.MAX_CLICKS_PER_TICK)) {
                clicks++;
            }
        }
        assertTrue(clicks == HoverCollectPass.MAX_CLICKS_PER_TICK);
        assertTrue(pass.shouldClick(20, 8, HoverCollectPass.MAX_CLICKS_PER_TICK)); // tick novo, limite zerado
    }

    @Test
    void stopsAfterConsecutiveFailuresUntilReset() {
        HoverCollectPass pass = new HoverCollectPass();
        for (int i = 0; i < HoverCollectPass.MAX_FAILURES; i++) {
            assertTrue(pass.shouldClick(i, i, 4));
            pass.clickResult(false);
        }
        assertFalse(pass.shouldClick(50, 100, 4));
        pass.reset();
        assertTrue(pass.shouldClick(50, 101, 4));
    }

    @Test
    void successResetsFailureCount() {
        HoverCollectPass pass = new HoverCollectPass();
        pass.shouldClick(1, 1, 4);
        pass.clickResult(false);
        pass.shouldClick(2, 2, 4);
        pass.clickResult(true);
        pass.shouldClick(3, 3, 4);
        pass.clickResult(false);
        assertTrue(pass.shouldClick(4, 4, 4));
    }

    @Test
    void ceilingComesFromTheParameter() {
        HoverCollectPass pass = new HoverCollectPass();
        assertTrue(pass.shouldClick(1, 9, 1));
        assertFalse(pass.shouldClick(2, 9, 1)); // ritmo cuidadoso: 1 por tick
        assertTrue(pass.shouldClick(3, 10, 1));
    }
}
