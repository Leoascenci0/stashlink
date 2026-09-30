package io.github.leoascenci0.stashlink.source;

import io.github.leoascenci0.stashlink.config.StashLinkConfig;
import io.github.leoascenci0.stashlink.platform.Services;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
 * <p><b>Baú duplo:</b> são duas block entities (uma por metade), mas para o jogador é um baú só. Aqui as duas
 * metades viram <b>um</b> {@link net.minecraft.world.CompoundContainer} (54 slots), que é o mesmo objeto que o
 * jogo usa ao abrir o baú duplo.
 *
 * <p>Nunca força carregar chunk: chunk descarregado simplesmente não conta.
 */
public final class NearbyContainers {
    private NearbyContainers() {
    }

    /** Containers achados, mais próximos primeiro. */
    public record Found(List<ContainerSource.Entry> shulkers, List<ContainerSource.Entry> storage) {
    }

    /** Para o reabastecimento: baús e barris só entram se a config ({@code includeChests}) mandar. */
    public static Found find(ServerPlayer player) {
        List<Hit> shulkers = new ArrayList<>();
        List<Hit> storage = new ArrayList<>();
        collect(player, StashLinkConfig.includeChests, shulkers, storage);
        return new Found(entries(player, shulkers), entries(player, storage));
    }

    /** Para guardar itens (tecla N): shulkers colocadas, baús e barris, todos juntos, do mais perto ao mais longe. */
    public static List<ContainerSource.Entry> findAllStorage(ServerPlayer player) {
        List<Hit> all = new ArrayList<>();
        collect(player, true, all, all);
        return entries(player, all);
    }

    private static void collect(ServerPlayer player, boolean chests, List<Hit> shulkers, List<Hit> storage) {
        int radius = StashLinkConfig.effectiveRadius();
        if (radius <= 0 || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos center = player.blockPosition();
        double maxSq = (double) radius * radius;
        Set<BlockPos> seenHalves = new HashSet<>();
        for (int cx = (center.getX() - radius) >> 4; cx <= (center.getX() + radius) >> 4; cx++) {
            for (int cz = (center.getZ() - radius) >> 4; cz <= (center.getZ() + radius) >> 4; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be.isRemoved() || !(be instanceof RandomizableContainerBlockEntity box) || !usable(box)) {
                        continue;
                    }
                    if (be instanceof ShulkerBoxBlockEntity) {
                        double distSq = distSq(player, box.getBlockPos());
                        if (distSq <= maxSq) {
                            shulkers.add(new Hit(box, box.getBlockPos(), distSq));
                        }
                    } else if (chests && be instanceof BarrelBlockEntity) {
                        double distSq = distSq(player, box.getBlockPos());
                        if (distSq <= maxSq) {
                            storage.add(new Hit(box, box.getBlockPos(), distSq));
                        }
                    } else if (chests && be instanceof ChestBlockEntity chest) {
                        chestHit(player, level, chest, maxSq, seenHalves, storage);
                    }
                }
            }
        }
    }

    /** Baú simples vira um Hit; baú duplo vira um Hit só (a outra metade é ignorada quando aparecer). */
    private static void chestHit(ServerPlayer player, ServerLevel level, ChestBlockEntity chest, double maxSq,
                                 Set<BlockPos> seenHalves, List<Hit> out) {
        BlockPos pos = chest.getBlockPos();
        if (seenHalves.contains(pos)) {
            return;
        }
        BlockState state = chest.getBlockState();
        if (!(state.getBlock() instanceof ChestBlock block)) {
            return;
        }
        double distSq = distSq(player, pos);
        if (state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            BlockPos otherPos = ChestBlock.getConnectedBlockPos(pos, state);
            // A metade que não veio no mapa deste chunk pode estar em outro chunk; também precisa estar liberada.
            if (!(level.getBlockEntity(otherPos) instanceof ChestBlockEntity other) || !usable(other)) {
                return;
            }
            seenHalves.add(pos);
            seenHalves.add(otherPos);
            distSq = Math.min(distSq, distSq(player, otherPos));
        }
        if (distSq > maxSq) {
            return;
        }
        // "true" = ignora o bloco por cima do baú: aqui queremos o conteúdo, não abrir a GUI.
        Container container = ChestBlock.getContainer(block, state, level, pos, true);
        if (container != null) {
            out.add(new Hit(container, pos, distSq));
        }
    }

    private static double distSq(ServerPlayer player, BlockPos pos) {
        return player.distanceToSqr(Vec3.atCenterOf(pos));
    }

    /**
     * Regras fixas do próprio jogo: trancado (chave) e baú de loot ainda não aberto ficam de fora. Ler um baú
     * de loot geraria o loot na hora — daria para "abrir" masmorras à distância.
     */
    private static boolean usable(RandomizableContainerBlockEntity box) {
        return !box.isLocked() && box.getLootTable() == null;
    }

    private record Hit(Container container, BlockPos pos, double distSq) {
    }

    private static List<ContainerSource.Entry> entries(ServerPlayer player, List<Hit> hits) {
        hits.sort(Comparator.comparingDouble(Hit::distSq));
        List<ContainerSource.Entry> out = new ArrayList<>(hits.size());
        for (Hit hit : hits) {
            BlockPos pos = hit.pos();
            // A permissão (claims etc.) é perguntada só se este container tiver o item — ver ContainerSource.
            out.add(new ContainerSource.Entry(hit.container(), () -> Services.PLATFORM.canPlayerUseBlock(player, pos)));
        }
        return out;
    }
}
