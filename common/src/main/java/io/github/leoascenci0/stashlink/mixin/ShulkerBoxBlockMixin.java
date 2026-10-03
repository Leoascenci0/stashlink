package io.github.leoascenci0.stashlink.mixin;

import io.github.leoascenci0.stashlink.compat.mc.LabelCompat;
import io.github.leoascenci0.stashlink.label.Label;
import io.github.leoascenci0.stashlink.label.LabelHolder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * A shulker quebrada em <b>sobrevivência</b> solta o item pela tabela de loot, que só copia nome, conteúdo, chave e
 * loot. O rótulo do Item 14 (em CUSTOM_DATA) se perdia: só o criativo, que copia tudo, o preservava. Aqui, no
 * fim de {@code getDrops}, o rótulo da shulker que está sendo quebrada vai para o item solto.
 */
@Mixin(ShulkerBoxBlock.class)
public abstract class ShulkerBoxBlockMixin {
    @Inject(method = "getDrops", at = @At("RETURN"))
    private void stashlink$labelOnDrops(BlockState state, LootParams.Builder params,
                                        CallbackInfoReturnable<List<ItemStack>> cir) {
        if (!(params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof ShulkerBoxBlockEntity box)
                || !(box instanceof LabelHolder holder)) {
            return;
        }
        Label label = holder.stashlink$label();
        if (label.isEmpty()) {
            return;
        }
        for (ItemStack stack : cir.getReturnValue()) {
            if (stack.getItem() == state.getBlock().asItem()) {
                LabelCompat.writeToStack(stack, label);
            }
        }
    }
}
