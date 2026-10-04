package io.github.leoascenci0.stashlink.bench;

import io.github.leoascenci0.stashlink.compat.mc.BenchCompat;
import io.github.leoascenci0.stashlink.compat.mc.StationRecipes;
import io.github.leoascenci0.stashlink.network.BenchPoolSync;
import io.github.leoascenci0.stashlink.source.ItemSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * O painel "Armazenamento" ao lado de uma estação. Em estações com receita própria (cortador de pedra, tear) lista os
 * <b>resultados</b> que o jogador já conhece (em vermelho os que faltam material, como o livro de receitas) e clicar
 * monta a receita: o servidor põe cada entrada no slot certo e escolhe a receita. Nas demais estações (bigorna,
 * amolar...) clicar num item o coloca direto no slot da estação que o aceita. Tudo é revalidado aqui.
 */
public final class BenchResults {
    /** {@code recipeId} do pedido: "ponha este item no slot da estação que o aceita" (em vez do cursor). */
    public static final int PLACE = -2;

    /** {@code recipeId} do pedido comum do painel: "ponha um stack deste item no meu cursor". */
    public static final int CURSOR = -1;

    /** Só estes {@code recipeId} chegam do cliente de verdade ({@code >= 0} é uma receita); o resto é ignorado. */
    public static boolean validRequestId(int recipeId) {
        return recipeId >= 0 || recipeId == PLACE || recipeId == CURSOR;
    }

    private BenchResults() {
    }

    public static boolean supports(AbstractContainerMenu menu) {
        return StationRecipes.supports(menu);
    }

    /** Os resultados conhecidos: os possíveis primeiro, depois os que faltam material (vermelho). */
    public static List<BenchPoolSync.Entry> list(ServerPlayer player) {
        List<ItemStack> have = available(player, BenchPool.of(player).contents());
        List<StationRecipes.Option> options = StationRecipes.options(player, player.containerMenu, have);
        List<BenchPoolSync.Entry> ok = new ArrayList<>();
        List<BenchPoolSync.Entry> missing = new ArrayList<>();
        List<BenchPoolSync.Entry> colors = new ArrayList<>();
        // Aba "Cores" (tear): uma entrada por cor de corante conhecida (vermelha se não há corante dela agora).
        for (StationRecipes.DyePick pick : StationRecipes.dyePicks(player, player.containerMenu, have)) {
            colors.add(new BenchPoolSync.Entry(pick.dye(), 0, BenchPoolSync.Entry.COLOR_PICK, !pick.has(), 0, pick.color()));
        }
        java.util.Set<Integer> seenOk = new java.util.HashSet<>();
        java.util.Set<Integer> seenMissing = new java.util.HashSet<>();
        for (int i = 0; i < options.size() && ok.size() + missing.size() < BenchPoolSync.MAX_ENTRIES; i++) {
            StationRecipes.Option option = options.get(i);
            boolean can = option.needs().stream().allMatch(need -> have.stream().anyMatch(need));
            int key = key(option);
            // O mesmo resultado pode sair de mais de uma pedra (ex.: ardósia abissal talhada). Uma entrada só: a possível
            // vence a que falta material, e entre iguais fica a primeira.
            if (can ? !seenOk.add(key) : !seenMissing.add(key)) {
                continue;
            }
            (can ? ok : missing).add(new BenchPoolSync.Entry(option.icon(), 0, key, !can, option.tab(), -1));
        }
        missing.removeIf(entry -> seenOk.contains(entry.id()));
        colors.addAll(ok);
        colors.addAll(missing);
        return colors;
    }

    /**
     * Identidade estável de uma receita no pedido: o que ela produz (item + componentes), não a posição na lista. A
     * lista é refeita a cada clique e o snapshot do cliente chega a 5 s atrasado, então um índice podia apontar para
     * outra receita. Sempre {@code >= 0} (os negativos têm significado próprio).
     */
    static int key(StationRecipes.Option option) {
        return ItemStack.hashItemAndComponents(option.icon()) & Integer.MAX_VALUE;
    }

    /** Aba de um item solto (ver {@link BenchCompat#slotTab}). */
    public static int slotTab(AbstractContainerMenu menu, ItemStack stack) {
        return BenchCompat.slotTab(menu, stack);
    }

