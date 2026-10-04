package io.github.leoascenci0.stashlink.client;

import io.github.leoascenci0.stashlink.clientmode.Click;
import io.github.leoascenci0.stashlink.compat.mc.ClientCompat;
import io.github.leoascenci0.stashlink.config.Feature;
import io.github.leoascenci0.stashlink.lootall.LootAllService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Item 21: com Shift e o botão esquerdo pressionados, cada slot por onde o mouse passa recebe um shift-clique (o mesmo de clicar com
 * Shift), uma vez por passagem. É só clique de inventário: o servidor faz a conta e nada duplica nem some, com ou
 * sem o mod no servidor. Só em baú/barril/shulker (e a mochila aberta junto), nunca em slot de resultado.
 */
public final class HoverCollectClient {
    private static final HoverCollectPass PASS = new HoverCollectPass();
    /** O botão esquerdo está apertado (avisado pelo mixin da tela)? */
    private static boolean leftDown;
    /** Quando (ms) o jogo entregou o último "arrastou com o botão apertado"; sem isso o botão não está de fato apertado. */
    private static long lastDragMs;
    /** O botão já estava apertado no quadro anterior? */
    private static boolean wasLeftDown;

    /** A tela avisa: o botão esquerdo foi apertado ({@code true}) ou solto ({@code false}); também ao abrir a tela. */
    public static void setLeftDown(boolean down) {
        leftDown = down;
    }

    /**
     * A tela avisa: o mouse se moveu com um botão apertado. O jogo só manda isso enquanto o botão está de fato apertado, ao
     * contrário do "soltou", que pode não chegar (outro mod trata o evento, soltar sobre um botão ou fora da janela).
     */
    public static void dragSeen() {
        lastDragMs = System.currentTimeMillis();
    }

    /** Janela em que um "arrastou" ainda vale como prova de que o botão está apertado. */
    private static final long DRAG_WINDOW_MS = 250;

    private HoverCollectClient() {
    }

    /** Chamado a cada quadro da tela de container, com o slot sob o mouse (ou {@code null}). */
    public static void onHover(Minecraft mc, AbstractContainerScreen<?> screen, Slot hovered) {
        AbstractContainerMenu menu = screen.getMenu();
        if (mc.player == null || mc.level == null || mc.player.isSpectator() || !ClientCompat.isShiftDown(mc)
                || !ClientFeatures.enabled(Feature.HOVER_COLLECT) || !LootAllService.isSupportedMenu(menu)
                || !menu.getCarried().isEmpty() || !leftDown) {
            PASS.reset();
            wasLeftDown = false;
            return;
        }
        if (!wasLeftDown) {
            // Acabou de apertar: o shift-clique do jogo trata o slot sob o mouse; só os próximos entram na conta.
            wasLeftDown = true;
            PASS.reset();
            PASS.arm(hovered == null ? -1 : hovered.index);
            return;
        }
        // Entrar em outro slot exige mover o mouse, e mover com o botão apertado gera "arrastou". Sem isso (botão solto
        // que o jogo não avisou), só passar o mouse com Shift não coleta nada.
        if (System.currentTimeMillis() - lastDragMs > DRAG_WINDOW_MS) {
            leftDown = false;
            wasLeftDown = false;
            PASS.reset();
            return;
        }
        // Fora de qualquer slot só "esquece" o slot anterior: voltar a ele depois é uma nova passagem.
        if (!PASS.shouldClick(hovered == null ? -1 : hovered.index, mc.level.getGameTime()) || hovered == null) {
            return;
        }
        if (!hovered.hasItem() || !hovered.mayPickup(mc.player) || isResultLike(hovered)
                || isLockedInventorySlot(mc, hovered)) {
            return;
        }
        int before = hovered.getItem().getCount();
        ItemStack kind = hovered.getItem().copy();
        ClientCompat.click(mc, menu, Click.quickMove(hovered.index));
        // O cliente aplica o clique na hora (previsão): se o slot não mudou, o destino não tinha onde caber.
        boolean moved = !hovered.hasItem() || !ItemStack.isSameItemSameComponents(kind, hovered.getItem())
                || hovered.getItem().getCount() < before;
        PASS.clickResult(moved);
    }

    /** Slot que não aceita item nenhum (resultado de bancada, forja, cortador...): nunca recebe clique daqui. */
    private static boolean isResultLike(Slot slot) {
        return !slot.mayPlace(slot.getItem());
    }

    /** Slot da mochila que o jogador travou na config: esse item fica onde está. */
    private static boolean isLockedInventorySlot(Minecraft mc, Slot slot) {
        return slot.container == mc.player.getInventory()
                && ClientPrefs.lockedSlots.contains(slot.getContainerSlot());
    }
}
