package io.github.leoascenci0.stashlink.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Liga o botão de config do Mod Menu à nossa tela. Mod Menu é dependência opcional: este entrypoint só é
 * carregado se ele estiver instalado (declarado como "modmenu" no fabric.mod.json).
 */
public class StashLinkModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return StashLinkConfigScreen::new;
    }
}
