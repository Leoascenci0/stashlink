package io.github.leoascenci0.stashlink.mixin;

import io.github.leoascenci0.stashlink.refill.RefillService;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
    /**
     * Início do tick do jogador, antes de o servidor enviar as mudanças de inventário ao cliente. Assim, um
     * stack esgotado por uma ação do tick anterior (colocar bloco, arremessar) é reposto antes de o cliente
     * chegar a ver a mão vazia.
     */
    @Inject(method = "tick", at = @At("HEAD"))
    private void stashlink$refillHands(CallbackInfo ci) {
        RefillService.tickPlayer((ServerPlayer) (Object) this);
    }
}
