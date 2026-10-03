package io.github.leoascenci0.stashlink;

import io.github.leoascenci0.stashlink.lootall.LootAllService;
import io.github.leoascenci0.stashlink.network.LootAllRequest;
import io.github.leoascenci0.stashlink.network.PlayerPrefsRequest;
import io.github.leoascenci0.stashlink.config.PlayerPrefsService;
import io.github.leoascenci0.stashlink.label.HologramService;
import io.github.leoascenci0.stashlink.label.LabelService;
import io.github.leoascenci0.stashlink.network.LabelEditRequest;
import io.github.leoascenci0.stashlink.network.LabelEditorData;
import io.github.leoascenci0.stashlink.network.SetLabelRequest;
import io.github.leoascenci0.stashlink.network.LockSlotRequest;
import io.github.leoascenci0.stashlink.network.SlotLocksSync;
import io.github.leoascenci0.stashlink.slotlock.SlotLockService;
import io.github.leoascenci0.stashlink.slotlock.SlotLockSync;
import io.github.leoascenci0.stashlink.network.PullItemRequest;
import io.github.leoascenci0.stashlink.network.QuickStackRequest;
import io.github.leoascenci0.stashlink.quickstack.QuickStackService;
import io.github.leoascenci0.stashlink.pull.PullItemService;
import io.github.leoascenci0.stashlink.refill.RefillService;
import io.github.leoascenci0.stashlink.config.StashLinkCommands;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
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

        // Comandos /stashlink: só cola, a lógica está em common.
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                StashLinkCommands.register(dispatcher));

        // Reabastecimento da mão: só cola, a lógica está em common.
        ServerTickEvents.END_SERVER_TICK.register(RefillService::tick);
        // Slots travados (Item 13): manda ao cliente com o mod o que está reservado no container aberto.
        ServerTickEvents.END_SERVER_TICK.register(SlotLockSync::tick);

        // Pedido de item vindo do cliente (Litematica). O tipo precisa ser registrado nos dois lados.
        PayloadTypeRegistry.serverboundPlay().register(PullItemRequest.TYPE, PullItemRequest.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(PullItemRequest.TYPE,
                (payload, context) -> PullItemService.handle(context.player(), payload));

        // Tecla N: pedido sem dados; o servidor decide tudo.
        PayloadTypeRegistry.serverboundPlay().register(QuickStackRequest.TYPE, QuickStackRequest.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(QuickStackRequest.TYPE,
                (payload, context) -> QuickStackService.handle(context.player()));

        // Tecla W numa tela de container: pedido sem dados; o servidor olha o container que ele sabe estar aberto.
        PayloadTypeRegistry.serverboundPlay().register(LootAllRequest.TYPE, LootAllRequest.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(LootAllRequest.TYPE,
                (payload, context) -> LootAllService.handle(context.player()));

        // Alt + clique: travar/destravar slot de baú aberto; o servidor revalida tudo. A lista de travas desce ao cliente.
        PayloadTypeRegistry.serverboundPlay().register(LockSlotRequest.TYPE, LockSlotRequest.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(LockSlotRequest.TYPE,
                (payload, context) -> SlotLockService.handle(context.player(), payload));
        PayloadTypeRegistry.clientboundPlay().register(SlotLocksSync.TYPE, SlotLocksSync.STREAM_CODEC);

        // Rótulos de baú (Item 14): hologramas por tick; editar = pedido -> editor no cliente -> pedido de gravar.
        ServerTickEvents.END_SERVER_TICK.register(HologramService::tick);
        PayloadTypeRegistry.serverboundPlay().register(LabelEditRequest.TYPE, LabelEditRequest.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(LabelEditRequest.TYPE,
                (payload, context) -> LabelService.handleEdit(context.player(), payload));
        PayloadTypeRegistry.clientboundPlay().register(LabelEditorData.TYPE, LabelEditorData.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SetLabelRequest.TYPE, SetLabelRequest.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(SetLabelRequest.TYPE,
                (payload, context) -> LabelService.handleSet(context.player(), payload));

        // Preferências pessoais do jogador (funcionam em Realms, sem comando): o servidor corrige e limita.
        PayloadTypeRegistry.serverboundPlay().register(PlayerPrefsRequest.TYPE, PlayerPrefsRequest.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(PlayerPrefsRequest.TYPE,
                (payload, context) -> PlayerPrefsService.handle(context.player(), payload));
    }
}
