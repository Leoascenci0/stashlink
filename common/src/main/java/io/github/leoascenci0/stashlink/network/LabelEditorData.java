package io.github.leoascenci0.stashlink.network;

import io.github.leoascenci0.stashlink.compat.mc.McCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Servidor: "abra o editor de rótulo deste bloco; o texto atual é este". Só vai a quem tem o mod. */
public record LabelEditorData(BlockPos pos, String name, String note) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<LabelEditorData> TYPE = McCompat.payloadType("label_editor");

    public static final StreamCodec<RegistryFriendlyByteBuf, LabelEditorData> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, LabelEditorData::pos,
            ByteBufCodecs.stringUtf8(256), LabelEditorData::name,
            ByteBufCodecs.stringUtf8(256), LabelEditorData::note,
            LabelEditorData::new);

    @Override
    public CustomPacketPayload.Type<LabelEditorData> type() {
        return TYPE;
    }
}
