package io.github.leoascenci0.stashlink.network;

import io.github.leoascenci0.stashlink.compat.mc.McCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Cliente: "quero editar o rótulo do bloco que estou olhando". O servidor responde com {@link LabelEditorData}. */
public record LabelEditRequest(BlockPos pos) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<LabelEditRequest> TYPE = McCompat.payloadType("label_edit");

    public static final StreamCodec<RegistryFriendlyByteBuf, LabelEditRequest> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, LabelEditRequest::pos,
            LabelEditRequest::new);

    @Override
    public CustomPacketPayload.Type<LabelEditRequest> type() {
        return TYPE;
    }
}
