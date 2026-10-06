package io.github.leoascenci0.stashlink.source;

import io.github.leoascenci0.stashlink.compat.mc.StorageCompat;
import io.github.leoascenci0.stashlink.config.Feature;
import io.github.leoascenci0.stashlink.config.FeatureGate;
import io.github.leoascenci0.stashlink.config.PlayerPrefsStore;
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
 * <p><b>Outros mods (Item 26):</b> na mesma passada, blocos da tag {@code stashlink:mod_storage}
 * ({@link StorageCompat}) viram {@link ModStorageSource.Entry}, lidos pela "tomada de itens" do loader. Baú, barril e
 * shulker do jogo (e baús que estendem o do jogo, como Iron Chests) continuam só pelo caminho de sempre, então nunca
 * contam duas vezes. Seguem o raio dos baús e o ajuste "usar baús como fonte", e só com a função {@code MOD_STORAGE}.
 *
 * <p>Nunca força carregar chunk: chunk descarregado simplesmente não conta.
 */
public final class NearbyContainers {
    private NearbyContainers() {
    }

    /** Containers achados, mais próximos primeiro; {@code modStorage} são os blocos de outros mods (Item 26). */
    public record Found(List<ContainerSource.Entry> shulkers, List<ContainerSource.Entry> storage,
                        List<ModStorageSource.Entry> modStorage) {
    }

    /** Para guardar itens (tecla N): os containers do jogo todos juntos e os blocos de outros mods. */
    public record Stash(List<ContainerSource.Entry> containers, List<ModStorageSource.Entry> modStorage) {
    }

    /** Para o reabastecimento: baús e barris só entram se a config ({@code includeChests}) mandar. */
    public static Found find(ServerPlayer player) {
        return find(player, PlayerPrefsStore.includeChests(player));
    }

    /** Igual a {@link #find(ServerPlayer)}, mas quem chama decide se baús e barris entram (a bancada, Item 16). */
    public static Found find(ServerPlayer player, boolean chests) {
        return find(player, chests, chests && FeatureGate.allowSilently(player, Feature.MOD_STORAGE));
    }

    /**
     * Quem chama decide também se os blocos de outros mods entram. A devolução usa {@code true}: o item volta a quem
     * o emprestou mesmo que a função tenha sido desligada no meio.
     */
    public static Found find(ServerPlayer player, boolean chests, boolean mods) {
        List<Hit> shulkers = new ArrayList<>();
        List<Hit> storage = new ArrayList<>();
        List<ModHit> modded = new ArrayList<>();
        collect(player, chests, shulkers, storage, mods ? modded : null);
        return new Found(entries(player, shulkers), entries(player, storage), modEntries(player, modded));
    }

    /** Para guardar itens (tecla N): shulkers colocadas, baús e barris, todos juntos, do mais perto ao mais longe. */
    public static List<ContainerSource.Entry> findAllStorage(ServerPlayer player) {
        List<Hit> all = new ArrayList<>();
        collect(player, true, all, all, null);
        return entries(player, all);
    }

    /** Como {@link #findAllStorage}, numa passada só, e mais os blocos de outros mods (se a função estiver ligada). */
    public static Stash findStash(ServerPlayer player) {
        List<Hit> all = new ArrayList<>();
        List<ModHit> modded = new ArrayList<>();
        boolean mods = FeatureGate.allowSilently(player, Feature.MOD_STORAGE);
        collect(player, true, all, all, mods ? modded : null);
        return new Stash(entries(player, all), modEntries(player, modded));
    }

