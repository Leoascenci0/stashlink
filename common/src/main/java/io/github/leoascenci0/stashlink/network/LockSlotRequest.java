package io.github.leoascenci0.stashlink.network;

import io.github.leoascenci0.stashlink.compat.mc.McCompat;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Pedido do cliente: "trave/destrave este slot do container aberto" (Alt + clique). Só diz <b>qual menu e qual
 * slot</b>; o servidor decide o item (o do slot ou o do cursor, que ELE conhece) e revalida tudo.
 */
public record LockSlotRequest(int containerId, int menuSlot) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<LockSlotRequest> TYPE = McCompat.payloadType("lock_slot");

    public static final StreamCodec<RegistryFriendlyByteBuf, LockSlotRequest> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, LockSlotRequest::containerId,
            ByteBufCodecs.VAR_INT, LockSlotRequest::menuSlot,
            LockSlotRequest::new);

    @Override
    public CustomPacketPayload.Type<LockSlotRequest> type() {
        return TYPE;
    }
}
