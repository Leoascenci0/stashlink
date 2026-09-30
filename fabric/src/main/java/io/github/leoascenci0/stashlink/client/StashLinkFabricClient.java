package io.github.leoascenci0.stashlink.client;

import io.github.leoascenci0.stashlink.network.LootAllRequest;
import io.github.leoascenci0.stashlink.network.QuickStackRequest;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Cola do cliente no Fabric: registra as teclas N e W e envia os pedidos. */
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

        // Tecla W: o gatilho é a tela de container aberta. O evento é registrado por tela, quando ela inicia.
        KeyMappingHelper.registerKeyMapping(LootAllKey.KEY);
        ScreenEvents.AFTER_INIT.register((mc, screen, width, height) ->
                ScreenKeyboardEvents.allowKeyPress(screen).register((s, event) ->
                        // allowKeyPress devolve "deixe seguir": se tratamos a tecla, devolvemos false (engole).
                        !LootAllKey.onKeyPressed(mc, s, event, () -> {
                            if (ClientPlayNetworking.canSend(LootAllRequest.TYPE)) {
                                ClientPlayNetworking.send(LootAllRequest.INSTANCE);
                            }
                        })));
    }
}
