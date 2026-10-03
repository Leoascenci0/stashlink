package io.github.leoascenci0.stashlink.network;

import io.github.leoascenci0.stashlink.compat.mc.McCompat;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Cliente → servidor: "tranque/destranque esta função para todo mundo" (o cadeado da tela de config). O servidor
 * só atende o dono do mundo ou um operador; de qualquer outro jogador o pedido é ignorado.
 *
 * @param featureBit {@link io.github.leoascenci0.stashlink.config.Feature#bit()} da função (um único bit)
 */
public record SetFeatureLockRequest(int featureBit, boolean locked) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SetFeatureLockRequest> TYPE = McCompat.payloadType("set_feature_lock");

    public static final StreamCodec<RegistryFriendlyByteBuf, SetFeatureLockRequest> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SetFeatureLockRequest::featureBit,
            ByteBufCodecs.BOOL, SetFeatureLockRequest::locked,
            SetFeatureLockRequest::new);

    @Override
    public CustomPacketPayload.Type<SetFeatureLockRequest> type() {
        return TYPE;
    }
}
