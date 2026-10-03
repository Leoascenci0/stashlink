package io.github.leoascenci0.stashlink.config;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Porteiro das funções no servidor: cada serviço pergunta aqui antes de agir. Se a função está trancada pelo
 * servidor, o jogador é avisado (para não achar que o mod quebrou); se foi ele quem desligou, fica quieto.
 */
public final class FeatureGate {
    private FeatureGate() {
    }

    /** {@code true} se a função vale para o jogador; senão avisa (quando é cadeado do servidor) e devolve false. */
    public static boolean allow(ServerPlayer player, Feature feature) {
        if (PlayerPrefsStore.featureEnabled(player, feature)) {
            return true;
        }
        if (StashLinkConfig.isFeatureLocked(feature)) {
            player.sendOverlayMessage(Component.translatableWithFallback("stashlink.feature.locked_here",
                    "This feature is locked on this server"));
        }
        return false;
    }

    /** Igual a {@link #allow}, mas sem nunca avisar (funções automáticas, como o reabastecimento). */
    public static boolean allowSilently(ServerPlayer player, Feature feature) {
        return PlayerPrefsStore.featureEnabled(player, feature);
    }
}
