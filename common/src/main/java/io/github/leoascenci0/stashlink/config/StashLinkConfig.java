package io.github.leoascenci0.stashlink.config;

/**
 * Configuração provisória (valores fixos). O Item 10 troca isto por um arquivo de config real; quem consome
 * só usa os métodos daqui, então essa troca não mexe no resto do código.
 */
public final class StashLinkConfig {
    /** Teto duro, imposto pelo servidor: nenhuma config ou pedido de cliente passa disto (performance). */
    public static final int HARD_MAX_RADIUS = 64;

    /** Raio (em blocos) em volta do jogador onde containers colocados servem de fonte. 0 desliga. */
    public static int sourceRadius = 8;

    /** Se baús e barris (além de shulkers colocadas) servem de fonte. Desligado por padrão. */
    public static boolean includeChests = false;

    /** Slots do inventário (0-8 hotbar, 9-35 mochila) que a tecla N nunca esvazia. A hotbar é sempre ignorada. */
    public static java.util.Set<Integer> lockedSlots = new java.util.HashSet<>();

    /** Tempo mínimo entre duas execuções da tecla N do mesmo jogador (impede spam de pacotes). */
    public static final int QUICK_STACK_COOLDOWN_TICKS = 10;

    private StashLinkConfig() {
    }

    /** Vale {@code true} se a tecla N deve deixar este slot do inventário em paz. */
    public static boolean isSlotLocked(int inventorySlot) {
        return lockedSlots.contains(inventorySlot);
    }

    /** Raio realmente usado: sempre entre 0 e {@link #HARD_MAX_RADIUS}. */
    public static int effectiveRadius() {
        return Math.max(0, Math.min(sourceRadius, HARD_MAX_RADIUS));
    }
}
