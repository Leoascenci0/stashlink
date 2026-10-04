package io.github.leoascenci0.stashlink.client;

import io.github.leoascenci0.stashlink.MinecraftTestSetup;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Item 16.3: mensagens e cliques do painel das bancadas. */
class BenchTextTest {
    @BeforeAll
    static void bootstrap() {
        MinecraftTestSetup.init();
    }

    private static TranslatableContents contents(Component c) {
        return (TranslatableContents) c.getContents();
    }

    @Test
    void emptyListNamesTheEffectiveRadius() {
        TranslatableContents msg = contents(BenchText.empty(false, 12));
        assertEquals(BenchText.EMPTY_RADIUS, msg.getKey());
        assertArrayEquals(new Object[]{12}, msg.getArgs());
    }

    @Test
    void emptyListWithoutKnownRadiusUsesTheGenericMessage() {
        assertEquals(BenchText.EMPTY, contents(BenchText.empty(false, -1)).getKey());
    }

    @Test
    void emptySearchSaysNoMatchWhateverTheRadius() {
        assertEquals(BenchText.NO_MATCH, contents(BenchText.empty(true, 12)).getKey());
    }

    @Test
    void radiusZeroIsStillReported() {
        assertArrayEquals(new Object[]{0}, contents(BenchText.empty(false, 0)).getArgs());
    }

    @Test
    void storageCountIsOneTranslatableLine() {
        TranslatableContents line = contents(BenchText.inStorage(1234));
        assertEquals(BenchText.COUNT, line.getKey());
        assertArrayEquals(new Object[]{1234}, line.getArgs());
        assertEquals(BenchText.COUNT, contents(BenchText.itemLines(5).get(0)).getKey());
    }

    @Test
    void leftIsAStackRightIsOneShiftLeftIsMaxAndOtherButtonsAreNotHandled() {
        assertEquals(BenchText.Amount.STACK, BenchText.amountFor(BenchText.LEFT, false));
        assertEquals(BenchText.Amount.MAX, BenchText.amountFor(BenchText.LEFT, true));
        assertEquals(BenchText.Amount.ONE, BenchText.amountFor(BenchText.RIGHT, false));
        assertEquals(BenchText.Amount.ONE, BenchText.amountFor(BenchText.RIGHT, true));
        assertNull(BenchText.amountFor(2, false), "botão do meio (e qualquer outro) não é do painel");
        assertNull(BenchText.amountFor(0, true));
    }
}
