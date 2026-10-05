package io.github.leoascenci0.stashlink.config;

import com.google.gson.JsonSyntaxException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

class StashLinkConfigTest {
    private int radius, max;
    private boolean chests;
    private Set<Integer> locked;

    @BeforeEach
    void save() {
        radius = StashLinkConfig.sourceRadius;
        max = StashLinkConfig.maxRadius;
        chests = StashLinkConfig.includeChests;
        locked = new TreeSet<>(StashLinkConfig.lockedSlots);
    }

    @AfterEach
    void restore() {
        StashLinkConfig.sourceRadius = radius;
        StashLinkConfig.maxRadius = max;
        StashLinkConfig.includeChests = chests;
        StashLinkConfig.lockedSlots = locked;
    }

    @Test
    void roundTripKeepsValues(@TempDir Path dir) {
        StashLinkConfig.sourceRadius = 10;
        StashLinkConfig.shulkerRadius = 40;
        StashLinkConfig.maxRadius = 12;
        StashLinkConfig.includeChests = true;
        StashLinkConfig.lockedSlots = new TreeSet<>(Set.of(9, 35));
        Path file = dir.resolve("sub/stashlink.json");
        assertTrue(StashLinkConfig.save(file));

        StashLinkConfig.sourceRadius = 1;
        StashLinkConfig.maxRadius = StashLinkConfig.HARD_MAX_RADIUS;
        StashLinkConfig.shulkerRadius = 1;
        StashLinkConfig.includeChests = false;
        StashLinkConfig.lockedSlots = new TreeSet<>();
        assertTrue(StashLinkConfig.load(file));

        assertEquals(10, StashLinkConfig.sourceRadius);
        assertEquals(40, StashLinkConfig.shulkerRadius);
        assertEquals(12, StashLinkConfig.maxRadius);
        assertTrue(StashLinkConfig.includeChests);
        assertEquals(Set.of(9, 35), StashLinkConfig.lockedSlots);
    }

    @Test
    void missingFileIsCreatedWithDefaults(@TempDir Path dir) {
        Path file = dir.resolve("stashlink.json");
        assertTrue(StashLinkConfig.load(file));
        assertTrue(Files.exists(file));
    }

    @Test
    void outOfRangeValuesAreCorrectedNotRejected() {
        StashLinkConfig.fromJson("{\"sourceRadius\":9999,\"maxRadius\":500,\"lockedSlots\":[-1,3,36,100,9]}");
        assertEquals(StashLinkConfig.HARD_MAX_RADIUS, StashLinkConfig.maxRadius);
        assertEquals(StashLinkConfig.HARD_MAX_RADIUS, StashLinkConfig.sourceRadius);
        assertEquals(Set.of(3, 9), StashLinkConfig.lockedSlots);
    }

    @Test
    void serverCapLimitsRadiusFromFile() {
        StashLinkConfig.fromJson("{\"configVersion\":2,\"sourceRadius\":50,\"maxRadius\":16}");
        assertEquals(16, StashLinkConfig.sourceRadius);
        assertEquals(16, StashLinkConfig.effectiveRadius());
    }

    /** Item 15: padrão 16, teto do código 32 (sem conduíte). */
    @Test
    void defaultRadiusIs16AndCodeCapIs32() {
        assertEquals(16, StashLinkConfig.DEFAULT_RADIUS);
        assertEquals(32, StashLinkConfig.HARD_MAX_RADIUS);
        StashLinkConfig.fromJson("{\"configVersion\":2}");
        assertEquals(16, StashLinkConfig.sourceRadius);
        assertEquals(32, StashLinkConfig.radiusCap());
        StashLinkConfig.maxRadius = StashLinkConfig.HARD_MAX_RADIUS;
        assertTrue(StashLinkConfig.trySetRadius(32));
        assertFalse(StashLinkConfig.trySetRadius(33));
    }

    /** Arquivo de antes do Item 15 com os padrões antigos (teto 16, raio 8) ganha os novos (32 e 16). */
    @Test
    void oldFileWithOldDefaultsIsUpgraded() {
        StashLinkConfig.fromJson("{\"sourceRadius\":8,\"maxRadius\":16}");
        assertEquals(32, StashLinkConfig.maxRadius);
        assertEquals(16, StashLinkConfig.sourceRadius);
    }

