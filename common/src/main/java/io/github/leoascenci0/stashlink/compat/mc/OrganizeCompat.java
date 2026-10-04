package io.github.leoascenci0.stashlink.compat.mc;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.storage.TagValueInput;

/**
 * API do Minecraft usada pelo destaque de baú do Item 20 que costuma mudar entre versões: a entidade de bloco com
 * contorno brilhante (visível através de paredes) que marca o baú achado pela busca. Quebrou numa atualização?
 * Conserte aqui.
 */
public final class OrganizeCompat {
    /** Marca das entidades de destaque do mod (nunca gravadas no disco; só o dono as vê). */
    public static final String HIGHLIGHT_TAG = "stashlink_highlight";
    private static final String OWNER_PREFIX = "stashlink_highlight_for_";

    /** Cor do contorno (RGB): âmbar, que se destaca de pedra, madeira e céu. */
    private static final int GLOW_COLOR = 0xFFB000;

    /** Folga do contorno para fora do bloco, para não brigar com as faces do baú. */
    private static final float MARGIN = 0.02f;

    private OrganizeCompat() {
    }

    /**
     * Cria o contorno em volta do container que ocupa de {@code min} a {@code max} (as duas metades do baú duplo).
     * A entidade é um bloco de vidro com brilho: o jogo desenha o <i>contorno</i> do brilho por cima de tudo,
     * inclusive através de paredes. Os campos entram por NBT porque os setters da entidade são privados.
     */
    public static Entity spawnHighlight(ServerLevel level, BlockPos min, BlockPos max, ServerPlayer owner) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "minecraft:block_display");
        CompoundTag state = new CompoundTag();
        state.putString("Name", "minecraft:white_stained_glass");
        tag.put("block_state", state);
        tag.putBoolean("Glowing", true);
        tag.putInt("glow_color_override", GLOW_COLOR);
        CompoundTag transformation = new CompoundTag();
        transformation.put("left_rotation", floats(0, 0, 0, 1));
        transformation.put("right_rotation", floats(0, 0, 0, 1));
        transformation.put("scale", floats(max.getX() - min.getX() + 1 + 2 * MARGIN, max.getY() - min.getY() + 1 + 2 * MARGIN,
                max.getZ() - min.getZ() + 1 + 2 * MARGIN));
        transformation.put("translation", floats(-MARGIN, -MARGIN, -MARGIN));
        tag.put("transformation", transformation);
        tag.putFloat("view_range", 1.0f);
        ListTag tags = new ListTag();
        tags.add(StringTag.valueOf(HIGHLIGHT_TAG));
        tags.add(StringTag.valueOf(OWNER_PREFIX + owner.getStringUUID()));
        tag.put("Tags", tags);
        Entity entity = EntityTypes.BLOCK_DISPLAY.create(level, EntitySpawnReason.COMMAND);
        if (entity == null) {
            return null;
        }
        entity.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
        entity.setPos(min.getX(), min.getY(), min.getZ());
        return level.addFreshEntity(entity) ? entity : null;
    }

    /** O destaque existe só para o dono: a quem não é o dono, o servidor nem manda a entidade. */
    public static boolean hiddenFrom(Entity entity, ServerPlayer player) {
        return entity.entityTags().contains(HIGHLIGHT_TAG) && !entity.entityTags().contains(OWNER_PREFIX + player.getStringUUID());
    }

    /** O contorno está de fato brilhando (para os testes). */
    public static boolean glows(Entity entity) {
        return entity.hasGlowingTag();
    }

    private static ListTag floats(float... values) {
        ListTag list = new ListTag();
        for (float v : values) {
            list.add(FloatTag.valueOf(v));
        }
        return list;
    }
}
