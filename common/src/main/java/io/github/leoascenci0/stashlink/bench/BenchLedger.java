package io.github.leoascenci0.stashlink.bench;

import io.github.leoascenci0.stashlink.compat.mc.BenchCompat;
import io.github.leoascenci0.stashlink.compat.mc.McCompat;
import io.github.leoascenci0.stashlink.config.PlayerPrefsStore;
import io.github.leoascenci0.stashlink.source.ItemSource;
import io.github.leoascenci0.stashlink.source.Origin;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * O caderno de "emprestados" de cada jogador (Item 16): o que o mod tirou do armazenamento para a estação aberta
 * (grade da bancada, slot de entrada, cursor) e de onde veio. Enquanto o item está na estação ele é do jogador
 * (nenhum outro o enxerga, então dois jogadores nunca disputam o mesmo item). Se ele <b>não for usado</b>, volta
 * para o container de origem, e nunca para a mochila:
 * <ul>
 *   <li>ao escolher outra receita ({@link #returnFromGrid}) ou outro item no painel ({@link #returnCursor});</li>
 *   <li>ao fechar a estação: o que é emprestado volta ao baú antes de o jogo esvaziar a grade ({@link #beforeClose}),
 *       e no tick seguinte {@link #tick} devolve o que o baú não aceitou e foi parar na mochila;</li>
 *   <li>ao sair ou morrer com a estação aberta ({@link #release}); trocar de dimensão o próprio jogo fecha a tela.</li>
 * </ul>
 * O que foi gasto (craftado) simplesmente some do caderno: a conta é refeita contra o que ainda está na estação.
 *
 * <p>Item 31: o caderno anota <b>a pilha inteira</b> (item + componentes: encantamento, nome, dano), não só o tipo.
 * Assim um livro encantado emprestado e não usado volta ao baú (antes ia para a mochila), e um item do jogador do
 * mesmo tipo mas diferente (a espada encantada dele, o resultado da bigorna) nunca é confundido com o emprestado. O
 * caderno nunca devolve mais do que emprestou: cada pilha só conta até o que ainda está na estação.
 * Guarda só pilhas, quantidades e posições, nunca objetos de container nem de mundo.
 */
public final class BenchLedger {
    /** Uma pilha emprestada: como era o item (1, com os componentes) e quantos ainda podem voltar ao baú. */
    private static final class Loan {
        final ItemStack model;
        int count;

        Loan(ItemStack model, int count) {
            this.model = model.copyWithCount(1);
            this.count = count;
        }

        /** É este o item emprestado? Mesmo tipo <b>e</b> mesmos componentes. */
        boolean matches(ItemStack stack) {
            return !stack.isEmpty() && ItemStack.isSameItemSameComponents(model, stack);
        }
    }

    private static final class Entry {
        /** Fraca: o valor deste mapa nunca pode segurar o menu (e, por ele, o jogador) vivo. */
        private final WeakReference<AbstractContainerMenu> menuRef;
        final List<Loan> loans = new ArrayList<>();
        Origin origin;

        Entry(AbstractContainerMenu menu) {
            this.menuRef = new WeakReference<>(menu);
        }

        AbstractContainerMenu menu() {
            return menuRef.get();
        }

        Loan find(ItemStack stack) {
            for (Loan loan : loans) {
                if (loan.matches(stack)) {
                    return loan;
                }
            }
            return null;
        }

        void add(ItemStack model, int count) {
            Loan loan = find(model);
            if (loan == null) {
                loans.add(new Loan(model, count));
            } else {
                loan.count += count;
            }
        }
    }

    /** O que ainda está emprestado numa estação aberta, para o diário gravado no mundo ({@link BenchJournal}). */
    public record Outstanding(UUID player, Origin origin, List<ItemStack> items) {
    }

    /** Por identidade do jogador; sai daqui no logout ({@link #release}) e o valor não segura o menu. */
    private static final Map<ServerPlayer, Entry> LEDGER = new WeakHashMap<>();

    private BenchLedger() {
    }

    /** O mod acabou de pôr {@code count} de {@code model} (com estes componentes) na estação aberta. */
    public static void record(ServerPlayer player, ItemStack model, int count, Origin origin) {
        if (count <= 0 || model.isEmpty()) {
            return;
        }
        Entry entry = LEDGER.get(player);
        if (entry != null && entry.menu() != player.containerMenu) {
            settleFromInventory(player, entry);
            entry = null;
        }
        if (entry == null) {
            entry = new Entry(player.containerMenu);
            LEDGER.put(player, entry);
        }
        entry.add(model, count);
        entry.origin = entry.origin == null ? origin : entry.origin.merge(origin);
        BenchJournal.touch(player);
    }

    /** O mod acabou de pôr {@code got} (itens comuns, sem componentes próprios) na estação aberta, vindo de {@code origin}. */
    public static void record(ServerPlayer player, Map<Item, Integer> got, Origin origin) {
        for (Map.Entry<Item, Integer> e : got.entrySet()) {
            record(player, new ItemStack(e.getKey()), e.getValue(), origin);
        }
    }

    /**
     * A estação vai fechar (chamado no início de {@code removed}, com os slots ainda cheios): refaz a conta agora.
     * Sem isso, craftar e fechar no mesmo tick deixava o caderno achando que a grade ainda tinha o emprestado, e
     * o assentamento levava itens <b>próprios</b> do mesmo tipo da mochila para o baú.
     */
    public static void beforeClose(ServerPlayer player, AbstractContainerMenu menu) {
        Entry entry = LEDGER.get(player);
        if (entry != null && entry.menu() == menu) {
            reconcile(player, entry);
            // O jogo devolve a grade com "pôr na mochila"; com a mochila cheia ele dropa no chão. Por isso o que ainda
            // é emprestado volta ao baú AGORA, antes de ele esvaziar a grade. Fornalha/poções ficam no bloco.
            if (!BenchCompat.keepsItemsInBlock(menu)) {
                List<Slot> station = new ArrayList<>();
                for (Slot slot : menu.slots) {
                    if (slot.container != player.getInventory()) {
                        station.add(slot);
                    }
                }
                returnFromGrid(player, station);
            }
            returnCursor(player);
            BenchJournal.touch(player);
        }
    }

    /** Todo tick: se a estação foi fechada, devolve o que não foi usado; senão, atualiza a conta. */
    public static void tick(ServerPlayer player) {
        Entry entry = LEDGER.get(player);
        if (entry == null) {
            return;
        }
        if (entry.menu() != player.containerMenu) {
            settleFromInventory(player, entry);
            LEDGER.remove(player);
        } else {
            reconcile(player, entry);
            if (entry.loans.isEmpty()) {
                LEDGER.remove(player);
            }
        }
        BenchJournal.touch(player);
    }

    /**
     * O jogador vai sair, morrer ou o servidor vai parar: fecha a estação (o que é emprestado volta
     * ao baú; o resto o jogo devolve à mochila) e devolve de lá ao baú de origem o que não foi usado. Roda antes do
     * save do jogador e, na morte, antes de o inventário cair no chão. {@code tellClient}: avisa o cliente que a tela
     * fechou (morte); no logout não há cliente para avisar.
     */
    public static void release(ServerPlayer player, boolean tellClient) {
        Entry entry = LEDGER.remove(player);
        if (entry == null) {
            return;
        }
        if (entry.menu() == player.containerMenu && player.containerMenu != player.inventoryMenu) {
            // beforeClose (no começo do removed) não acha mais a entrada: faz aqui o mesmo, com ela em mãos.
            LEDGER.put(player, entry);
            BenchCompat.closeMenu(player, tellClient);
            LEDGER.remove(player);
        }
        // Desconectado, o jogo joga no chão o que o baú não aceitou (não põe na mochila): procurar na mochila levaria
        // ao baú itens próprios do jogador do mesmo jeito.
        if (!player.hasDisconnected()) {
            settleFromInventory(player, entry);
        }
        BenchJournal.touch(player);
    }

    /** Antes de montar outra receita: o que o mod pôs na grade e sobrou volta ao container de origem. */
    public static void returnFromGrid(ServerPlayer player, List<Slot> grid) {
        Entry entry = LEDGER.get(player);
        if (entry == null || entry.menu() != player.containerMenu) {
            return;
        }
        reconcile(player, entry);
        ItemSource target = BenchPool.returnTarget(player, entry.origin);
        for (Iterator<Loan> it = entry.loans.iterator(); it.hasNext(); ) {
            Loan loan = it.next();
            for (Slot slot : grid) {
                if (loan.count <= 0) {
                    break;
                }
                ItemStack stack = slot.getItem();
                if (!loan.matches(stack) || !slot.mayPlace(stack)) {
                    continue;   // mayPlace: o slot de resultado nunca é devolução
                }
                int move = Math.min(stack.getCount(), loan.count);
                ItemStack rest = target.give(stack.copyWithCount(move));
                int moved = move - rest.getCount();
                if (moved > 0) {
                    stack.shrink(moved);
                    slot.set(stack.isEmpty() ? ItemStack.EMPTY : stack);
                    loan.count -= moved;
                }
            }
            if (loan.count <= 0) {
                it.remove();
            }
        }
        BenchJournal.touch(player);
    }

    /** O caderno da estação aberta ainda deve esta pilha (mesmo item e componentes) a um baú? */
    public static boolean owes(ServerPlayer player, ItemStack stack) {
        Entry entry = LEDGER.get(player);
        if (entry == null || entry.menu() != player.containerMenu) {
            return false;
        }
        Loan loan = entry.find(stack);
        return loan != null && loan.count > 0;
    }

    /** Antes de pegar outro item do painel: o que está no cursor e veio do armazenamento volta à origem. */
    public static void returnCursor(ServerPlayer player) {
        Entry entry = LEDGER.get(player);
        AbstractContainerMenu menu = player.containerMenu;
        if (entry == null || entry.menu() != menu) {
            return;
        }
        ItemStack carried = menu.getCarried();
        Loan loan = entry.find(carried);
        if (loan == null || loan.count <= 0) {
            return;   // item do jogador (ou resultado da estação), mesmo do mesmo tipo: nunca vai para o baú
        }
        int move = Math.min(carried.getCount(), loan.count);
        ItemSource target = BenchPool.returnTarget(player, entry.origin);
        ItemStack rest = target.give(carried.copyWithCount(move));
        int moved = move - rest.getCount();
        if (!rest.isEmpty()) {
            // O baú de origem encheu (ou saiu do alcance): o cursor não pode ficar preso ao item que não cabe.
            // Vai para a mochila (nunca some nem duplica) e deixa de ser "emprestado".
            McCompat.placeBackInInventory(player, rest);
            moved = move;
        }
        if (moved > 0) {
            ItemStack now = carried.copy();
            now.shrink(moved);
            menu.setCarried(now.isEmpty() ? ItemStack.EMPTY : now);
            menu.broadcastChanges();
            loan.count -= moved;
            if (loan.count <= 0) {
                entry.loans.remove(loan);
            }
        }
        BenchJournal.touch(player);
    }

    /**
     * Para o diário ({@link BenchJournal}): o que cada jogador tem emprestado <b>agora</b> numa estação aberta, só a
     * parte que um save não guardaria (cursor e grade; fornalha e suporte de poções guardam os slots no bloco, ali só o
     * cursor conta). Não mexe em nada: o save roda no meio do tick, e a conta é feita de novo aqui, contra o que
     * ainda está na estação. Pilhas cortadas no tamanho máximo do item.
     */
    public static List<Outstanding> outstanding() {
        List<Outstanding> out = new ArrayList<>();
        for (Map.Entry<ServerPlayer, Entry> e : LEDGER.entrySet()) {
            ServerPlayer player = e.getKey();
            Entry entry = e.getValue();
            if (player == null || entry.origin == null || entry.menu() == null
                    || entry.menu() != player.containerMenu || player.containerMenu == player.inventoryMenu) {
                continue;   // estação já fechada: o que sobrou foi para a mochila (que o save guarda) ou para o baú
            }
            List<ItemStack> items = new ArrayList<>();
            for (Loan loan : entry.loans) {
                int left = Math.min(loan.count, held(player, entry, loan));
                int max = Math.max(1, loan.model.getMaxStackSize());
                while (left > 0) {
                    int n = Math.min(left, max);
                    items.add(loan.model.copyWithCount(n));
                    left -= n;
                }
            }
            if (!items.isEmpty()) {
                out.add(new Outstanding(player.getUUID(), entry.origin, items));
            }
        }
        return out;
    }

    /**
     * Testes: simula a queda do servidor para este jogador. Esquece o caderno <b>sem</b> devolver nada (a grade e o
     * cursor, que o save não guarda, se perdem), como acontece quando o processo morre.
     */
    public static void forgetAsIfCrashed(ServerPlayer player) {
        LEDGER.remove(player);
    }

    /** Quantos desta pilha ainda estão no cursor e nos slots de entrada da estação. */
    private static int held(ServerPlayer player, Entry entry, Loan loan) {
        AbstractContainerMenu menu = entry.menu();
        int held = 0;
        if (loan.matches(menu.getCarried())) {
            held += menu.getCarried().getCount();
        }
        // Fornalha/suporte de poções: o que está nos slots fica no bloco e já é do jogador, só o cursor conta.
        if (!BenchCompat.keepsItemsInBlock(menu)) {
            Inventory inventory = player.getInventory();
            for (Slot slot : menu.slots) {
                if (slot.container != inventory && loan.matches(slot.getItem()) && slot.mayPlace(loan.model)) {
                    held += slot.getItem().getCount();
                }
            }
        }
        return held;
    }

    /** Refaz a conta: nada além do que ainda está no cursor e nos slots de entrada da estação pode estar "emprestado". */
    private static void reconcile(ServerPlayer player, Entry entry) {
        for (Iterator<Loan> it = entry.loans.iterator(); it.hasNext(); ) {
            Loan loan = it.next();
            int held = held(player, entry, loan);
            if (held <= 0) {
                it.remove();
            } else if (held < loan.count) {
                loan.count = held;
            }
        }
    }

    /** A estação fechou: o jogo já devolveu a grade à mochila; do que não foi usado, devolve à origem. */
    private static void settleFromInventory(ServerPlayer player, Entry entry) {
        if (entry.origin == null) {
            return;
        }
        ItemSource target = BenchPool.returnTarget(player, entry.origin);
        List<ItemStack> backpack = player.getInventory().getNonEquipmentItems();
        for (Loan loan : entry.loans) {
            int left = loan.count;
            for (int i = 0; i < backpack.size() && left > 0; i++) {
                ItemStack stack = backpack.get(i);
                // Item 32: slot travado pelo jogador nunca perde item para o mod (o emprestado que ficou lá é dele).
                if (PlayerPrefsStore.isSlotLocked(player, i) || !loan.matches(stack)) {
                    continue;
                }
                int move = Math.min(stack.getCount(), left);
                ItemStack rest = target.give(stack.copyWithCount(move));
                int moved = move - rest.getCount();
                if (moved > 0) {
                    stack.shrink(moved);
                    if (stack.isEmpty()) {
                        backpack.set(i, ItemStack.EMPTY);
                    }
                    left -= moved;
                }
            }
        }
        entry.loans.clear();
    }
}
