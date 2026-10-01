package io.github.leoascenci0.stashlink;


import io.github.leoascenci0.stashlink.lootall.LootAllService;
import io.github.leoascenci0.stashlink.network.LootAllRequest;
import io.github.leoascenci0.stashlink.network.PlayerPrefsRequest;
import io.github.leoascenci0.stashlink.config.PlayerPrefsService;
import io.github.leoascenci0.stashlink.network.PullItemRequest;
import io.github.leoascenci0.stashlink.network.QuickStackRequest;
import io.github.leoascenci0.stashlink.quickstack.QuickStackService;
import io.github.leoascenci0.stashlink.pull.PullItemService;
import io.github.leoascenci0.stashlink.refill.RefillService;
import io.github.leoascenci0.stashlink.config.StashLinkCommands;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@Mod(Constants.MOD_ID)
public class StashLinkNeoForge {

    public StashLinkNeoForge(IEventBus eventBus) {

        // This method is invoked by the NeoForge mod loader when it is ready
        // to load your mod. You can access NeoForge and Common code in this
        // project.

        // Use NeoForge to bootstrap the Common mod.
        StashLink.init();

        // Comandos /stashlink: só cola, a lógica está em common.
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) ->
                StashLinkCommands.register(event.getDispatcher()));

        // Reabastecimento da mão: só cola, a lógica está em common.
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> RefillService.tick(event.getServer()));

        // Pedidos do cliente: item do Litematica e teclas N e W. Só o lado servidor vive aqui (o Litematica
        // oficial é Fabric; um port poderá usar o mesmo pacote; as teclas são registradas em StashLinkNeoForgeClient).
        // "optional" deixa clientes sem o mod entrarem. Um único registrar por mod.
        eventBus.addListener((RegisterPayloadHandlersEvent event) -> {
            PayloadRegistrar registrar = event.registrar(Constants.MOD_ID).optional();
            registrar.playToServer(PullItemRequest.TYPE, PullItemRequest.STREAM_CODEC,
                    (payload, context) -> PullItemService.handle((ServerPlayer) context.player(), payload));
            registrar.playToServer(QuickStackRequest.TYPE, QuickStackRequest.STREAM_CODEC,
                    (payload, context) -> QuickStackService.handle((ServerPlayer) context.player()));
            registrar.playToServer(LootAllRequest.TYPE, LootAllRequest.STREAM_CODEC,
                    (payload, context) -> LootAllService.handle((ServerPlayer) context.player()));
            // Preferências pessoais do jogador (funcionam em Realms, sem comando): o servidor corrige e limita.
            registrar.playToServer(PlayerPrefsRequest.TYPE, PlayerPrefsRequest.STREAM_CODEC,
                    (payload, context) -> PlayerPrefsService.handle((ServerPlayer) context.player(), payload));
        });
    }
}
