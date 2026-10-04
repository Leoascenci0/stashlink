package io.github.leoascenci0.stashlink.bench;

import io.github.leoascenci0.stashlink.Constants;
import io.github.leoascenci0.stashlink.compat.mc.BenchCompat;
import io.github.leoascenci0.stashlink.config.Feature;
import io.github.leoascenci0.stashlink.config.FeatureGate;
import io.github.leoascenci0.stashlink.config.PlayerPrefsStore;
import io.github.leoascenci0.stashlink.network.BenchPoolSync;
import io.github.leoascenci0.stashlink.platform.Services;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.lang.ref.WeakReference;
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
        /** Fraca: o valor deste mapa nunca pode segurar o menu (e, por ele, o jogador) vivo. */
        final WeakReference<AbstractContainerMenu> menu;
        /** Nulo até o primeiro envio: a primeira lista vai sempre, mesmo vazia (o painel mostra "nada por perto"). */
        List<BenchPoolSync.Entry> last = null;
        int lastRadius = BenchPoolSync.UNKNOWN_RADIUS;
        int age;
        /** {@link BenchCompat#listKey} da última passada: mudou (item no 1º slot da bigorna) → reenvia já. */
        int listKey;
        boolean dirty = true;
        boolean unsupported;

        State(AbstractContainerMenu menu) {
            this.menu = new WeakReference<>(menu);
        }
    }

    /** Por identidade do jogador; sai daqui no logout ({@link #release}) e o valor não segura o menu. */
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

    /**
     * O jogador vai sair (desconectou) ou o servidor vai parar: fecha a estação e devolve ao baú o que foi emprestado,
     * <b>antes</b> de o jogo salvar o jogador. Só roda na thread do servidor (baú e inventário não são thread-safe). Sem isso o que está na grade e no cursor seria salvo na mochila
     * do jogador sem o baú saber (e o baú já foi desfalcado). Tolera erro: o logout nunca pode travar por aqui.
     */
    public static void release(ServerPlayer player) {
        if (!player.level().getServer().isSameThread()) {
            return;   // só a thread do servidor mexe em baú e inventário (o evento de rede de desconexão roda fora dela)
        }
        try {
            BenchLedger.release(player);
        } catch (RuntimeException e) {
            Constants.LOG.error("Falha ao devolver o emprestado de {} ao sair", player.getGameProfile().name(), e);
        } finally {
            STATES.remove(player);
        }
    }

    /** Parada do servidor: libera todo mundo que ainda está online (o save de todos vem logo depois). */
    public static void releaseAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            release(player);
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
        BenchLedger.tick(player);   // devolve à origem o que não foi usado ao fechar a estação
        AbstractContainerMenu menu = player.containerMenu;
        if (menu == player.inventoryMenu || !BenchCompat.isStation(menu)
                || player.isSpectator() || !FeatureGate.allowSilently(player, Feature.BENCH)) {
            STATES.remove(player);
            return;
        }
        State state = STATES.get(player);
        if (state == null || state.menu.get() != menu) {
            state = new State(menu);
            STATES.put(player, state);
        }
        state.age++;
        BenchLapis.tick(player, menu);   // lápis-lazúli automático no encantamento (também para cliente sem o mod)
        int listKey = BenchCompat.listKey(menu);
        if (listKey != state.listKey) {
            state.listKey = listKey;
            state.dirty = true;
        }
        if (state.unsupported || (!state.dirty && state.age < REFRESH_TICKS)) {
            return;
        }
        state.dirty = false;
        state.age = 0;
        List<BenchPoolSync.Entry> now = snapshot(player);
        int radius = radiusFor(player);
        if (now.equals(state.last) && radius == state.lastRadius) {
            return;
        }
        state.last = now;
        state.lastRadius = radius;
        if (!Services.PLATFORM.sendIfSupported(player, new BenchPoolSync(menu.containerId, now, radius))) {
            state.unsupported = true;
        }
    }

    /** O que a estação aberta enxerga e aceita (só o que serve nela), pronto para o pacote. Público para os testes. */
    public static List<BenchPoolSync.Entry> snapshot(ServerPlayer player) {
        return capBytes(player, build(player));
    }

    /** Corta a lista no teto de bytes do pacote ({@link BenchPoolSync#MAX_BYTES}), mantendo a ordem. */
    static List<BenchPoolSync.Entry> capBytes(ServerPlayer player, List<BenchPoolSync.Entry> list) {
        List<BenchPoolSync.Entry> out = new ArrayList<>(list.size());
        long bytes = 0;
        for (BenchPoolSync.Entry entry : list) {
            bytes += BenchCompat.packetSize(player, entry.item()) + 16;
            if (bytes > BenchPoolSync.MAX_BYTES) {
                break;
            }
            out.add(entry);
        }
        return out;
    }

    /**
     * O raio que a estação enxerga de verdade (o efetivo: escolha do jogador limitada pelo teto do servidor, ou o
     * padrão), e 0 se "usar baús como fonte" está desligado. É o mesmo valor que {@code NearbyContainers} usa.
     */
    public static int radiusFor(ServerPlayer player) {
        return PlayerPrefsStore.includeChests(player) ? PlayerPrefsStore.radius(player) : 0;
    }

    private static List<BenchPoolSync.Entry> build(ServerPlayer player) {
        AbstractContainerMenu menu = player.containerMenu;
        if (BenchResults.supports(menu)) {
            return BenchResults.list(player);
        }
        List<BenchPoolSync.Entry> out = new ArrayList<>();
        boolean autoLapis = FeatureGate.allowSilently(player, Feature.BENCH_LAPIS);
        boolean bookFilter = FeatureGate.allowSilently(player, Feature.BENCH_BOOK_FILTER);
        for (BenchPool.Stack stack : BenchPool.of(player).contents()) {
            if (!BenchCompat.relevant(menu, player, stack.item())) {
                continue;
            }
            // Lápis automático ligado: o lápis não aparece no painel (o servidor o põe sozinho).
            if (autoLapis && BenchCompat.isLapisFor(menu, stack.item())) {
                continue;
            }
            // Bigorna: com um item no 1º slot, só os livros com encantamento que serve nele.
            if (bookFilter && !BenchCompat.bookFits(menu, stack.item())) {
                continue;
            }
            out.add(new BenchPoolSync.Entry(stack.item(), stack.count(), -1, false,
                    BenchResults.slotTab(menu, stack.item()), -1));
        }
        // Ordem única (BenchOrder); o corte do teto é depois de ordenar.
        return BenchOrder.sortedAndCapped(out);
    }
}
