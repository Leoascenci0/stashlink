package io.github.leoascenci0.stashlink.clientmode;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** O servidor recusa cliques: o menu volta ao estado de antes. Duas voltas seguidas à mesma situação = parar. */
class ClickRefusalsTest {
    @Test
    void twoBounceBacksToTheSameBaseStop() {
        ClickRefusals r = new ClickRefusals();
        r.beforeClicks(10);
        r.afterClicks(20);
        assertFalse(r.observe(10));   // 1ª volta
        r.beforeClicks(10);           // o job tenta de novo
        r.afterClicks(20);
        assertTrue(r.observe(10));    // 2ª volta
    }

    @Test
    void aSingleBounceBackDoesNotStop() {
        ClickRefusals r = new ClickRefusals();
        r.beforeClicks(10);
        r.afterClicks(20);
        assertFalse(r.observe(10));
        assertFalse(r.observe(10));
    }

    @Test
    void bouncesFromDifferentBasesAreNotConsecutive() {
        ClickRefusals r = new ClickRefusals();
        r.beforeClicks(10);
        r.afterClicks(20);
        assertFalse(r.observe(10));
        r.beforeClicks(30);
        r.afterClicks(40);
        assertFalse(r.observe(30));   // outra situação de partida: volta a contar 1
    }

    @Test
    void acceptedClickClearsTheCount() {
        ClickRefusals r = new ClickRefusals();
        r.beforeClicks(10);
        r.afterClicks(20);
        assertFalse(r.observe(10));   // 1 recusa
        r.beforeClicks(10);
        r.afterClicks(20);
        for (int i = 0; i < ClickRefusals.QUIET_TICKS; i++) {
            assertFalse(r.observe(20));   // fica no previsto: aceito
        }
        r.beforeClicks(20);
        r.afterClicks(30);
        assertFalse(r.observe(20));   // nova recusa, mas a contagem recomeçou
        r.beforeClicks(20);
        r.afterClicks(30);
        assertTrue(r.observe(20));
    }

    @Test
    void clickThatChangedNothingIsNotARefusal() {
        ClickRefusals r = new ClickRefusals();
        for (int i = 0; i < 5; i++) {
            r.beforeClicks(10);
            r.afterClicks(10);   // previsto == base: destino cheio
            assertFalse(r.observe(10));
        }
    }

    @Test
    void resetStartsOver() {
        ClickRefusals r = new ClickRefusals();
        r.beforeClicks(10);
        r.afterClicks(20);
        r.observe(10);
        r.beforeClicks(10);
        r.afterClicks(20);
        assertTrue(r.observe(10));
        r.reset();
        assertFalse(r.observe(10));
    }

    @Test
    void withoutClicksNothingHappens() {
        ClickRefusals r = new ClickRefusals();
        for (int i = 0; i < 10; i++) {
            assertFalse(r.observe(i));
        }
    }
}
