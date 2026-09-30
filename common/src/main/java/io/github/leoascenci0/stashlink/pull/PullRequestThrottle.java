package io.github.leoascenci0.stashlink.pull;

import net.minecraft.world.item.Item;

/**
 * Freio do lado do cliente: o Easy Place do Litematica pede o item a cada tick, mas o servidor leva alguns
 * ticks para responder. Sem isto o cliente mandaria dezenas de pedidos iguais antes da primeira resposta.
 */
public final class PullRequestThrottle {
    private final int cooldownTicks;
    private Item lastItem;
    private long lastTick = Long.MIN_VALUE;

    public PullRequestThrottle(int cooldownTicks) {
        this.cooldownTicks = cooldownTicks;
    }

    /** Vale {@code true} (e registra o envio) se pode mandar agora um pedido deste item. */
    public boolean tryAcquire(Item item, long tick) {
        boolean sameItem = item == lastItem;
        if (sameItem && tick >= lastTick && tick - lastTick < cooldownTicks) {
            return false;
        }
        lastItem = item;
        lastTick = tick;
        return true;
    }
}
