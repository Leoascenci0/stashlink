package io.github.leoascenci0.stashlink.client;

import io.github.leoascenci0.stashlink.Constants;
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
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn event) -> ClientPrefs.sync());

        modBus.addListener((RegisterKeyMappingsEvent event) -> {
            event.registerCategory(QuickStackKey.CATEGORY);
            event.register(QuickStackKey.KEY);
            event.register(LootAllKey.KEY);
            event.register(ConfigKey.KEY);
        });
        // Tecla W: gatilho é a tela de container. Cancelar o evento engole a tecla (não fecha a tela etc.).
        NeoForge.EVENT_BUS.addListener((ScreenEvent.KeyPressed.Pre event) -> {
            Minecraft mc = Minecraft.getInstance();
            if (LootAllKey.onKeyPressed(mc, event.getScreen(), event.getKeyEvent(), () -> {
                if (mc.getConnection() != null && mc.getConnection().hasChannel(LootAllRequest.TYPE)) {
                    ClientPacketDistributor.sendToServer(LootAllRequest.INSTANCE);
                }
            })) {
                event.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> {
            Minecraft mc = Minecraft.getInstance();
            ConfigKey.poll(mc);
            QuickStackKey.poll(mc, () -> {
                if (mc.getConnection() != null && mc.getConnection().hasChannel(QuickStackRequest.TYPE)) {
                    ClientPacketDistributor.sendToServer(QuickStackRequest.INSTANCE);
                }
            });
        });
    }
}
