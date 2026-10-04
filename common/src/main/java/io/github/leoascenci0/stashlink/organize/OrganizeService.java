package io.github.leoascenci0.stashlink.organize;

import io.github.leoascenci0.stashlink.Constants;
import io.github.leoascenci0.stashlink.compat.mc.McCompat;
import io.github.leoascenci0.stashlink.config.Feature;
import io.github.leoascenci0.stashlink.config.FeatureGate;
import io.github.leoascenci0.stashlink.label.Labels;
import io.github.leoascenci0.stashlink.lootall.LootAllService;
import io.github.leoascenci0.stashlink.network.OrganizeRequest;
import io.github.leoascenci0.stashlink.network.OrganizeSync;
import io.github.leoascenci0.stashlink.platform.Services;
import io.github.leoascenci0.stashlink.quickstack.QuickStackService;
import io.github.leoascenci0.stashlink.slotlock.SlotLocks;
import io.github.leoascenci0.stashlink.source.ContainerSource;
import io.github.leoascenci0.stashlink.source.NearbyContainers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Trata os pedidos do Item 20 no servidor. O cliente só <i>pede</i>; aqui o servidor revalida a cada pedido: jogador vivo e
 * não espectador, função {@link Feature#ORGANIZE} liberada, frequência, e cada container individualmente (raio, claims, não
 * aberto por outro jogador). Nada é gravado numa prévia; Aplicar/Desfazer só gravam se tudo ainda está como no plano.
 */
public final class OrganizeService {
    /** Intervalo mínimo (ticks) entre dois pedidos do mesmo tipo do mesmo jogador, por ação. */
    private static final int[] COOLDOWN = {5, 10, 10, 10, 8, 4, 10};

    private record Pending(Object dimension, OrganizeSystem.Plan plan) {
    }

    /** Por identidade do jogador: relogar cria outro objeto e o antigo é coletado sozinho. */
    private static final Map<ServerPlayer, Pending> PENDING = new WeakHashMap<>();
    private static final Map<ServerPlayer, Pending> APPLIED = new WeakHashMap<>();
    private static final Map<ServerPlayer, long[]> LAST = new WeakHashMap<>();

    private OrganizeService() {
    }

    /** Roda na thread do servidor. */
    public static void handle(ServerPlayer player, OrganizeRequest request) {
        try {
            process(player, request);
        } catch (RuntimeException e) {
            // Pacote vindo da rede: um erro aqui nunca pode derrubar o servidor.
            Constants.LOG.error("Falha ao organizar o armazenamento de {}", player.getGameProfile().name(), e);
        }
    }

