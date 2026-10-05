package io.github.leoascenci0.stashlink.bench;

import io.github.leoascenci0.stashlink.compat.mc.BrewingCompat;
import io.github.leoascenci0.stashlink.network.BenchPoolSync;
import io.github.leoascenci0.stashlink.source.ItemSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Suporte de poções (Item 16.3, Fase 3): a aba "Poções" do painel lista <b>todas</b> as poções que o jogo sabe fazer a
 * partir de garrafas de água, em vermelho as que o jogador não consegue com o que tem (baús do raio + mochila +
 * o que já está no suporte). Clicar monta <b>só o próximo passo</b> (garrafas + ingrediente, e pó de blaze se o
 * suporte está sem combustível); quando o suporte terminar, o próximo clique continua de onde ficou.
 *
 * <p>Integridade: primeiro confere tudo (slots livres ou já certos, material à mão), só depois mexe; cada item que sai
 * de um baú ou da mochila entra no slot no mesmo passo. O suporte guarda o que está nos slots, então o que entra já é
 * do jogador (como na fornalha) e nada vai para o caderno de emprestados.
 */
public final class BenchBrewing {
    private BenchBrewing() {
    }

    /** O que o jogador tem para este suporte, numa varredura só. */
    private record Have(BenchPool pool, List<BenchPool.Stack> stored, List<ItemStack> all) {
        static Have of(ServerPlayer player) {
            BenchPool pool = BenchPool.of(player);
            List<BenchPool.Stack> stored = pool.contents();
            List<ItemStack> all = new ArrayList<>();
            for (BenchPool.Stack stack : stored) {
                all.add(stack.item());
            }
            for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
                if (!stack.isEmpty()) {
                    all.add(stack);
                }
            }
            return new Have(pool, stored, all);
        }

