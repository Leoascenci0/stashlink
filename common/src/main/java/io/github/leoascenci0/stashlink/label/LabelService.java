package io.github.leoascenci0.stashlink.label;

import io.github.leoascenci0.stashlink.Constants;
import io.github.leoascenci0.stashlink.config.Feature;
import io.github.leoascenci0.stashlink.config.FeatureGate;
import io.github.leoascenci0.stashlink.network.LabelEditRequest;
import io.github.leoascenci0.stashlink.network.LabelEditorData;
import io.github.leoascenci0.stashlink.network.SetLabelRequest;
import io.github.leoascenci0.stashlink.platform.Services;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Pedidos do cliente sobre rótulos. O servidor confere tudo: jogador vivo e não espectador, bloco ao alcance
 * e carregado, tipo de bloco que aceita rótulo, e permissão dos mods de proteção para cada metade. O texto é
 * sempre limpo e cortado aqui ({@link LabelText#sanitize}); nada do que o cliente manda vai direto para o mundo.
 * Mexe só em metadado: nenhum item muda de lugar (por isso não exige o container fechado para os outros).
 */
public final class LabelService {
    /** Alcance extra além do alcance normal de blocos (mesmo critério de abrir o baú). */
    private static final double REACH_SLACK = 1.0;

    private LabelService() {
    }

    public static void handleEdit(ServerPlayer player, LabelEditRequest request) {
        try {
            if (!FeatureGate.allow(player, Feature.LABEL)) {
                return;
            }
            BlockEntity be = target(player, request.pos());
            if (be != null) {
                Label label = Labels.get(be);
                Services.PLATFORM.sendIfSupported(player, new LabelEditorData(request.pos(), label.name(), label.note()));
            }
        } catch (RuntimeException e) {
            Constants.LOG.error("Falha ao abrir o editor de rótulo de {}", player.getGameProfile().name(), e);
        }
    }

    public static void handleSet(ServerPlayer player, SetLabelRequest request) {
        try {
            apply(player, request.pos(), request.name(), request.note());
        } catch (RuntimeException e) {
            Constants.LOG.error("Falha ao gravar rótulo de {}", player.getGameProfile().name(), e);
        }
    }

    /** Valida e grava. Devolve se gravou (para os testes). */
    public static boolean apply(ServerPlayer player, BlockPos pos, String rawName, String rawNote) {
        if (!FeatureGate.allow(player, Feature.LABEL)) {
            return false;
        }
        BlockEntity be = target(player, pos);
        if (be == null) {
            return false;
        }
        Label label = new Label(rawName, rawNote).sanitized();
        Labels.set(be, label);
        player.sendOverlayMessage(label.isEmpty()
                ? Component.translatableWithFallback("stashlink.label.cleared", "Label removed")
                : Component.translatableWithFallback("stashlink.label.saved", "Label saved"));
        return true;
    }

    /** O bloco que o jogador pode mesmo rotular, ou {@code null}. */
    private static BlockEntity target(ServerPlayer player, BlockPos pos) {
        if (!player.isAlive() || player.isSpectator() || !(player.level() instanceof ServerLevel level)
                || !player.isWithinBlockInteractionRange(pos, REACH_SLACK)) {
            return null;
        }
        BlockEntity be = Labels.at(level, pos);
        if (be == null || !Labels.supports(be)) {
            return null;
        }
        for (BlockEntity part : Labels.group(be)) {
            if (!Services.PLATFORM.canPlayerUseBlock(player, part.getBlockPos())) {
                return null;
            }
        }
        return be;
    }
}
