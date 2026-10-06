package io.github.leoascenci0.stashlink.platform;

import io.github.leoascenci0.stashlink.platform.services.IPlatformHelper;
import io.github.leoascenci0.stashlink.source.ModStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.minecraft.util.TriState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public class NeoForgePlatformHelper implements IPlatformHelper {

    @Override
    public String getPlatformName() {

        return "NeoForge";
    }

    @Override
    public boolean isModLoaded(String modId) {

        return ModList.get().isLoaded(modId);
    }

    @Override
    public boolean canPlayerUseBlock(ServerPlayer player, BlockPos pos) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        PlayerInteractEvent.RightClickBlock event =
                new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, pos, hit);
        NeoForge.EVENT_BUS.post(event);
        // Mods de claim cancelam o evento ou desligam o "usar bloco".
        return !event.isCanceled() && event.getUseBlock() != TriState.FALSE;
    }

    @Override
    public boolean sendIfSupported(ServerPlayer player, net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        if (player.connection == null || !player.connection.hasChannel(payload.type())) {
            return false;
        }
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, payload);
        return true;
    }

    @Override
    public ModStorage modStorageAt(ServerLevel level, BlockPos pos, BlockState state, BlockEntity blockEntity) {
        // Lado null = o inventário inteiro, sem as restrições de lado (as de funil).
        ResourceHandler<ItemResource> handler = level.getCapability(Capabilities.Item.BLOCK, pos, state, blockEntity, null);
        return handler == null ? null : new NeoForgeModStorage(handler);
    }

    @Override
    public java.nio.file.Path getConfigDir() {
        return net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get();
    }

    @Override
    public boolean isDevelopmentEnvironment() {

        return !FMLLoader.getCurrent().isProduction();
    }
}