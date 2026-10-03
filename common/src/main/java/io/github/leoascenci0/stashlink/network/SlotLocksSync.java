package io.github.leoascenci0.stashlink.network;

import io.github.leoascenci0.stashlink.compat.mc.McCompat;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Servidor → cliente com o mod: "estes slots do menu {@code containerId} estão reservados para estes itens".
 * Só enviado a quem tem o mod (cliente vanilla nunca recebe). É só aparência/previsão; nunca cria item.
 */
public record SlotLocksSync(int containerId, List<Entry> entries) implements CustomPacketPayload {
    /** {@code menuSlot} é o índice do slot no menu; {@code item} é só o ícone (quantidade 1). */
    public record Entry(int menuSlot, ItemStack item) {
        @Override
        public boolean equals(Object o) {
            return o instanceof Entry e && e.menuSlot == menuSlot && ItemStack.isSameItem(e.item, item);
        }

        @Override
        public int hashCode() {
            return 31 * menuSlot + item.getItem().hashCode();
        }
    }

    public static final CustomPacketPayload.Type<SlotLocksSync> TYPE = McCompat.payloadType("slot_locks");

    private static final StreamCodec<RegistryFriendlyByteBuf, Entry> ENTRY_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Entry::menuSlot,
            ItemStack.STREAM_CODEC, Entry::item,
            Entry::new);

    public static final StreamCodec<RegistryFriendlyByteBuf, SlotLocksSync> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SlotLocksSync::containerId,
            ENTRY_CODEC.apply(ByteBufCodecs.list(256)), SlotLocksSync::entries,
            SlotLocksSync::new);

    @Override
    public CustomPacketPayload.Type<SlotLocksSync> type() {
        return TYPE;
    }
}
