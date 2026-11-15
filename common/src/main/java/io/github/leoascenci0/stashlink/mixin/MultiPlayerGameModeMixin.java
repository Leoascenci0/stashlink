package io.github.leoascenci0.stashlink.mixin;

import io.github.leoascenci0.stashlink.clientmode.ClientModeEngine;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Cliente (modo cliente seguro): anota cada "usar" de bloco ou de entidade, para saber de onde veio a tela de
 * container que abrir em seguida (baú do mundo ou loja/kit de plugin). Só anota, não muda o que o jogo faz. O
 * clique que o próprio mod dá para abrir baús passa pelo mesmo {@code useItemOn}, então também fica coberto.
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
    @Inject(method = "useItemOn", at = @At("HEAD"))
    private void stashlink$blockUsed(LocalPlayer player, InteractionHand hand, BlockHitResult hit,
                                     CallbackInfoReturnable<InteractionResult> cir) {
        ClientModeEngine.onBlockUsed(hit.getBlockPos());
    }

    /** Em 26.3 {@code interact} cobre também o "interactAt" (clique em entidade com ponto de acerto). */
    @Inject(method = "interact", at = @At("HEAD"))
    private void stashlink$entityUsed(Player player, Entity entity, EntityHitResult hit, InteractionHand hand,
                                      CallbackInfoReturnable<InteractionResult> cir) {
        ClientModeEngine.onEntityUsed();
    }
}
