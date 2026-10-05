package io.github.leoascenci0.stashlink.config;

import io.github.leoascenci0.stashlink.Constants;
import io.github.leoascenci0.stashlink.network.PlayerPrefsRequest;
import io.github.leoascenci0.stashlink.network.RequestLimiter;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Trata as preferências vindas do cliente. Roda na thread do servidor. Cada aplicação responde com a política das
 * funções (um pacote a mais), então há um intervalo entre elas; um pedido que chega dentro do intervalo não se perde:
 * fica guardado só o último, e o {@link #tick} o aplica quando o intervalo acaba.
 */
public final class PlayerPrefsService {
    private static final RequestLimiter LIMITER = new RequestLimiter(StashLinkConfig.PLAYER_PREFS_COOLDOWN_TICKS);
    /** Por identidade do jogador: relogar cria outro objeto e o antigo é coletado sozinho. */
    private static final Map<ServerPlayer, PlayerPrefsRequest> PENDING = new WeakHashMap<>();

    private PlayerPrefsService() {
    }

    public static void handle(ServerPlayer player, PlayerPrefsRequest request) {
        if (!LIMITER.allow(player)) {
            PENDING.put(player, request);
            return;
        }
        PENDING.remove(player);
        apply(player, request);
    }

    /** Aplica os pedidos que esperavam o intervalo acabar (a cada tick do servidor; barato quando não há nenhum). */
    public static void tick(MinecraftServer server) {
        if (PENDING.isEmpty()) {
            return;
        }
        for (Map.Entry<ServerPlayer, PlayerPrefsRequest> e : new ArrayList<>(PENDING.entrySet())) {
            ServerPlayer player = e.getKey();
            if (player != null && LIMITER.allow(player)) {
                PENDING.remove(player);
                apply(player, e.getValue());
            }
        }
    }

    /** O jogador está saindo: o pedido que esperava não vale mais (senão guardaria as preferências de quem já foi). */
    public static void forget(ServerPlayer player) {
        PENDING.remove(player);
    }

    private static void apply(ServerPlayer player, PlayerPrefsRequest request) {
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
