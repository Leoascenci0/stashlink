package io.github.leoascenci0.stashlink.client;

import io.github.leoascenci0.stashlink.Constants;
import io.github.leoascenci0.stashlink.network.BenchPullRequest;
import io.github.leoascenci0.stashlink.network.LabelEditRequest;
import io.github.leoascenci0.stashlink.network.LockSlotRequest;
import io.github.leoascenci0.stashlink.network.LootAllRequest;
import io.github.leoascenci0.stashlink.network.PlayerPrefsRequest;
import io.github.leoascenci0.stashlink.network.QuickStackRequest;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.NeoForge;

/** Cola do cliente no NeoForge: só carrega no cliente (dist), então o servidor dedicado nunca toca em KeyMapping. */
@Mod(value = Constants.MOD_ID, dist = Dist.CLIENT)
public class StashLinkNeoForgeClient {
    public StashLinkNeoForgeClient(ModContainer container, IEventBus modBus) {
        // Botão "Config" na lista de mods abre a nossa tela (a mesma do Fabric).
        container.registerExtensionPoint(IConfigScreenFactory.class,
                (mod, parent) -> new StashLinkConfigScreen(parent));

        // Preferências pessoais: lidas do arquivo, enviadas ao servidor ao entrar (e ao fechar a tela de config).
        ClientPrefs.load();
        ClientPrefs.setSender(request -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getConnection() != null && mc.getConnection().hasChannel(PlayerPrefsRequest.TYPE)) {
                ClientPacketDistributor.sendToServer(request);
            }
        });
        // Ao entrar: esquece os cadeados do servidor anterior; o servidor responde às preferências com os novos.
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn event) -> {
            io.github.leoascenci0.stashlink.config.ClientPolicy.reset();
            ClientPrefs.sync();
        });
        ClientFeatures.setLockSender(request -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getConnection() != null
                    && mc.getConnection().hasChannel(io.github.leoascenci0.stashlink.network.SetFeatureLockRequest.TYPE)) {
                ClientPacketDistributor.sendToServer(request);
            }
        });

        // Modo cliente: "o servidor conhece o nosso pacote?" no NeoForge é hasChannel (só com conexão aberta).
        ClientMode.setServerHasModCheck(() -> {
            Minecraft mc = Minecraft.getInstance();
            return mc.getConnection() != null && mc.getConnection().hasChannel(QuickStackRequest.TYPE);
        });

        // Slots travados: Alt + clique pede ao servidor (só se ele conhece o pacote).
        SlotLockClient.setServerHasMod(() -> {
            Minecraft mc = Minecraft.getInstance();
            return mc.getConnection() != null && mc.getConnection().hasChannel(LockSlotRequest.TYPE);
        });
        SlotLockClient.setSender(ClientPacketDistributor::sendToServer);

        // Bancadas com armazenamento (Item 16): o painel pede um item ao cursor (só se o servidor conhece o pacote).
        BenchClient.setServerHasMod(() -> {
            Minecraft mc = Minecraft.getInstance();
            return mc.getConnection() != null && mc.getConnection().hasChannel(BenchPullRequest.TYPE);
        });
        BenchClient.setSender(ClientPacketDistributor::sendToServer);

        // Rótulos de baú (Item 14): tecla J pede o editor ao servidor (só se ele conhece o pacote).
        LabelClient.setServerHasMod(() -> {
            Minecraft mc = Minecraft.getInstance();
            return mc.getConnection() != null && mc.getConnection().hasChannel(LabelEditRequest.TYPE);
        });
        LabelClient.setSenders(ClientPacketDistributor::sendToServer, ClientPacketDistributor::sendToServer);

        // Organizar o armazenamento (Item 20): tecla O abre a tela (só se o servidor conhece o pacote).
        OrganizeClient.setServerHasMod(() -> {
            Minecraft mc = Minecraft.getInstance();
            return mc.getConnection() != null
                    && mc.getConnection().hasChannel(io.github.leoascenci0.stashlink.network.OrganizeRequest.TYPE);
        });
        OrganizeClient.setSender(ClientPacketDistributor::sendToServer);

        modBus.addListener((RegisterKeyMappingsEvent event) -> {
            event.registerCategory(QuickStackKey.CATEGORY);
            event.register(QuickStackKey.KEY);
            event.register(LootAllKey.KEY);
            event.register(ConfigKey.KEY);
            event.register(LabelClient.KEY);
            event.register(OrganizeClient.KEY);
        });
        // Tecla W: gatilho é a tela de container. Cancelar o evento engole a tecla (não fecha a tela etc.).
        NeoForge.EVENT_BUS.addListener((ScreenEvent.KeyPressed.Pre event) -> {
            Minecraft mc = Minecraft.getInstance();
            if (LootAllKey.onKeyPressed(mc, event.getScreen(), event.getKeyEvent(), () -> {
                if (mc.getConnection() != null && mc.getConnection().hasChannel(LootAllRequest.TYPE)) {
                    ClientPacketDistributor.sendToServer(LootAllRequest.INSTANCE);
                } else {
                    // Servidor sem o StashLink: o modo cliente assume (se ativo); nunca manda pacote desconhecido.
                    ClientMode.onLootAllKey();
                }
            })) {
                event.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> {
            Minecraft mc = Minecraft.getInstance();
            ConfigKey.poll(mc);
            LabelClient.poll(mc);
            OrganizeClient.poll(mc);
            QuickStackKey.poll(mc, () -> {
                if (mc.getConnection() != null && mc.getConnection().hasChannel(QuickStackRequest.TYPE)) {
                    ClientPacketDistributor.sendToServer(QuickStackRequest.INSTANCE);
                } else {
                    ClientMode.onQuickStackKey();
                }
            });
            // O motor do modo cliente anda um passo por tick; fora do modo cliente não faz nada.
            ClientMode.tick(mc);
        });
    }
}
