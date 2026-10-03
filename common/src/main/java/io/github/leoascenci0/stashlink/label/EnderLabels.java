package io.github.leoascenci0.stashlink.label;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.leoascenci0.stashlink.compat.mc.McCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rótulos dos baús do End. O baú do End não guarda o conteúdo no bloco (é do jogador) e, quebrado, solta o
 * próprio bloco sem dados — então o rótulo fica gravado <b>por posição</b>, no mundo (arquivo
 * {@code data/stashlink/ender_labels.dat}), e sobrevive a quebrar e colocar de novo no mesmo lugar.
 */
public final class EnderLabels extends SavedData {
    /** Uma entrada gravada: dimensão + posição + rótulo. */
    public record Entry(String dimension, BlockPos pos, Label label) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("dimension").forGetter(Entry::dimension),
                BlockPos.CODEC.fieldOf("pos").forGetter(Entry::pos),
                Label.CODEC.fieldOf("label").forGetter(Entry::label)
        ).apply(i, Entry::new));
    }

    private static final Codec<EnderLabels> CODEC = RecordCodecBuilder.create(i -> i.group(
            Entry.CODEC.listOf().optionalFieldOf("labels", List.of()).forGetter(EnderLabels::entries)
    ).apply(i, EnderLabels::new));

    private static final SavedDataType<EnderLabels> TYPE = new SavedDataType<>(
            McCompat.id("ender_labels"), EnderLabels::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private final Map<String, Entry> byKey = new LinkedHashMap<>();

    public EnderLabels() {
    }

    private EnderLabels(List<Entry> entries) {
        for (Entry entry : entries) {
            Label clean = entry.label().sanitized();
            if (!clean.isEmpty()) {
                byKey.put(key(entry.dimension(), entry.pos()), new Entry(entry.dimension(), entry.pos(), clean));
            }
        }
    }

    private static String key(String dimension, BlockPos pos) {
        return dimension + "|" + pos.asLong();
    }

    private static String dimension(ServerLevel level) {
        return level.dimension().identifier().toString();
    }

    /** Uma única lista para o mundo todo (guardada no mundo principal; cada entrada diz a dimensão). */
    public static EnderLabels of(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public List<Entry> entries() {
        return new ArrayList<>(byKey.values());
    }

    public Label get(ServerLevel level, BlockPos pos) {
        Entry entry = byKey.get(key(dimension(level), pos));
        return entry == null ? Label.EMPTY : entry.label();
    }

    public void set(ServerLevel level, BlockPos pos, Label label) {
        String key = key(dimension(level), pos);
        if (label.isEmpty()) {
            byKey.remove(key);
        } else {
            byKey.put(key, new Entry(dimension(level), pos.immutable(), label));
        }
        setDirty();
    }
}