        boolean has(ItemStack model) {
            return all.stream().anyMatch(s -> ItemStack.isSameItemSameComponents(s, model));
        }
    }

    /** As garrafas de onde partir: as do suporte primeiro (continuar o que já está lá), depois as dos baús e da mochila. */
    private static List<Integer> sources(BrewingCompat.Graph graph, AbstractContainerMenu menu, Have have) {
        Set<Integer> out = new LinkedHashSet<>();
        for (int i = 0; i < BrewingCompat.BOTTLE_SLOTS; i++) {
            int node = graph.potionOf(menu.getSlot(i).getItem());
            if (node >= 0) {
                out.add(node);
            }
        }
        for (ItemStack stack : have.all()) {
            if (BrewingCompat.isPotion(stack)) {
                int node = graph.potionOf(stack);
                if (node >= 0) {
                    out.add(node);
                }
            }
        }
        return new ArrayList<>(out);
    }

    private static Set<Integer> ingredientsAtHand(BrewingCompat.Graph graph, AbstractContainerMenu menu, Have have) {
        Set<Integer> out = new HashSet<>();
        for (int i = 0; i < graph.ingredients().size(); i++) {
            ItemStack ingredient = graph.ingredients().get(i);
            if (have.has(ingredient) || ItemStack.isSameItemSameComponents(
                    menu.getSlot(BrewingCompat.INGREDIENT_SLOT).getItem(), ingredient)) {
                out.add(i);
            }
        }
        return out;
    }

    /** O suporte tem como funcionar: combustível no tanque, pó no slot de combustível ou pó à mão. */
    private static boolean fuelOk(AbstractContainerMenu menu, Have have) {
        return BrewingCompat.fuel(menu) > 0 || menu.getSlot(BrewingCompat.FUEL_SLOT).hasItem()
                || have.has(BrewingCompat.fuelItem());
    }

    private static Map<Integer, BrewPlanner.Edge> plan(BrewingCompat.Graph graph, AbstractContainerMenu menu, Have have) {
        Set<Integer> ingredients = ingredientsAtHand(graph, menu, have);
        return BrewPlanner.firstSteps(graph.edges(), sources(graph, menu, have), ingredients::contains);
    }

    /** As entradas da aba "Poções" (aba 0): uma por poção do jogo; vermelha a que não dá para fazer agora. */
    public static List<BenchPoolSync.Entry> list(ServerPlayer player) {
        AbstractContainerMenu menu = player.containerMenu;
        BrewingCompat.Graph graph = BrewingCompat.graph(player.level());
        Have have = Have.of(player);
        Map<Integer, BrewPlanner.Edge> steps = plan(graph, menu, have);
        boolean fuel = fuelOk(menu, have);
        List<BenchPoolSync.Entry> out = new ArrayList<>();
        for (int node : BrewPlanner.reachable(graph.edges(), graph.roots())) {
            ItemStack potion = graph.potions().get(node);
            boolean can = fuel && steps.containsKey(node);
            out.add(new BenchPoolSync.Entry(potion, 0, BenchResults.keyOf(potion), !can, 0, -1));
        }
        return out;
    }

    /**
     * Clicou na poção de chave {@code id}: monta o próximo passo. {@code one} (botão direito) = uma garrafa só em vez
     * de três. Qualquer coisa fora do lugar (slot de garrafa com outra poção, ingrediente diferente no slot, falta de
     * material): não mexe em nada e reenvia a lista.
     */
    public static void brew(ServerPlayer player, AbstractContainerMenu menu, int id, boolean one) {
        BrewingCompat.Graph graph = BrewingCompat.graph(player.level());
        int target = -1;
        for (int i = 0; i < graph.potions().size(); i++) {
            if (BenchResults.keyOf(graph.potions().get(i)) == id) {
                target = i;
                break;
            }
        }
        Have have = Have.of(player);
        BrewPlanner.Edge step = target < 0 ? null : plan(graph, menu, have).get(target);
        if (step == null || !fuelOk(menu, have)) {
            BenchSync.markDirty(player);
            return;
        }
        ItemStack from = graph.potions().get(step.from());
        ItemStack ingredient = graph.ingredients().get(step.ingredient());

        // 1) Confere tudo antes de mexer.
        int already = 0;
        List<Slot> empty = new ArrayList<>();
        for (int i = 0; i < BrewingCompat.BOTTLE_SLOTS; i++) {
            Slot slot = menu.getSlot(i);
            if (!slot.hasItem()) {
                empty.add(slot);
            } else if (ItemStack.isSameItemSameComponents(slot.getItem(), from)) {
                already++;
            } else {
                BenchSync.markDirty(player);   // outra poção no suporte: não mistura nem tira a do jogador
                return;
            }
        }
        Slot ingredientSlot = menu.getSlot(BrewingCompat.INGREDIENT_SLOT);
        boolean ingredientReady = ItemStack.isSameItemSameComponents(ingredientSlot.getItem(), ingredient);
        if (ingredientSlot.hasItem() && !ingredientReady) {
            BenchSync.markDirty(player);
            return;
        }
        int wantBottles = Math.max(0, (one ? 1 : BrewingCompat.BOTTLE_SLOTS) - already);
        if (already == 0 && !have.has(from)) {
            BenchSync.markDirty(player);
            return;
        }
        Slot fuelSlot = menu.getSlot(BrewingCompat.FUEL_SLOT);
        boolean needFuel = BrewingCompat.fuel(menu) <= 0 && !fuelSlot.hasItem();

        // 2) Monta: garrafas, ingrediente, combustível. Cada item sai e entra no mesmo passo.
        for (int i = 0; i < wantBottles && i < empty.size(); i++) {
            if (!move(player, have, from, empty.get(i))) {
                break;   // acabaram as garrafas: o suporte trabalha com as que entraram
            }
        }
        if (!ingredientReady) {
            move(player, have, ingredient, ingredientSlot);
        }
        if (needFuel) {
            move(player, have, BrewingCompat.fuelItem(), fuelSlot);
        }
        menu.broadcastChanges();
        BenchSync.markDirty(player);
    }

    /** Põe 1 de {@code model} em {@code slot}: do armazenamento, ou da mochila se lá não houver. */
    private static boolean move(ServerPlayer player, Have have, ItemStack model, Slot slot) {
        ItemStack one = model.copyWithCount(1);
        if (!slot.mayPlace(one)) {
            return false;
        }
        for (BenchPool.Stack stack : have.stored()) {
            if (ItemStack.isSameItemSameComponents(stack.item(), one)) {
                if (ItemSource.sum(have.pool().source().take(one, 1)) > 0) {
                    slot.set(one);
                    return true;
                }
                break;
            }
        }
        var items = player.getInventory().getNonEquipmentItems();
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, one)) {
                slot.set(stack.copyWithCount(1));
                stack.shrink(1);
                if (stack.isEmpty()) {
                    items.set(i, ItemStack.EMPTY);
                }
                return true;
            }
        }
        return false;
    }
}
