package io.github.leoascenci0.stashlink.organize;

import io.github.leoascenci0.stashlink.Constants;
import io.github.leoascenci0.stashlink.compat.mc.OrganizeCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Destaque de baú no mundo (Item 20.3): um contorno brilhante, visível através de paredes, que dura alguns segundos e
 * <b>só o jogador que buscou vê</b>. É uma entidade de bloco que o servidor cria e apaga; nunca é gravada no disco
 * ({@code EntityMixin}), então não sobra destaque órfão num crash. Cada novo destaque do mesmo jogador substitui o anterior.
 */
public final class OrganizeHighlight {
    /** Quanto o contorno dura (15 s). */
    public static final int DURATION_TICKS = 300;
    /** Teto de contornos por jogador (a busca pode achar muitos baús). */
    public static final int MAX = 8;

    private record Shown(Entity entity, ServerLevel level, long expires) {
    }

    private static final Map<ServerPlayer, List<Shown>> SHOWN = new WeakHashMap<>();

    private OrganizeHighlight() {
    }

    /** Destaca estes containers (cada um = o conjunto de posições dele) para o jogador, trocando o destaque anterior. */
    public static int show(ServerPlayer player, List<Set<BlockPos>> containers) {
        clear(player);
        if (!(player.level() instanceof ServerLevel level)) {
            return 0;
        }
        List<Shown> list = new ArrayList<>();
        for (Set<BlockPos> where : containers) {
            if (list.size() >= MAX) {
                break;
            }
            if (where.isEmpty()) {
                continue;
            }
            int x0 = Integer.MAX_VALUE;
            int y0 = Integer.MAX_VALUE;
            int z0 = Integer.MAX_VALUE;
            int x1 = Integer.MIN_VALUE;
            int y1 = Integer.MIN_VALUE;
            int z1 = Integer.MIN_VALUE;
            for (BlockPos pos : where) {
                x0 = Math.min(x0, pos.getX());
                y0 = Math.min(y0, pos.getY());
                z0 = Math.min(z0, pos.getZ());
                x1 = Math.max(x1, pos.getX());
                y1 = Math.max(y1, pos.getY());
                z1 = Math.max(z1, pos.getZ());
            }
            Entity entity = OrganizeCompat.spawnHighlight(level, new BlockPos(x0, y0, z0), new BlockPos(x1, y1, z1), player);
            if (entity != null) {
                list.add(new Shown(entity, level, level.getGameTime() + DURATION_TICKS));
            }
        }
        if (!list.isEmpty()) {
            SHOWN.put(player, list);
        }
        return list.size();
    }

    /** Apaga os destaques do jogador. */
    public static void clear(ServerPlayer player) {
        List<Shown> list = SHOWN.remove(player);
        if (list != null) {
            list.forEach(shown -> shown.entity().discard());
        }
    }

    /** Quantos destaques este jogador tem agora (para os testes). */
    public static int count(ServerPlayer player) {
        List<Shown> list = SHOWN.get(player);
        return list == null ? 0 : list.size();
    }

    /** Roda a cada tick do servidor: apaga o que venceu e o que ficou sem dono. */
    public static void tick(MinecraftServer server) {
        if (SHOWN.isEmpty()) {
            return;
        }
        try {
            List<ServerPlayer> owners = new ArrayList<>(SHOWN.keySet());
            for (ServerPlayer owner : owners) {
                List<Shown> list = SHOWN.get(owner);
                if (list == null) {
                    continue;
                }
                boolean gone = owner.isRemoved() || !server.getPlayerList().getPlayers().contains(owner);
                list.removeIf(shown -> {
                    boolean done = gone || shown.entity().isRemoved() || shown.level().getGameTime() >= shown.expires();
                    if (done) {
                        shown.entity().discard();
                    }
                    return done;
                });
                if (list.isEmpty()) {
                    SHOWN.remove(owner);
                }
            }
        } catch (RuntimeException e) {
            Constants.LOG.error("Falha ao atualizar destaques de baú", e);
        }
    }
}
