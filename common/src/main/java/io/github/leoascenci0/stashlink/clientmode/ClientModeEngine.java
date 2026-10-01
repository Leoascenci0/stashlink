package io.github.leoascenci0.stashlink.clientmode;

import io.github.leoascenci0.stashlink.client.ClientPrefs;
import io.github.leoascenci0.stashlink.compat.mc.ClientCompat;
import io.github.leoascenci0.stashlink.config.PlayerPrefs;
import io.github.leoascenci0.stashlink.lootall.LootAllService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;

/**
 * O motor do modo cliente: guarda a sessão em andamento, o cache de conteúdo, e é movido uma vez
 * por tick do cliente por {@code ClientMode.tick}. Tudo é estado de <b>uma</b> conexão: ao trocar de mundo o
 * estado é jogado fora.
 *
 * <p>Regras de UX: espectador, agachado ou com outra tela aberta = ignora o pedido; já tem sessão rodando =
 * ignora (nada de empilhar pedidos).
 */
public final class ClientModeEngine {
    /** Cliques por tick dentro de um container (poucos: não parecer uma rajada de pacotes). */
    public static final int CLICKS_PER_TICK = 5;
    /** Máximo de containers abertos por aperto da N. */
    static final int MAX_CONTAINERS_QUICK_STACK = 8;
    /** Container que falhou ao abrir fica "de castigo" por este tempo (evita insistir em baú bloqueado). */
    private static final long FAIL_MEMORY_TICKS = 200;

    private static final ContentsCache CACHE = new ContentsCache();
    private static final Map<Long, Long> FAILED_UNTIL = new HashMap<>();

    private static ContainerSession session;
    private static ClientLevel level;
    // Aprendizado do que o jogador abre sozinho (para o cache).
    private static AbstractContainerMenu learnMenu;
    private static long[] learnKeys;
    private static int learnTick;

    private ClientModeEngine() {
    }

    /** Mensagem curta na barra de ação. */
    static void say(Minecraft mc, Component message) {
        ClientCompat.overlay(mc, message);
    }

    // ------------------------------------------------------------------ tick

