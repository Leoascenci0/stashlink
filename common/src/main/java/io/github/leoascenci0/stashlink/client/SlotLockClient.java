package io.github.leoascenci0.stashlink.client;

import io.github.leoascenci0.stashlink.compat.mc.ClientCompat;
import io.github.leoascenci0.stashlink.lootall.LootAllService;
import io.github.leoascenci0.stashlink.network.LockSlotRequest;
import io.github.leoascenci0.stashlink.network.SlotLocksSync;
import io.github.leoascenci0.stashlink.slotlock.ClientSlotLocks;
import io.github.leoascenci0.stashlink.slotlock.SlotLocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Lado cliente dos slots travados: Alt + clique pede ao servidor para travar/destravar, a lista que o servidor
 * manda vira a "prévia" desenhada no slot vazio. Nada aqui muda item: o servidor decide, e a prévia é só um
 * desenho (nunca existe como item dentro do container do cliente).
 */
public final class SlotLockClient {
    /** Cor do fundo de slot do jogo; sobre o item desenhado dá o efeito de "fantasma". */
    private static final int GHOST_VEIL = 0xA08B8B8B;
    private static final int RESERVED_FRAME = 0xFF4FA3FF;

    private static Consumer<LockSlotRequest> sender = request -> { };
    private static BooleanSupplier serverHasMod = () -> false;

    private SlotLockClient() {
    }

    /** Cada loader diz como enviar o pedido e se o servidor conhece o pacote (sem o mod no servidor, nada acontece). */
    public static void setSender(Consumer<LockSlotRequest> value) {
        sender = value;
    }

    public static void setServerHasMod(BooleanSupplier value) {
        serverHasMod = value;
    }

    /**
     * Alt + clique num slot. Devolve {@code true} se o clique foi tratado aqui (o jogo não deve fazer o clique
     * normal); {@code false} deixa o clique seguir como sempre (slot do próprio inventário, container sem suporte,
     * servidor sem o mod).
     */
    public static boolean onAltClick(Minecraft mc, AbstractContainerScreen<?> screen, Slot slot) {
        AbstractContainerMenu menu = screen.getMenu();
        if (slot == null || mc.player == null || mc.player.isSpectator() || !LootAllService.isSupportedMenu(menu)
                || slot.container == mc.player.getInventory() || !serverHasMod.getAsBoolean()) {
            return false;
        }
        sender.accept(new LockSlotRequest(menu.containerId, slot.index));
        return true;
    }

    /** Recebeu a lista do servidor (thread do cliente): troca o que se sabe sobre os containers do menu aberto. */
    public static void apply(SlotLocksSync payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        AbstractContainerMenu menu = ClientCompat.menu(mc.player);
        if (menu.containerId != payload.containerId()) {
            return;
        }
        Map<Container, Map<Integer, Item>> byContainer = new HashMap<>();
        for (Slot slot : menu.slots) {
            if (slot.container != mc.player.getInventory()) {
                byContainer.computeIfAbsent(slot.container, c -> new HashMap<>());
            }
        }
        for (SlotLocksSync.Entry entry : payload.entries()) {
            if (entry.menuSlot() >= 0 && entry.menuSlot() < menu.slots.size()) {
                Slot slot = menu.slots.get(entry.menuSlot());
                Map<Integer, Item> map = byContainer.get(slot.container);
                if (map != null) {
                    map.put(slot.getContainerSlot(), entry.item().getItem());
                }
            }
        }
        byContainer.forEach(ClientSlotLocks::set);
    }

    /** Desenha a prévia (item fantasma no slot vazio) e um pontinho de "reservado". */
    public static void drawGhost(GuiGraphicsExtractor graphics, Slot slot) {
        Item locked = SlotLocks.lockedItem(slot.container, slot.getContainerSlot());
        if (locked == null) {
            return;
        }
        if (slot.hasItem()) {
            drawMarker(graphics, slot);
            return;
        }
        graphics.fakeItem(new ItemStack(locked), slot.x, slot.y);
        graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, GHOST_VEIL);
        drawMarker(graphics, slot);
    }

    /** Pontinho de 2x2 no canto superior direito: indica "reservado" sem poluir o slot. */
    private static void drawMarker(GuiGraphicsExtractor graphics, Slot slot) {
        graphics.fill(slot.x + 14, slot.y, slot.x + 16, slot.y + 2, RESERVED_FRAME);
    }
}
