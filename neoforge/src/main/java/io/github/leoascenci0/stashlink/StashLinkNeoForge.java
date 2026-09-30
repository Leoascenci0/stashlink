package io.github.leoascenci0.stashlink;


import io.github.leoascenci0.stashlink.network.PullItemRequest;
import io.github.leoascenci0.stashlink.pull.PullItemService;
import io.github.leoascenci0.stashlink.refill.RefillService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

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

        // Pedido de item do cliente (Litematica). Só o lado servidor existe aqui: o Litematica oficial é
        // Fabric; um port (Forgematica) poderá usar este mesmo pacote. "optional" deixa clientes sem o mod entrarem.
        eventBus.addListener((RegisterPayloadHandlersEvent event) ->
                event.registrar(Constants.MOD_ID).optional().playToServer(
                        PullItemRequest.TYPE, PullItemRequest.STREAM_CODEC,
                        (payload, context) -> PullItemService.handle((ServerPlayer) context.player(), payload)));
    }
}
