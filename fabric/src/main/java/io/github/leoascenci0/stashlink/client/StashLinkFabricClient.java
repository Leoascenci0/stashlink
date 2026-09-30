package io.github.leoascenci0.stashlink.client;

import io.github.leoascenci0.stashlink.network.QuickStackRequest;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Cola do cliente no Fabric: registra a tecla N e envia o pedido. */
public class StashLinkFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        KeyMappingHelper.registerKeyMapping(QuickStackKey.KEY);
        ClientTickEvents.END_CLIENT_TICK.register(mc -> QuickStackKey.poll(mc, () -> {
            // Servidor sem o StashLink: não há quem atenda; não manda pacote que ele não conhece.
            if (ClientPlayNetworking.canSend(QuickStackRequest.TYPE)) {
                ClientPlayNetworking.send(QuickStackRequest.INSTANCE);
            }
        }));
    }
}
