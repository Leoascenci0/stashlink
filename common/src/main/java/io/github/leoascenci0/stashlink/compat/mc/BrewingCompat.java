package io.github.leoascenci0.stashlink.compat.mc;

import io.github.leoascenci0.stashlink.bench.BrewPlanner;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.BrewingInput;
import net.minecraft.world.item.crafting.BrewingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * O suporte de poções do jogo, para o painel (Item 16.3, Fase 3): as receitas viram o mapa de {@link BrewPlanner}
 * (poções e ingredientes numerados). Toda API frágil de poção fica aqui: {@code BrewingRecipe} (26.3: receitas de dados), {@code PotionContents},
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

    /** O mapa já montado e de qual conjunto de receitas ele veio (recalcula se o servidor recarregar receitas). */
    private record Cached(Object manager, int recipes, Graph graph) {
    }

    private static volatile Cached cached;

    private static final Graph EMPTY = new Graph(List.of(), List.of(), List.of(), List.of());

    /**
     * O mapa das poções do servidor. No 26.3 as receitas do suporte são receitas de dados ({@code BrewingRecipe},
     * {@code data/minecraft/recipe/brewing/*.json}), guardadas com as outras no gerenciador de receitas do servidor;
     * por isso também valem receitas de datapack e de mod. Fora do servidor: mapa vazio.
     */
    public static Graph graph(Level level) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return EMPTY;
        }
        var manager = serverLevel.getServer().getRecipeManager();
        List<BrewingRecipe> recipes = new ArrayList<>();
        for (RecipeHolder<?> holder : manager.getRecipes()) {
            if (holder.value() instanceof BrewingRecipe recipe) {
                recipes.add(recipe);
            }
        }
        Cached now = cached;
        if (now != null && now.manager() == manager && now.recipes() == recipes.size()) {
            return now.graph();
        }
        Graph graph = build(recipes);
        cached = new Cached(manager, recipes.size(), graph);
        return graph;
    }

    private static Graph build(List<BrewingRecipe> recipes) {
        // Ingredientes: os itens que alguma receita aceita como reagente.
        List<ItemStack> ingredients = new ArrayList<>();
        for (BrewingRecipe recipe : recipes) {
            recipe.getReagent().ingredient().items().forEach(holder -> {
                ItemStack stack = new ItemStack(holder.value());
                if (!stack.isEmpty() && ingredients.stream().noneMatch(s -> ItemStack.isSameItemSameComponents(s, stack))) {
                    ingredients.add(stack);
                }
            });
        }
        // Para cada receita, quais ingredientes servem de reagente (calculado uma vez).
        List<List<Integer>> reagents = new ArrayList<>();
        for (BrewingRecipe recipe : recipes) {
            List<Integer> ok = new ArrayList<>();
            for (int i = 0; i < ingredients.size(); i++) {
                if (recipe.getReagent().test(ingredients.get(i))) {
                    ok.add(i);
                }
            }
            reagents.add(ok);
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
            for (int r = 0; r < recipes.size(); r++) {
                BrewingRecipe recipe = recipes.get(r);
                if (!recipe.getInput().test(input)) {
                    continue;
                }
                for (int i : reagents.get(r)) {
                    BrewingInput mix = new BrewingInput(input.copy(), ingredients.get(i).copy());
                    if (!recipe.matches(mix)) {
                        continue;
                    }
                    ItemStack out = recipe.assemble(mix);
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
        }
        return new Graph(List.copyOf(potions), List.copyOf(ingredients), List.copyOf(edges), List.copyOf(roots));
    }
}
