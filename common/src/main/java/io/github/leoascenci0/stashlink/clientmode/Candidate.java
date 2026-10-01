package io.github.leoascenci0.stashlink.clientmode;

import net.minecraft.core.BlockPos;

/**
 * Um container colocado no mundo que o modo cliente pode abrir.
 *
 * @param pos     bloco em que vamos "clicar" (num baú duplo, a metade mais perto do jogador)
 * @param other   a outra metade do baú duplo, ou {@code null}
 * @param distSq  distância ao quadrado até o jogador, para ordenar (mais perto primeiro)
 */
public record Candidate(BlockPos pos, BlockPos other, double distSq) {
    /** Chaves do cache: baú duplo guarda o conteúdo (54 slots) sob as duas metades. */
    public long[] keys() {
        return other == null ? new long[]{pos.asLong()} : new long[]{pos.asLong(), other.asLong()};
    }
}
