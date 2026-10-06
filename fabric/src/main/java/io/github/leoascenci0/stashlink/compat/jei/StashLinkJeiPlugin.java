package io.github.leoascenci0.stashlink.compat.jei;

import io.github.leoascenci0.stashlink.client.PanelZones;
import io.github.leoascenci0.stashlink.compat.mc.McCompat;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Plugin opcional do JEI (Item 24): avisa que o painel "Armazenamento" ocupa aquele espaço, para a lista de itens não
 * ficar por baixo. Só o JEI carrega esta classe (entrypoint {@code jei_mod_plugin}); sem o JEI nada disto existe.
 */
@JeiPlugin
public final class StashLinkJeiPlugin implements IModPlugin {
    @Override
    public Identifier getPluginUid() {
        return McCompat.id("jei");
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGenericGuiContainerHandler(AbstractContainerScreen.class, new IGuiContainerHandler<AbstractContainerScreen<?>>() {
            @Override
            public List<Rect2i> getGuiExtraAreas(AbstractContainerScreen<?> screen) {
                return PanelZones.of(screen).stream()
                        .map(zone -> new Rect2i(zone.x(), zone.y(), zone.width(), zone.height()))
                        .toList();
            }
        });
    }
}
