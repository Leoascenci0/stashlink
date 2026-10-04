package io.github.leoascenci0.stashlink.mixin;

import io.github.leoascenci0.stashlink.compat.mc.LabelCompat;
import io.github.leoascenci0.stashlink.compat.mc.OrganizeCompat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hologramas de rótulo nunca vão para o disco: são só o reflexo do rótulo do bloco e o mod os recria quando o
 * bloco volta (ver {@code HologramService}). Sem isto, descarregar a área deixaria hologramas "órfãos".
 */
@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "shouldBeSaved", at = @At("HEAD"), cancellable = true)
    private void stashlink$noSaveHolograms(CallbackInfoReturnable<Boolean> cir) {
        if (((Entity) (Object) this).entityTags().contains(LabelCompat.HOLOGRAM_TAG)
                || ((Entity) (Object) this).entityTags().contains(OrganizeCompat.HIGHLIGHT_TAG)) {
            cir.setReturnValue(false);
        }
    }

    /** O contorno de destaque da busca (Item 20.3) só existe para quem buscou: os outros jogadores nem recebem a entidade. */
    @Inject(method = "broadcastToPlayer", at = @At("HEAD"), cancellable = true)
    private void stashlink$highlightOwnerOnly(ServerPlayer player, CallbackInfoReturnable<Boolean> cir) {
        if (OrganizeCompat.hiddenFrom((Entity) (Object) this, player)) {
            cir.setReturnValue(false);
        }
    }

    /**
     * Quem vê o holograma é decidido aqui, no servidor: só jogadores a até {@link LabelCompat#SHOW_RANGE} blocos
     * (o jogo reavalia isso sempre que o jogador anda), sem depender da opção "distância de entidades" do cliente.
     */
    @Inject(method = "broadcastToPlayer", at = @At("HEAD"), cancellable = true)
    private void stashlink$hologramRange(ServerPlayer player, CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;
        if (self.entityTags().contains(LabelCompat.HOLOGRAM_TAG)
                && self.distanceToSqr(player) > LabelCompat.SHOW_RANGE * LabelCompat.SHOW_RANGE) {
            cir.setReturnValue(false);
        }
    }
}
