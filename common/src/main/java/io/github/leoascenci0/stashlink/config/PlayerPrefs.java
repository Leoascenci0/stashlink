package io.github.leoascenci0.stashlink.config;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Preferências pessoais de UM jogador, que ele manda ao servidor (inclusive em Realms, onde não há comando nem
 * arquivo de config). O servidor nunca confia no valor: {@link #sanitized()} corrige tudo antes de guardar.
 *
 * @param radius           raio pedido em blocos, ou {@link #UNSET} para usar o padrão do servidor
 * @param chests           {@link #UNSET} = padrão do servidor, 0 = não usar baús/barris como fonte, 1 = usar
 * @param lockedSlots      slots (0-35) que a tecla N nunca esvazia
 * @param disabledFeatures máscara ({@link Feature#bit()}) das funções que o jogador desligou para si
 * @param shulkerRadius   raio das shulkers colocadas, ou {@link #UNSET} para o padrão do servidor
 */
public record PlayerPrefs(int radius, int chests, List<Integer> lockedSlots, int disabledFeatures, int shulkerRadius) {
    public static final int UNSET = -1;

    /** Sem personalização nenhuma: vale tudo o que o servidor definir. */
    public static final PlayerPrefs NONE = new PlayerPrefs(UNSET, UNSET, List.of(), 0, UNSET);

    /** Atalho sem funções desligadas. */
    public PlayerPrefs(int radius, int chests, List<Integer> lockedSlots) {
        this(radius, chests, lockedSlots, 0);
    }

    /** Atalho sem raio de shulker personalizado. */
    public PlayerPrefs(int radius, int chests, List<Integer> lockedSlots, int disabledFeatures) {
        this(radius, chests, lockedSlots, disabledFeatures, UNSET);
    }

    /**
     * Cópia segura: raio 0..teto do servidor (ou UNSET), chests só -1/0/1, slots 0..35 sem repetição, máscara só
     * com bits de funções que existem.
     */
    public PlayerPrefs sanitized() {
        int r = radius < 0 ? UNSET : Math.min(radius, StashLinkConfig.radiusCap());
        int c = chests < 0 ? UNSET : (chests == 0 ? 0 : 1);
        TreeSet<Integer> slots = new TreeSet<>();
        if (lockedSlots != null) {
            for (Integer s : lockedSlots) {
                if (s != null && s >= 0 && s < StashLinkConfig.INVENTORY_SLOTS) {
                    slots.add(s);
                }
            }
        }
        int s = shulkerRadius < 0 ? UNSET : Math.min(shulkerRadius, StashLinkConfig.shulkerCap());
        return new PlayerPrefs(r, c, new ArrayList<>(slots), disabledFeatures & Feature.ALL_MASK, s);
    }
}
