package io.github.leoascenci0.stashlink.mixin;

import io.github.leoascenci0.stashlink.compat.mc.LabelCompat;
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
        if (((Entity) (Object) this).entityTags().contains(LabelCompat.HOLOGRAM_TAG)) {
            cir.setReturnValue(false);
        }
    }
}