    private static void collect(ServerPlayer player, boolean chests, List<Hit> shulkers, List<Hit> storage,
                                List<ModHit> modded) {
        // Baús e barris têm um raio; shulkers colocadas, outro (maior). Varre os chunks do maior e confere cada tipo.
        int chestRadius = PlayerPrefsStore.radius(player);
        int shulkerRadius = PlayerPrefsStore.shulkerRadius(player);
        int radius = Math.max(chestRadius, shulkerRadius);
        if (radius <= 0 || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos center = player.blockPosition();
        double maxSq = (double) chestRadius * chestRadius;
        double shulkerMaxSq = (double) shulkerRadius * shulkerRadius;
        Set<BlockPos> seenHalves = new HashSet<>();
        for (int cx = (center.getX() - radius) >> 4; cx <= (center.getX() + radius) >> 4; cx++) {
            for (int cz = (center.getZ() - radius) >> 4; cz <= (center.getZ() + radius) >> 4; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be.isRemoved()) {
                        continue;
                    }
                    if (be instanceof ShulkerBoxBlockEntity box) {
                        double distSq = distSq(player, box.getBlockPos());
                        if (usable(box) && distSq <= shulkerMaxSq) {
                            shulkers.add(Hit.single(box, box.getBlockPos(), distSq));
                        }
                    } else if (be instanceof BarrelBlockEntity box) {
                        double distSq = distSq(player, box.getBlockPos());
                        if (chests && usable(box) && distSq <= maxSq) {
                            storage.add(Hit.single(box, box.getBlockPos(), distSq));
                        }
                    } else if (be instanceof ChestBlockEntity chest && chest.getBlockState().getBlock() instanceof ChestBlock) {
                        if (chests && usable(chest)) {
                            chestHit(player, level, chest, maxSq, seenHalves, storage);
                        }
                    } else if (modded != null && StorageCompat.isModStorageBlock(be)
                            && (!(be instanceof RandomizableContainerBlockEntity box) || usable(box))) {
                        double distSq = distSq(player, be.getBlockPos());
                        if (distSq <= maxSq) {
                            modded.add(new ModHit(be, distSq));
                        }
                    }
                }
            }
        }
    }

    /** Baú simples vira um Hit; baú duplo vira um Hit só (a outra metade é ignorada quando aparecer). */
    private static void chestHit(ServerPlayer player, ServerLevel level, ChestBlockEntity chest, double maxSq,
                                 Set<BlockPos> seenHalves, List<Hit> out) {
        BlockPos pos = chest.getBlockPos();
        Set<BlockPos> where = Set.of(pos);
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
            // A outra metade pode estar num chunk que não está carregado: aí o baú fica de fora (ler a block entity
            // ou o bloco de lá carregaria o chunk à força).
            if (level.getChunkSource().getChunkNow(otherPos.getX() >> 4, otherPos.getZ() >> 4) == null) {
                return;
            }
            // A metade que não veio no mapa deste chunk pode estar em outro chunk; também precisa estar liberada.
            if (!(level.getBlockEntity(otherPos) instanceof ChestBlockEntity other) || !usable(other)) {
                return;
            }
            seenHalves.add(pos);
            seenHalves.add(otherPos);
            where = Set.of(pos, otherPos);
            distSq = Math.min(distSq, distSq(player, otherPos));
        }
        if (distSq > maxSq) {
            return;
        }
        // "true" = ignora o bloco por cima do baú: aqui queremos o conteúdo, não abrir a GUI.
        Container container = ChestBlock.getContainer(block, state, level, pos, true);
        if (container != null) {
            out.add(new Hit(container, pos, distSq, where));
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

    private record Hit(Container container, BlockPos pos, double distSq, Set<BlockPos> where) {
        static Hit single(Container container, BlockPos pos, double distSq) {
            return new Hit(container, pos, distSq, Set.of(pos));
        }
    }

    private record ModHit(BlockEntity be, double distSq) {
    }

    private static List<ContainerSource.Entry> entries(ServerPlayer player, List<Hit> hits) {
        hits.sort(Comparator.comparingDouble(Hit::distSq));
        List<ContainerSource.Entry> out = new ArrayList<>(hits.size());
        for (Hit hit : hits) {
            BlockPos pos = hit.pos();
            // A permissão (claims etc.) é perguntada só se este container tiver o item — ver ContainerSource.
            out.add(new ContainerSource.Entry(hit.container(), () -> Services.PLATFORM.canPlayerUseBlock(player, pos), hit.where()));
        }
        return out;
    }

    /** Pergunta ao loader a "tomada de itens" de cada bloco achado; quem não oferece nenhuma fica de fora. */
    private static List<ModStorageSource.Entry> modEntries(ServerPlayer player, List<ModHit> hits) {
        if (hits.isEmpty() || !(player.level() instanceof ServerLevel level)) {
            return List.of();
        }
        hits.sort(Comparator.comparingDouble(ModHit::distSq));
        List<ModStorageSource.Entry> out = new ArrayList<>(hits.size());
        for (ModHit hit : hits) {
            BlockPos pos = hit.be().getBlockPos();
            ModStorage storage;
            try {
                storage = Services.PLATFORM.modStorageAt(level, pos, hit.be().getBlockState(), hit.be());
            } catch (RuntimeException e) {
                continue;   // mod com defeito: o bloco simplesmente não conta
            }
            if (storage != null) {
                out.add(new ModStorageSource.Entry(storage, () -> Services.PLATFORM.canPlayerUseBlock(player, pos), Set.of(pos)));
            }
        }
        return out;
    }
}
