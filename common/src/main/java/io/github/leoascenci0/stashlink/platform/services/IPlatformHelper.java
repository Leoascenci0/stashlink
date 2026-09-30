package io.github.leoascenci0.stashlink.platform.services;

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
     * Gets the name of the environment type as a string.
     *
     * @return The name of the environment type.
     */
    default String getEnvironmentName() {

        return isDevelopmentEnvironment() ? "development" : "production";
    }
}