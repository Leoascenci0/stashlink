package io.github.leoascenci0.stashlink.config;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * Cada função do mod que pode ser ligada/desligada na tela de config, como o seletor de dificuldade do jogo:
 * o jogador liga/desliga a <i>sua</i> cópia, e quem manda no servidor pode <b>trancar</b> (cadeado) a função
 * para todos. Função trancada não funciona naquele servidor, aconteça o que acontecer no cliente.
 *
 * <p>O {@link #id()} é o nome estável usado no arquivo de config, nos comandos e nas chaves de idioma
 * ({@code stashlink.feature.<id>}); o {@link #bit()} é a posição nas máscaras que viajam pela rede. Nunca
 * reordene nem reaproveite posições: acrescente sempre no fim.
 */
public enum Feature {
    /** Reabastecer a mão quando o item acaba. */
    REFILL("refill"),
    /** Tecla N: guardar a mochila nos baús próximos. */
    QUICK_STACK("quick_stack"),
    /** Tecla W: puxar tudo do container aberto. */
    LOOT_ALL("loot_all"),
    /** Litematica (e pegar bloco): trazer o item do armazenamento para a hotbar. */
    PULL("pull"),
    /** Alt + clique: reservar um slot de baú para um item. */
    SLOT_LOCK("slot_lock"),
    /** Tecla J / lápis no baú: nome e resumo do armazenamento, com holograma. */
    LABEL("label"),
    /** Bancadas e estações usam o armazenamento por perto como se fosse a mochila (Item 16). */
    BENCH("bench");

    public static final int ALL_MASK = (1 << values().length) - 1;

    private final String id;

    Feature(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    /** Posição desta função nas máscaras de bits. */
    public int bit() {
        return 1 << ordinal();
    }

    public static Optional<Feature> byId(String id) {
        for (Feature f : values()) {
            if (f.id.equals(id)) {
                return Optional.of(f);
            }
        }
        return Optional.empty();
    }

    /** Conjunto -> máscara. */
    public static int toMask(Set<Feature> features) {
        int mask = 0;
        for (Feature f : features) {
            mask |= f.bit();
        }
        return mask;
    }

    /** Máscara -> conjunto; bits que não correspondem a nenhuma função são ignorados. */
    public static Set<Feature> fromMask(int mask) {
        Set<Feature> out = EnumSet.noneOf(Feature.class);
        for (Feature f : values()) {
            if ((mask & f.bit()) != 0) {
                out.add(f);
            }
        }
        return out;
    }
}
