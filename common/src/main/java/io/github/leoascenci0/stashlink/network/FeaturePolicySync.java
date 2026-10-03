package io.github.leoascenci0.stashlink.network;

import io.github.leoascenci0.stashlink.compat.mc.McCompat;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Servidor → cliente com o mod: "estas funções estão trancadas aqui, e você {@code pode} (ou não) mexer nos
 * cadeados". Só enviado a quem tem o mod. A tela de config usa para desenhar os cadeados; quem decide de verdade
 * é sempre o servidor, a cada pedido.
 */
public record FeaturePolicySync(int lockedMask, boolean canEdit) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<FeaturePolicySync> TYPE = McCompat.payloadType("feature_policy");

    public static final StreamCodec<RegistryFriendlyByteBuf, FeaturePolicySync> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, FeaturePolicySync::lockedMask,
            ByteBufCodecs.BOOL, FeaturePolicySync::canEdit,
            FeaturePolicySync::new);

    @Override
    public CustomPacketPayload.Type<FeaturePolicySync> type() {
        return TYPE;
    }
}