    /** Arquivo antigo com valores escolhidos pelo dono do servidor: nada muda. */
    @Test
    void oldFileWithChosenValuesIsKept() {
        StashLinkConfig.fromJson("{\"sourceRadius\":5,\"maxRadius\":12}");
        assertEquals(12, StashLinkConfig.maxRadius);
        assertEquals(5, StashLinkConfig.sourceRadius);
    }

    /** O arquivo gravado agora já diz a versão nova, então "16" escolhido depois não volta a virar 32. */
    @Test
    void savedFileCarriesTheNewVersion() {
        StashLinkConfig.maxRadius = 16;
        StashLinkConfig.sourceRadius = 8;
        String json = StashLinkConfig.toJson();
        assertTrue(json.contains("\"configVersion\": 2"), json);
        StashLinkConfig.fromJson(json);
        assertEquals(16, StashLinkConfig.maxRadius);
        assertEquals(8, StashLinkConfig.sourceRadius);
    }

    @Test
    void brokenFileKeepsCurrentValuesAndIsNotOverwritten(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("stashlink.json");
        Files.writeString(file, "{ isto não é json");
        StashLinkConfig.sourceRadius = 12;
        assertFalse(StashLinkConfig.load(file));
        assertEquals(12, StashLinkConfig.sourceRadius);
        assertEquals("{ isto não é json", Files.readString(file));
        assertThrows(JsonSyntaxException.class, () -> StashLinkConfig.fromJson(""));
    }

    @Test
    void savingOverABrokenFileKeepsABackupFirst(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("stashlink.json");
        Path bak = dir.resolve("stashlink.json.bak");
        Files.writeString(file, "{ isto não é json");
        assertFalse(StashLinkConfig.load(file));

        assertTrue(StashLinkConfig.save(file));
        assertEquals("{ isto não é json", Files.readString(bak), "o conteúdo quebrado ficou guardado");
        assertTrue(Files.readString(file).contains("configVersion"), "e o arquivo agora é um JSON de verdade");

        // O arquivo já está bom: salvar de novo não gera outra cópia nem mexe no .bak.
        assertTrue(StashLinkConfig.save(file));
        assertEquals("{ isto não é json", Files.readString(bak));
    }

    @Test
    void anExistingBackupIsNeverOverwritten(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("stashlink.json");
        Path bak = dir.resolve("stashlink.json.bak");
        Files.writeString(bak, "backup antigo");
        Files.writeString(file, "{ quebrado de novo");
        assertFalse(StashLinkConfig.load(file));

        assertTrue(StashLinkConfig.save(file));
        assertEquals("backup antigo", Files.readString(bak), "o .bak que já existia continua igual");
        try (var files = Files.list(dir)) {
            assertEquals(1, files.filter(p -> p.getFileName().toString().endsWith(".bak")
                    && !p.equals(bak)).filter(p -> {
                try {
                    return Files.readString(p).equals("{ quebrado de novo");
                } catch (java.io.IOException e) {
                    return false;
                }
            }).count(), "a cópia nova ganhou a data no nome");
        }
    }

    @Test
    void aFutureVersionFileIsNotDowngradedByLoad(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("stashlink.json");
        String future = "{ \"configVersion\": 3, \"sourceRadius\": 11, \"campoDoFuturo\": true }";
        Files.writeString(file, future);

        assertTrue(StashLinkConfig.load(file), "o que o mod entende é usado");
        assertEquals(11, StashLinkConfig.sourceRadius);
        assertEquals(future, Files.readString(file), "mas o arquivo não é regravado (nem como versão 2)");
        assertFalse(Files.exists(dir.resolve("stashlink.json.bak")));
    }

    @Test
    void savingOverAFutureVersionFileKeepsABackup(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("stashlink.json");
        String future = "{ \"configVersion\": 3, \"sourceRadius\": 11, \"campoDoFuturo\": true }";
        Files.writeString(file, future);
        StashLinkConfig.load(file);

        assertTrue(StashLinkConfig.save(file), "comando/tela ainda gravam");
        assertEquals(future, Files.readString(dir.resolve("stashlink.json.bak")), "mas o arquivo do futuro fica guardado");
    }

    @Test
    void trySetRadiusRespectsServerCap() {
        StashLinkConfig.maxRadius = 16;
        assertTrue(StashLinkConfig.trySetRadius(16));
        assertEquals(16, StashLinkConfig.sourceRadius);
        assertFalse(StashLinkConfig.trySetRadius(17));
        assertFalse(StashLinkConfig.trySetRadius(-1));
        assertEquals(16, StashLinkConfig.sourceRadius);
    }
}
