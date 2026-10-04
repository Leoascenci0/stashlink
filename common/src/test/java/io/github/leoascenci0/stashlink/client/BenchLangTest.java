package io.github.leoascenci0.stashlink.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Item 16.3: chaves {@code stashlink.bench.*} em paridade nos dois idiomas; as usadas existem; nada de texto de reserva. */
class BenchLangTest {
    private static final Path LANG = Path.of("src/main/resources/assets/stashlink/lang");
    private static final Path CLIENT = Path.of("src/main/java/io/github/leoascenci0/stashlink/client");

    private static Set<String> benchKeys(String file) throws IOException {
        JsonObject json = JsonParser.parseString(Files.readString(LANG.resolve(file), StandardCharsets.UTF_8)).getAsJsonObject();
        Set<String> keys = new TreeSet<>();
        for (String key : json.keySet()) {
            if (key.startsWith("stashlink.bench.")) {
                keys.add(key);
            }
        }
        return keys;
    }

    @Test
    void benchKeysAreTheSameInBothLanguages() throws IOException {
        assertEquals(benchKeys("en_us.json"), benchKeys("pt_br.json"));
    }

    @Test
    void everyBenchKeyUsedByThePanelExistsAndThereIsNoFallbackText() throws IOException {
        Set<String> known = benchKeys("en_us.json");
        Pattern literal = Pattern.compile("\"(stashlink\\.bench\\.[a-z_.]+)\"");
        List<String> problems = new ArrayList<>();
        try (Stream<Path> files = Files.list(CLIENT).filter(p -> p.getFileName().toString().startsWith("Bench"))) {
            for (Path file : (Iterable<Path>) files::iterator) {
                if (file.getFileName().toString().endsWith("Test.java")) {
                    continue;
                }
                String text = Files.readString(file, StandardCharsets.UTF_8);
                if (text.contains("translatableWithFallback")) {
                    problems.add(file.getFileName() + ": translatableWithFallback");
                }
                Matcher m = literal.matcher(text);
                while (m.find()) {
                    if (!known.contains(m.group(1))) {
                        problems.add(file.getFileName() + ": chave sem tradução " + m.group(1));
                    }
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }
}
