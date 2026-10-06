package io.github.leoascenci0.stashlink.gametest;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

/** Registra os blocos só dos testes (Item 26) e liga a gaveta de teste à Transfer API do Fabric. */
public class FabricTestSetup implements ModInitializer {
    @Override
    public void onInitialize() {
        TestBlocks.create();
        Registry.register(BuiltInRegistries.BLOCK, TestBlocks.API_DRAWER_ID, TestBlocks.API_DRAWER);
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, TestBlocks.API_DRAWER_ID, TestBlocks.API_DRAWER_TYPE);
        ItemStorage.SIDED.registerForBlockEntity((be, side) -> new ApiDrawerStorage(be), TestBlocks.API_DRAWER_TYPE);
    }
}
