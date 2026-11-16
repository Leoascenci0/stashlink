package io.github.leoascenci0.stashlink.mixin;

import io.github.leoascenci0.stashlink.bench.BenchLedger;
import io.github.leoascenci0.stashlink.slotlock.SlotLocks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMixin {
    /**
     * A estação está fechando: antes de o jogo devolver a grade à mochila, o caderno de emprestados refaz a conta
     * com os slots ainda cheios (ver {@link BenchLedger#beforeClose}).
     */
    @Inject(method = "removed", at = @At("HEAD"))
    private void stashlink$settleBeforeClose(Player player, CallbackInfo ci) {
        if (player instanceof ServerPlayer serverPlayer) {
            BenchLedger.beforeClose(serverPlayer, (AbstractContainerMenu) (Object) this);
        }
    }

    /**
     * Item 32: o shift-clique do jogo ({@code moveItemStackTo}) tem dois passos: primeiro completa pilhas iguais e depois
     * usa slots vazios. Só o segundo pergunta {@code Slot.mayPlace}, que é onde a reserva (Item 13) barra o item errado.
     * O primeiro passo completaria, sem perguntar, uma pilha que está num slot reservado para OUTRO item (um funil ou
     * outro mod pôs lá, pois a reserva só vale para menus). Aqui o primeiro passo passa a ver "sem espaço" nesse slot, e o
     * item segue para outro slot. Quem tira o item do slot reservado (clique, W, funil) continua livre.
     */
    @Redirect(method = "moveItemStackTo", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/inventory/Slot;getMaxStackSize(Lnet/minecraft/world/item/ItemStack;)I", ordinal = 0))
    private int stashlink$noTopUpInAnotherItemsReservation(Slot slot, ItemStack target) {
        return SlotLocks.mayPlace(slot.container, slot.getContainerSlot(), target) ? slot.getMaxStackSize(target) : 0;
    }
}