    private static void process(ServerPlayer player, OrganizeRequest request) {
        int action = request.action();
        if (action < 0 || action >= COOLDOWN.length || !player.isAlive() || player.isSpectator()) {
            return;
        }
        if (!FeatureGate.allow(player, Feature.ORGANIZE)) {
            return;
        }
        long now = McCompat.gameTime(player);
        long[] last = LAST.computeIfAbsent(player, p -> {
            long[] init = new long[COOLDOWN.length];
            java.util.Arrays.fill(init, Long.MIN_VALUE);
            return init;
        });
        if (last[action] != Long.MIN_VALUE && now >= last[action] && now - last[action] < COOLDOWN[action]) {
            return;
        }
        last[action] = now;

        switch (action) {
            case OrganizeRequest.CHEST -> chest(player, request);
            case OrganizeRequest.PREVIEW -> preview(player);
            case OrganizeRequest.APPLY -> apply(player);
            case OrganizeRequest.UNDO -> undo(player);
            case OrganizeRequest.SEARCH -> search(player, request, false);
            case OrganizeRequest.HIGHLIGHT -> highlight(player, request.pos());
            case OrganizeRequest.HIGHLIGHT_ALL -> search(player, request, true);
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------------------------------------- 20.1 baú aberto

    /** O container (baú, barril, shulker, baú do End) do menu aberto: o primeiro slot que não é do jogador. */
    static Container storageOf(ServerPlayer player, AbstractContainerMenu menu) {
        for (Slot slot : menu.slots) {
            if (slot.container != player.getInventory()) {
                return slot.container;
            }
        }
        return null;
    }

    private static void chest(ServerPlayer player, OrganizeRequest request) {
        AbstractContainerMenu menu = player.containerMenu;
        if (menu == player.inventoryMenu || menu.containerId != request.containerId()
                || !LootAllService.isSupportedMenu(menu) || !menu.stillValid(player)) {
            return;
        }
        Container container = storageOf(player, menu);
        if (container == null) {
            return;
        }
        for (BlockEntity be : SlotLocks.holders(container)) {
            if (!Services.PLATFORM.canPlayerUseBlock(player, be.getBlockPos())) {
                return;
            }
        }
        if (QuickStackService.openedByAnother(player, container)) {
            player.sendOverlayMessage(Component.translatableWithFallback("stashlink.slot_lock.busy",
                    "Another player has this container open"));
            return;
        }
        boolean changed = OrganizeLogic.tidy(container);
        if (changed) {
            menu.broadcastChanges();
        }
        player.sendOverlayMessage(changed
                ? Component.translatableWithFallback("stashlink.organize.chest_done", "Container organized")
                : Component.translatableWithFallback("stashlink.organize.chest_nothing", "Already organized"));
    }

    // -------------------------------------------------------------------------------------- 20.2 sistema todo

    /**
     * Os containers do sistema, do mais perto ao mais longe. Para mexer, cada um só vale se os mods de proteção liberam
     * <b>e</b> nenhum outro jogador está com ele aberto agora (a mesma regra da tecla N).
     */
    public static List<ContainerSource.Entry> entries(ServerPlayer player) {
        List<ContainerSource.Entry> out = new ArrayList<>();
        for (ContainerSource.Entry entry : NearbyContainers.findAllStorage(player)) {
            out.add(new ContainerSource.Entry(entry.container(),
                    () -> !QuickStackService.openedByAnother(player, entry.container()) && entry.allowed().getAsBoolean(),
                    entry.where()));
        }
        return out;
    }

    /** Só mexe com a mochila/inventário "fechado": com outro menu aberto o jogador já está mexendo em itens. */
    private static boolean idle(ServerPlayer player) {
        return player.containerMenu == player.inventoryMenu;
    }

    private static void preview(ServerPlayer player) {
        if (!idle(player)) {
            return;
        }
        OrganizeSystem.Plan plan = OrganizeSystem.plan(entries(player), stack -> QuickStackService.categoryOff(player, stack));
        PENDING.put(player, new Pending(McCompat.dimensionOf(player), plan));
        List<OrganizeSync.Row> rows = new ArrayList<>();
        for (OrganizeSystem.Move move : plan.moves()) {
            if (rows.size() >= OrganizeSync.MAX_ROWS) {
                break;
            }
            BlockPos from = anchor(move.from());
            BlockPos to = anchor(move.to());
            rows.add(new OrganizeSync.Row(move.item(), move.count(), from, to, name(player, from), name(player, to)));
        }
        int message = plan.isEmpty() ? OrganizeSync.MSG_NOTHING
                : plan.moves().size() > rows.size() ? OrganizeSync.MSG_TOO_MANY : OrganizeSync.MSG_NONE;
        Services.PLATFORM.sendIfSupported(player, new OrganizeSync(OrganizeSync.PREVIEW, rows, !plan.isEmpty(),
                APPLIED.containsKey(player), plan.tidied(), message));
    }

    private static void apply(ServerPlayer player) {
        Pending pending = PENDING.remove(player);
        if (!idle(player)) {
            return;
        }
        if (pending == null || !pending.dimension().equals(McCompat.dimensionOf(player))
                || !OrganizeSystem.apply(pending.plan(), entries(player))) {
            state(player, OrganizeSync.MSG_STALE, false);
            return;
        }
        APPLIED.put(player, pending);
        player.sendOverlayMessage(Component.translatableWithFallback("stashlink.organize.applied", "System organized"));
        state(player, OrganizeSync.MSG_APPLIED, false);
    }

    private static void undo(ServerPlayer player) {
        Pending applied = APPLIED.get(player);
        if (!idle(player)) {
            return;
        }
        if (applied == null) {
            state(player, OrganizeSync.MSG_NOTHING, false);
            return;
        }
        if (!applied.dimension().equals(McCompat.dimensionOf(player)) || !OrganizeSystem.undo(applied.plan(), entries(player))) {
            state(player, OrganizeSync.MSG_UNDO_FAILED, false);
            return;
        }
        APPLIED.remove(player);
        player.sendOverlayMessage(Component.translatableWithFallback("stashlink.organize.undone", "Organization undone"));
        state(player, OrganizeSync.MSG_UNDONE, false);
    }

    private static void state(ServerPlayer player, int message, boolean canApply) {
        Services.PLATFORM.sendIfSupported(player, new OrganizeSync(OrganizeSync.STATE, List.of(), canApply,
                APPLIED.containsKey(player), 0, message));
    }

    // ----------------------------------------------------------------------------------------- 20.3 busca e destaque

    /** Um achado da busca: o container (posições) e a linha que o cliente vê. */
    public record Hit(Set<BlockPos> where, OrganizeSync.Row row) {
    }

    private static void search(ServerPlayer player, OrganizeRequest request, boolean highlightAll) {
        List<Hit> hits = find(player, request);
        if (hits == null) {
            if (!highlightAll) {
                Services.PLATFORM.sendIfSupported(player, new OrganizeSync(OrganizeSync.SEARCH, List.of(), false,
                        APPLIED.containsKey(player), 0, OrganizeSync.MSG_NONE));
            }
            return;
        }
        if (highlightAll) {
            List<Set<BlockPos>> shown = new ArrayList<>();
            for (Hit hit : hits) {
                if (!shown.contains(hit.where())) {
                    shown.add(hit.where());
                }
            }
            OrganizeHighlight.show(player, shown);
            return;
        }
        List<OrganizeSync.Row> rows = new ArrayList<>();
        for (Hit hit : hits) {
            if (rows.size() >= OrganizeSync.MAX_ROWS) {
                break;
            }
            rows.add(hit.row());
        }
        Services.PLATFORM.sendIfSupported(player, new OrganizeSync(OrganizeSync.SEARCH, rows, false,
                APPLIED.containsKey(player), 0, hits.size() > rows.size() ? OrganizeSync.MSG_TOO_MANY : OrganizeSync.MSG_NONE));
    }

    /**
     * A busca em si: os containers por perto (do mais perto ao mais longe) que têm algo do pedido, só os que os mods de
     * proteção liberam. {@code null} se o pedido não diz o que buscar. O servidor só conhece nomes em inglês; por isso o
     * cliente manda os {@code items} que casam no idioma dele, e {@code text} serve de reserva.
     */
    public static List<Hit> find(ServerPlayer player, OrganizeRequest request) {
        Set<Item> wanted = new HashSet<>();
        for (Identifier id : request.items()) {
            BuiltInRegistries.ITEM.getOptional(id).ifPresent(wanted::add);
        }
        String text = request.text().trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        if (wanted.isEmpty() && text.isEmpty()) {
            return null;
        }
        List<Hit> hits = new ArrayList<>();
        for (ContainerSource.Entry entry : NearbyContainers.findAllStorage(player)) {
            List<ItemStack> models = new ArrayList<>();
            List<Integer> counts = new ArrayList<>();
            Container c = entry.container();
            for (int slot = 0; slot < c.getContainerSize(); slot++) {
                ItemStack stack = c.getItem(slot);
                if (stack.isEmpty() || !matches(stack, wanted, text)) {
                    continue;
                }
                int at = indexOf(models, stack);
                if (at < 0) {
                    models.add(stack.copyWithCount(1));
                    counts.add(stack.getCount());
                } else {
                    counts.set(at, counts.get(at) + stack.getCount());
                }
            }
            // A permissão (claims) só é perguntada se o container tem algo que casa: ler o conteúdo já é informação.
            if (models.isEmpty() || !entry.allowed().getAsBoolean()) {
                continue;
            }
            BlockPos anchor = anchor(entry.where());
            String name = name(player, anchor);
            for (int i = 0; i < models.size(); i++) {
                hits.add(new Hit(entry.where(), new OrganizeSync.Row(models.get(i), counts.get(i), anchor, anchor, name, name)));
            }
        }
        return hits;
    }

    private static boolean matches(ItemStack stack, Set<Item> wanted, String text) {
        if (!wanted.isEmpty()) {
            return wanted.contains(stack.getItem());
        }
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().contains(text);
    }

    private static int indexOf(List<ItemStack> models, ItemStack stack) {
        for (int i = 0; i < models.size(); i++) {
            if (ItemStack.isSameItemSameComponents(models.get(i), stack)) {
                return i;
            }
        }
        return -1;
    }

    private static void highlight(ServerPlayer player, BlockPos pos) {
        for (ContainerSource.Entry entry : NearbyContainers.findAllStorage(player)) {
            if (entry.where().contains(pos)) {
                if (entry.allowed().getAsBoolean()) {
                    OrganizeHighlight.show(player, List.of(entry.where()));
                }
                return;
            }
        }
        player.sendOverlayMessage(Component.translatableWithFallback("stashlink.organize.not_found",
                "That container is no longer in range"));
    }

    // ------------------------------------------------------------------------------------------------------ utilidades

    /** A posição que representa o container (a de menor valor; baú duplo tem duas). */
    static BlockPos anchor(Set<BlockPos> where) {
        return where.stream().min(Comparator.comparingLong(BlockPos::asLong)).orElse(BlockPos.ZERO);
    }

    /** O rótulo (Item 14) do bloco, ou vazio. */
    private static String name(ServerPlayer player, BlockPos pos) {
        BlockEntity be = player.level().getBlockEntity(pos);
        if (be == null || !Labels.supports(be)) {
            return "";
        }
        String name = Labels.get(be).name();
        return name.length() > 64 ? name.substring(0, 64) : name;
    }

    /** Quanto de cada item o sistema tem agora (para os testes: a soma tem de ser a mesma antes e depois). */
    public static OrganizeLogic.Totals totals(List<ContainerSource.Entry> entries) {
        OrganizeLogic.Totals totals = new OrganizeLogic.Totals();
        for (ContainerSource.Entry entry : entries) {
            totals.addAll(OrganizeLogic.snapshot(entry.container()));
        }
        return totals;
    }

    /** Há plano de "organizar o sistema" pendente / aplicado para este jogador (para os testes). */
    public static boolean hasPending(ServerPlayer player) {
        return PENDING.containsKey(player);
    }

    public static boolean hasApplied(ServerPlayer player) {
        return APPLIED.containsKey(player);
    }

}
