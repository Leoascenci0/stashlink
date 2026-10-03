package io.github.leoascenci0.stashlink.network;

import io.github.leoascenci0.stashlink.compat.mc.McCompat;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Pedido do cliente: "o container aberto (menu {@code containerId}) passa a {@code receives} itens com a tecla N"
 * (botão na tela do baú, Item 17). O servidor revalida tudo; isto só diz qual menu e o valor desejado.
 */
public record ReceivesRequest(int containerId, boolean receives) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ReceivesRequest> TYPE = McCompat.payloadType("receives");

    public static final StreamCodec<RegistryFriendlyByteBuf, ReceivesRequest> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ReceivesRequest::containerId,
            ByteBufCodecs.BOOL, ReceivesRequest::receives,
            ReceivesRequest::new);

    @Override
    public CustomPacketPayload.Type<ReceivesRequest> type() {
        return TYPE;
    }
}
