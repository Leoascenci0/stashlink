package io.github.leoascenci0.stashlink.client;

import io.github.leoascenci0.stashlink.compat.mc.BenchCompat;
import io.github.leoascenci0.stashlink.compat.mc.ClientCompat;
import io.github.leoascenci0.stashlink.config.Feature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Botão de combustível das fornalhas (fornalha, defumador, alto-forno; Item 16.3): um botão do jogo com o balde de lava,
 * à esquerda do slot de combustível. Esquerdo = uma pilha, direito = um, Shift+esquerdo = o máximo (o mesmo gesto do
 * painel). O cliente só pede: o servidor escolhe o combustível pela mesma regra ({@link BenchCompat#pickFuel}), tira do
 * armazenamento e põe no slot. Sem combustível no raio, o botão fica desativado e a dica diz por quê.
 * Essas estações usam o livro de receitas do jogo (não o painel), por isso o botão é separado.
 */
public final class BenchFuelButton {
    /** Tamanho do botão: o de um slot com borda (18), como os botões pequenos do jogo. */
    private static final int SIZE = 18;
    /** Espaço entre o botão e a borda do slot de combustível. */
    private static final int GAP = 2;
    /** O ícone (16 px) centrado no botão. */
    private static final int ICON_INSET = 1;
    /** A borda do slot fica 1 px fora da posição do item. */
    private static final int SLOT_BORDER = 1;

    private static final Identifier BUTTON = Identifier.withDefaultNamespace("widget/button");
    private static final Identifier BUTTON_HOVER = Identifier.withDefaultNamespace("widget/button_highlighted");
    private static final Identifier BUTTON_OFF = Identifier.withDefaultNamespace("widget/button_disabled");
    private static final ItemStack ICON = new ItemStack(Items.LAVA_BUCKET);

    static final String TITLE = "stashlink.bench.fuel.title";
    static final String NONE = "stashlink.bench.fuel.none";
    static final String NONE_RADIUS = "stashlink.bench.fuel.none.radius";

    private final AbstractContainerMenu menu;
    private final Slot slot;
    private int x;
    private int y;

    private BenchFuelButton(AbstractContainerMenu menu, Slot slot) {
        this.menu = menu;
        this.slot = slot;
    }

    /** O botão para a tela aberta, se ela é uma fornalha; {@code null} caso contrário. */
    public static BenchFuelButton create(AbstractContainerMenu menu) {
        Slot slot = BenchCompat.fuelSlot(menu);
        return slot == null ? null : new BenchFuelButton(menu, slot);
    }

    /** Acompanha a estação: abrir/fechar o livro de receitas desloca {@code leftPos} sem recriar a tela. */
    public void layout(int leftPos, int topPos) {
        x = leftPos + slot.x - SLOT_BORDER - GAP - SIZE;
        y = topPos + slot.y - SLOT_BORDER;
    }

    /** Função ligada para mim, servidor com o mod e a lista desta fornalha já chegou. */
    private boolean visible() {
        return BenchClient.activeFor(menu) && ClientFeatures.enabled(Feature.BENCH_FUEL);
    }

    /** O combustível que um clique poria agora (vazio = nada a pôr). */
    private ItemStack pick() {
        return BenchCompat.pickFuel(slot, stack -> BenchClient.countOf(stack) > 0);
    }

    private boolean over(double mx, double my) {
        return mx >= x && mx < x + SIZE && my >= y && my < y + SIZE;
    }

    public void draw(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (!visible()) {
            return;
        }
        ItemStack fuel = pick();
        boolean hover = over(mouseX, mouseY);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, fuel.isEmpty() ? BUTTON_OFF : hover ? BUTTON_HOVER : BUTTON,
                x, y, SIZE, SIZE);
        graphics.fakeItem(ICON, x + ICON_INSET, y + ICON_INSET);
        if (hover) {
            graphics.setComponentTooltipForNextFrame(Minecraft.getInstance().font, tooltip(fuel), mouseX, mouseY);
        }
    }

    private List<Component> tooltip(ItemStack fuel) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(TITLE));
        if (fuel.isEmpty()) {
            int radius = BenchClient.radius();
            lines.add(radius >= 0 ? Component.translatable(NONE_RADIUS, radius) : Component.translatable(NONE));
        } else {
            lines.add(fuel.getHoverName());
            lines.add(BenchText.inStorage(BenchClient.countOf(fuel)));
            lines.add(Component.translatable(BenchText.ITEM_TIP));
        }
        return lines;
    }

    /** Clique no botão: pede ao servidor e não deixa o clique chegar ao jogo. Outros botões do mouse seguem para o jogo. */
    public boolean mouseClicked(MouseButtonEvent event) {
        if (!visible() || !over(event.x(), event.y())) {
            return false;
        }
        BenchText.Amount amount = BenchText.amountFor(event.button(), ClientCompat.isShiftDown(Minecraft.getInstance()));
        if (amount == null) {
            return false;
        }
        ItemStack fuel = pick();
        if (!fuel.isEmpty()) {
            BenchClient.requestFuel(menu, fuel, BenchText.one(amount));
        }
        return true;
    }
}
