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
    /**
     * Um tipo de item (ícone, quantidade 1) e quanto há. Nas estações com receita ({@code id >= 0}, ex.: cortador de
     * pedra) é um <b>resultado</b>: {@code item} é o que sai, {@code count} não é usado e {@code missing} diz que falta
     * material (o painel pinta de vermelho, como o livro de receitas).
     */
    public record Entry(ItemStack item, int count, int id, boolean missing, int tab, int color) {
        /** {@code id} de uma entrada que só escolhe uma cor de corante no painel (tear): não pede nada ao servidor. */
        public static final int COLOR_PICK = -3;

        public Entry(ItemStack item, int count) {
            this(item, count, -1, false, 0, -1);
        }

        public Entry(ItemStack item, int count, int id, boolean missing) {
            this(item, count, id, missing, 0, -1);
        }

        public boolean isColorPick() {
            return id == COLOR_PICK;
        }

        public boolean isResult() {
            return id >= 0;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Entry e && e.count == count && e.id == id && e.missing == missing && e.tab == tab
                    && e.color == color && ItemStack.isSameItemSameComponents(e.item, item);
        }

        @Override
        public int hashCode() {
            return 31 * (31 * count + id) + item.getItem().hashCode();
        }
    }

    /** Teto de tipos de item por pacote (o resto fica de fora; a lista vai em ordem alfabética do item). */
    public static final int MAX_ENTRIES = 512;

    /**
     * Teto de bytes dos itens por pacote. O protocolo aceita 1 MiB por pacote do servidor ao cliente e passar disso
     * derruba o cliente (shulkers cheias levam o conteúdo junto); o resto fica de fora, como no teto de tipos.
     */
    public static final int MAX_BYTES = 700_000;

    public static final CustomPacketPayload.Type<BenchPoolSync> TYPE = McCompat.payloadType("bench_pool");

    private static final StreamCodec<RegistryFriendlyByteBuf, Entry> ENTRY_CODEC = StreamCodec.composite(
            ItemStack.STREAM_CODEC, Entry::item,
            ByteBufCodecs.VAR_INT, Entry::count,
            ByteBufCodecs.VAR_INT, Entry::id,
            ByteBufCodecs.BOOL, Entry::missing,
            ByteBufCodecs.VAR_INT, Entry::tab,
            ByteBufCodecs.VAR_INT, Entry::color,
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
