package io.github.leoascenci0.stashlink.quickstack;

import io.github.leoascenci0.stashlink.compat.mc.ItemKinds;
import io.github.leoascenci0.stashlink.config.Feature;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

/**
 * Categorias de item que a tecla N pode guardar ou não (Item 17). Cada categoria tem um botão na tela de
 * config (o {@link Feature} correspondente: ligado = a N guarda, desligado = nunca guarda). Blocos comuns e todo o
 * resto <b>não têm categoria</b> e a N os trata como sempre.
 *
 * <p>Um item pertence a no máximo uma categoria (a primeira da ordem abaixo que o reconhece), então um machado
 * não depende de dois botões ao mesmo tempo.
 */
public enum ItemCategory {
    ARMOR(Feature.CAT_ARMOR, "iron_chestplate", ItemKinds::isArmor),
    TOOLS(Feature.CAT_TOOLS, "iron_pickaxe", ItemKinds::isTool),
    WEAPONS(Feature.CAT_WEAPONS, "iron_sword", ItemKinds::isWeapon),
    FOOD(Feature.CAT_FOOD, "cooked_beef", ItemKinds::isFood),
    POTIONS(Feature.CAT_POTIONS, "potion", ItemKinds::isPotion);

    private final Feature feature;
    private final String icon;
    private final Predicate<ItemStack> matcher;

    ItemCategory(Feature feature, String icon, Predicate<ItemStack> matcher) {
        this.icon = icon;
        this.feature = feature;
        this.matcher = matcher;
    }

    /** O botão (função) que liga/desliga esta categoria. */
    public Feature feature() {
        return feature;
    }

    /**
     * Nome da textura de item (atlas {@code items}) usada como "emoji" do botão. O jogo não tem emoji colorido na
     * fonte (Item 14); o ícone inline de item é recurso do próprio jogo e aparece em qualquer fonte.
     */
    public String icon() {
        return icon;
    }

    /** A categoria que este botão controla, ou {@code null} se {@code feature} não é uma categoria. */
    public static ItemCategory forFeature(Feature feature) {
        for (ItemCategory category : values()) {
            if (category.feature == feature) {
                return category;
            }
        }
        return null;
    }

    /** A categoria do item, ou {@code null} se ele não é de nenhuma (blocos comuns, minérios, tralha...). */
    public static ItemCategory of(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        for (ItemCategory category : values()) {
            if (category.matcher.test(stack)) {
                return category;
            }
        }
        return null;
    }
}
