package io.github.leoascenci0.stashlink.clientmode;

import net.minecraft.world.item.ItemStack;

/**
 * Foto de um slot do menu aberto, para a lógica pura decidir sem tocar no jogo.
 *
 * @param menuIndex índice do slot dentro do menu (é o que se manda no clique)
 * @param invIndex  índice no inventário do jogador (0-8 hotbar, 9-35 mochila), ou -1 se o slot é do container
 * @param stack     o que há no slot agora (não altere)
 */
public record MenuEntry(int menuIndex, int invIndex, ItemStack stack) {
    public boolean isPlayerSlot() {
        return invIndex >= 0;
    }

    public boolean isHotbar() {
        return invIndex >= 0 && invIndex < HOTBAR_SIZE;
    }

    public static final int HOTBAR_SIZE = 9;
    public static final int INVENTORY_SIZE = 36;
}
