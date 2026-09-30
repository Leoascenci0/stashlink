package io.github.leoascenci0.stashlink.client;

import io.github.leoascenci0.stashlink.Constants;
import io.github.leoascenci0.stashlink.network.QuickStackRequest;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.NeoForge;

/** Cola do cliente no NeoForge: só carrega no cliente (dist), então o servidor dedicado nunca toca em KeyMapping. */
@Mod(value = Constants.MOD_ID, dist = Dist.CLIENT)
public class StashLinkNeoForgeClient {
    public StashLinkNeoForgeClient(IEventBus modBus) {
        modBus.addListener((RegisterKeyMappingsEvent event) -> {
            event.registerCategory(QuickStackKey.CATEGORY);
            event.register(QuickStackKey.KEY);
        });
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> {
            Minecraft mc = Minecraft.getInstance();
            QuickStackKey.poll(mc, () -> {
                if (mc.getConnection() != null && mc.getConnection().hasChannel(QuickStackRequest.TYPE)) {
                    ClientPacketDistributor.sendToServer(QuickStackRequest.INSTANCE);
                }
            });
        });
    }
}
