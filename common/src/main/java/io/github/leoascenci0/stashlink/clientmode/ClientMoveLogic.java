package io.github.leoascenci0.stashlink.clientmode;

import io.github.leoascenci0.stashlink.compat.mc.McCompat;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
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
        return stackSlots(entries, locked, s -> false, tried);
    }

    /** Como acima; {@code excluded} são itens que a N nunca guarda (categoria desligada, Item 17). */
    public static List<Integer> stackSlots(List<MenuEntry> entries, IntPredicate locked,
                                           Predicate<ItemStack> excluded, Set<Integer> tried) {
        List<Integer> out = new ArrayList<>();
        for (MenuEntry e : entries) {
            if (!e.isPlayerSlot() || e.isHotbar() || e.invIndex() >= MenuEntry.INVENTORY_SIZE
                    || e.stack().isEmpty() || locked.test(e.invIndex()) || excluded.test(e.stack())
                    || tried.contains(e.menuIndex())) {
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
        return storableCount(entries, locked, s -> false);
    }

    /** Como acima, sem contar os itens {@code excluded} (que a N não vai guardar). */
    public static int storableCount(List<MenuEntry> entries, IntPredicate locked, Predicate<ItemStack> excluded) {
        int n = 0;
        for (MenuEntry e : entries) {
            if (e.isPlayerSlot() && !e.isHotbar() && e.invIndex() < MenuEntry.INVENTORY_SIZE
                    && !locked.test(e.invIndex()) && !excluded.test(e.stack())) {
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

    // ------------------------------------------------------------------ reabastecer a mão (Item 10.3)

    /** O que há agora no slot da hotbar que estava na mão: vazio (precisa repor), já com o item, ou outra coisa. */
    public enum HandState { EMPTY, REFILLED, OCCUPIED }

    /**
     * Dois stacks são "o mesmo item" para reabastecer? Mesmo item e mesmos componentes (nome, encantamentos...),
     * <b>ignorando o desgaste</b>: a ferramenta que quebrou tem desgaste máximo e a da reserva está nova, então
     * comparar o desgaste nunca casaria. Igual ao {@code RefillLogic} do servidor. Compara cópias: não altera nada.
     */
    public static boolean sameForRefill(ItemStack a, ItemStack b) {
        if (a.isEmpty() || b.isEmpty()) {
            return false;
        }
        ItemStack x = a.copyWithCount(1);
        ItemStack y = b.copyWithCount(1);
        McCompat.resetDamage(x);
        McCompat.resetDamage(y);
        return ItemStack.isSameItemSameComponents(x, y);
    }

    /** Estado do slot da hotbar selecionado, ou {@code null} se ele não aparece no menu (não deveria acontecer). */
    public static HandState handState(List<MenuEntry> entries, ItemStack model, int hotbarIndex) {
        for (MenuEntry e : entries) {
            if (e.isHotbar() && e.invIndex() == hotbarIndex) {
                if (e.stack().isEmpty()) {
                    return HandState.EMPTY;
                }
                return sameForRefill(e.stack(), model) ? HandState.REFILLED : HandState.OCCUPIED;
            }
        }
        return null;
    }

    /**
     * Slot do <b>container</b> de onde repor a mão: o que tem o item e, entre vários, o stack maior (leva mais de
     * uma vez e gasta menos cliques). Empate = o primeiro. {@code tried} são slots já tentados e que o servidor
     * não deixou mover. Devolve -1 se o container não tem o item.
     */
    public static int refillSource(List<MenuEntry> entries, ItemStack model, Set<Integer> tried) {
        int best = -1;
        int bestCount = 0;
        for (MenuEntry e : entries) {
            if (e.isPlayerSlot() || tried.contains(e.menuIndex()) || !sameForRefill(e.stack(), model)) {
                continue;
            }
            if (e.stack().getCount() > bestCount) {
                best = e.menuIndex();
                bestCount = e.stack().getCount();
            }
        }
        return best;
    }

    /**
     * O clique que leva o stack do slot {@code src} do container para o slot da hotbar selecionado. Usamos SWAP
     * (a tecla numérica do jogo): com a hotbar vazia, o stack <b>inteiro</b> vai para a mão em um clique só, sem
     * passar pelo cursor (nada fica "pendurado" se algo falhar) e sem o shift-clique escolher outro destino.
     */
    public static Click refillClick(int src, int hotbarIndex) {
        return Click.swapToHotbar(src, hotbarIndex);
    }

    /** Posição no ranking do cache para o reabastecimento: 0 = tem o item, 1 = nunca visto, 2 = visto e sem o item. */
    public static int cacheRank(Boolean seenHas) {
        return seenHas == null ? 1 : (seenHas ? 0 : 2);
    }

    /**
     * Em que condições o reabastecimento do modo cliente pode agir (e o vigia da mão conta um esgotamento).
     * Fora delas a mão vazia é intencional (jogador mexendo no inventário, soltou com Q...) ou impossível de
     * atender. Espelha as condições do {@code RefillService} do servidor.
     *
     * @param dropKey a tecla Q está (ou acabou de estar) apertada
     */
    public static boolean refillAllowed(boolean screenOpen, boolean sneaking, boolean alive, boolean spectator,
                                        boolean creative, boolean dropKey, boolean cursorEmpty) {
        return !screenOpen && !sneaking && alive && !spectator && !creative && !dropKey && cursorEmpty;
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
