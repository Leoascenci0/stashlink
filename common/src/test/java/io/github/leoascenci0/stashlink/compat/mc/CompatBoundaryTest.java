package io.github.leoascenci0.stashlink.compat.mc;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regra do projeto (docs/UPDATING.md): API frágil do Minecraft só em {@code compat/mc/}. Nas bancadas (Item 16) isso
 * quer dizer: as classes de menu de cada estação, o livro de receitas e as cores/itens de corante só aparecem lá; o
 * resto pergunta por {@link BenchCompat.Station} e pelos métodos do compat.
 */
class CompatBoundaryTest {
    private static final Pattern FRAGILE = Pattern.compile(
            "\\b(LoomMenu|StonecutterMenu|SmithingMenu|EnchantmentMenu|BrewingStandMenu|AnvilMenu|CraftingMenu"
                    + "|GrindstoneMenu|CartographyTableMenu|AbstractFurnaceMenu|RecipeBookMenu|DyeColor|SlotDisplayContext)\\b"
                    + "|Items\\.(DYE|BANNER)\\b");

    private static final Path SOURCE = Path.of("src/main/java/io/github/leoascenci0/stashlink");

    @Test
    void benchCodeOutsideCompatNeverTouchesStationApi() throws IOException {
        List<String> leaks = new ArrayList<>();
        try (Stream<Path> files = Stream.concat(Files.list(SOURCE.resolve("bench")),
                Files.list(SOURCE.resolve("client")).filter(p -> p.getFileName().toString().startsWith("Bench")))) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                List<String> lines = Files.readAllLines(file);
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i).strip();
                    if (!line.startsWith("*") && !line.startsWith("//") && !line.startsWith("/*")
                            && FRAGILE.matcher(line).find()) {
                        leaks.add(file.getFileName() + ":" + (i + 1) + "  " + line);
                    }
                }
            }
        }
        assertTrue(leaks.isEmpty(), "API frágil do Minecraft fora de compat/mc/:\n" + String.join("\n", leaks));
    }
}
