package io.github.leoascenci0.stashlink.mixin;

import io.github.leoascenci0.stashlink.bench.BenchLedger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
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
}
