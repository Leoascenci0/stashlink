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
 * armazenamento, cursor livre ou do mesmo item. Com {@code recipeId >= 0} o pedido é "monte esta receita" (cortador de
 * pedra): o servidor refaz a lista de receitas e põe a entrada no slot ({@code one}: um de cada ingrediente em vez de uma pilha);
 * {@code item} é só o ícone. O cliente nunca cria nem escolhe de onde sai.
 */
public record BenchPullRequest(int containerId, ItemStack item, boolean one, int recipeId) implements CustomPacketPayload {
    public BenchPullRequest(int containerId, ItemStack item, boolean one) {
        this(containerId, item, one, -1);
    }

    public static final CustomPacketPayload.Type<BenchPullRequest> TYPE = McCompat.payloadType("bench_pull");

    public static final StreamCodec<RegistryFriendlyByteBuf, BenchPullRequest> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, BenchPullRequest::containerId,
            ItemStack.STREAM_CODEC, BenchPullRequest::item,
            ByteBufCodecs.BOOL, BenchPullRequest::one,
            ByteBufCodecs.VAR_INT, BenchPullRequest::recipeId,
            BenchPullRequest::new);

    @Override
    public CustomPacketPayload.Type<BenchPullRequest> type() {
        return TYPE;
    }
}
