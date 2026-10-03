package io.github.leoascenci0.stashlink.label;

import io.github.leoascenci0.stashlink.Constants;
import io.github.leoascenci0.stashlink.compat.mc.LabelCompat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tudo sobre o texto de um rótulo: limpar o que o jogador digitou (o servidor NUNCA confia no cliente) e
 * transformar em texto do jogo.
 *
 * <p><b>Emojis.</b> A fonte padrão do Minecraft (conferido no unifont do 26.3) não tem os emojis coloridos
 * (😀📦🔥): o plano 1 do Unicode só tem escritas antigas. Ela tem uns 60 símbolos úteis (❤ ⭐ ⚡ ✔ ⚔ ⛏...), que
 * passam direto, e o texto do jogo aceita <i>ícones inline</i> de qualquer textura de item ou bloco
 * ({@code Component.object}). Então: símbolos da fonte passam como digitados; {@code :apple:} vira o ícone da
 * maçã; {@code :heart:} vira ❤. Emoji de verdade digitado é descartado (apareceria como quadradinho).
 */
public final class LabelText {
    public static final int MAX_NAME = 48;
    public static final int MAX_NOTE = 64;

    private static final Pattern TOKEN = Pattern.compile(":([a-z0-9_]{1,48}):");

    /** Atalhos para símbolos que a fonte padrão tem. */
    private static final Map<String, String> SYMBOLS = new HashMap<>();
    /** Atalhos para ícones: nome -> "atlas:sprite" (arquivo {@code stashlink_sprites.txt}, gerado das texturas do jogo). */
    private static final Map<String, String[]> SPRITES = new HashMap<>();

    static {
        String[][] symbols = {
                {"heart", "❤"}, {"star", "⭐"}, {"bolt", "⚡"}, {"check", "✔"}, {"cross", "❌"},
                {"swords", "⚔"}, {"skull", "☠"}, {"mining", "⛏"}, {"snowflake", "❄"},
                {"sun", "☀"}, {"cloud", "☁"}, {"music", "♪"}, {"mail", "✉"},
                {"hourglass", "⌛"}, {"anchor", "⚓"}, {"flag", "⚐"}, {"spade", "♠"},
                {"club", "♣"}, {"diamond_suit", "♦"}, {"snowman", "⛄"}, {"pencil", "✎"},
                {"umbrella", "☂"}, {"infinity", "∞"}, {"smile", "☺"}, {"sad", "☹"},
                {"arrow_left", "←"}, {"arrow_up", "↑"}, {"arrow_right", "→"}, {"arrow_down", "↓"},
        };
        for (String[] s : symbols) {
            SYMBOLS.put(s[0], s[1]);
        }
        try (InputStream in = LabelText.class.getResourceAsStream("/stashlink_sprites.txt")) {
            if (in != null) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(" ");
                    if (parts.length == 2) {
                        // Itens primeiro: se existir item e bloco com o mesmo nome, vale o item.
                        if (parts[0].equals("i") || !SPRITES.containsKey(parts[1])) {
                            SPRITES.put(parts[1], parts);
                        }
                    }
                }
            }
        } catch (IOException e) {
            Constants.LOG.error("Não consegui ler a lista de ícones dos rótulos", e);
        }
    }

    private LabelText() {
    }

    /** O atalho existe (símbolo ou ícone)? Para os testes e para a ajuda. */
    public static boolean knowsShortcode(String name) {
        return SYMBOLS.containsKey(name) || SPRITES.containsKey(name);
    }

    public static Set<String> shortcodes() {
        Set<String> all = new HashSet<>(SYMBOLS.keySet());
        all.addAll(SPRITES.keySet());
        return all;
    }

    /**
     * Limpa o texto vindo de fora: tira códigos de formatação ({@code §}), caracteres de controle, de
     * direção (que embaralham o texto), invisíveis, de uso privado e emojis que a fonte não desenha; junta
     * espaços e corta em {@code maxChars} caracteres (pontos de código, não bytes).
     */
    public static String sanitize(String raw, int maxChars) {
        if (raw == null) {
            return "";
        }
        String text = Normalizer.normalize(raw, Normalizer.Form.NFC);
        StringBuilder out = new StringBuilder();
        int count = 0;
        boolean pendingSpace = false;
        for (int i = 0; i < text.length() && count < maxChars; ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (Character.isWhitespace(cp) || Character.getType(cp) == Character.SPACE_SEPARATOR) {
                pendingSpace = out.length() > 0;
                continue;
            }
            if (!allowed(cp)) {
                continue;
            }
            if (pendingSpace) {
                out.append(' ');
                count++;
                pendingSpace = false;
                if (count >= maxChars) {
                    break;
                }
            }
            out.appendCodePoint(cp);
            count++;
        }
        return out.toString();
    }

    private static boolean allowed(int cp) {
        if (cp == '§') {
            return false;                                   // § começa código de formatação
        }
        if (cp >= 0x1F000 && cp <= 0x1FAFF) {
            return false;                                   // emojis coloridos: a fonte não tem
        }
        if (cp >= 0x200B && cp <= 0x200F || cp >= 0x202A && cp <= 0x202E || cp >= 0x2060 && cp <= 0x206F
                || cp >= 0xFE00 && cp <= 0xFE0F || cp == 0xFEFF || cp >= 0xE0000 && cp <= 0xE01EF) {
            return false;                                   // invisíveis e direção de texto
        }
        return switch (Character.getType(cp)) {
            case Character.CONTROL, Character.FORMAT, Character.PRIVATE_USE, Character.SURROGATE,
                 Character.UNASSIGNED, Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR -> false;
            default -> true;
        };
    }

    /** Converte o texto limpo em texto do jogo: {@code :nome:} vira símbolo ou ícone; o resto fica como está. */
    public static MutableComponent toComponent(String text) {
        MutableComponent out = Component.empty();
        Matcher m = TOKEN.matcher(text);
        int last = 0;
        while (m.find()) {
            String code = m.group(1);
            String symbol = SYMBOLS.get(code);
            String[] sprite = symbol == null ? SPRITES.get(code) : null;
            if (symbol == null && sprite == null) {
                continue;                                   // ":qualquer_coisa:" sem atalho fica como texto
            }
            if (m.start() > last) {
                out.append(Component.literal(text.substring(last, m.start())));
            }
            out.append(symbol != null ? Component.literal(symbol)
                    : LabelCompat.sprite(sprite[0].equals("i"), sprite[1]));
            last = m.end();
        }
        if (last < text.length()) {
            out.append(Component.literal(text.substring(last)));
        }
        return out;
    }

    /** O que o holograma mostra: nome, e embaixo a nota em cinza. {@code null} se o rótulo é vazio. */
    public static Component hologram(Label label) {
        if (label.isEmpty()) {
            return null;
        }
        MutableComponent out = Component.empty();
        if (!label.name().isEmpty()) {
            out.append(toComponent(label.name()));
        }
        if (!label.note().isEmpty()) {
            if (!label.name().isEmpty()) {
                out.append(Component.literal("\n"));
            }
            out.append(toComponent(label.note()).withStyle(ChatFormatting.GRAY));
        }
        return out;
    }
}
