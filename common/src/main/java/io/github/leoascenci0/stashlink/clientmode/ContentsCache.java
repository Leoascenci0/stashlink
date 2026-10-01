package io.github.leoascenci0.stashlink.clientmode;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Memória "posição → o que vimos lá dentro". O cliente só recebe o conteúdo de um container quando o abre, então
 * guardamos o que viu para, na próxima vez, abrir primeiro os containers com mais chance de ter o item.
 *
 * <p>É só uma dica de <b>ordem</b>: nunca decide sozinha que um container "não tem" (o conteúdo pode ter mudado
 * desde que foi visto — outro jogador, funil), os desconhecidos e os "sem o item" só são deixados para depois.
 * Vive só em memória e é limpa ao trocar de mundo.
 */
public final class ContentsCache {
    /** Teto de entradas: protege a memória num mundo com milhares de baús. */
    private static final int MAX_ENTRIES = 2048;

    private final Map<Long, List<ItemStack>> seen = new HashMap<>();

    /** Guarda (copiando) o que há no container nas posições {@code keys}. */
    public void put(long[] keys, List<ItemStack> stacks) {
        List<ItemStack> copy = new ArrayList<>(stacks.size());
        for (ItemStack s : stacks) {
            if (!s.isEmpty()) {
                copy.add(s.copy());
            }
        }
        if (seen.size() >= MAX_ENTRIES) {
            seen.clear();
        }
        for (long key : keys) {
            seen.put(key, copy);
        }
    }

    /**
     * @return {@code TRUE} se o que vimos tem um stack que casa, {@code FALSE} se vimos e não tem,
     *         {@code null} se nunca vimos esse container
     */
    public Boolean has(long[] keys, Predicate<ItemStack> match) {
        for (long key : keys) {
            List<ItemStack> content = seen.get(key);
            if (content != null) {
                for (ItemStack s : content) {
                    if (match.test(s)) {
                        return Boolean.TRUE;
                    }
                }
                return Boolean.FALSE;
            }
        }
        return null;
    }

    public void clear() {
        seen.clear();
    }

    public int size() {
        return seen.size();
    }
}
