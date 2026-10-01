package io.github.leoascenci0.stashlink.clientmode;

import io.github.leoascenci0.stashlink.compat.mc.ClientCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Acha, no mundo que o <b>cliente</b> conhece, os containers que ele consegue abrir sem sair do lugar. Como no
 * servidor ({@code NearbyContainers}) usamos o mapa de block entities que cada chunk carregado já mantém — sem
 * cache próprio que possa ficar velho e sem varrer o cubo de blocos. A diferença: aqui só vale o que está dentro
 * do alcance normal de interação do jogador (o modo cliente age como um jogador, não como o servidor).
 *
 * <p>Atenção: o cliente <b>não</b> sabe o conteúdo (só chega ao abrir), então nada aqui diz se o container tem
 * o item; só se ele existe e está ao alcance.
 */
public final class ClientContainers {
    private ClientContainers() {
    }

    /**
     * @param includeChests {@code false} = só shulkers colocadas; {@code true} = também baús e barris
     * @param maxDistance   teto extra de distância em blocos (preferência do jogador); {@code <= 0} = só o alcance do jogo
     * @return candidatos ao alcance, em ordem qualquer (quem usa ordena)
     */
    public static List<Candidate> find(Minecraft mc, boolean includeChests, double maxDistance) {
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        List<Candidate> out = new ArrayList<>();
        if (player == null || level == null) {
            return out;
        }
        // Os containers alcançáveis estão a poucos blocos (alcance ~4.5): olhamos só os chunks em volta.
        int scan = (int) Math.ceil(player.blockInteractionRange()) + 1;
        BlockPos center = player.blockPosition();
        double capSq = maxDistance > 0 ? maxDistance * maxDistance : Double.MAX_VALUE;
        Set<BlockPos> seenHalves = new HashSet<>();
        for (int cx = (center.getX() - scan) >> 4; cx <= (center.getX() + scan) >> 4; cx++) {
            for (int cz = (center.getZ() - scan) >> 4; cz <= (center.getZ() + scan) >> 4; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity be : new ArrayList<>(chunk.getBlockEntities().values())) {
                    if (be.isRemoved()) {
                        continue;
                    }
                    BlockPos pos = be.getBlockPos();
                    if (be instanceof ShulkerBoxBlockEntity || (includeChests && be instanceof BarrelBlockEntity)) {
                        add(out, player, pos, null, capSq);
                    } else if (includeChests && be instanceof ChestBlockEntity chest) {
                        BlockPos other = otherHalf(chest.getBlockState(), pos);
                        if (seenHalves.contains(pos)) {
                            continue;
                        }
                        if (other != null) {
                            // A outra metade precisa existir como baú (o mapa do chunk dela pode ser outro).
                            if (!(level.getBlockEntity(other) instanceof ChestBlockEntity)) {
                                continue;
                            }
                            seenHalves.add(pos);
                            seenHalves.add(other);
                        }
                        add(out, player, pos, other, capSq);
                    }
                }
            }
        }
        return out;
    }

    /**
     * Chaves do cache para o container no bloco {@code pos} (as duas metades de um baú duplo), ou {@code null}
     * se ali não há baú/barril/shulker (ex.: ender chest, que é conteúdo do jogador e não do bloco).
     */
    public static long[] keysAt(ClientLevel level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof ShulkerBoxBlockEntity || be instanceof BarrelBlockEntity) {
            return new long[]{pos.asLong()};
        }
        if (be instanceof ChestBlockEntity chest) {
            BlockPos other = otherHalf(chest.getBlockState(), pos);
            return other == null ? new long[]{pos.asLong()} : new long[]{pos.asLong(), other.asLong()};
        }
        return null;
    }

    /** Posição da outra metade se for baú duplo, senão {@code null}. */
    private static BlockPos otherHalf(BlockState state, BlockPos pos) {
        if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            return ChestBlock.getConnectedBlockPos(pos, state);
        }
        return null;
    }

    /** Aceita o container se alguma metade está ao alcance; a metade ao alcance mais perto é onde clicamos. */
    private static void add(List<Candidate> out, LocalPlayer player, BlockPos pos, BlockPos other, double capSq) {
        double d1 = player.distanceToSqr(Vec3.atCenterOf(pos));
        boolean ok1 = d1 <= capSq && ClientCompat.withinReach(player, pos);
        if (other == null) {
            if (ok1) {
                out.add(new Candidate(pos, null, d1));
            }
            return;
        }
        double d2 = player.distanceToSqr(Vec3.atCenterOf(other));
        boolean ok2 = d2 <= capSq && ClientCompat.withinReach(player, other);
        if (ok1 && (!ok2 || d1 <= d2)) {
            out.add(new Candidate(pos, other, d1));
        } else if (ok2) {
            out.add(new Candidate(other, pos, d2));
        }
    }
}
