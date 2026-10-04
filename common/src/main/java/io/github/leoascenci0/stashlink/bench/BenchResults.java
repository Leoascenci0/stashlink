package io.github.leoascenci0.stashlink.bench;

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

    private BenchResults() {
    }

    public static boolean supports(AbstractContainerMenu menu) {
        return StationRecipes.supports(menu);
    }

    /** Os resultados conhecidos: os possíveis primeiro, depois os que faltam material (vermelho). */
    public static List<BenchPoolSync.Entry> list(ServerPlayer player) {
        List<ItemStack> have = available(player);
        List<StationRecipes.Option> options = StationRecipes.options(player, player.containerMenu, have);
        List<BenchPoolSync.Entry> ok = new ArrayList<>();
        List<BenchPoolSync.Entry> missing = new ArrayList<>();
        List<BenchPoolSync.Entry> colors = new ArrayList<>();
        if (player.containerMenu instanceof net.minecraft.world.inventory.LoomMenu) {
            // Aba "Cores": uma entrada por cor de corante conhecida (vermelha se não há corante dela agora).
            for (net.minecraft.world.item.DyeColor color : StationRecipes.knownDyeColors(player, have)) {
                ItemStack dye = new ItemStack(net.minecraft.world.item.Items.DYE.pick(color));
                boolean has = have.stream().anyMatch(s -> s.is(dye.getItem()));
                colors.add(new BenchPoolSync.Entry(dye, 0, BenchPoolSync.Entry.COLOR_PICK, !has, 0, color.getId()));
            }
        }
        for (int i = 0; i < options.size() && ok.size() + missing.size() < BenchPoolSync.MAX_ENTRIES; i++) {
            StationRecipes.Option option = options.get(i);
            boolean can = option.needs().stream().allMatch(need -> have.stream().anyMatch(need));
            (can ? ok : missing).add(new BenchPoolSync.Entry(option.icon(), 0, i, !can, option.tab(), -1));
        }
        colors.addAll(ok);
        colors.addAll(missing);
        return colors;
    }

    /** Aba de um item solto: na mesa de ferraria, qual slot o aceita (enfeite, equipamento ou minério); nas outras, 0. */
    public static int slotTab(AbstractContainerMenu menu, ItemStack stack) {
        if (menu instanceof net.minecraft.world.inventory.SmithingMenu) {
            for (int i = 0; i < 3; i++) {
                if (menu.getSlot(i).mayPlace(stack)) {
                    return i;
                }
            }
        }
        if (menu instanceof net.minecraft.world.inventory.EnchantmentMenu) {
            // Encantamento: equipamento, livros ou lápis-lazúli (o slot 1 só aceita o lápis).
            if (menu.getSlot(1).mayPlace(stack)) {
                return 2;
            }
            return stack.is(net.minecraft.world.item.Items.BOOK) || stack.is(net.minecraft.world.item.Items.ENCHANTED_BOOK) ? 1 : 0;
        }
        if (menu instanceof net.minecraft.world.inventory.BrewingStandMenu) {
            // Suporte de poções: garrafas (slots 0-2), ingrediente (3) ou combustível (4).
            // Do combustível para as garrafas: o pó de blaze serve nos dois, e aqui conta como combustível.
            for (int i = 4; i >= 0; i--) {
                if (menu.getSlot(i).mayPlace(stack)) {
                    return i < 3 ? 0 : i - 2;
                }
            }
            return 0;
        }
        if (menu instanceof net.minecraft.world.inventory.AnvilMenu) {
            // Bigorna: livros, equipamento (ferramenta, armadura, arma... o que tem durabilidade ou encantamento) e o resto (material de conserto).
            if (stack.is(net.minecraft.world.item.Items.ENCHANTED_BOOK) || stack.is(net.minecraft.world.item.Items.BOOK)) {
                return 0;
            }
            return stack.isDamageableItem() || stack.isEnchanted() || stack.is(net.minecraft.world.item.Items.NAME_TAG) ? 1 : 2;
        }
        return 0;
    }

    /** O que o jogador tem à mão para esta estação: armazenamento do raio e mochila. */
    private static List<ItemStack> available(ServerPlayer player) {
        List<ItemStack> have = new ArrayList<>();
        for (BenchPool.Stack stack : BenchPool.of(player).contents()) {
            have.add(stack.item());
        }
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (!stack.isEmpty()) {
                have.add(stack);
            }
        }
        return have;
    }

    /** Monta a receita {@code id}: põe cada entrada no slot certo (armazenamento primeiro, mochila se faltar) e a escolhe. */
    public static void craft(ServerPlayer player, AbstractContainerMenu menu, int id, ItemStack choice) {
        List<StationRecipes.Option> options = StationRecipes.options(player, menu, available(player));
        if (id < 0 || id >= options.size()) {
            return;
        }
        StationRecipes.Option option = options.get(id);
        List<java.util.function.Predicate<ItemStack>> needs = new ArrayList<>(option.needs());
        if (menu instanceof net.minecraft.world.inventory.LoomMenu && StationRecipes.isDye(choice)) {
            needs.set(1, s -> s.is(choice.getItem()));   // o corante da cor que o jogador escolheu no painel
        }
        BenchPool pool = BenchPool.of(player);
        // Primeiro confere que dá para montar tudo: nunca deixa a estação pela metade.
        List<ItemStack> have = available(player);
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
            if (!fillSlot(player, pool, slot, needs.get(i))) {
                BenchSync.markDirty(player);
                return;
            }
        }
        StationRecipes.select(menu, player, option);
        menu.broadcastChanges();
        BenchSync.markDirty(player);
    }

    /** Põe em {@code slot} um item que satisfaz {@code need}: do armazenamento, ou da mochila se não houver lá. */
    private static boolean fillSlot(ServerPlayer player, BenchPool pool, Slot slot, java.util.function.Predicate<ItemStack> need) {
        for (BenchPool.Stack stack : pool.contents()) {
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
        List<Slot> order = new ArrayList<>(menu.slots);
        if (menu instanceof net.minecraft.world.inventory.EnchantmentMenu) {
            // O slot do item do encantamento aceita qualquer coisa: o lápis-lazúli tem que testar o slot dele primeiro.
            java.util.Collections.reverse(order.subList(0, 2));
        }
        for (Slot slot : order) {
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
