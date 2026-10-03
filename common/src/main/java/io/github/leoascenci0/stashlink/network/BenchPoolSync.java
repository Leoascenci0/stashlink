package io.github.leoascenci0.stashlink.network;

import io.github.leoascenci0.stashlink.compat.mc.McCompat;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Servidor → cliente com o mod: "este é o armazenamento que a estação {@code containerId} enxerga". Alimenta o
 * painel "Armazenamento" e acende o livro de receitas. Só enviado a quem tem o mod. É só uma lista para
 * mostrar: o cliente nunca tira item por ela, só <i>pede</i> (e o servidor confere de novo).
 */
public record BenchPoolSync(int containerId, List<Entry> entries) implements CustomPacketPayload {
    /** Um tipo de item (ícone, quantidade 1) e quanto há. */
    public record Entry(ItemStack item, int count) {
        @Override
        public boolean equals(Object o) {
            return o instanceof Entry e && e.count == count && ItemStack.isSameItemSameComponents(e.item, item);
        }

        @Override
        public int hashCode() {
            return 31 * count + item.getItem().hashCode();
        }
    }

    /** Teto de tipos de item por pacote (o resto fica de fora; a lista vai em ordem alfabética do item). */
    public static final int MAX_ENTRIES = 512;

    public static final CustomPacketPayload.Type<BenchPoolSync> TYPE = McCompat.payloadType("bench_pool");

    private static final StreamCodec<RegistryFriendlyByteBuf, Entry> ENTRY_CODEC = StreamCodec.composite(
            ItemStack.STREAM_CODEC, Entry::item,
            ByteBufCodecs.VAR_INT, Entry::count,
            Entry::new);

    public static final StreamCodec<RegistryFriendlyByteBuf, BenchPoolSync> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, BenchPoolSync::containerId,
            ENTRY_CODEC.apply(ByteBufCodecs.list(MAX_ENTRIES)), BenchPoolSync::entries,
            BenchPoolSync::new);

    @Override
    public CustomPacketPayload.Type<BenchPoolSync> type() {
        return TYPE;
    }
}
