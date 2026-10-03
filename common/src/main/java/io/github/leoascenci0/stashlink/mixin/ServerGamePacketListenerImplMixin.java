package io.github.leoascenci0.stashlink.mixin;

import io.github.leoascenci0.stashlink.pull.PullItemService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Item 19. Desde o 1.21.x o botão do meio (pick block) é resolvido <b>no servidor</b>: o cliente manda só a posição
 * do bloco e {@code tryPickItem} escolhe o slot, mas só conhece o inventário do jogador. O mixin dá ao mod a vez
 * antes: se o item está guardado por perto, ele vem para a hotbar. Não precisa de mixin no cliente.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {
    @Shadow
    public ServerPlayer player;

    @Inject(method = "tryPickItem", at = @At("HEAD"), cancellable = true)
    private void stashlink$pickFromStorage(ItemStack stack, CallbackInfo ci) {
        if (PullItemService.pickBlock(this.player, stack)) {
            ci.cancel();
        }
    }
}
