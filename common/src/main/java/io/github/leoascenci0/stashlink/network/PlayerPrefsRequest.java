package io.github.leoascenci0.stashlink.network;

import io.github.leoascenci0.stashlink.compat.mc.McCompat;
import io.github.leoascenci0.stashlink.config.PlayerPrefs;
import io.github.leoascenci0.stashlink.config.StashLinkConfig;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Pedido do cliente: "estas são as minhas preferências". Só preferências pessoais; o servidor corrige e limita
 * tudo ({@link PlayerPrefs#sanitized()}), então o cliente não consegue pedir mais do que o servidor permite.
 */
public record PlayerPrefsRequest(PlayerPrefs prefs) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<PlayerPrefsRequest> TYPE =
            McCompat.payloadType("player_prefs");

    // Raio e "chests" viajam como VAR_INT; -1 (UNSET) vira int grande em VAR_INT, então somamos 1 na ida.
    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerPrefsRequest> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, r -> r.prefs().radius() + 1,
            ByteBufCodecs.VAR_INT, r -> r.prefs().chests() + 1,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(StashLinkConfig.INVENTORY_SLOTS)), r -> r.prefs().lockedSlots(),
            (radius, chests, slots) -> new PlayerPrefsRequest(new PlayerPrefs(radius - 1, chests - 1, slots)));

    @Override
    public CustomPacketPayload.Type<PlayerPrefsRequest> type() {
        return TYPE;
    }
}
