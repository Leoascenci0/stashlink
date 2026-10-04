package io.github.leoascenci0.stashlink.client;

import io.github.leoascenci0.stashlink.network.BenchPoolSync;
import io.github.leoascenci0.stashlink.network.BenchPullRequest;
import io.github.leoascenci0.stashlink.network.LabelEditRequest;
import io.github.leoascenci0.stashlink.network.LabelEditorData;
import io.github.leoascenci0.stashlink.network.LockSlotRequest;
import io.github.leoascenci0.stashlink.network.SlotLocksSync;
import io.github.leoascenci0.stashlink.config.ClientPolicy;
import io.github.leoascenci0.stashlink.network.FeaturePolicySync;
import io.github.leoascenci0.stashlink.network.LootAllRequest;
import io.github.leoascenci0.stashlink.network.PlayerPrefsRequest;
import io.github.leoascenci0.stashlink.network.QuickStackRequest;
import io.github.leoascenci0.stashlink.network.SetFeatureLockRequest;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Cola do cliente no Fabric: registra as teclas N e W e envia os pedidos. */
public class StashLinkFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Preferências pessoais: lidas do arquivo, enviadas ao servidor ao entrar (e ao fechar a tela de config).
        ClientPrefs.load();
        ClientPrefs.setSender(request -> {
            if (ClientPlayNetworking.canSend(PlayerPrefsRequest.TYPE)) {
                ClientPlayNetworking.send(request);
            }
        });
        // Ao entrar: esquece os cadeados do servidor anterior; o servidor responde às preferências com os novos.
        ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> {
            ClientPolicy.reset();
            ClientPrefs.sync();
        });
        ClientPlayNetworking.registerGlobalReceiver(FeaturePolicySync.TYPE,
                (payload, context) -> ClientPolicy.apply(payload.lockedMask(), payload.canEdit()));
        ClientFeatures.setLockSender(request -> {
            if (ClientPlayNetworking.canSend(SetFeatureLockRequest.TYPE)) {
                ClientPlayNetworking.send(request);
            }
        });

        // Modo cliente: "o servidor conhece o nosso pacote?" no Fabric é canSend. Só consultado com conexão aberta.
        ClientMode.setServerHasModCheck(() -> ClientPlayNetworking.canSend(QuickStackRequest.TYPE));
        // O motor do modo cliente (abre/clica/fecha) anda um passo por tick; fora do modo cliente não faz nada.
        ClientTickEvents.END_CLIENT_TICK.register(ClientMode::tick);

        // Slots travados: Alt + clique pede, o servidor manda a lista de travas para desenhar a prévia.
        SlotLockClient.setServerHasMod(() -> ClientPlayNetworking.canSend(LockSlotRequest.TYPE));
        SlotLockClient.setSender(ClientPlayNetworking::send);
        ClientPlayNetworking.registerGlobalReceiver(SlotLocksSync.TYPE, (payload, context) -> SlotLockClient.apply(payload));

        // Bancadas com armazenamento (Item 16): o servidor manda a lista; o painel pede um item ao cursor.
        BenchClient.setServerHasMod(() -> ClientPlayNetworking.canSend(BenchPullRequest.TYPE));
        BenchClient.setSender(ClientPlayNetworking::send);
        ClientPlayNetworking.registerGlobalReceiver(BenchPoolSync.TYPE, (payload, context) -> BenchClient.apply(payload));

        // Rótulos de baú (Item 14): tecla J pede o editor; o servidor responde com o texto atual.
        LabelClient.setServerHasMod(() -> ClientPlayNetworking.canSend(LabelEditRequest.TYPE));
        LabelClient.setSenders(ClientPlayNetworking::send, ClientPlayNetworking::send);
        ClientPlayNetworking.registerGlobalReceiver(LabelEditorData.TYPE, (payload, context) -> LabelClient.openEditor(payload));
        KeyMappingHelper.registerKeyMapping(LabelClient.KEY);
        ClientTickEvents.END_CLIENT_TICK.register(LabelClient::poll);

        // Organizar o armazenamento (Item 20): tecla O abre a tela; o servidor responde com a prévia e a busca.
        OrganizeClient.setServerHasMod(() -> ClientPlayNetworking.canSend(io.github.leoascenci0.stashlink.network.OrganizeRequest.TYPE));
        OrganizeClient.setSender(ClientPlayNetworking::send);
        ClientPlayNetworking.registerGlobalReceiver(io.github.leoascenci0.stashlink.network.OrganizeSync.TYPE,
                (payload, context) -> OrganizeClient.apply(payload));
        KeyMappingHelper.registerKeyMapping(OrganizeClient.KEY);
        ClientTickEvents.END_CLIENT_TICK.register(OrganizeClient::poll);

        KeyMappingHelper.registerKeyMapping(QuickStackKey.KEY);
        ClientTickEvents.END_CLIENT_TICK.register(mc -> QuickStackKey.poll(mc, () -> {
            if (ClientPlayNetworking.canSend(QuickStackRequest.TYPE)) {
                ClientPlayNetworking.send(QuickStackRequest.INSTANCE);
            } else {
                // Servidor sem o StashLink: não manda pacote que ele não conhece; o modo cliente assume (se ativo).
                ClientMode.onQuickStackKey();
            }
        }));

        // Tecla K: abre a tela de config (alternativa ao Mod Menu).
        KeyMappingHelper.registerKeyMapping(ConfigKey.KEY);
        ClientTickEvents.END_CLIENT_TICK.register(ConfigKey::poll);

        // Tecla W: o gatilho é a tela de container aberta. O evento é registrado por tela, quando ela inicia.
        KeyMappingHelper.registerKeyMapping(LootAllKey.KEY);
        ScreenEvents.AFTER_INIT.register((mc, screen, width, height) ->
                ScreenKeyboardEvents.allowKeyPress(screen).register((s, event) ->
                        // allowKeyPress devolve "deixe seguir": se tratamos a tecla, devolvemos false (engole).
                        !LootAllKey.onKeyPressed(mc, s, event, () -> {
                            if (ClientPlayNetworking.canSend(LootAllRequest.TYPE)) {
                                ClientPlayNetworking.send(LootAllRequest.INSTANCE);
                            } else {
                                ClientMode.onLootAllKey();
                            }
                        })));
    }
}
