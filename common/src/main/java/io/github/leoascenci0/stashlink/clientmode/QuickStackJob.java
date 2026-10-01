package io.github.leoascenci0.stashlink.clientmode;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import io.github.leoascenci0.stashlink.compat.mc.ClientCompat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntPredicate;

/**
 * Tecla N no modo cliente. Dentro de cada container aberto, shift-clica na mochila apenas os itens que o
 * container <b>já tem</b> (mesmo item e componentes). Hotbar e slots travados nunca são tocados.
 *
 * <p>Quem move é o shift-clique do jogo (o servidor decide o destino e garante que nada some nem duplica);
 * a gente conta o resultado olhando a mochila <b>depois</b>: itens guardados = mochila antes − mochila depois.
 */
public final class QuickStackJob implements Job {
    private final IntPredicate locked;
    private final ContentsCache cache;
    private final Set<Integer> tried = new HashSet<>();
    private int before;
    private int storableLeft = Integer.MAX_VALUE;
    private int itemsMoved;
    private int containersUsed;

    public QuickStackJob(IntPredicate locked, ContentsCache cache) {
        this.locked = locked;
        this.cache = cache;
    }

    @Override
    public void onOpened(Minecraft mc, Candidate candidate, List<MenuEntry> entries) {
        tried.clear();
        before = ClientMoveLogic.storableCount(entries, locked);
    }

    @Override
    public Step step(Minecraft mc, List<MenuEntry> entries, ItemStack carried) {
        List<Integer> slots = ClientMoveLogic.stackSlots(entries, locked, tried);
        if (slots.isEmpty()) {
            return Step.finish();
        }
        List<Click> clicks = new ArrayList<>();
        for (int slot : slots) {
            if (clicks.size() >= ClientModeEngine.CLICKS_PER_TICK) {
                break;
            }
            tried.add(slot);
            clicks.add(Click.quickMove(slot));
        }
        return Step.clicks(clicks);
    }

    @Override
    public void onClosed(Minecraft mc, Candidate candidate, List<MenuEntry> entries) {
        storableLeft = ClientMoveLogic.storableCount(entries, locked);
        int moved = before - storableLeft;
        if (moved > 0) {
            itemsMoved += moved;
            containersUsed++;
        }
        if (candidate != null) {
            cache.put(candidate.keys(), MenuSnapshot.containerStacks(entries));
        }
    }

    @Override
    public boolean wantsMore() {
        // Sem nada guardável na mochila não adianta abrir mais baús.
        return storableLeft > 0;
    }

    @Override
    public void onFinish(Minecraft mc, boolean aborted) {
        if (aborted) {
            ClientModeEngine.say(mc, Component.translatableWithFallback("stashlink.client_mode.aborted",
                    "StashLink: cancelled"));
        } else if (itemsMoved > 0) {
            ClientModeEngine.say(mc, Component.translatableWithFallback("stashlink.client_mode.quick_stack.done",
                    "%s items stored in %s containers", itemsMoved, containersUsed));
        } else {
            ClientModeEngine.say(mc, Component.translatableWithFallback("stashlink.client_mode.quick_stack.nothing",
                    "Nothing to store nearby"));
        }
    }
}
