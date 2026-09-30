package io.github.leoascenci0.stashlink;


import io.github.leoascenci0.stashlink.refill.RefillService;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@Mod(Constants.MOD_ID)
public class StashLinkNeoForge {

    public StashLinkNeoForge(IEventBus eventBus) {

        // This method is invoked by the NeoForge mod loader when it is ready
        // to load your mod. You can access NeoForge and Common code in this
        // project.

        // Use NeoForge to bootstrap the Common mod.
        StashLink.init();

        // Reabastecimento da mão: só cola, a lógica está em common.
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> RefillService.tick(event.getServer()));
    }
}