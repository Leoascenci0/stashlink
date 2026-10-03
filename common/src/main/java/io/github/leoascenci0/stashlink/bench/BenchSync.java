package io.github.leoascenci0.stashlink.bench;

import io.github.leoascenci0.stashlink.Constants;
import io.github.leoascenci0.stashlink.compat.mc.BenchCompat;
import io.github.leoascenci0.stashlink.config.Feature;
import io.github.leoascenci0.stashlink.config.FeatureGate;
import io.github.leoascenci0.stashlink.network.BenchPoolSync;
import io.github.leoascenci0.stashlink.platform.Services;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Mantém o cliente com o mod sabendo o que o armazenamento tem enquanto uma estação está aberta (Item 16). Manda
 * ao abrir, quando o próprio mod mexeu no armazenamento ({@link #markDirty}) e de tempos em tempos (outro jogador
 * pode ter mexido nos baús). Só reenvia se a lista mudou. Cliente sem o mod nunca recebe nada, e para ele a
 * varredura não se repete.
 */
public final class BenchSync {
    /** A cada quantos ticks reconferir o armazenamento com a estação aberta (5 s). */
    static final int REFRESH_TICKS = 100;

    private static final class State {
        final AbstractContainerMenu menu;
        /** Nulo até o primeiro envio: a primeira lista vai sempre, mesmo vazia (o painel mostra "nada por perto"). */
        List<BenchPoolSync.Entry> last = null;
        int age;
        boolean dirty = true;
        boolean unsupported;

        State(AbstractContainerMenu menu) {
            this.menu = menu;
        }
    }

    /** Por identidade do jogador: relogar cria outro objeto e o antigo é coletado sozinho. */
    private static final Map<ServerPlayer, State> STATES = new WeakHashMap<>();

    private BenchSync() {
    }

    public static void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            try {
                tickPlayer(player);
            } catch (RuntimeException e) {
                Constants.LOG.error("Falha ao sincronizar o armazenamento da estação de {}",
                        player.getGameProfile().name(), e);
            }
        }
    }

    /** Pede para reenviar na próxima passada (o mod acabou de mexer no armazenamento). */
    public static void markDirty(ServerPlayer player) {
        State state = STATES.get(player);
        if (state != null) {
            state.dirty = true;
        }
    }

    private static void tickPlayer(ServerPlayer player) {
        AbstractContainerMenu menu = player.containerMenu;
        if (menu == player.inventoryMenu || !BenchCompat.isStation(menu)
                || player.isSpectator() || !FeatureGate.allowSilently(player, Feature.BENCH)) {
            STATES.remove(player);
            return;
        }
        State state = STATES.get(player);
        if (state == null || state.menu != menu) {
            state = new State(menu);
            STATES.put(player, state);
        }
        state.age++;
        if (state.unsupported || (!state.dirty && state.age < REFRESH_TICKS)) {
            return;
        }
        state.dirty = false;
        state.age = 0;
        List<BenchPoolSync.Entry> now = snapshot(player);
        if (now.equals(state.last)) {
            return;
        }
        state.last = now;
        if (!Services.PLATFORM.sendIfSupported(player, new BenchPoolSync(menu.containerId, now))) {
            state.unsupported = true;
        }
    }

    /** O que a estação aberta enxerga, pronto para o pacote. Público para os testes. */
    public static List<BenchPoolSync.Entry> snapshot(ServerPlayer player) {
        List<BenchPoolSync.Entry> out = new ArrayList<>();
        for (BenchPool.Stack stack : BenchPool.of(player).contents()) {
            if (out.size() >= BenchPoolSync.MAX_ENTRIES) {
                break;
            }
            out.add(new BenchPoolSync.Entry(stack.item(), stack.count()));
        }
        return out;
    }
}
