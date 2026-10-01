package io.github.leoascenci0.stashlink.network;

import io.github.leoascenci0.stashlink.compat.mc.McCompat;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Pedido do cliente: "guarde meus itens nos baús próximos" (tecla N). Não carrega dado nenhum: o servidor
 * decide sozinho quais slots, quais containers e quanto — o cliente não tem como pedir mais do que a tecla dá.
 */
public record QuickStackRequest() implements CustomPacketPayload {
    public static final QuickStackRequest INSTANCE = new QuickStackRequest();

    public static final CustomPacketPayload.Type<QuickStackRequest> TYPE =
            McCompat.payloadType("quick_stack");

    public static final StreamCodec<RegistryFriendlyByteBuf, QuickStackRequest> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public CustomPacketPayload.Type<QuickStackRequest> type() {
        return TYPE;
    }
}
