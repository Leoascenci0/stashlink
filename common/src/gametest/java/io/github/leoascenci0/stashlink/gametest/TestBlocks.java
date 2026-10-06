package io.github.leoascenci0.stashlink.gametest;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.Set;

/**
 * Blocos só dos testes (Item 26), iguais nos dois loaders. Cada loader registra estes objetos do jeito dele e liga a
 * "tomada de itens" ({@code ItemStorage.SIDED} no Fabric, a capability no NeoForge) à {@link ApiDrawerBlockEntity}.
 */
public final class TestBlocks {
    public static final String NAMESPACE = "stashlink_test";
    public static final Identifier API_DRAWER_ID = Identifier.fromNamespaceAndPath(NAMESPACE, "api_drawer");

    public static Block API_DRAWER;
    public static BlockEntityType<ApiDrawerBlockEntity> API_DRAWER_TYPE;

    private TestBlocks() {
    }

    /** Cria os objetos (o loader registra depois, com {@link #API_DRAWER_ID}). */
    public static void create() {
        if (API_DRAWER != null) {
            return;
        }
        API_DRAWER = new ApiDrawerBlock(BlockBehaviour.Properties.of()
                .setId(ResourceKey.create(Registries.BLOCK, API_DRAWER_ID))
                .strength(1.0f)
                .sound(SoundType.WOOD));
        API_DRAWER_TYPE = new BlockEntityType<>(ApiDrawerBlockEntity::new, Set.of(API_DRAWER));
    }
}
