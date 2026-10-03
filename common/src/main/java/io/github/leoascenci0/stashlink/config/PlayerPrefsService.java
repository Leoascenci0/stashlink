package io.github.leoascenci0.stashlink.config;

import io.github.leoascenci0.stashlink.Constants;
import io.github.leoascenci0.stashlink.network.PlayerPrefsRequest;
import net.minecraft.server.level.ServerPlayer;

/** Trata as preferências vindas do cliente. Roda na thread do servidor. */
public final class PlayerPrefsService {
    private PlayerPrefsService() {
    }

    public static void handle(ServerPlayer player, PlayerPrefsRequest request) {
        try {
            PlayerPrefsStore.set(player.getUUID(), request.prefs());
            // A resposta serve de "oi": o cliente fica sabendo dos cadeados e se pode mexer neles (a tela de
            // config manda as preferências ao abrir justamente para isso).
            FeaturePolicyService.send(player);
        } catch (RuntimeException e) {
            // Pacote vindo da rede: um erro aqui nunca pode derrubar o servidor.
            Constants.LOG.error("Falha ao guardar preferências de {}", player.getGameProfile().name(), e);
        }
    }
}