    /** O que o jogador tem à mão para esta estação: o armazenamento já varrido ({@code stored}) e a mochila. */
    private static List<ItemStack> available(ServerPlayer player, List<BenchPool.Stack> stored) {
        List<ItemStack> have = new ArrayList<>();
        for (BenchPool.Stack stack : stored) {
            have.add(stack.item());
        }
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (!stack.isEmpty()) {
                have.add(stack);
            }
        }
        return have;
    }

    /** Monta a receita de chave {@code id} ({@link #key}): põe cada entrada no slot certo (armazenamento primeiro, mochila se faltar) e a escolhe. */
    public static void craft(ServerPlayer player, AbstractContainerMenu menu, int id, ItemStack choice) {
        // Uma varredura só por clique: o pool, a lista do que há e o que sai dos baús vêm todos da mesma passada.
        BenchPool pool = BenchPool.of(player);
        List<BenchPool.Stack> stored = pool.contents();
        List<ItemStack> have = available(player, stored);
        List<StationRecipes.Option> options = StationRecipes.options(player, menu, have);
        StationRecipes.Option option = null;
        for (StationRecipes.Option candidate : options) {
            if (key(candidate) != id) {
                continue;
            }
            // Mesmo resultado de várias pedras: usa a que tem material agora (a lista mostra uma só entrada por resultado).
            boolean can = candidate.needs().stream().allMatch(need -> have.stream().anyMatch(need));
            if (option == null || can) {
                option = candidate;
            }
            if (can) {
                break;
            }
        }
        if (option == null) {
            BenchSync.markDirty(player);   // a lista mudou desde o snapshot do cliente: manda a nova e não monta nada
            return;
        }
        List<java.util.function.Predicate<ItemStack>> needs = new ArrayList<>(option.needs());
        StationRecipes.applyChoice(menu, needs, choice);   // tear: o corante da cor escolhida no painel
        // Primeiro confere que dá para montar tudo: nunca deixa a estação pela metade.
        for (int i = 0; i < option.slots().length; i++) {
            Slot slot = menu.getSlot(option.slots()[i]);
            if (slot.hasItem() ? !needs.get(i).test(slot.getItem()) : have.stream().noneMatch(needs.get(i))) {
                BenchSync.markDirty(player);
                return;
            }
        }
        for (int i = 0; i < option.slots().length; i++) {
            Slot slot = menu.getSlot(option.slots()[i]);
            if (slot.hasItem()) {
                continue;   // já tem um item que serve (talvez de uma receita anterior)
            }
            if (!fillSlot(player, pool, stored, slot, needs.get(i))) {
                BenchSync.markDirty(player);
                return;
            }
        }
        StationRecipes.select(menu, player, option);
        menu.broadcastChanges();
        BenchSync.markDirty(player);
    }

    /** Põe em {@code slot} um item que satisfaz {@code need}: do armazenamento, ou da mochila se não houver lá. */
    private static boolean fillSlot(ServerPlayer player, BenchPool pool, List<BenchPool.Stack> stored, Slot slot,
                                    java.util.function.Predicate<ItemStack> need) {
        for (BenchPool.Stack stack : stored) {
            ItemStack model = stack.item();
            if (need.test(model) && slot.mayPlace(model)) {
                int want = Math.min(model.getMaxStackSize(), slot.getMaxStackSize(model));
                int total = ItemSource.sum(pool.source().take(model, want));
                if (total > 0) {
                    slot.set(model.copyWithCount(total));
                    BenchLedger.record(player, Map.of(model.getItem(), total), pool.origin());
                    return true;
                }
            }
        }
        var items = player.getInventory().getNonEquipmentItems();
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            if (!stack.isEmpty() && need.test(stack) && slot.mayPlace(stack)) {
                int move = Math.min(stack.getCount(), slot.getMaxStackSize(stack));
                slot.set(stack.copyWithCount(move));
                stack.shrink(move);
                if (stack.isEmpty()) {
                    items.set(i, ItemStack.EMPTY);
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Clicou num item solto do painel: vai para o primeiro slot <b>da estação</b> que o aceita (corante no slot do
     * corante, banner no do banner...), do armazenamento. Um stack, ou um só com o botão direito.
     */
    public static void place(ServerPlayer player, AbstractContainerMenu menu, ItemStack requested, boolean one) {
        ItemStack model = requested.copyWithCount(1);
        BenchPool pool = BenchPool.of(player);
        for (Slot slot : BenchCompat.placementOrder(menu)) {
            if (slot.container == player.getInventory() || !slot.mayPlace(model)) {
                continue;
            }
            ItemStack inside = slot.getItem();
            if (!inside.isEmpty() && !ItemStack.isSameItemSameComponents(inside, model)) {
                continue;
            }
            int room = Math.min(model.getMaxStackSize(), slot.getMaxStackSize(model)) - inside.getCount();
            if (room <= 0) {
                continue;
            }
            int total = ItemSource.sum(pool.source().take(model, one ? 1 : room));
            if (total <= 0) {
                break;   // não há no armazenamento (a lista vai se atualizar)
            }
            slot.set(model.copyWithCount(inside.getCount() + total));
            BenchLedger.record(player, Map.of(model.getItem(), total), pool.origin());
            menu.broadcastChanges();
            break;
        }
        BenchSync.markDirty(player);
    }
}
