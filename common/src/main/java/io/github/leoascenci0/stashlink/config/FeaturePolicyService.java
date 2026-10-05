package io.github.leoascenci0.stashlink.config;

import io.github.leoascenci0.stashlink.Constants;
import io.github.leoascenci0.stashlink.compat.mc.McCompat;
import io.github.leoascenci0.stashlink.network.FeaturePolicySync;
import io.github.leoascenci0.stashlink.network.SetFeatureLockRequest;
import io.github.leoascenci0.stashlink.platform.Services;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Política de funções do servidor: quais estão trancadas (cadeado) e quem pode mexer nisso. O servidor é a
 * autoridade: o pedido do cliente é conferido a cada vez (dono/operador) e a mudança é gravada em
 * {@code stashlink.json} e avisada a todos os jogadores com o mod.
 */
public final class FeaturePolicyService {
    private FeaturePolicyService() {
    }

    /** Manda a política atual a um jogador (e se ele pode mexer nela). Cliente sem o mod não recebe nada. */
    public static void send(ServerPlayer player) {
        Services.PLATFORM.sendIfSupported(player,
                new FeaturePolicySync(Feature.toMask(StashLinkConfig.lockedFeatures), McCompat.canManageServer(player)));
    }

    /** Manda a política a todos os jogadores do servidor (depois de uma mudança). */
    public static void broadcast(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            send(player);
        }
    }

    /** Pedido do cadeado vindo da tela de config. Roda na thread do servidor. */
    public static void handle(ServerPlayer player, SetFeatureLockRequest request) {
        try {
            process(player, request);
        } catch (RuntimeException e) {
            // Pacote vindo da rede: um erro aqui nunca pode derrubar o servidor.
            Constants.LOG.error("Falha ao trancar função a pedido de {}", player.getGameProfile().name(), e);
        }
    }

    private static void process(ServerPlayer player, SetFeatureLockRequest request) {
        if (!McCompat.canManageServer(player)) {
            player.sendOverlayMessage(Component.translatableWithFallback("stashlink.feature.not_allowed",
                    "Only the server owner or an operator can lock features"));
            send(player); // corrige a tela de quem ainda achava que podia
            return;
        }
        // Só aceita exatamente um bit conhecido.
        Feature target = null;
        for (Feature f : Feature.values()) {
            if (f.bit() == request.featureBit()) {
                target = f;
            }
        }
        if (target == null) {
            return;
        }
        apply(player.level().getServer(), target, request.locked());
    }

    /** Tranca/destranca, grava e avisa todos. Usado pelo pedido da tela e pelo comando. */
    public static void apply(MinecraftServer server, Feature feature, boolean locked) {
        if (StashLinkConfig.isFeatureLocked(feature) == locked) {
            return;   // já está assim: não regrava o arquivo nem avisa todos (pedido repetido não vira escrita em disco)
        }
        StashLinkConfig.setFeatureLocked(feature, locked);
        StashLinkConfig.save();
        broadcast(server);
    }
}
