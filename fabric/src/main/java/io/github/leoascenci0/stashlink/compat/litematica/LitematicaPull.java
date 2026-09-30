package io.github.leoascenci0.stashlink.compat.litematica;

import io.github.leoascenci0.stashlink.network.PullItemRequest;
import io.github.leoascenci0.stashlink.pull.PullRequestThrottle;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * Lado cliente da integração com o Litematica: quando o Litematica quer um item para construir e o jogador
 * não o tem em nenhum slot, pede ao servidor que traga o item das shulkers para a hotbar.
 */
public final class LitematicaPull {
    private static final PullRequestThrottle THROTTLE = new PullRequestThrottle(6);

    private LitematicaPull() {
    }

    /**
     * Chamado no início de {@code InventoryUtils.schematicWorldPickBlock} do Litematica.
     *
     * @return {@code true} se o pedido foi tratado por nós e o Litematica não deve continuar (ele tentaria
     *         puxar a própria shulker para a mão, que é justamente o que queremos evitar)
     */
    public static boolean onPickBlock(ItemStack wanted, Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || wanted.isEmpty() || player.isCreative() || player.isSpectator()) {
            return false;
        }
        // Já tem o item em algum lugar: o Litematica resolve sozinho (troca de slot).
        if (hasItem(player, wanted)) {
            return false;
        }
        // Servidor sem o StashLink: não há quem atenda, deixa o Litematica fazer o que faz hoje.
        if (!ClientPlayNetworking.canSend(PullItemRequest.TYPE)) {
            return false;
        }
        if (THROTTLE.tryAcquire(wanted.getItem(), mc.level.getGameTime())) {
            ClientPlayNetworking.send(new PullItemRequest(wanted.getItem(), wanted.getMaxStackSize()));
        }
        return true;
    }

    private static boolean hasItem(LocalPlayer player, ItemStack wanted) {
        if (ItemStack.isSameItem(player.getItemInHand(InteractionHand.OFF_HAND), wanted)) {
            return true;
        }
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (ItemStack.isSameItem(stack, wanted)) {
                return true;
            }
        }
        return false;
    }
}
