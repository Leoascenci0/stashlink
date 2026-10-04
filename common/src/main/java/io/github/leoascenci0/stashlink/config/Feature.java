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
    BENCH("bench"),
    // As três abaixo são AJUSTES, não funções: não têm liga/desliga. Só reaproveitam o cadeado (o servidor tranca e o
    // valor dele passa a valer para todos) e a mesma máscara de bits que viaja pela rede.
    /** Ajuste: raio de baús, barris e bancadas. */
    RADIUS("radius", true),
    /** Ajuste: raio das shulkers colocadas. */
    SHULKER_RADIUS("shulker_radius", true),
    /** Ajuste: usar baús e barris como fonte. */
    CHESTS("chests", true),
    // Categorias da tecla N (Item 17): ligada = a N guarda esse tipo de item; desligada = a N nunca o guarda. Usam o
    // mesmo bit "desligado pelo jogador" e o mesmo cadeado das funções (cadeado = a N nunca guarda aquele tipo aqui).
    /** Categoria da N: armaduras. */
    CAT_ARMOR("cat_armor", false, true),
    /** Categoria da N: ferramentas. */
    CAT_TOOLS("cat_tools", false, true),
    /** Categoria da N: armas. */
    CAT_WEAPONS("cat_weapons", false, true),
    /** Categoria da N: comida. */
    CAT_FOOD("cat_food", false, true),
    /** Categoria da N: poções. */
    CAT_POTIONS("cat_potions", false, true),
    /** Shift + passar o mouse coleta (ou guarda) o item do slot, sem clicar (Item 21). */
    HOVER_COLLECT("hover_collect"),
    /** Organizar o armazenamento: botão no baú, organizar o sistema todo (prévia/desfazer), busca e destaque (Item 20). */
    ORGANIZE("organize"),
    /** Botão de combustível nas fornalhas (fornalha, defumador, alto-forno): puxa carvão etc. do armazenamento (Item 16.3). */
    BENCH_FUEL("bench_fuel"),
    /** Mesa de encantamento: o lápis-lazúli do armazenamento entra sozinho no slot dele (Item 16.3). */
    BENCH_LAPIS("bench_lapis"),
    /** Bigorna: com um item no 1º slot, a aba Livros mostra só os livros que servem nele (Item 16.3). */
    BENCH_BOOK_FILTER("bench_book_filter"),
    /** Sinalizador: os ícones de pagamento viram botões que puxam o minério do armazenamento (Item 16.3). */
    BENCH_BEACON("bench_beacon");

    public static final int ALL_MASK = (1 << values().length) - 1;

    private final String id;
    private final boolean setting;
    private final boolean category;

    Feature(String id) {
        this(id, false, false);
    }

    Feature(String id, boolean setting) {
        this(id, setting, false);
    }

    Feature(String id, boolean setting, boolean category) {
        this.id = id;
        this.setting = setting;
        this.category = category;
    }

    /** É uma categoria de item da tecla N (liga/desliga + cadeado, mas mostrada na aba própria)? */
    public boolean isCategory() {
        return category;
    }

    /** É um ajuste (raio, usar baús) e não uma função com liga/desliga? Só tem cadeado. */
    public boolean isSetting() {
        return setting;
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
