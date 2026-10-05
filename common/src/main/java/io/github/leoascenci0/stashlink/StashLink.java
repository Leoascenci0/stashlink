package io.github.leoascenci0.stashlink;

import io.github.leoascenci0.stashlink.bench.BenchSync;
import io.github.leoascenci0.stashlink.config.PlayerPrefsService;
import io.github.leoascenci0.stashlink.config.PlayerPrefsStore;
import io.github.leoascenci0.stashlink.platform.Services;
import net.minecraft.server.level.ServerPlayer;

// Ponto de entrada comum: os dois loaders (Fabric e NeoForge) chamam este init().
// Toda a lógica do mod deve nascer aqui; os módulos de loader são só "cola".
public class StashLink {

    public static void init() {
        io.github.leoascenci0.stashlink.config.StashLinkConfig.load();
        Constants.LOG.info("StashLink carregado em {} ({})", Services.PLATFORM.getPlatformName(), Services.PLATFORM.getEnvironmentName());
    }

    /**
     * O jogador está saindo (os dois loaders chamam aqui, na thread do servidor, antes de salvar o jogador): devolve
     * ao baú o que a bancada emprestou e esquece as preferências da sessão (o cliente as reenvia ao voltar).
     */
    public static void onPlayerLeave(ServerPlayer player) {
        BenchSync.release(player);
        PlayerPrefsStore.remove(player.getUUID());
        PlayerPrefsService.forget(player);
    }
}
