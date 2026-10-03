package io.github.leoascenci0.stashlink.config;

import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Preferências recebidas dos jogadores (só em memória: o cliente reenvia ao entrar). Quem não mandou nada, ou
 * não tem o mod, usa a config do servidor. O raio sempre respeita o teto do servidor, mesmo que a preferência
 * tenha sido guardada antes de o teto mudar.
 */
public final class PlayerPrefsStore {
    private static final Map<UUID, PlayerPrefs> PREFS = new ConcurrentHashMap<>();

    private PlayerPrefsStore() {
    }

    public static void set(UUID player, PlayerPrefs prefs) {
        PREFS.put(player, prefs.sanitized());
    }

    private static PlayerPrefs of(ServerPlayer player) {
        return PREFS.getOrDefault(player.getUUID(), PlayerPrefs.NONE);
    }

    /** Raio efetivo do jogador: a escolha dele ou o padrão do servidor, sempre entre 0 e o teto. */
    public static int radius(ServerPlayer player) {
        int r = of(player).radius();
        return r == PlayerPrefs.UNSET ? StashLinkConfig.effectiveRadius()
                : Math.max(0, Math.min(r, StashLinkConfig.radiusCap()));
    }

    /** Raio das shulkers colocadas: a escolha do jogador ou o padrão do servidor (32), limitado ao teto. */
    public static int shulkerRadius(ServerPlayer player) {
        int s = of(player).shulkerRadius();
        return s == PlayerPrefs.UNSET ? StashLinkConfig.effectiveShulkerRadius()
                : Math.max(0, Math.min(s, StashLinkConfig.shulkerCap()));
    }

    /** Baús e barris entram como fonte? A escolha do jogador, ou o padrão do servidor. */
    public static boolean includeChests(ServerPlayer player) {
        int c = of(player).chests();
        return c == PlayerPrefs.UNSET ? StashLinkConfig.includeChests : c == 1;
    }

    /**
     * A função está valendo para este jogador? Só se o servidor não a trancou (cadeado) <b>e</b> o jogador não a
     * desligou para si. O servidor decide aqui; o cliente só repete a conta para não mandar pedido à toa.
     */
    public static boolean featureEnabled(ServerPlayer player, Feature feature) {
        return !StashLinkConfig.isFeatureLocked(feature) && (of(player).disabledFeatures() & feature.bit()) == 0;
    }

    /** Slot travado pelo servidor (para todos) ou pelo próprio jogador. */
    public static boolean isSlotLocked(ServerPlayer player, int inventorySlot) {
        return StashLinkConfig.isSlotLocked(inventorySlot) || of(player).lockedSlots().contains(inventorySlot);
    }
}
