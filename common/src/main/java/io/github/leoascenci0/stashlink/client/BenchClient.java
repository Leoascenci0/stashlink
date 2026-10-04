package io.github.leoascenci0.stashlink.client;

import io.github.leoascenci0.stashlink.compat.mc.BenchCompat;
import io.github.leoascenci0.stashlink.config.Feature;
import io.github.leoascenci0.stashlink.network.BenchPoolSync;
import io.github.leoascenci0.stashlink.network.BenchPullRequest;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Lado cliente das bancadas com armazenamento (Item 16): guarda a última lista que o servidor mandou e a oferece
 * ao livro de receitas (para acender o que dá para fazer com os baús) e ao painel "Armazenamento". Nada aqui muda
 * item: o painel só <i>pede</i> ao servidor, que confere tudo de novo e põe o item no cursor.
 */
public final class BenchClient {
    private static int poolContainerId = -1;
    /** Raio efetivo de baús do jogador, como o servidor mandou (para a mensagem de lista vazia); -1 se desconhecido. */
    private static int radius = BenchPoolSync.UNKNOWN_RADIUS;
    private static List<BenchPoolSync.Entry> pool = List.of();
    /** As cores do tear por id, montadas a cada lista nova (o painel pergunta isto a cada frame). */
    private static Map<Integer, BenchPoolSync.Entry> colorPicks = Map.of();
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
        radius = payload.radius();
        pool = List.copyOf(payload.entries());
        Map<Integer, BenchPoolSync.Entry> picks = new HashMap<>();
        for (BenchPoolSync.Entry entry : pool) {
            if (entry.isColorPick()) {
                picks.putIfAbsent(entry.color(), entry);
            }
        }
        colorPicks = picks;
        version++;
    }

    /** Saiu do servidor (ou entrou em outro): esquece a lista e a estação da conexão anterior. */
    public static void reset() {
        poolContainerId = -1;
        radius = BenchPoolSync.UNKNOWN_RADIUS;
        pool = List.of();
        colorPicks = Map.of();
        version++;
    }

    /** A entrada da cor {@code color} do tear na lista do servidor, ou {@code null}. */
    public static BenchPoolSync.Entry colorPick(int color) {
        return colorPicks.get(color);
    }

    /** Id da estação a que a lista pertence (-1 se nenhuma); para testes. */
    public static int poolContainerId() {
        return poolContainerId;
    }

    /** Raio que o servidor diz que a estação enxerga, ou {@link BenchPoolSync#UNKNOWN_RADIUS}. */
    public static int radius() {
        return radius;
    }

    public static int version() {
        return version;
    }

    /** O painel vai aparecer nesta estação (função ligada para mim e servidor com o mod), mesmo antes de a lista chegar? */
    public static boolean expectedFor(AbstractContainerMenu menu) {
        return BenchCompat.isStation(menu) && ClientFeatures.enabled(Feature.BENCH) && serverHasMod.getAsBoolean();
    }

    /** A função vale para mim, o servidor tem o mod e já mandou a lista desta estação? */
    public static boolean activeFor(AbstractContainerMenu menu) {
        return menu.containerId == poolContainerId && BenchCompat.isStation(menu)
                && ClientFeatures.enabled(Feature.BENCH) && serverHasMod.getAsBoolean();
    }

    /** O armazenamento tem este item (a lista que o servidor mandou da estação aberta)? */
    public static boolean poolHas(net.minecraft.world.item.ItemStack stack) {
        for (BenchPoolSync.Entry entry : pool) {
            if (entry.item().is(stack.getItem()) && entry.count() > 0) {
                return true;
            }
        }
        return false;
    }

    /** Quantos deste item (mesmo item e componentes) a lista do servidor diz que há no armazenamento; 0 se nenhum. */
    public static int countOf(ItemStack stack) {
        int total = 0;
        for (BenchPoolSync.Entry entry : pool) {
            if (!entry.isResult() && !entry.isColorPick() && ItemStack.isSameItemSameComponents(entry.item(), stack)) {
                total += entry.count();
            }
        }
        return total;
    }

    public static List<BenchPoolSync.Entry> pool() {
        return pool;
    }

    public static void request(AbstractContainerMenu menu, BenchPoolSync.Entry entry, boolean one) {
        sender.accept(new BenchPullRequest(menu.containerId, entry.item().copyWithCount(1), one,
                io.github.leoascenci0.stashlink.bench.BenchResults.PLACE));
    }

    /** Botão de combustível da fornalha: {@code shown} é só o ícone (o servidor escolhe o combustível de novo). */
    public static void requestFuel(AbstractContainerMenu menu, ItemStack shown, boolean one) {
        sender.accept(new BenchPullRequest(menu.containerId, shown.copyWithCount(1), one,
                io.github.leoascenci0.stashlink.bench.BenchResults.FUEL));
    }

    /** Botão de pagamento do sinalizador: 1 de {@code payment} no slot de pagamento (o servidor confere tudo). */
    public static void requestPayment(AbstractContainerMenu menu, ItemStack payment) {
        sender.accept(new BenchPullRequest(menu.containerId, payment.copyWithCount(1), true,
                io.github.leoascenci0.stashlink.bench.BenchResults.PAY));
    }

    /** "Monte esta receita": {@code choice} é o item que o jogador escolheu junto (o corante da cor, no tear); {@code one}
     * = um de cada ingrediente (botão direito) em vez de um stack. */
    public static void requestRecipe(AbstractContainerMenu menu, BenchPoolSync.Entry entry, ItemStack choice, boolean one) {
        sender.accept(new BenchPullRequest(menu.containerId, choice.copyWithCount(1), one, entry.id()));
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
