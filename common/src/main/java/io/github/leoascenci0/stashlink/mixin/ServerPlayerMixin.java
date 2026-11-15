package io.github.leoascenci0.stashlink.mixin;

import io.github.leoascenci0.stashlink.bench.BenchSync;
import io.github.leoascenci0.stashlink.refill.RefillService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
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

    /**
     * Item 31: o jogo não fecha a tela na morte. Antes de o inventário cair no chão, a estação fecha e o que a bancada
     * emprestou volta ao baú (senão cairia junto com o resto e o baú ficaria desfalcado). Na troca de dimensão não
     * precisa: o próprio jogo fecha a tela ({@code Player.remove(CHANGED_DIMENSION)}, ainda no mundo antigo), e o
     * {@code beforeClose} de sempre devolve o emprestado.
     */
    @Inject(method = "die", at = @At("HEAD"))
    private void stashlink$releaseBenchOnDeath(DamageSource source, CallbackInfo ci) {
        BenchSync.releaseStaying((ServerPlayer) (Object) this);
    }
}
