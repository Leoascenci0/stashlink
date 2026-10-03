package io.github.leoascenci0.stashlink.slotlock;

import net.minecraft.world.item.Item;

import java.util.Map;

/**
 * "Guarda a memória dos slots travados". Um mixin faz toda block entity de container (baú, barril, shulker...)
 * implementar isto; o mapa vai para o disco junto com o bloco (ver {@code BaseContainerBlockEntityMixin}).
 *
 * <p>A chave é o índice do slot <b>dentro daquela block entity</b> (0-26 num baú). Num baú duplo cada metade
 * guarda os seus; quem junta as duas é {@link SlotLocks}.
 */
public interface SlotLockHolder {
    Map<Integer, Item> stashlink$locks();
}
