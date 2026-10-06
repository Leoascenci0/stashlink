package io.github.leoascenci0.stashlink.platform;

import io.github.leoascenci0.stashlink.platform.services.IPlatformHelper;
import io.github.leoascenci0.stashlink.source.ModStorage;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class FabricPlatformHelper implements IPlatformHelper {

    @Override
    public String getPlatformName() {
        return "Fabric";
    }

    @Override
    public boolean isModLoaded(String modId) {

        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public boolean canPlayerUseBlock(ServerPlayer player, BlockPos pos) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        // Mods de claim devolvem FAIL para barrar; PASS/SUCCESS/CONSUME não são negação.
        return UseBlockCallback.EVENT.invoker().interact(player, player.level(), InteractionHand.MAIN_HAND, hit)
                != InteractionResult.FAIL;
    }

    @Override
    public boolean sendIfSupported(ServerPlayer player, net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        if (!net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player, payload.type())) {
            return false;
        }
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, payload);
        return true;
    }

    @Override
    public ModStorage modStorageAt(ServerLevel level, BlockPos pos, BlockState state, BlockEntity blockEntity) {
        // Direção null = o inventário inteiro, sem as restrições de lado (as de funil).
        Storage<ItemVariant> storage = ItemStorage.SIDED.find(level, pos, state, blockEntity, null);
        return storage == null ? null : new FabricModStorage(storage);
    }

    @Override
    public java.nio.file.Path getConfigDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public boolean isDevelopmentEnvironment() {

        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }
}
