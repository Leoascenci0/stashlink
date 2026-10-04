package io.github.leoascenci0.stashlink.network;

import io.github.leoascenci0.stashlink.compat.mc.McCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Servidor → cliente com o mod (Item 20): a prévia de "organizar o sistema" ou o resultado de uma busca. É só uma lista
 * para <i>mostrar</i>: o cliente nunca mexe em item por ela, só pede (e o servidor confere de novo).
 *
 * <p>{@code kind = PREVIEW}: cada linha é "{@code count} de {@code item}: de {@code from} para {@code to}".
 * {@code kind = SEARCH}: cada linha é "este container ({@code from}) tem {@code count} de {@code item}" ({@code to} igual a
 * {@code from}). {@code kind = STATE}: sem linhas, só atualiza os botões e a mensagem.
 */
public record OrganizeSync(int kind, List<Row> rows, boolean canApply, boolean canUndo, int tidied, int message)
        implements CustomPacketPayload {
    public static final int PREVIEW = 0;
    public static final int SEARCH = 1;
    public static final int STATE = 2;

    /** Mensagens curtas do servidor (o texto é do cliente, no idioma dele). */
    public static final int MSG_NONE = 0;
    public static final int MSG_APPLIED = 1;
    public static final int MSG_UNDONE = 2;
    public static final int MSG_STALE = 3;
    public static final int MSG_NOTHING = 4;
    public static final int MSG_UNDO_FAILED = 5;
    public static final int MSG_TOO_MANY = 6;

    /** Teto de linhas por pacote. */
    public static final int MAX_ROWS = 256;

    /** Um item (contagem 1, só o ícone), quanto e entre quais containers ({@code *Name} = rótulo do baú, pode ser vazio). */
    public record Row(ItemStack item, int count, BlockPos from, BlockPos to, String fromName, String toName) {
    }

    public static final CustomPacketPayload.Type<OrganizeSync> TYPE = McCompat.payloadType("organize_sync");

    private static final StreamCodec<RegistryFriendlyByteBuf, Row> ROW_CODEC = StreamCodec.composite(
            ItemStack.STREAM_CODEC, Row::item,
            ByteBufCodecs.VAR_INT, Row::count,
            BlockPos.STREAM_CODEC, Row::from,
            BlockPos.STREAM_CODEC, Row::to,
            ByteBufCodecs.stringUtf8(64), Row::fromName,
            ByteBufCodecs.stringUtf8(64), Row::toName,
            Row::new);

    public static final StreamCodec<RegistryFriendlyByteBuf, OrganizeSync> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, OrganizeSync::kind,
            ROW_CODEC.apply(ByteBufCodecs.list(MAX_ROWS)), OrganizeSync::rows,
            ByteBufCodecs.BOOL, OrganizeSync::canApply,
            ByteBufCodecs.BOOL, OrganizeSync::canUndo,
            ByteBufCodecs.VAR_INT, OrganizeSync::tidied,
            ByteBufCodecs.VAR_INT, OrganizeSync::message,
            OrganizeSync::new);

    @Override
    public CustomPacketPayload.Type<OrganizeSync> type() {
        return TYPE;
    }
}
