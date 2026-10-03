package io.github.leoascenci0.stashlink.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Liga/desliga + cadeado das funções: máscaras, arquivo de config, preferências e política do cliente. */
class FeatureTest {
    private Set<Feature> locked;

    @BeforeEach
    void save() {
        locked = EnumSet.copyOf(StashLinkConfig.lockedFeatures.isEmpty()
                ? EnumSet.noneOf(Feature.class) : StashLinkConfig.lockedFeatures);
        ClientPolicy.reset();
    }

    @AfterEach
    void restore() {
        StashLinkConfig.lockedFeatures = locked;
        ClientPolicy.reset();
    }

    @Test
    void bitsAreUniqueAndMaskRoundTrips() {
        int all = 0;
        for (Feature f : Feature.values()) {
            assertEquals(0, all & f.bit(), "bit repetido em " + f);
            all |= f.bit();
        }
        assertEquals(Feature.ALL_MASK, all);
        Set<Feature> some = EnumSet.of(Feature.REFILL, Feature.SLOT_LOCK);
        assertEquals(some, Feature.fromMask(Feature.toMask(some)));
        // bits que não existem são ignorados
        assertEquals(EnumSet.noneOf(Feature.class), Feature.fromMask(~Feature.ALL_MASK));
    }

    @Test
    void idsAreStableAndFindable() {
        for (Feature f : Feature.values()) {
            assertEquals(f, Feature.byId(f.id()).orElseThrow());
        }
        assertTrue(Feature.byId("nao_existe").isEmpty());
    }

    @Test
    void lockedFeaturesSurviveTheConfigFile(@TempDir Path dir) {
        StashLinkConfig.lockedFeatures = EnumSet.of(Feature.LOOT_ALL, Feature.PULL);
        Path file = dir.resolve("stashlink.json");
        assertTrue(StashLinkConfig.save(file));

        StashLinkConfig.lockedFeatures = EnumSet.noneOf(Feature.class);
        assertTrue(StashLinkConfig.load(file));
        assertEquals(EnumSet.of(Feature.LOOT_ALL, Feature.PULL), StashLinkConfig.lockedFeatures);
    }

    @Test
    void unknownFeatureNamesInTheFileAreIgnored(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("stashlink.json");
        Files.writeString(file, "{\"lockedFeatures\": [\"quick_stack\", \"funcao_do_futuro\"]}");
        assertTrue(StashLinkConfig.load(file));
        assertEquals(EnumSet.of(Feature.QUICK_STACK), StashLinkConfig.lockedFeatures);
    }

    @Test
    void fileWithoutTheFieldLocksNothing(@TempDir Path dir) throws Exception {
        StashLinkConfig.lockedFeatures = EnumSet.of(Feature.REFILL);
        Path file = dir.resolve("stashlink.json");
        Files.writeString(file, "{\"sourceRadius\": 8}");
        assertTrue(StashLinkConfig.load(file));
        assertTrue(StashLinkConfig.lockedFeatures.isEmpty());
    }

    @Test
    void setFeatureLockedLocksAndUnlocks() {
        StashLinkConfig.lockedFeatures = EnumSet.noneOf(Feature.class);
        StashLinkConfig.setFeatureLocked(Feature.PULL, true);
        assertTrue(StashLinkConfig.isFeatureLocked(Feature.PULL));
        assertFalse(StashLinkConfig.isFeatureLocked(Feature.REFILL));
        StashLinkConfig.setFeatureLocked(Feature.PULL, false);
        assertFalse(StashLinkConfig.isFeatureLocked(Feature.PULL));
    }

    @Test
    void playerPrefsKeepOnlyRealFeatureBits() {
        PlayerPrefs p = new PlayerPrefs(8, 0, List.of(), -1).sanitized();
        assertEquals(Feature.ALL_MASK, p.disabledFeatures());
        assertEquals(0, new PlayerPrefs(8, 0, List.of()).sanitized().disabledFeatures());
    }

    @Test
    void clientPolicyFallsBackToLocalConfigUntilTheServerSpeaks() {
        StashLinkConfig.lockedFeatures = EnumSet.of(Feature.SLOT_LOCK);
        assertFalse(ClientPolicy.known());
        assertTrue(ClientPolicy.locked(Feature.SLOT_LOCK)); // sem política do servidor: vale o arquivo local

        ClientPolicy.apply(Feature.REFILL.bit(), true);
        assertTrue(ClientPolicy.known());
        assertTrue(ClientPolicy.canEdit());
        assertTrue(ClientPolicy.locked(Feature.REFILL));
        assertFalse(ClientPolicy.locked(Feature.SLOT_LOCK)); // agora manda o servidor

        int before = ClientPolicy.version();
        ClientPolicy.reset();
        assertFalse(ClientPolicy.known());
        assertFalse(ClientPolicy.canEdit());
        assertTrue(ClientPolicy.version() != before);
    }
}
