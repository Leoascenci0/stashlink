package io.github.leoascenci0.stashlink.source;

import net.minecraft.core.BlockPos;

import java.util.HashSet;
import java.util.Set;

/**
 * De onde vieram os itens que o mod puxou para a mão: shulkers do inventário e/ou containers do mundo.
 *
 * <p>Guarda só <b>posições e a dimensão</b>, nunca o objeto do container (ou do mundo). Dois motivos: (1) o
 * baú pode ter sido quebrado ou o jogador ter se afastado até o pedido seguinte, então na hora de devolver o
 * container é procurado de novo e revalidado (raio, permissão), em vez de confiar num objeto antigo; (2) um
 * objeto de container guardado ligaria a memória ao mundo inteiro.
 */
public record Origin(boolean inventoryShulkers, Object dimension, Set<BlockPos> positions) {
    public Origin {
        positions = Set.copyOf(positions);
    }

    /** Une duas origens (o mesmo slot recebeu itens de dois pedidos). */
    public Origin merge(Origin other) {
        Set<BlockPos> all = new HashSet<>(positions);
        all.addAll(other.positions);
        return new Origin(inventoryShulkers || other.inventoryShulkers, other.dimension, all);
    }
}
