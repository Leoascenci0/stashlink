package io.github.leoascenci0.stashlink;

import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.server.Bootstrap;

/** Liga o "mínimo do Minecraft" para testes sem abrir o jogo. Seguro chamar várias vezes. */
public final class MinecraftTestSetup {
    private static HolderLookup.Provider lookup;

    private MinecraftTestSetup() {
    }

    public static synchronized HolderLookup.Provider init() {
        if (lookup == null) {
            SharedConstants.tryDetectVersion();
            Bootstrap.bootStrap();
            // No jogo real, os componentes padrão dos itens (ex.: tamanho de stack) são ligados ao carregar o
            // mundo. Sem isso, "new ItemStack(...)" falha com "Components not bound yet".
            lookup = VanillaRegistries.createLookup();
            BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(lookup)
                    .forEach(DataComponentInitializers.PendingComponents::apply);
        }
        return lookup;
    }
}
