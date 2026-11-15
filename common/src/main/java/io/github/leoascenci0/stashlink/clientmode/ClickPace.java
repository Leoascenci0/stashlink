package io.github.leoascenci0.stashlink.clientmode;

/**
 * Ritmo dos cliques do modo cliente (preferência do jogador, não é uma função com cadeado). Servidor sem o mod e
 * com anticheat pode punir rajadas de cliques; o ritmo cuidadoso troca velocidade por segurança. Não conhece o
 * Minecraft.
 */
public enum ClickPace {
    /** Servidor tranquilo (Realms, vanilla): o mais rápido. */
    FAST("fast", 5, 4, 3),
    /** Padrão. */
    NORMAL("normal", 3, 2, 3),
    /** Servidor com anticheat: um clique por tick e pausa maior entre containers. */
    CAREFUL("careful", 1, 1, 6);

    private final String id;
    private final int clicksPerTick;
    private final int hoverPerTick;
    private final int pauseTicks;

    ClickPace(String id, int clicksPerTick, int hoverPerTick, int pauseTicks) {
        this.id = id;
        this.clicksPerTick = clicksPerTick;
        this.hoverPerTick = hoverPerTick;
        this.pauseTicks = pauseTicks;
    }

    /** Nome estável (arquivo de config e chaves de idioma). */
    public String id() {
        return id;
    }

    /** Cliques por tick dentro de um container (tecla N e W). */
    public int clicksPerTick() {
        return clicksPerTick;
    }

    /** Cliques por tick no Shift + passar o mouse. */
    public int hoverPerTick() {
        return hoverPerTick;
    }

    /** Ticks de pausa entre um container e o próximo. */
    public int pauseTicks() {
        return pauseTicks;
    }

    /** Acha pelo nome; desconhecido ou {@code null} = {@link #NORMAL}. */
    public static ClickPace byId(String id) {
        for (ClickPace p : values()) {
            if (p.id.equals(id)) {
                return p;
            }
        }
        return NORMAL;
    }

    /** Próximo do ciclo do botão (rápido, normal, cuidadoso, e volta). */
    public ClickPace next() {
        return values()[(ordinal() + 1) % values().length];
    }
}
