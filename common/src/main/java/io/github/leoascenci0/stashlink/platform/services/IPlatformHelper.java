package io.github.leoascenci0.stashlink.platform.services;

import io.github.leoascenci0.stashlink.source.ModStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public interface IPlatformHelper {

    /**
     * Gets the name of the current platform
     *
     * @return The name of the current platform.
     */
    String getPlatformName();

    /**
     * Checks if a mod with the given id is loaded.
     *
     * @param modId The mod to check if it is loaded.
     * @return True if the mod is loaded, false otherwise.
     */
    boolean isModLoaded(String modId);

    /**
     * Check if the game is currently in a development environment.
     *
     * @return True if in a development environment, false otherwise.
     */
    boolean isDevelopmentEnvironment();

    /**
     * Pergunta aos mods de proteção (claims, spawn, etc.) se o jogador poderia abrir o bloco em {@code pos}.
     * Dispara o evento de "usar bloco" do loader e vale {@code false} se alguém o cancelou.
     */
    boolean canPlayerUseBlock(net.minecraft.server.level.ServerPlayer player, net.minecraft.core.BlockPos pos);

    /**
     * Envia um pacote servidor → cliente <b>só se</b> o cliente do jogador tem o mod (registrou o canal). Cliente
     * vanilla nunca recebe pacote desconhecido. Devolve se enviou.
     */
    boolean sendIfSupported(net.minecraft.server.level.ServerPlayer player,
                            net.minecraft.network.protocol.common.custom.CustomPacketPayload payload);

    /**
     * A "tomada padrão" de itens do bloco (Item 26): no NeoForge a capability de itens, no Fabric a Transfer API, sem
     * lado (o inventário inteiro). {@code null} se o bloco não oferece nenhuma. Só "cola": quem decide se o bloco é
     * armazenamento é {@code NearbyContainers}.
     */
    @Nullable ModStorage modStorageAt(ServerLevel level, BlockPos pos, BlockState state, BlockEntity blockEntity);

    /** Pasta de configuração do loader (onde fica {@code stashlink.json}). */
    java.nio.file.Path getConfigDir();

    /**
     * Gets the name of the environment type as a string.
     *
     * @return The name of the environment type.
     */
    default String getEnvironmentName() {

        return isDevelopmentEnvironment() ? "development" : "production";
    }
}