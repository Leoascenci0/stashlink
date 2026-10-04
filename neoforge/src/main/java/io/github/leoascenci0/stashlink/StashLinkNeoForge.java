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
        // Slots travados (Item 13): manda ao cliente com o mod o que está reservado no container aberto.
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> SlotLockSync.tick(event.getServer()));
        // Bancadas com armazenamento (Item 16): manda ao cliente com o mod o que há por perto.
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> BenchSync.tick(event.getServer()));
        // Organizar o armazenamento (Item 20): apaga os contornos de destaque vencidos.
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> io.github.leoascenci0.stashlink.organize.OrganizeHighlight.tick(event.getServer()));
        // Rótulos de baú (Item 14): mantém os hologramas.
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> HologramService.tick(event.getServer()));

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
            // Alt + clique: travar/destravar slot; a lista de travas desce ao cliente (só para quem tem o mod).
            registrar.playToServer(ReceivesRequest.TYPE, ReceivesRequest.STREAM_CODEC,
                    (payload, context) -> QuickStackReceiveService.handle((ServerPlayer) context.player(), payload));
            registrar.playToServer(LockSlotRequest.TYPE, LockSlotRequest.STREAM_CODEC,
                    (payload, context) -> SlotLockService.handle((ServerPlayer) context.player(), payload));
            registrar.playToClient(SlotLocksSync.TYPE, SlotLocksSync.STREAM_CODEC,
                    (payload, context) -> io.github.leoascenci0.stashlink.client.SlotLockClient.apply(payload));
            // Bancadas com armazenamento (Item 16): o cliente pede um item ao cursor; a lista de itens desce ao cliente.
            registrar.playToServer(BenchPullRequest.TYPE, BenchPullRequest.STREAM_CODEC,
                    (payload, context) -> BenchPullService.handle((ServerPlayer) context.player(), payload));
            registrar.playToClient(BenchPoolSync.TYPE, BenchPoolSync.STREAM_CODEC,
                    (payload, context) -> io.github.leoascenci0.stashlink.client.BenchClient.apply(payload));
            // Rótulos de baú (Item 14): pedir o editor, receber o texto atual, gravar.
            registrar.playToServer(LabelEditRequest.TYPE, LabelEditRequest.STREAM_CODEC,
                    (payload, context) -> LabelService.handleEdit((ServerPlayer) context.player(), payload));
            registrar.playToClient(LabelEditorData.TYPE, LabelEditorData.STREAM_CODEC,
                    (payload, context) -> io.github.leoascenci0.stashlink.client.LabelClient.openEditor(payload));
            registrar.playToServer(SetLabelRequest.TYPE, SetLabelRequest.STREAM_CODEC,
                    (payload, context) -> LabelService.handleSet((ServerPlayer) context.player(), payload));
            // Organizar o armazenamento (Item 20): pedido do cliente; prévia/busca descem ao cliente.
            registrar.playToServer(io.github.leoascenci0.stashlink.network.OrganizeRequest.TYPE,
                    io.github.leoascenci0.stashlink.network.OrganizeRequest.STREAM_CODEC,
                    (payload, context) -> io.github.leoascenci0.stashlink.organize.OrganizeService.handle((ServerPlayer) context.player(), payload));
            registrar.playToClient(io.github.leoascenci0.stashlink.network.OrganizeSync.TYPE,
                    io.github.leoascenci0.stashlink.network.OrganizeSync.STREAM_CODEC,
                    (payload, context) -> io.github.leoascenci0.stashlink.client.OrganizeClient.apply(payload));
            // Preferências pessoais do jogador (funcionam em Realms, sem comando): o servidor corrige e limita.
            registrar.playToServer(PlayerPrefsRequest.TYPE, PlayerPrefsRequest.STREAM_CODEC,
                    (payload, context) -> PlayerPrefsService.handle((ServerPlayer) context.player(), payload));
            // Cadeados das funções: o cliente pede (só dono/operador é atendido) e o servidor avisa todos.
            registrar.playToServer(SetFeatureLockRequest.TYPE, SetFeatureLockRequest.STREAM_CODEC,
                    (payload, context) -> FeaturePolicyService.handle((ServerPlayer) context.player(), payload));
            registrar.playToClient(FeaturePolicySync.TYPE, FeaturePolicySync.STREAM_CODEC,
                    (payload, context) -> io.github.leoascenci0.stashlink.config.ClientPolicy
                            .apply(payload.lockedMask(), payload.canEdit()));
        });
    }
}
