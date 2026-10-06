package io.github.leoascenci0.stashlink.bench;

import io.github.leoascenci0.stashlink.compat.mc.BenchCompat;
import io.github.leoascenci0.stashlink.compat.mc.McCompat;
import io.github.leoascenci0.stashlink.config.Feature;
import io.github.leoascenci0.stashlink.config.FeatureGate;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Lápis-lazúli automático na mesa de encantamento (Item 16.3, Fase 3): com a mesa aberta, o servidor completa o slot
 * de lápis até {@link BenchCompat#LAPIS_TARGET}, sem clique: <b>da mochila de quem abriu primeiro</b> e do
 * armazenamento ao alcance o que faltar (Item 16.5). Só o que veio do armazenamento entra no caderno de emprestados
 * ({@link BenchLedger}), igual a um item do painel: o que não for gasto volta ao baú de origem ao fechar a mesa, ao
 * sair do servidor ou ao parar o servidor (a mesa devolve os slots ao fechar, e o caderno os leva de volta antes
 * disso); o lápis da mochila volta para a mochila, como faria o jogo. O caderno conta o gasto primeiro do lápis do
 * jogador (o que sobra no slot é "emprestado" até o limite do que veio do baú). Tirar e pôr no slot é um passo só na
 * thread do servidor: nada some nem duplica.
 */
public final class BenchLapis {
    /** Depois de uma tentativa, espera este tanto (0,5 s) antes da próxima: sem lápis no raio, não varre os baús todo tick. */
    static final int RETRY_TICKS = 10;

    /** Por identidade do jogador: relogar cria outro objeto, e o antigo é coletado sozinho. */
    private static final Map<ServerPlayer, Long> NEXT_TRY = new WeakHashMap<>();

    private BenchLapis() {
    }

    /** Roda todo tick com uma estação aberta (de {@link BenchSync}); só faz algo na mesa de encantamento. */
    static void tick(ServerPlayer player, AbstractContainerMenu menu) {
        Slot slot = BenchCompat.lapisSlot(menu);
        if (slot == null || !player.isAlive() || player.isSpectator() || !menu.stillValid(player)
                || !FeatureGate.allowSilently(player, Feature.BENCH_LAPIS)) {
            return;
        }
        ItemStack model = BenchCompat.lapisModel();
        ItemStack inside = slot.getItem();
        if (!inside.isEmpty() && !ItemStack.isSameItemSameComponents(inside, model)) {
            return;   // algo diferente no slot (lápis com nome, por exemplo): não mistura
        }
        int want = Math.min(BenchCompat.LAPIS_TARGET, slot.getMaxStackSize(model)) - inside.getCount();
        if (want <= 0) {
            return;
        }
        long now = McCompat.gameTime(player);
        Long next = NEXT_TRY.get(player);
        if (next != null && now < next && now >= next - RETRY_TICKS) {
            return;
        }
        NEXT_TRY.put(player, now + RETRY_TICKS);
        BenchPool pool = BenchPool.of(player);
        // Mochila primeiro, baú completa. take() nunca passa de "want" e "want" cabe no slot: tudo o que sai entra.
        BenchPool.Taken taken = pool.take(model, want);
        if (taken.total() <= 0) {
            return;
        }
        slot.set(model.copyWithCount(inside.getCount() + taken.total()));
        if (taken.storage() > 0) {
            BenchLedger.record(player, Map.of(model.getItem(), taken.storage()), pool.origin());
        }
        menu.broadcastChanges();
        BenchSync.markDirty(player);
    }
}
