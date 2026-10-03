package io.github.leoascenci0.stashlink.compat.mc;

import io.github.leoascenci0.stashlink.label.Label;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.objects.AtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.storage.TagValueInput;

/**
 * API do Minecraft usada pelos rótulos de baú (Item 14) que costuma mudar entre versões: ícones inline no texto,
 * o holograma (entidade de texto) e o rótulo que a shulker leva no item. Quebrou numa atualização? Conserte aqui.
 */
public final class LabelCompat {
    /** Marca das entidades de holograma do mod (para reconhecê-las e nunca gravá-las no disco). */
    public static final String HOLOGRAM_TAG = "stashlink_label_hologram";

    /** Alcance de desenho no cliente (multiplicador de 64 blocos): folgado de propósito; quem decide quem vê é o servidor ({@link #SHOW_RANGE}). */
    private static final float VIEW_RANGE = 1.0f;

    /** Distância (blocos) até onde o servidor mostra o holograma a um jogador. */
    public static final double SHOW_RANGE = 32.0;

    /** Tamanho do texto (1 = tamanho de placa grande). */
    private static final float SCALE = 0.5f;

    private LabelCompat() {
    }

    /**
     * Ícone inline de uma textura de item ({@code items}) ou bloco ({@code blocks}). Recurso nativo do texto do
     * jogo (objeto "atlas"/"sprite"), desenhado até por cliente sem o mod.
     */
    public static MutableComponent sprite(boolean item, String name) {
        Identifier atlas = Identifier.withDefaultNamespace(item ? "items" : "blocks");
        Identifier sprite = Identifier.withDefaultNamespace((item ? "item/" : "block/") + name);
        return Component.object(new AtlasSprite(atlas, sprite));
    }

    /**
     * Cria o holograma: uma entidade de texto que sempre olha para o jogador, sem hitbox, vista só de perto.
     * Os campos são carregados por NBT porque os setters da entidade são privados.
     */
    public static Entity spawnHologram(ServerLevel level, double x, double y, double z, Component text) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "minecraft:text_display");
        tag.put("text", ComponentSerialization.CODEC.encodeStart(level.registryAccess().createSerializationContext(NbtOps.INSTANCE), text)
                .getOrThrow(IllegalStateException::new));
        tag.putString("billboard", "center");
        CompoundTag transformation = new CompoundTag();
        transformation.put("left_rotation", floats(0, 0, 0, 1));
        transformation.put("right_rotation", floats(0, 0, 0, 1));
        transformation.put("scale", floats(SCALE, SCALE, SCALE));
        transformation.put("translation", floats(0, 0, 0));
        tag.put("transformation", transformation);
        tag.putFloat("view_range", VIEW_RANGE);
        tag.putString("alignment", "center");
        tag.putInt("line_width", 220);
        tag.putBoolean("shadow", true);
        ListTag tags = new ListTag();
        tags.add(StringTag.valueOf(HOLOGRAM_TAG));
        tag.put("Tags", tags);
        Entity entity = EntityTypes.TEXT_DISPLAY.create(level, EntitySpawnReason.COMMAND);
        if (entity == null) {
            return null;
        }
        entity.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
        entity.setPos(x, y, z);
        if (!level.addFreshEntity(entity)) {
            return null;                                    // área ainda sem entidades ativas: tenta no próximo ciclo
        }
        return entity;
    }
private static ListTag floats(float... values) {        ListTag list = new ListTag();        for (float v : values) {            list.add(net.minecraft.nbt.FloatTag.valueOf(v));        }        return list;    }

    // ----------------------------------------------------------- rótulo no item da shulker

    /** Coloca o rótulo nos componentes do item que a shulker solta (vai em CUSTOM_DATA, que o jogo já grava). */
    public static void writeToItem(DataComponentMap.Builder builder, Label label) {
        CompoundTag tag = new CompoundTag();
        tag.put(Label.KEY, Label.CODEC.encodeStart(NbtOps.INSTANCE, label).getOrThrow(IllegalStateException::new));
        builder.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    /** Lê o rótulo do item da shulker que acabou de ser colocada; {@link Label#EMPTY} se não tem. */
    public static Label readFromItem(DataComponentGetter components) {
        CustomData data = components.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return Label.EMPTY;
        }
        return data.copyTag().get(Label.KEY) == null ? Label.EMPTY
                : Label.CODEC.parse(NbtOps.INSTANCE, data.copyTag().get(Label.KEY)).result().orElse(Label.EMPTY);
    }
}
