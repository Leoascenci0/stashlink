package io.github.leoascenci0.stashlink.network;

import io.github.leoascenci0.stashlink.compat.mc.McCompat;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Anti-flood dos pedidos que chegam da rede: deixa passar no máximo um pedido a cada {@code cooldownTicks} ticks por
 * jogador. Quem passa do limite é ignorado em silêncio (sem mensagem: responder a cada pacote só ajudaria o flood).
 * Cada tipo de pedido tem a sua instância, então um não gasta o limite do outro. Roda só na thread do servidor.
 */
public final class RequestLimiter {
    /** Chave por identidade do objeto do jogador: relogar cria outro objeto, e o antigo é coletado sozinho. */
    private final Map<ServerPlayer, Long> last = new WeakHashMap<>();
    private final int cooldownTicks;

    public RequestLimiter(int cooldownTicks) {
        this.cooldownTicks = cooldownTicks;
    }

    /** {@code true} (e conta o pedido) se já passou o intervalo desde o último aceito; {@code false} se é para ignorar. */
    public boolean allow(ServerPlayer player) {
        long now = McCompat.gameTime(player);
        Long before = last.get(player);
        // "now < before" = o relógio do mundo voltou (outro mundo/dimensão): não prende o jogador.
        if (before != null && now >= before && now - before < cooldownTicks) {
            return false;
        }
        last.put(player, now);
        return true;
    }
}
