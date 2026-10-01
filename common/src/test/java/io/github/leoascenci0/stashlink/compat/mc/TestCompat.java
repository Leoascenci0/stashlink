package io.github.leoascenci0.stashlink.compat.mc;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** API do Minecraft usada só nos testes e que muda entre versões. Conserte aqui (docs/UPDATING.md). */
public final class TestCompat {
    private TestCompat() {
    }

    /** Shulker box colorida. Em MC 26.3 não há mais RED_SHULKER_BOX etc.: é DYED_SHULKER_BOX.pick(cor). */
    public static Item dyedShulker(DyeColor color) {
        return Items.DYED_SHULKER_BOX.pick(color);
    }

    /** Lookup de registros para testes. Em MC 26.3 {@code createLookup()} virou {@code createWorldLookup()}. */
    public static HolderLookup.Provider registryLookup() {
        return VanillaRegistries.createWorldLookup();
    }
}
