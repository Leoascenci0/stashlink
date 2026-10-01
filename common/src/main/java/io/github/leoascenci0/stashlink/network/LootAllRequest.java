package io.github.leoascenci0.stashlink.network;

import io.github.leoascenci0.stashlink.compat.mc.McCompat;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Pedido do cliente: "traga para o meu inventário tudo o que couber do container aberto" (tecla W). Sem dados:
 * o servidor olha o container que <i>ele</i> sabe que o jogador tem aberto, então o cliente não consegue apontar
 * para outro baú nem pedir uma quantidade.
 */
public record LootAllRequest() implements CustomPacketPayload {
    public static final LootAllRequest INSTANCE = new LootAllRequest();

    public static final CustomPacketPayload.Type<LootAllRequest> TYPE =
            McCompat.payloadType("loot_all");

    public static final StreamCodec<RegistryFriendlyByteBuf, LootAllRequest> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public CustomPacketPayload.Type<LootAllRequest> type() {
        return TYPE;
    }
}
