package io.github.leoascenci0.stashlink.compat.litematica.mixin;

import io.github.leoascenci0.stashlink.compat.litematica.LitematicaPull;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ponto de extensão no Litematica 0.27.x: {@code InventoryUtils.schematicWorldPickBlock} é chamado tanto
 * pelo pick block da pré-visualização quanto pelo Easy Place. (A API de eventos de pick block do Litematica
 * não serve: o Easy Place não passa por ela.) O alvo é dado por nome para não precisar do Litematica para
 * compilar; se a assinatura mudar numa versão futura, {@code require = 0} apenas desliga a integração.
 */
@Mixin(targets = "fi.dy.masa.litematica.util.InventoryUtils")
public abstract class InventoryUtilsMixin {
    @Inject(method = "schematicWorldPickBlock", at = @At("HEAD"), cancellable = true, require = 0)
    private static void stashlink$pullFromShulkers(ItemStack stack, BlockPos pos, Level schematicWorld, Minecraft mc,
                                                   CallbackInfo ci) {
        if (LitematicaPull.onPickBlock(stack, mc)) {
            ci.cancel();
        }
    }
}
