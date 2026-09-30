package io.github.leoascenci0.stashlink.network;

import io.github.leoascenci0.stashlink.Constants;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

/**
 * Pedido do cliente: "preciso deste item na hotbar" (usado pela integração com o Litematica). Só carrega o
 * tipo do item e a quantidade; o servidor decide de onde tirar, quanto cabe e se é permitido.
 */
public record PullItemRequest(Item item, int count) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<PullItemRequest> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "pull_item"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PullItemRequest> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.registry(Registries.ITEM), PullItemRequest::item,
            ByteBufCodecs.VAR_INT, PullItemRequest::count,
            PullItemRequest::new);

    @Override
    public CustomPacketPayload.Type<PullItemRequest> type() {
        return TYPE;
    }
}
