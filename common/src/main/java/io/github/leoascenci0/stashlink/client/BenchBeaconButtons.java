package io.github.leoascenci0.stashlink.client;

import io.github.leoascenci0.stashlink.compat.mc.BenchCompat;
import io.github.leoascenci0.stashlink.config.Feature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.ArrayList;
import java.util.List;

/**
 * Sinalizador (Item 16.3): os ícones de pagamento que o próprio jogo desenha (netherite, esmeralda, diamante, ouro,
 * ferro) viram botões. Clicar num deles pede ao servidor 1 daquele item do armazenamento no slot de pagamento. O que
 * não existe no raio ganha um véu vermelho (só um retângulo translúcido, nenhuma textura nova) e a dica diz por quê.
 * O sinalizador não tem o painel "Armazenamento": a lista do servidor só serve para saber o que há.
 */
public final class BenchBeaconButtons {
    /** Ícone de item: 16 px. */
    private static final int ICON = 16;
    /** O mesmo realce translúcido que o jogo põe sobre o slot sob o mouse. */
    private static final int HOVER = 0x80FFFFFF;
    /** Véu vermelho sobre o ícone do pagamento que não há no raio (como o slot vermelho do livro de receitas). */
    private static final int MISSING = 0x80FF2020;

    static final String TIP = "stashlink.bench.beacon.tip";
    static final String NONE = "stashlink.bench.beacon.none";
    static final String NONE_RADIUS = "stashlink.bench.beacon.none.radius";

    private final AbstractContainerMenu menu;
    private final List<BenchCompat.BeaconIcon> icons = BenchCompat.beaconIcons();
    private int left;
    private int top;

    private BenchBeaconButtons(AbstractContainerMenu menu) {
        this.menu = menu;
    }

    /** Os botões para a tela aberta, se ela é um sinalizador; {@code null} caso contrário. */
    public static BenchBeaconButtons create(AbstractContainerMenu menu) {
        return BenchCompat.beaconPaymentSlot(menu) == null ? null : new BenchBeaconButtons(menu);
    }

    public void layout(int leftPos, int topPos) {
        left = leftPos;
        top = topPos;
    }

    /** Função ligada para mim, servidor com o mod e a lista deste sinalizador já chegou. */
    private boolean visible() {
        return BenchClient.activeFor(menu) && ClientFeatures.enabled(Feature.BENCH_BEACON);
    }

    private BenchCompat.BeaconIcon at(double mx, double my) {
        for (BenchCompat.BeaconIcon icon : icons) {
            int x = left + icon.x();
            int y = top + icon.y();
            if (mx >= x && mx < x + ICON && my >= y && my < y + ICON) {
                return icon;
            }
        }
        return null;
    }

    private static int count(BenchCompat.BeaconIcon icon) {
        return BenchClient.countOf(icon.item());
    }

    /** Desenha só por cima dos ícones do jogo: véu vermelho no que falta, realce no que está sob o mouse, e a dica. */
    public void draw(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (!visible()) {
            return;
        }
        for (BenchCompat.BeaconIcon icon : icons) {
            if (count(icon) <= 0) {
                int x = left + icon.x();
                int y = top + icon.y();
                graphics.fill(x, y, x + ICON, y + ICON, MISSING);
            }
        }
        BenchCompat.BeaconIcon hovered = at(mouseX, mouseY);
        if (hovered == null) {
            return;
        }
        int x = left + hovered.x();
        int y = top + hovered.y();
        int have = count(hovered);
        if (have > 0) {
            graphics.fill(x, y, x + ICON, y + ICON, HOVER);
        }
        List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(Minecraft.getInstance(), hovered.item()));
        if (have > 0) {
            lines.add(BenchText.inStorage(have));
            lines.add(Component.translatable(TIP));
        } else {
            int radius = BenchClient.radius();
            lines.add(radius >= 0 ? Component.translatable(NONE_RADIUS, radius) : Component.translatable(NONE));
        }
        graphics.setComponentTooltipForNextFrame(Minecraft.getInstance().font, lines, mouseX, mouseY);
    }

    /** Clique esquerdo ou direito num ícone: pede 1 ao servidor (o slot só guarda 1). Outros botões seguem para o jogo. */
    public boolean mouseClicked(MouseButtonEvent event) {
        if (!visible() || (event.button() != BenchText.LEFT && event.button() != BenchText.RIGHT)) {
            return false;
        }
        BenchCompat.BeaconIcon icon = at(event.x(), event.y());
        if (icon == null) {
            return false;
        }
        if (count(icon) > 0) {
            BenchClient.requestPayment(menu, icon.item());
        }
        return true;
    }
}
