package io.github.leoascenci0.stashlink;

import io.github.leoascenci0.stashlink.bench.BenchPullService;
import io.github.leoascenci0.stashlink.bench.BenchSync;
import io.github.leoascenci0.stashlink.network.BenchPoolSync;
import io.github.leoascenci0.stashlink.network.BenchPullRequest;
import io.github.leoascenci0.stashlink.lootall.LootAllService;
import io.github.leoascenci0.stashlink.config.FeaturePolicyService;
import io.github.leoascenci0.stashlink.network.FeaturePolicySync;
import io.github.leoascenci0.stashlink.network.LootAllRequest;
import io.github.leoascenci0.stashlink.network.PlayerPrefsRequest;
import io.github.leoascenci0.stashlink.config.PlayerPrefsService;
import io.github.leoascenci0.stashlink.label.HologramService;
import io.github.leoascenci0.stashlink.label.LabelService;
import io.github.leoascenci0.stashlink.network.LabelEditRequest;
import io.github.leoascenci0.stashlink.network.LabelEditorData;
import io.github.leoascenci0.stashlink.network.SetLabelRequest;
import io.github.leoascenci0.stashlink.network.LockSlotRequest;
import io.github.leoascenci0.stashlink.network.ReceivesRequest;
import io.github.leoascenci0.stashlink.quickstack.QuickStackReceiveService;
import io.github.leoascenci0.stashlink.network.SlotLocksSync;
import io.github.leoascenci0.stashlink.slotlock.SlotLockService;
import io.github.leoascenci0.stashlink.slotlock.SlotLockSync;
import io.github.leoascenci0.stashlink.network.PullItemRequest;
import io.github.leoascenci0.stashlink.network.QuickStackRequest;
import io.github.leoascenci0.stashlink.network.SetFeatureLockRequest;
import io.github.leoascenci0.stashlink.quickstack.QuickStackService;
import io.github.leoascenci0.stashlink.pull.PullItemService;
import io.github.leoascenci0.stashlink.refill.RefillService;
import io.github.leoascenci0.stashlink.config.StashLinkCommands;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
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
        PayloadTypeRegistry.serverboundPlay().register(ReceivesRequest.TYPE, ReceivesRequest.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ReceivesRequest.TYPE,
                (payload, context) -> QuickStackReceiveService.handle(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(LockSlotRequest.TYPE,
                (payload, context) -> SlotLockService.handle(context.player(), payload));
        PayloadTypeRegistry.clientboundPlay().register(SlotLocksSync.TYPE, SlotLocksSync.STREAM_CODEC);

        // Bancadas com armazenamento (Item 16): o servidor manda o que há por perto; o cliente pede um item ao cursor.
        ServerTickEvents.END_SERVER_TICK.register(BenchSync::tick);
        // Sair com a estação aberta (ou parar o servidor) devolve o emprestado ao baú antes do save do jogador.
        // (LEAVE roda na thread do servidor, no começo de PlayerList.remove, antes do save; o DISCONNECT de rede roda fora dela.)
        ServerPlayerEvents.LEAVE.register(StashLink::onPlayerLeave);
        ServerLifecycleEvents.SERVER_STOPPING.register(BenchSync::releaseAll);
        PayloadTypeRegistry.clientboundPlay().register(BenchPoolSync.TYPE, BenchPoolSync.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(BenchPullRequest.TYPE, BenchPullRequest.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(BenchPullRequest.TYPE,
                (payload, context) -> BenchPullService.handle(context.player(), payload));

        // Rótulos de baú (Item 14): hologramas por tick; editar = pedido -> editor no cliente -> pedido de gravar.
        ServerTickEvents.END_SERVER_TICK.register(HologramService::tick);
        PayloadTypeRegistry.serverboundPlay().register(LabelEditRequest.TYPE, LabelEditRequest.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(LabelEditRequest.TYPE,
                (payload, context) -> LabelService.handleEdit(context.player(), payload));
        PayloadTypeRegistry.clientboundPlay().register(LabelEditorData.TYPE, LabelEditorData.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SetLabelRequest.TYPE, SetLabelRequest.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(SetLabelRequest.TYPE,
                (payload, context) -> LabelService.handleSet(context.player(), payload));

        // Organizar o armazenamento (Item 20): pedido do cliente -> servidor revalida; prévia/busca descem ao cliente.
        ServerTickEvents.END_SERVER_TICK.register(io.github.leoascenci0.stashlink.organize.OrganizeHighlight::tick);
        PayloadTypeRegistry.serverboundPlay().register(io.github.leoascenci0.stashlink.network.OrganizeRequest.TYPE,
                io.github.leoascenci0.stashlink.network.OrganizeRequest.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(io.github.leoascenci0.stashlink.network.OrganizeRequest.TYPE,
                (payload, context) -> io.github.leoascenci0.stashlink.organize.OrganizeService.handle(context.player(), payload));
        PayloadTypeRegistry.clientboundPlay().register(io.github.leoascenci0.stashlink.network.OrganizeSync.TYPE,
                io.github.leoascenci0.stashlink.network.OrganizeSync.STREAM_CODEC);

        // Preferências pessoais do jogador (funcionam em Realms, sem comando): o servidor corrige e limita.
        PayloadTypeRegistry.serverboundPlay().register(PlayerPrefsRequest.TYPE, PlayerPrefsRequest.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(PlayerPrefsRequest.TYPE,
                (payload, context) -> PlayerPrefsService.handle(context.player(), payload));

        // Cadeados das funções: o cliente pede (só dono/operador é atendido) e o servidor avisa todos da política.
        PayloadTypeRegistry.serverboundPlay().register(SetFeatureLockRequest.TYPE, SetFeatureLockRequest.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(SetFeatureLockRequest.TYPE,
                (payload, context) -> FeaturePolicyService.handle(context.player(), payload));
        PayloadTypeRegistry.clientboundPlay().register(FeaturePolicySync.TYPE, FeaturePolicySync.STREAM_CODEC);
    }
}
