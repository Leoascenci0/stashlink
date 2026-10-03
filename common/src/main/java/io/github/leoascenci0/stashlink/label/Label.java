package io.github.leoascenci0.stashlink.label;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * O "rótulo" de um baú: um nome curto e uma linha de resumo. Guarda o texto <b>como o jogador digitou</b>
 * (já limpo por {@link LabelText#sanitize}), com atalhos como {@code :apple:}; a conversão para texto do jogo
 * (com ícones) acontece só na hora de mostrar.
 */
public record Label(String name, String note) {
    public static final Label EMPTY = new Label("", "");

    /** Chave no NBT do bloco (e dentro do CUSTOM_DATA do item da shulker). */
    public static final String KEY = "stashlink_label";

    public static final Codec<Label> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("name", "").forGetter(Label::name),
            Codec.STRING.optionalFieldOf("note", "").forGetter(Label::note)
    ).apply(i, Label::new));

    public boolean isEmpty() {
        return name.isEmpty() && note.isEmpty();
    }

    /** Versão limpa e dentro dos limites; usada em tudo que vem de fora (rede, disco). */
    public Label sanitized() {
        return new Label(LabelText.sanitize(name, LabelText.MAX_NAME), LabelText.sanitize(note, LabelText.MAX_NOTE));
    }
}
