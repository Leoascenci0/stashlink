package io.github.leoascenci0.stashlink.network;

import io.github.leoascenci0.stashlink.compat.mc.McCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Pedido do cliente (Item 20): organizar o baú aberto, ver/aplicar/desfazer a organização do sistema, buscar item e
 * destacar um baú. O servidor revalida tudo (jogador, função, distância, claims); isto só diz o que se quer.
 *
 * <p>Na busca, o <b>cliente</b> traduz o texto digitado para os itens que casam <i>no idioma dele</i> ("ferro" ->
 * lingote de ferro, pepita...) e manda os ids; o servidor só conhece nomes em inglês. {@code text} vai junto como reserva.
 */
public record OrganizeRequest(int action, int containerId, String text, List<Identifier> items, BlockPos pos)
        implements CustomPacketPayload {
    /** Organizar o container aberto ({@code containerId}). */
    public static final int CHEST = 0;
    /** Montar a prévia do sistema. */
    public static final int PREVIEW = 1;
    /** Aplicar a última prévia. */
    public static final int APPLY = 2;
    /** Desfazer a última aplicação. */
    public static final int UNDO = 3;
    /** Buscar itens ({@code items}/{@code text}). */
    public static final int SEARCH = 4;
    /** Destacar o container em {@code pos}. */
    public static final int HIGHLIGHT = 5;
    /** Destacar todos os containers que a busca ({@code items}/{@code text}) achar (até o teto). */
    public static final int HIGHLIGHT_ALL = 6;

    public static final int MAX_ITEMS = 128;
    public static final int MAX_TEXT = 64;

    public static final CustomPacketPayload.Type<OrganizeRequest> TYPE = McCompat.payloadType("organize");

    public static final StreamCodec<RegistryFriendlyByteBuf, OrganizeRequest> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, OrganizeRequest::action,
            ByteBufCodecs.VAR_INT, OrganizeRequest::containerId,
            ByteBufCodecs.stringUtf8(MAX_TEXT), OrganizeRequest::text,
            Identifier.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ITEMS)), OrganizeRequest::items,
            BlockPos.STREAM_CODEC, OrganizeRequest::pos,
            OrganizeRequest::new);

    public static OrganizeRequest of(int action) {
        return new OrganizeRequest(action, 0, "", List.of(), BlockPos.ZERO);
    }

    @Override
    public CustomPacketPayload.Type<OrganizeRequest> type() {
        return TYPE;
    }
}
