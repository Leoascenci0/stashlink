package io.github.leoascenci0.stashlink.compat.mc;

import io.github.leoascenci0.stashlink.bench.BrewPlanner;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * O suporte de poções do jogo, para o painel (Item 16.3, Fase 3): as receitas viram o mapa de {@link BrewPlanner}
 * (poções e ingredientes numerados). Toda API frágil de poção fica aqui: {@code PotionBrewing}, {@code PotionContents},
 * os slots e o combustível do {@code BrewingStandMenu}. Mudou numa versão do jogo? Conserte aqui.
 */
public final class BrewingCompat {
    /** Slots do suporte: 0..2 garrafas, 3 ingrediente, 4 combustível. */
    public static final int BOTTLE_SLOTS = 3;
    public static final int INGREDIENT_SLOT = 3;
    public static final int FUEL_SLOT = 4;
    /** Teto de poções no mapa: protege o servidor de um mod com receitas em laço infinito. */
    private static final int MAX_NODES = 1024;

    /**
     * O mapa das receitas: {@code potions.get(i)} é o nó {@code i} (contagem 1), {@code ingredients.get(j)} o
     * ingrediente {@code j}; {@code roots} são as garrafas de água (comum, arremesso, persistente).
     */
    public record Graph(List<ItemStack> potions, List<ItemStack> ingredients, List<BrewPlanner.Edge> edges,
                        List<Integer> roots) {
        /** O nó desta poção (mesmo item e componentes), ou -1. */
        public int potionOf(ItemStack stack) {
            for (int i = 0; i < potions.size(); i++) {
                if (ItemStack.isSameItemSameComponents(potions.get(i), stack)) {
                    return i;
                }
            }
            return -1;
        }

        /** O número deste ingrediente, ou -1. */
        public int ingredientOf(ItemStack stack) {
            for (int i = 0; i < ingredients.size(); i++) {
                if (ItemStack.isSameItemSameComponents(ingredients.get(i), stack)) {
                    return i;
                }
            }
            return -1;
        }
    }

    /**
     * Um mapa por conjunto de receitas do jogo (muda só com datapack/flags): calculado uma vez. A chave é o objeto de
     * receitas do mundo; o tipo não aparece no código ({@code var}) porque o nome da classe muda entre versões.
     */
    private static final Map<Object, Graph> CACHE = Collections.synchronizedMap(new WeakHashMap<>());

    private BrewingCompat() {
    }

    public static boolean isBrewing(AbstractContainerMenu menu) {
        return menu instanceof BrewingStandMenu;
    }

    /** O combustível que o suporte ainda tem (cargas de pó de blaze já queimadas); 0 se não é um suporte. */
    public static int fuel(AbstractContainerMenu menu) {
        return menu instanceof BrewingStandMenu brewing ? brewing.getFuel() : 0;
    }

    public static Slot slot(AbstractContainerMenu menu, int index) {
        return menu.getSlot(index);
    }

    /** O item que alimenta o suporte. */
    public static ItemStack fuelItem() {
        return new ItemStack(Items.BLAZE_POWDER);
    }

    /** É uma garrafa de poção (comum, arremesso ou persistente)? Frasco vazio não conta. */
    public static boolean isPotion(ItemStack stack) {
        return (stack.is(Items.POTION) || stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION))
                && stack.has(DataComponents.POTION_CONTENTS);
    }

    public static Graph graph(Level level) {
        return CACHE.computeIfAbsent(level.potionBrewing(), key -> build(level));
    }

    private static Graph build(Level level) {
        var brewing = level.potionBrewing();
        List<ItemStack> ingredients = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack stack = new ItemStack(item);
            if (!stack.isEmpty() && brewing.isIngredient(stack)) {
                ingredients.add(stack);
            }
        }
        List<ItemStack> potions = new ArrayList<>();
        List<Integer> roots = new ArrayList<>();
        for (Item bottle : List.of(Items.POTION, Items.SPLASH_POTION, Items.LINGERING_POTION)) {
            roots.add(potions.size());
            potions.add(PotionContents.createItemStack(bottle, Potions.WATER));
        }
        List<BrewPlanner.Edge> edges = new ArrayList<>();
        Graph graph = new Graph(potions, ingredients, edges, roots);
        Deque<Integer> queue = new ArrayDeque<>(roots);
        while (!queue.isEmpty()) {
            int from = queue.poll();
            ItemStack input = potions.get(from);
            for (int i = 0; i < ingredients.size(); i++) {
                ItemStack ingredient = ingredients.get(i);
                if (!brewing.hasMix(input, ingredient)) {
                    continue;
                }
                ItemStack out = brewing.mix(ingredient, input.copy());
                if (out.isEmpty() || ItemStack.isSameItemSameComponents(out, input)) {
                    continue;
                }
                int to = graph.potionOf(out);
                if (to < 0) {
                    if (potions.size() >= MAX_NODES) {
                        continue;
                    }
                    to = potions.size();
                    potions.add(out.copyWithCount(1));
                    queue.add(to);
                }
                edges.add(new BrewPlanner.Edge(from, i, to));
            }
        }
        return new Graph(List.copyOf(potions), List.copyOf(ingredients), List.copyOf(edges), List.copyOf(roots));
    }
}