    public static void tick(Minecraft mc, boolean active) {
        if (mc.level != level) {
            // Mundo novo (entrou/saiu/mudou de dimensão): nada do que sabíamos vale.
            level = mc.level;
            reset();
        }
        if (!active) {
            if (session != null) {
                session.abort(mc, "client mode off");
                session = null;
            }
            return;
        }
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }
        if (session != null) {
            if (session.tick(mc)) {
                session = null;
            }
            if (session != null && session.isAttached()) {
                learnFromOpenMenu(mc, player);
            }
            return;
        }
        learnFromOpenMenu(mc, player);
    }

    private static void reset() {
        session = null;
        CACHE.clear();
        FAILED_UNTIL.clear();
        learnMenu = null;
        learnKeys = null;
    }

    // ------------------------------------------------------------------ teclas

    /** Tecla N: abre os containers próximos que já têm o que há na mochila e guarda lá. */
    public static void startQuickStack(Minecraft mc) {
        if (!canStart(mc) || ClientCompat.hasScreenOpen(mc)) {
            return;
        }
        LocalPlayer player = mc.player;
        IntPredicate locked = lockedPredicate();
        List<ItemStack> inv = player.getInventory().getNonEquipmentItems();
        // Só a mochila conta (hotbar nunca é esvaziada), e só o que não está travado.
        List<ItemStack> storable = new java.util.ArrayList<>();
        for (int i = MenuEntry.HOTBAR_SIZE; i < Math.min(inv.size(), MenuEntry.INVENTORY_SIZE); i++) {
            if (!inv.get(i).isEmpty() && !locked.test(i)) {
                storable.add(inv.get(i));
            }
        }
        List<Candidate> found = filterFailed(mc, ClientContainers.find(mc, true, radiusCap()));
        if (storable.isEmpty() || found.isEmpty()) {
            say(mc, Component.translatableWithFallback("stashlink.client_mode.quick_stack.nothing",
                    "Nothing to store nearby"));
            return;
        }
        // Ordem: o cache diz que tem algo que casa (0) > nunca visto (1) > visto e sem nada que case (2).
        List<Candidate> order = ClientMoveLogic.order(found, c -> {
            Boolean has = CACHE.has(c.keys(), seen -> storable.stream()
                    .anyMatch(s -> ItemStack.isSameItemSameComponents(s, seen)));
            return has == null ? 1 : (has ? 0 : 2);
        }, MAX_CONTAINERS_QUICK_STACK);
        session = ContainerSession.opening(mc, new QuickStackJob(locked, CACHE), order, c -> markFailed(mc, c));
    }

    /** Tecla W: puxa tudo do container que o jogador tem aberto (não abre nem fecha nada). */
    public static void startLootAll(Minecraft mc) {
        if (!canStart(mc)) {
            return;
        }
        AbstractContainerMenu menu = ClientCompat.screenMenu(mc);
        if (menu == null || menu != ClientCompat.menu(mc.player) || !LootAllService.isSupportedMenu(menu)) {
            return;
        }
        session = ContainerSession.attached(mc, new PullJob(lockedPredicate(), !ClientPrefs.lockedSlots.isEmpty(), CACHE));
    }

    // ------------------------------------------------------------------ apoio

    /** Pode começar uma sessão agora? (vivo, não espectador, não agachado, nenhuma sessão rodando.) */
    private static boolean canStart(Minecraft mc) {
        LocalPlayer p = mc.player;
        return p != null && mc.level != null && session == null && p.isAlive() && !p.isSpectator()
                && !ClientCompat.isSneaking(p);
    }

    private static IntPredicate lockedPredicate() {
        return slot -> ClientPrefs.lockedSlots.contains(slot);
    }

    /** Teto de distância das preferências do jogador (raio); sem preferência, só vale o alcance do jogo. */
    private static double radiusCap() {
        return ClientPrefs.radius == PlayerPrefs.UNSET ? 0 : ClientPrefs.radius;
    }

    private static void markFailed(Minecraft mc, Candidate c) {
        if (mc.level != null) {
            FAILED_UNTIL.put(c.pos().asLong(), mc.level.getGameTime() + FAIL_MEMORY_TICKS);
        }
    }

    private static List<Candidate> filterFailed(Minecraft mc, List<Candidate> all) {
        long now = mc.level.getGameTime();
        FAILED_UNTIL.values().removeIf(until -> until <= now);
        return all.stream().filter(c -> !FAILED_UNTIL.containsKey(c.pos().asLong())).toList();
    }

    /**
     * Quando o jogador abre um baú/shulker com as próprias mãos, anotamos o conteúdo no cache (a posição é o
     * bloco para onde a mira apontava quando o menu abriu). Só uma dica de ordem, nunca afeta o que é movido.
     */
    private static void learnFromOpenMenu(Minecraft mc, LocalPlayer player) {
        AbstractContainerMenu menu = ClientCompat.menu(player);
        if (menu == ClientCompat.inventoryMenu(player) || !LootAllService.isSupportedMenu(menu)) {
            learnMenu = null;
            learnKeys = null;
            return;
        }
        if (menu != learnMenu) {
            learnMenu = menu;
            BlockPos looked = ClientCompat.lookedAtBlock(mc);
            learnKeys = looked == null || mc.level == null ? null : ClientContainers.keysAt(mc.level, looked);
            learnTick = 0;
        }
        // A cada 4 ticks (e logo no começo): o conteúdo chega depois do menu e muda enquanto o jogador mexe.
        if (learnKeys != null && learnTick++ % 4 == 2) {
            CACHE.put(learnKeys, MenuSnapshot.containerStacks(MenuSnapshot.of(menu, player.getInventory())));
        }
    }
}
