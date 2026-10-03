package io.github.leoascenci0.stashlink.label;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Ler e gravar o rótulo de um bloco, escondendo as duas formas de guardar: na block entity (baú, barril,
 * shulker; vai e some com o bloco) ou por posição no mundo (baú do End). Baú duplo = uma coisa só: as duas
 * metades recebem o mesmo rótulo, e só a metade "âncora" (a de menor posição) mostra o holograma.
 */
public final class Labels {
    private Labels() {
    }

    /** Este bloco pode ter rótulo? */
    public static boolean supports(BlockEntity be) {
        return be instanceof LabelHolder || be instanceof EnderChestBlockEntity;
    }

    public static Label get(BlockEntity be) {
        if (be instanceof LabelHolder holder) {
            return holder.stashlink$label();
        }
        if (be instanceof EnderChestBlockEntity && be.getLevel() instanceof ServerLevel level) {
            return EnderLabels.of(level).get(level, be.getBlockPos());
        }
        return Label.EMPTY;
    }

    /** O bloco e, se for baú duplo, a outra metade. */
    public static List<BlockEntity> group(BlockEntity be) {
        List<BlockEntity> out = new ArrayList<>();
        out.add(be);
        BlockPos other = partner(be);
        if (other != null && be.getLevel() != null) {
            BlockEntity second = be.getLevel().getBlockEntity(other);
            if (second != null && second.getType() == be.getType()) {
                out.add(second);
            }
        }
        return out;
    }

    private static BlockPos partner(BlockEntity be) {
        BlockState state = be.getBlockState();
        if (state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST)) {
            if (state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
                return be.getBlockPos().relative(ChestBlock.getConnectedDirection(state));
            }
        }
        return null;
    }

    /** Grava (já limpo) o rótulo em todas as metades e avisa o holograma. Vazio = tira o rótulo. */
    public static void set(BlockEntity be, Label label) {
        Label clean = label.sanitized();
        for (BlockEntity part : group(be)) {
            if (part instanceof LabelHolder holder) {
                holder.stashlink$setLabel(clean);
                part.setChanged();
            } else if (part instanceof EnderChestBlockEntity && part.getLevel() instanceof ServerLevel level) {
                EnderLabels.of(level).set(level, part.getBlockPos(), clean);
            }
            HologramService.track(part);
        }
    }

    /** Dono do holograma: a metade de menor posição. */
    public static boolean isAnchor(BlockEntity be) {
        BlockPos other = partner(be);
        return other == null || be.getBlockPos().asLong() < other.asLong();
    }

    /** Onde o texto flutua: acima do bloco (ou do meio do baú duplo). */
    public static Vec3 hologramPos(BlockEntity be) {
        BlockPos pos = be.getBlockPos();
        BlockPos other = partner(be);
        double x = pos.getX() + 0.5;
        double z = pos.getZ() + 0.5;
        if (other != null) {
            x = (x + other.getX() + 0.5) / 2;
            z = (z + other.getZ() + 0.5) / 2;
        }
        return new Vec3(x, pos.getY() + 1.35, z);
    }

    /** Atalho para os testes e o serviço: o bloco em {@code pos} (carregado) como block entity. */
    public static BlockEntity at(Level level, BlockPos pos) {
        return level.isLoaded(pos) ? level.getBlockEntity(pos) : null;
    }
}
