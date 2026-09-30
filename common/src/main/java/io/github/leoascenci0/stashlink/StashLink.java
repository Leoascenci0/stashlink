package io.github.leoascenci0.stashlink;

import io.github.leoascenci0.stashlink.platform.Services;

// Ponto de entrada comum: os dois loaders (Fabric e NeoForge) chamam este init().
// Toda a lógica do mod deve nascer aqui; os módulos de loader são só "cola".
public class StashLink {

    public static void init() {
        Constants.LOG.info("StashLink carregado em {} ({})", Services.PLATFORM.getPlatformName(), Services.PLATFORM.getEnvironmentName());
    }
}
