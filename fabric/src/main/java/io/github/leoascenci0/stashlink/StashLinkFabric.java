package io.github.leoascenci0.stashlink;

import io.github.leoascenci0.stashlink.network.PullItemRequest;
import io.github.leoascenci0.stashlink.pull.PullItemService;
import io.github.leoascenci0.stashlink.refill.RefillService;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public class StashLinkFabric implements ModInitializer {

    @Override
    public void onInitialize() {

        // This method is invoked by the Fabric mod loader when it is ready
        // to load your mod. You can access Fabric and Common code in this
        // project.

        // Use Fabric to bootstrap the Common mod.
        StashLink.init();

        // Reabastecimento da mão: só cola, a lógica está em common.
        ServerTickEvents.END_SERVER_TICK.register(RefillService::tick);

        // Pedido de item vindo do cliente (Litematica). O tipo precisa ser registrado nos dois lados.
        PayloadTypeRegistry.serverboundPlay().register(PullItemRequest.TYPE, PullItemRequest.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(PullItemRequest.TYPE,
                (payload, context) -> PullItemService.handle(context.player(), payload));
    }
}
