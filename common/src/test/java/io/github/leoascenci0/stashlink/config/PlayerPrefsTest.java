package io.github.leoascenci0.stashlink.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerPrefsTest {
    private int max;

    @BeforeEach
    void save() {
        max = StashLinkConfig.maxRadius;
    }

    @AfterEach
    void restore() {
        StashLinkConfig.maxRadius = max;
    }

    @Test
    void radiusIsCappedByServer() {
        StashLinkConfig.maxRadius = 16;
        assertEquals(16, new PlayerPrefs(64, 1, List.of()).sanitized().radius());
        assertEquals(5, new PlayerPrefs(5, 1, List.of()).sanitized().radius());
    }

    @Test
    void negativeMeansServerDefault() {
        PlayerPrefs p = new PlayerPrefs(-99, -7, List.of()).sanitized();
        assertEquals(PlayerPrefs.UNSET, p.radius());
        assertEquals(PlayerPrefs.UNSET, p.chests());
    }

    @Test
    void chestsOnlyZeroOrOne() {
        assertEquals(1, new PlayerPrefs(8, 5, List.of()).sanitized().chests());
        assertEquals(0, new PlayerPrefs(8, 0, List.of()).sanitized().chests());
    }

    @Test
    void slotsAreFilteredSortedAndDeduplicated() {
        PlayerPrefs p = new PlayerPrefs(8, 0, Arrays.asList(35, 9, 9, -1, 36, 100, null, 0)).sanitized();
        assertEquals(List.of(0, 9, 35), p.lockedSlots());
    }
}
