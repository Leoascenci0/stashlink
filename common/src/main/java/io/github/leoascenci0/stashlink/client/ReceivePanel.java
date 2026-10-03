package io.github.leoascenci0.stashlink.client;

import io.github.leoascenci0.stashlink.quickstack.QuickStackReceive;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import java.util.List;

/**
 * O botão "N" na tela do baú (Item 17): normal = o baú recebe itens com a tecla N; cinza riscado = a N nunca
 * coloca nada nele. Só aparece com o mod no servidor. O valor mostrado é o que o servidor contou
 * ({@link QuickStackReceive#accepts}); o clique só <b>pede</b> a mudança e o servidor decide.
 */
public final class ReceivePanel {
    /** Largura reservada à direita do título para o botão (LabelPanel encolhe o campo do nome por isso). */
    public static final int RESERVED = 14;

    private final Button button;
    private final AbstractContainerMenu menu;
    private final Container storage;

    private ReceivePanel(AbstractContainerMenu menu, Container storage, int left, int top, int width) {
        this.menu = menu;
        this.storage = storage;
        // À esquerda do lápis do rótulo (que fica a 22 px da borda direita).
        button = Button.builder(Component.literal("N"), b -> toggle())
                .bounds(left + width - 20 - RESERVED, top + 4, 12, 12).build();
        refresh();
    }

    /** Cria o botão para a tela de container aberta, ou {@code null} se não cabe (sem mod no servidor, tecla N desligada). */
    public static ReceivePanel create(AbstractContainerMenu menu, net.minecraft.world.entity.player.Inventory inventory,
                                      int left, int top, int width) {
        if (!SlotLockClient.receivesButtonAvailable()) {
            return null;
        }
        for (Slot slot : menu.slots) {
            if (slot.container != inventory) {
                return new ReceivePanel(menu, slot.container, left, top, width);
            }
        }
        return null;
    }

    public List<AbstractWidget> widgets() {
        return List.of(button);
    }

    private void toggle() {
        SlotLockClient.requestReceives(menu.containerId, !QuickStackReceive.accepts(storage));
    }

    /** Põe no botão o que o servidor contou (chamado a cada desenho: é barato). */
    public void refresh() {
        boolean on = QuickStackReceive.accepts(storage);
        // Ligado: "N" na cor normal dos botões (igual ao lápis). Desligado: cinza e riscado.
        button.setMessage(on ? Component.literal("N")
                : Component.literal("N").withStyle(ChatFormatting.GRAY, ChatFormatting.STRIKETHROUGH));
        button.setTooltip(Tooltip.create(on
                ? Component.translatableWithFallback("stashlink.receives.tip_on",
                        "N stores items here. Click to stop N from putting items in this container")
                : Component.translatableWithFallback("stashlink.receives.tip_off",
                        "N never puts items here. Click to let N store items in this container")));
    }
}
