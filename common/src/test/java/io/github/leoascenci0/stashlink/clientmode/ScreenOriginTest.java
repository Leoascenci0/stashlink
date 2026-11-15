package io.github.leoascenci0.stashlink.clientmode;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Modo cliente seguro: a tela só é confiável se veio de um bloco de armazenamento usado há pouco, com o tamanho certo. */
class ScreenOriginTest {
    @Test
    void singleChestOpensA27SlotScreen() {
        ScreenOrigin o = new ScreenOrigin();
        o.blockUsed(100, 27);
        assertTrue(o.menuOpened(103, 27));
    }

    @Test
    void doubleChestOpensA54SlotScreen() {
        ScreenOrigin o = new ScreenOrigin();
        o.blockUsed(100, 54);
        assertTrue(o.menuOpened(102, 54));
    }

    @Test
    void screenWithoutBlockClickIsNotTrusted() {
        // Loja aberta por comando: nenhum clique em bloco antes.
        assertFalse(new ScreenOrigin().menuOpened(500, 27));
    }

    @Test
    void clickOnNonStorageBlockIsNotTrusted() {
        ScreenOrigin o = new ScreenOrigin();
        o.blockUsed(100, 0);   // um botão, uma placa...
        assertFalse(o.menuOpened(101, 27));
    }

    @Test
    void lastBlockUsedWins() {
        ScreenOrigin o = new ScreenOrigin();
        o.blockUsed(100, 27);
        o.blockUsed(101, 0);
        assertFalse(o.menuOpened(102, 27));
    }

    @Test
    void differentSizeIsNotTrusted() {
        ScreenOrigin o = new ScreenOrigin();
        o.blockUsed(100, 27);
        assertFalse(o.menuOpened(101, 54));
    }

    @Test
    void outsideTheWindowIsNotTrusted() {
        ScreenOrigin o = new ScreenOrigin();
        o.blockUsed(100, 27);
        assertFalse(o.menuOpened(100 + ScreenOrigin.WINDOW_TICKS + 1, 27));
        o.blockUsed(100, 27);
        assertTrue(o.menuOpened(100 + ScreenOrigin.WINDOW_TICKS, 27));   // no limite ainda vale
    }

    @Test
    void recordIsConsumedByTheFirstScreen() {
        ScreenOrigin o = new ScreenOrigin();
        o.blockUsed(100, 27);
        assertTrue(o.menuOpened(101, 27));
        assertFalse(o.menuOpened(102, 27));   // página 2 da loja: menu novo sem novo clique
    }

    @Test
    void entityClickErasesTheRecord() {
        ScreenOrigin o = new ScreenOrigin();
        o.blockUsed(100, 27);
        o.entityUsed();   // NPC vendedor
        assertFalse(o.menuOpened(101, 27));
    }

    @Test
    void timeGoingBackwardsIsNotTrusted() {
        assertFalse(ScreenOrigin.fromStorage(true, 27, -1, 27));
        assertFalse(ScreenOrigin.fromStorage(false, 27, 1, 27));
    }
}
