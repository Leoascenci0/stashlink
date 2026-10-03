package io.github.leoascenci0.stashlink.slotlock;

import net.minecraft.world.Container;
import net.minecraft.world.item.Item;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Lado cliente: o que o servidor contou sobre os slots travados do container aberto. É só <b>aparência e
 * previsão</b> (desenhar a prévia e já recusar o clique que o servidor recusaria); quem decide é sempre o
 * servidor. A chave é o {@link Container} do menu do cliente (a prévia nunca vira item dentro dele).
 */
public final class ClientSlotLocks {
    private static final Map<Container, Map<Integer, Item>> STORE = new WeakHashMap<>();

    private ClientSlotLocks() {
    }

    /** Substitui tudo o que se sabe sobre {@code container}. */
    public static void set(Container container, Map<Integer, Item> locks) {
        if (locks.isEmpty()) {
            STORE.remove(container);
        } else {
            STORE.put(container, locks);
        }
    }

    /** O item reservado para o slot, ou {@code null}. */
    public static Item lockedItem(Container container, int slot) {
        Map<Integer, Item> map = STORE.get(container);
        return map == null ? null : map.get(slot);
    }
}
