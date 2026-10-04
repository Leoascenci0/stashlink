package io.github.leoascenci0.stashlink.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HoverCollectPassTest {
    @Test
    void clicksOncePerEntry() {
        HoverCollectPass pass = new HoverCollectPass();
        assertTrue(pass.shouldClick(5, 1));
        assertFalse(pass.shouldClick(5, 2)); // parado em cima não repete
        assertFalse(pass.shouldClick(-1, 3)); // saiu
        assertTrue(pass.shouldClick(5, 4)); // voltou: nova passagem
    }

    @Test
    void limitsClicksPerTick() {
        HoverCollectPass pass = new HoverCollectPass();
        int clicks = 0;
        for (int slot = 0; slot < 10; slot++) {
            if (pass.shouldClick(slot, 7)) {
                clicks++;
            }
        }
        assertTrue(clicks == HoverCollectPass.MAX_CLICKS_PER_TICK);
        assertTrue(pass.shouldClick(20, 8)); // tick novo, limite zerado
    }

    @Test
    void stopsAfterConsecutiveFailuresUntilReset() {
        HoverCollectPass pass = new HoverCollectPass();
        for (int i = 0; i < HoverCollectPass.MAX_FAILURES; i++) {
            assertTrue(pass.shouldClick(i, i));
            pass.clickResult(false);
        }
        assertFalse(pass.shouldClick(50, 100));
        pass.reset();
        assertTrue(pass.shouldClick(50, 101));
    }

    @Test
    void successResetsFailureCount() {
        HoverCollectPass pass = new HoverCollectPass();
        pass.shouldClick(1, 1);
        pass.clickResult(false);
        pass.shouldClick(2, 2);
        pass.clickResult(true);
        pass.shouldClick(3, 3);
        pass.clickResult(false);
        assertTrue(pass.shouldClick(4, 4));
    }
}
