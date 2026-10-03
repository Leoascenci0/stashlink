package io.github.leoascenci0.stashlink.client;

import io.github.leoascenci0.stashlink.compat.mc.BenchCompat;
import io.github.leoascenci0.stashlink.config.Feature;
import io.github.leoascenci0.stashlink.network.BenchPoolSync;
import io.github.leoascenci0.stashlink.network.BenchPullRequest;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Lado cliente das bancadas com armazenamento (Item 16): guarda a última lista que o servidor mandou e a oferece
 * ao livro de receitas (para acender o que dá para fazer com os baús) e ao painel "Armazenamento". Nada aqui muda
 * item: o painel só <i>pede</i> ao servidor, que confere tudo de novo e põe o item no cursor.
 */
public final class BenchClient {
    private static int poolContainerId = -1;
    private static List<BenchPoolSync.Entry> pool = List.of();
    /** Sobe a cada lista nova: o livro de receitas e o painel se recalculam quando mudou. */
    private static int version;

    private static Consumer<BenchPullRequest> sender = request -> { };
    private static BooleanSupplier serverHasMod = () -> false;

    private BenchClient() {
    }

    /** Cada loader diz como enviar o pedido e se o servidor conhece o pacote (sem o mod no servidor, nada acontece). */
    public static void setSender(Consumer<BenchPullRequest> value) {
        sender = value;
    }

    public static void setServerHasMod(BooleanSupplier value) {
        serverHasMod = value;
    }

    /** Recebeu a lista do servidor (thread do cliente). */
    public static void apply(BenchPoolSync payload) {
        poolContainerId = payload.containerId();
        pool = List.copyOf(payload.entries());
        version++;
    }

    public static int version() {
        return version;
    }

    /** A função vale para mim, o servidor tem o mod e já mandou a lista desta estação? */
    public static boolean activeFor(AbstractContainerMenu menu) {
        return menu.containerId == poolContainerId && BenchCompat.isStation(menu)
                && ClientFeatures.enabled(Feature.BENCH) && serverHasMod.getAsBoolean();
    }

    public static List<BenchPoolSync.Entry> pool() {
        return pool;
    }

    public static void request(AbstractContainerMenu menu, BenchPoolSync.Entry entry, boolean one) {
        sender.accept(new BenchPullRequest(menu.containerId, entry.item().copyWithCount(1), one));
    }

    /** O livro de receitas desta estação soma ao que conhece da mochila o que o armazenamento tem (itens comuns). */
    public static void addTo(StackedItemContents contents, AbstractContainerMenu menu) {
        if (!activeFor(menu)) {
            return;
        }
        for (BenchPoolSync.Entry entry : pool) {
            if (BenchCompat.usableForCrafting(entry.item())) {
                BenchCompat.account(contents, entry.item(), entry.count());
            }
        }
    }
}
