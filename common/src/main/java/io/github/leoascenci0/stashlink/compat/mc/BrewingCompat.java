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

    /**
     * TEMPORÁRIO: a API de receitas de poção mudou no 26.3 e ainda está sendo mapeada; até lá o mapa fica vazio (a aba
     * Poções não lista nada e o resto do suporte funciona como antes).
     */
    public static Graph graph(Level level) {
        return EMPTY;
    }

    private static final Graph EMPTY = new Graph(List.of(), List.of(), List.of(), List.of());
}
