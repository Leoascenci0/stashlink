package io.github.leoascenci0.stashlink.compat.rei;

import io.github.leoascenci0.stashlink.client.PanelZones;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.screen.ExclusionZones;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/**
 * Plugin opcional do REI (Item 24): mesma zona de exclusão do painel "Armazenamento". Só o REI carrega esta classe
 * (entrypoint {@code rei_client}); sem o REI nada disto existe.
 */
public final class StashLinkReiPlugin implements REIClientPlugin {
    @Override
    public void registerExclusionZones(ExclusionZones zones) {
        zones.register(AbstractContainerScreen.class, screen -> PanelZones.of(screen).stream()
                .map(zone -> new Rectangle(zone.x(), zone.y(), zone.width(), zone.height()))
                .toList());
    }
}
