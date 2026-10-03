package io.github.leoascenci0.stashlink.network;

import io.github.leoascenci0.stashlink.compat.mc.McCompat;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

/**
 * Cliente → servidor: "do painel Armazenamento da estação {@code containerId}, ponha este item no meu cursor"
 * (um stack, ou só um se {@code one}). O servidor revalida tudo: estação aberta, função ligada, item de fato no
 * armazenamento, cursor livre ou do mesmo item. O cliente nunca cria nem escolhe de onde sai.
 */
public record BenchPullRequest(int containerId, ItemStack item, boolean one) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BenchPullRequest> TYPE = McCompat.payloadType("bench_pull");

    public static final StreamCodec<RegistryFriendlyByteBuf, BenchPullRequest> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, BenchPullRequest::containerId,
            ItemStack.STREAM_CODEC, BenchPullRequest::item,
            ByteBufCodecs.BOOL, BenchPullRequest::one,
            BenchPullRequest::new);

    @Override
    public CustomPacketPayload.Type<BenchPullRequest> type() {
        return TYPE;
    }
}
