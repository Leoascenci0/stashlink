package io.github.leoascenci0.stashlink.clientmode;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.IntPredicate;
import java.util.function.ToIntFunction;

/**
 * As regras do modo cliente, sem nada de jogador, rede ou tela (por isso dá para testar de forma exaustiva):
 * dado o que há nos slots do menu aberto, decide <b>quais cliques</b> fazer. Quem executa os cliques e confere o
 * resultado depois é o {@code ContainerSession}.
 *
 * <p>Por que decidir cliques em vez de mover itens: no modo cliente quem move o item é o <b>servidor</b>, como
 * se fosse um jogador clicando. Assim a conservação (nada some, nada duplica) é garantida pelo próprio jogo; o
 * nosso trabalho é só escolher bons cliques e nunca assumir que deram certo — a cada tick lemos o menu de novo.
 */
public final class ClientMoveLogic {
    private ClientMoveLogic() {
    }

    // ------------------------------------------------------------------ guardar (tecla N)

    /** O container (slots que não são do jogador) já tem um stack igual — mesmo item e mesmos componentes? */
    public static boolean containerHas(List<MenuEntry> entries, ItemStack stack) {
        for (MenuEntry e : entries) {
            if (!e.isPlayerSlot() && !e.stack().isEmpty() && ItemStack.isSameItemSameComponents(e.stack(), stack)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Slots do jogador que devem ser shift-clicados para guardar no container aberto: só a mochila (9-35),
     * nunca a hotbar nem slots travados, e só itens que o container <b>já tem</b>. {@code tried} são os slots
     * que já clicamos neste container (um clique move o que couber; repetir seria inútil).
     *
     * @return índices de menu, em ordem
     */
    public static List<Integer> stackSlots(List<MenuEntry> entries, IntPredicate locked, Set<Integer> tried) {
        List<Integer> out = new ArrayList<>();
        for (MenuEntry e : entries) {
            if (!e.isPlayerSlot() || e.isHotbar() || e.invIndex() >= MenuEntry.INVENTORY_SIZE
                    || e.stack().isEmpty() || locked.test(e.invIndex()) || tried.contains(e.menuIndex())) {
                continue;
            }
            if (containerHas(entries, e.stack())) {
                out.add(e.menuIndex());
            }
        }
        return out;
    }

    /** Quantos itens "guardáveis" há na mochila (fora hotbar e travados). Serve para medir quanto saiu. */
    public static int storableCount(List<MenuEntry> entries, IntPredicate locked) {
        int n = 0;
        for (MenuEntry e : entries) {
            if (e.isPlayerSlot() && !e.isHotbar() && e.invIndex() < MenuEntry.INVENTORY_SIZE
                    && !locked.test(e.invIndex())) {
                n += e.stack().getCount();
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ puxar tudo (tecla W)

    /** Quantos itens iguais a {@code stack} ainda cabem nos slots do jogador que não estão travados. */
    public static int room(List<MenuEntry> entries, ItemStack stack, IntPredicate locked) {
        int limit = stack.getMaxStackSize();
        int room = 0;
        for (MenuEntry e : entries) {
            if (!e.isPlayerSlot() || e.invIndex() >= MenuEntry.INVENTORY_SIZE || locked.test(e.invIndex())) {
                continue;
            }
            if (e.stack().isEmpty()) {
                room += limit;
            } else if (ItemStack.isSameItemSameComponents(e.stack(), stack)) {
                room += Math.max(0, limit - e.stack().getCount());
            }
        }
        return room;
    }

    /**
     * Cliques para trazer o stack do slot {@code src} (do container) para o inventário.
     *
     * <ul>
     *   <li>Sem slots travados: um shift-clique. O jogo escolhe o destino (completa stacks iguais, depois
     *       slots vazios) — o caminho mais simples e igual ao do jogador.</li>
     *   <li>Com slots travados: o shift-clique poderia cair num slot que o jogador travou, e não dá para
     *       escolher. Então usa cliques normais: pega o stack, solta nos destinos permitidos (completando
     *       iguais antes, depois vazios com a mochila na frente da hotbar, como o W do servidor) e, se sobrar,
     *       devolve ao slot de origem. Nunca deixa item no cursor.</li>
     * </ul>
     * Devolve vazio se nada caberia (não gasta clique à toa).
     */
    public static List<Click> pullClicks(MenuEntry src, List<MenuEntry> entries, IntPredicate locked,
                                         boolean anyLocked) {
        ItemStack stack = src.stack();
        if (src.isPlayerSlot() || stack.isEmpty()) {
            return List.of();
        }
        if (room(entries, stack, anyLocked ? locked : i -> false) <= 0) {
            return List.of();
        }
        if (!anyLocked) {
            return List.of(Click.quickMove(src.menuIndex()));
        }
        List<Click> clicks = new ArrayList<>();
        clicks.add(Click.pickup(src.menuIndex()));
        int remaining = stack.getCount();
        int limit = stack.getMaxStackSize();
        // Passo 1: completar stacks iguais.
        for (MenuEntry e : entries) {
            if (remaining <= 0) {
                break;
            }
            if (!e.isPlayerSlot() || e.invIndex() >= MenuEntry.INVENTORY_SIZE || locked.test(e.invIndex())
                    || e.stack().isEmpty() || !ItemStack.isSameItemSameComponents(e.stack(), stack)) {
                continue;
            }
            int move = Math.min(remaining, limit - e.stack().getCount());
            if (move > 0) {
                clicks.add(Click.pickup(e.menuIndex()));
                remaining -= move;
            }
        }
        // Passo 2: slots vazios, mochila (9-35) antes da hotbar (0-8).
        List<MenuEntry> empties = new ArrayList<>();
        for (MenuEntry e : entries) {
            if (e.isPlayerSlot() && e.invIndex() < MenuEntry.INVENTORY_SIZE && !locked.test(e.invIndex())
                    && e.stack().isEmpty()) {
                empties.add(e);
            }
        }
        empties.sort(Comparator.comparingInt((MenuEntry e) -> e.isHotbar() ? 1 : 0)
                .thenComparingInt(MenuEntry::invIndex));
        for (MenuEntry e : empties) {
            if (remaining <= 0) {
                break;
            }
            clicks.add(Click.pickup(e.menuIndex()));
            remaining -= Math.min(remaining, limit);
        }
        if (remaining > 0) {
            // Não coube tudo: o resto do cursor volta para o slot de origem (agora vazio).
            clicks.add(Click.pickup(src.menuIndex()));
        }
        return clicks;
    }

    /** Soma dos itens nos slots do container (para contar quanto saiu depois). */
    public static int containerItemCount(List<MenuEntry> entries) {
        int n = 0;
        for (MenuEntry e : entries) {
            if (!e.isPlayerSlot()) {
                n += e.stack().getCount();
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ ordem dos containers

    /**
     * Ordena os candidatos por {@code rank} (menor primeiro) e, dentro do mesmo rank, do mais perto ao mais
     * longe; devolve no máximo {@code max}. Quem escolhe o rank é quem chama (ex.: 0 = o cache diz que tem o
     * item, 1 = nunca visto, 2 = visto e sem o item).
     */
    public static List<Candidate> order(List<Candidate> candidates, ToIntFunction<Candidate> rank, int max) {
        List<Candidate> sorted = new ArrayList<>(candidates);
        sorted.sort(Comparator.comparingInt(rank).thenComparingDouble(Candidate::distSq));
        return sorted.size() > max ? new ArrayList<>(sorted.subList(0, max)) : sorted;
    }
}
