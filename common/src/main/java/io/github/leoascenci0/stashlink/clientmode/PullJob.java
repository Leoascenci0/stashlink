package io.github.leoascenci0.stashlink.clientmode;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntPredicate;

/**
 * Tecla W no modo cliente: puxa para o inventário o que couber do container que o jogador já tem aberto.
 *
 * <p>Sem slots travados usa shift-clique (vários por tick). Com slots travados usa cliques normais para escolher
 * o destino (ver {@link ClientMoveLogic#pullClicks}); nesse caso é <b>um stack por tick</b>, porque cada plano de
 * cliques depende do estado do inventário depois do anterior. Cada slot do container é tentado uma vez só.
 */
public final class PullJob implements Job {
    private final IntPredicate locked;
    private final boolean anyLocked;
    private final ContentsCache cache;
    private final Set<Integer> tried = new HashSet<>();
    private int before;
    private int left;

    public PullJob(IntPredicate locked, boolean anyLocked, ContentsCache cache) {
        this.locked = locked;
        this.anyLocked = anyLocked;
        this.cache = cache;
    }

    @Override
    public void onOpened(Minecraft mc, Candidate candidate, List<MenuEntry> entries) {
        before = ClientMoveLogic.containerItemCount(entries);
        left = before;
    }

    @Override
    public Step step(Minecraft mc, List<MenuEntry> entries, ItemStack carried) {
        if (!carried.isEmpty()) {
            // Sobrou algo no cursor (o servidor corrigiu um clique): devolve a um slot vazio do container.
            for (MenuEntry e : entries) {
                if (!e.isPlayerSlot() && e.stack().isEmpty()) {
                    return Step.clicks(List.of(Click.pickup(e.menuIndex())));
                }
            }
            return Step.finish(); // sem lugar: ao fechar, o jogo devolve o cursor ao inventário
        }
        List<Click> clicks = new ArrayList<>();
        for (MenuEntry e : entries) {
            if (e.isPlayerSlot() || e.stack().isEmpty() || tried.contains(e.menuIndex())) {
                continue;
            }
            List<Click> plan = ClientMoveLogic.pullClicks(e, entries, locked, anyLocked);
            tried.add(e.menuIndex());
            if (plan.isEmpty()) {
                continue; // não cabe: nem gasta clique
            }
            clicks.addAll(plan);
            if (anyLocked || clicks.size() >= ClientModeEngine.CLICKS_PER_TICK) {
                break;
            }
        }
        return clicks.isEmpty() ? Step.finish() : Step.clicks(clicks);
    }

    @Override
    public void onClosed(Minecraft mc, Candidate candidate, List<MenuEntry> entries) {
        left = ClientMoveLogic.containerItemCount(entries);
        if (candidate != null) {
            cache.put(candidate.keys(), MenuSnapshot.containerStacks(entries));
        }
    }

    @Override
    public boolean wantsMore() {
        return false;
    }

    @Override
    public void onFinish(Minecraft mc, boolean aborted) {
        int moved = Math.max(0, before - left);
        if (aborted) {
            ClientModeEngine.say(mc, Component.translatableWithFallback("stashlink.client_mode.aborted",
                    "StashLink: cancelled"));
        } else if (moved > 0 && left > 0) {
            ClientModeEngine.say(mc, Component.translatableWithFallback("stashlink.client_mode.loot_all.partial",
                    "%s items taken, %s did not fit", moved, left));
        } else if (moved > 0) {
            ClientModeEngine.say(mc, Component.translatableWithFallback("stashlink.client_mode.loot_all.done",
                    "%s items taken", moved));
        } else if (left > 0) {
            ClientModeEngine.say(mc, Component.translatableWithFallback("stashlink.client_mode.loot_all.full",
                    "No room in your inventory"));
        } else {
            ClientModeEngine.say(mc, Component.translatableWithFallback("stashlink.client_mode.loot_all.empty",
                    "The container is empty"));
        }
    }
}
