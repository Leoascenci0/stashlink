package io.github.leoascenci0.stashlink.network;

import io.github.leoascenci0.stashlink.compat.mc.McCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Cliente: "grave este rótulo neste bloco". O texto é limitado a 256 caracteres no próprio pacote (o servidor
 * ainda limpa e corta para 32/64) e o servidor revalida alcance, permissão e tipo de bloco.
 */
public record SetLabelRequest(BlockPos pos, String name, String note) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SetLabelRequest> TYPE = McCompat.payloadType("label_set");

    public static final StreamCodec<RegistryFriendlyByteBuf, SetLabelRequest> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SetLabelRequest::pos,
            ByteBufCodecs.stringUtf8(256), SetLabelRequest::name,
            ByteBufCodecs.stringUtf8(256), SetLabelRequest::note,
            SetLabelRequest::new);

    @Override
    public CustomPacketPayload.Type<SetLabelRequest> type() {
        return TYPE;
    }
}
