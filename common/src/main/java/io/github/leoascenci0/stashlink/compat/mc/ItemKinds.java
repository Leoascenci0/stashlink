package io.github.leoascenci0.stashlink.compat.mc;

import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Como o jogo diz que um item é armadura, ferramenta, arma, comida ou poção (Item 17). Só tags e componentes do
 * jogo, sem lista de itens na mão: item de outro mod que entra nessas tags/componentes é reconhecido sozinho.
 * Quebrou numa atualização? Conserte aqui (nomes de tags e componentes mudam de versão).
 */
public final class ItemKinds {
    private ItemKinds() {
    }

    /** Elmo, peitoral, calça, bota (tags do jogo), asa-delta e escudo. */
    public static boolean isArmor(ItemStack stack) {
        return stack.is(ItemTags.HEAD_ARMOR) || stack.is(ItemTags.CHEST_ARMOR) || stack.is(ItemTags.LEG_ARMOR)
                || stack.is(ItemTags.FOOT_ARMOR) || stack.is(Items.ELYTRA) || stack.is(Items.SHIELD);
    }

    /** Picareta, machado, pá, enxada, tesoura e vara de pescar. (Machado conta como ferramenta, não como arma.) */
    public static boolean isTool(ItemStack stack) {
        return stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.AXES) || stack.is(ItemTags.SHOVELS)
                || stack.is(ItemTags.HOES) || stack.is(Items.SHEARS) || stack.is(Items.FISHING_ROD);
    }

    /** Espada, lança, arco, besta, tridente e maça. */
    public static boolean isWeapon(ItemStack stack) {
        return stack.is(ItemTags.SWORDS) || stack.is(ItemTags.SPEARS) || stack.is(Items.BOW)
                || stack.is(Items.CROSSBOW) || stack.is(Items.TRIDENT) || stack.is(Items.MACE);
    }

    /** Qualquer item comestível (componente de comida do jogo). Poção é outra categoria. */
    public static boolean isFood(ItemStack stack) {
        return stack.has(DataComponents.FOOD);
    }

    /** Poção normal, de arremesso, persistente (flecha de poção não: é munição). */
    public static boolean isPotion(ItemStack stack) {
        return stack.has(DataComponents.POTION_CONTENTS) && !stack.is(ItemTags.ARROWS);
    }
}
