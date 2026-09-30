package io.github.leoascenci0.stashlink.source;

import io.github.leoascenci0.stashlink.config.StashLinkConfig;
import io.github.leoascenci0.stashlink.platform.Services;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Acha os containers colocados perto do jogador (F3).
 *
 * <p><b>Índice:</b> o próprio Minecraft já guarda, em cada chunk carregado, o mapa das block entities dele
 * ({@code LevelChunk.getBlockEntities}) e o mantém em dia quando um bloco é colocado, quebrado ou o chunk
 * carrega/descarrega. Usamos esse mapa em vez de um cache próprio: não existe cache para ficar desatualizado
 * (que seria uma fonte de item duplicado ou "fantasma"), e o custo é só percorrer as block entities — nunca o
 * cubo de blocos. A varredura roda só quando um stack esgota (não a cada tick) e só se as shulkers do
 * inventário não bastaram (ver {@link LazyItemSource}).
 *
 * <p>Nunca força carregar chunk: chunk descarregado simplesmente não conta.
 */
public final class NearbyContainers {
    private NearbyContainers() {
    }

    /** Containers achados, mais próximos primeiro. */
    public record Found(List<ContainerSource.Entry> shulkers, List<ContainerSource.Entry> storage) {
    }

    public static Found find(ServerPlayer player) {
        int radius = StashLinkConfig.effectiveRadius();
        List<Hit> shulkers = new ArrayList<>();
        List<Hit> storage = new ArrayList<>();
        if (radius > 0 && player.level() instanceof ServerLevel level) {
            boolean chests = StashLinkConfig.includeChests;
            BlockPos center = player.blockPosition();
            double maxSq = (double) radius * radius;
            for (int cx = (center.getX() - radius) >> 4; cx <= (center.getX() + radius) >> 4; cx++) {
                for (int cz = (center.getZ() - radius) >> 4; cz <= (center.getZ() + radius) >> 4; cz++) {
                    LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                    if (chunk == null) {
                        continue;
                    }
                    for (BlockEntity be : chunk.getBlockEntities().values()) {
                        boolean isShulker = be instanceof ShulkerBoxBlockEntity;
                        boolean isStorage = chests && (be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity);
                        if (!isShulker && !isStorage) {
                            continue;
                        }
                        RandomizableContainerBlockEntity box = (RandomizableContainerBlockEntity) be;
                        double distSq = player.distanceToSqr(Vec3.atCenterOf(box.getBlockPos()));
                        if (be.isRemoved() || distSq > maxSq || !usable(box)) {
                            continue;
                        }
                        (isShulker ? shulkers : storage).add(new Hit(box, distSq));
                    }
                }
            }
        }
        return new Found(entries(player, shulkers), entries(player, storage));
    }

    /**
     * Regras fixas do próprio jogo: trancado (chave) e baú de loot ainda não aberto ficam de fora. Ler um baú
     * de loot geraria o loot na hora — daria para "abrir" masmorras à distância.
     */
    private static boolean usable(RandomizableContainerBlockEntity box) {
        return !box.isLocked() && box.getLootTable() == null;
    }

    private record Hit(RandomizableContainerBlockEntity box, double distSq) {
    }

    private static List<ContainerSource.Entry> entries(ServerPlayer player, List<Hit> hits) {
        hits.sort(Comparator.comparingDouble(Hit::distSq));
        List<ContainerSource.Entry> out = new ArrayList<>(hits.size());
        for (Hit hit : hits) {
            RandomizableContainerBlockEntity box = hit.box();
            // A permissão (claims etc.) é perguntada só se este container tiver o item — ver ContainerSource.
            out.add(new ContainerSource.Entry(box,
                    () -> Services.PLATFORM.canPlayerUseBlock(player, box.getBlockPos())));
        }
        return out;
    }
}
