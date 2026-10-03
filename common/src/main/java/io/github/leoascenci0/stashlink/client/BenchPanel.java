package io.github.leoascenci0.stashlink.client;

import io.github.leoascenci0.stashlink.compat.mc.BenchCompat;
import io.github.leoascenci0.stashlink.network.BenchPoolSync;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * O painel "Armazenamento" ao lado de uma estação (Item 16): uma grade rolável com o que há nos baús do raio e uma
 * busca. Clique pega um stack e botão direito pega um, direto para o cursor (o servidor faz a retirada e confere).
 * Só aparece se a função vale para mim e o servidor mandou a lista; senão a tela fica exatamente como no jogo base.
 */
public final class BenchPanel {
    private static final int COLS = 7;
    private static final int ROWS = 6;
    private static final int CELL = 18;
    private static final int PAD = 4;
    private static final int SEARCH_H = 14;
    private static final int GAP = 6;
    private static final int WIDTH = PAD * 2 + COLS * CELL + 4;
    private static final int HEIGHT = PAD + SEARCH_H + 2 + ROWS * CELL + PAD;

    private static final int BACKGROUND = 0xE0101010;
    private static final int BORDER = 0xFF555555;
    private static final int SLOT = 0xFF373737;
    private static final int HOVER = 0x80FFFFFF;
    private static final int THUMB = 0xFFAAAAAA;

    private final AbstractContainerMenu menu;
    private final EditBox search;
    private final Screen screen;
    private int x;
    private int y;

    private int scrollRows;
    private int filteredVersion = -1;
    private String filteredQuery = null;
    private List<BenchPoolSync.Entry> filtered = List.of();

    private BenchPanel(AbstractContainerMenu menu, Screen screen) {
        Minecraft mc = Minecraft.getInstance();
        this.menu = menu;
        this.screen = screen;
        search = new EditBox(mc.font, 0, 0, WIDTH - PAD * 2, SEARCH_H,
                Component.translatableWithFallback("stashlink.bench.panel.title", "Storage"));
        search.setMaxLength(32);
        search.setHint(Component.translatableWithFallback("stashlink.bench.panel.title", "Storage"));
        search.setVisible(false);
    }

    /** Cria o painel para a tela aberta, se o menu for uma estação; {@code null} caso contrário. */
    public static BenchPanel create(AbstractContainerMenu menu, int leftPos, int topPos, int imageWidth, Screen screen) {
        if (!BenchCompat.isStation(menu)) {
            return null;
        }
        BenchPanel panel = new BenchPanel(menu, screen);
        panel.layout(leftPos, topPos, imageWidth);
        return panel;
    }

    /**
     * Posição do painel: à direita da estação; se não couber (janela estreita), à esquerda; no pior caso, encostado
     * na borda. Refeita a cada uso porque o livro de receitas empurra a tela depois de ela iniciar.
     */
    public void layout(int leftPos, int topPos, int imageWidth) {
        int right = leftPos + imageWidth + GAP;
        int px = right + WIDTH <= screen.width ? right : Math.max(2, leftPos - GAP - WIDTH);
        x = Math.max(2, Math.min(px, screen.width - WIDTH - 2));
        y = Math.max(2, topPos);
        search.setX(x + PAD);
        search.setY(y + PAD);
    }

    public List<AbstractWidget> widgets() {
        return List.of(search);
    }

    private boolean active() {
        return BenchClient.activeFor(menu);
    }

    private boolean inside(double mx, double my) {
        return mx >= x && mx < x + WIDTH && my >= y && my < y + HEIGHT;
    }

    private int gridX() {
        return x + PAD;
    }

    private int gridY() {
        return y + PAD + SEARCH_H + 2;
    }

    /** A lista depois do filtro da busca; só refaz quando a lista do servidor ou o texto mudou. */
    private List<BenchPoolSync.Entry> entries() {
        String query = search.getValue().trim().toLowerCase(Locale.ROOT);
        if (filteredVersion != BenchClient.version() || !query.equals(filteredQuery)) {
            filteredVersion = BenchClient.version();
            filteredQuery = query;
            if (query.isEmpty()) {
                filtered = BenchClient.pool();
            } else {
                List<BenchPoolSync.Entry> out = new ArrayList<>();
                for (BenchPoolSync.Entry entry : BenchClient.pool()) {
                    if (entry.item().getHoverName().getString().toLowerCase(Locale.ROOT).contains(query)) {
                        out.add(entry);
                    }
                }
                filtered = out;
            }
            scrollRows = 0;
        }
        int maxScroll = Math.max(0, (filtered.size() + COLS - 1) / COLS - ROWS);
        scrollRows = Math.max(0, Math.min(scrollRows, maxScroll));
        return filtered;
    }

    /** Desenha o painel por cima da tela (depois dos slots) e a dica do item sob o mouse. */
    public void draw(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        search.setVisible(active());
        if (!active()) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        graphics.fill(x, y, x + WIDTH, y + HEIGHT, BACKGROUND);
        graphics.outline(x, y, WIDTH, HEIGHT, BORDER);
        // O campo de busca já foi desenhado com os outros componentes da tela, mas o fundo do painel o cobriu.
        search.extractRenderState(graphics, mouseX, mouseY, delta);

        List<BenchPoolSync.Entry> list = entries();
        if (list.isEmpty()) {
            graphics.centeredText(font, Component.translatableWithFallback("stashlink.bench.panel.empty",
                    "Nothing nearby"), x + WIDTH / 2, gridY() + ROWS * CELL / 2 - 4, 0xFFAAAAAA);
            return;
        }
        BenchPoolSync.Entry hovered = null;
        int hx = 0;
        int hy = 0;
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int index = (scrollRows + row) * COLS + col;
                int cx = gridX() + col * CELL;
                int cy = gridY() + row * CELL;
                graphics.fill(cx, cy, cx + CELL - 1, cy + CELL - 1, SLOT);
                if (index >= list.size()) {
                    continue;
                }
                BenchPoolSync.Entry entry = list.get(index);
                graphics.fakeItem(entry.item(), cx + 1, cy + 1);
                graphics.itemDecorations(font, entry.item(), cx + 1, cy + 1, shortCount(entry.count()));
                if (mouseX >= cx && mouseX < cx + CELL - 1 && mouseY >= cy && mouseY < cy + CELL - 1) {
                    graphics.fill(cx, cy, cx + CELL - 1, cy + CELL - 1, HOVER);
                    hovered = entry;
                    hx = mouseX;
                    hy = mouseY;
                }
            }
        }
        int totalRows = (list.size() + COLS - 1) / COLS;
        if (totalRows > ROWS) {
            int trackX = gridX() + COLS * CELL;
            int trackH = ROWS * CELL;
            int thumbH = Math.max(8, trackH * ROWS / totalRows);
            int thumbY = gridY() + (trackH - thumbH) * scrollRows / (totalRows - ROWS);
            graphics.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, THUMB);
        }
        if (hovered != null) {
            List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(Minecraft.getInstance(), hovered.item()));
            lines.add(Component.literal("x" + hovered.count()));
            lines.add(Component.translatableWithFallback("stashlink.bench.panel.tip",
                    "Click: take a stack. Right-click: take one."));
            graphics.setComponentTooltipForNextFrame(font, lines, hx, hy);
        }
    }

    /** "1,2k" em vez de "1234": cabe no slot. Abaixo de mil mostra o número; um só não mostra nada, como o jogo. */
    static String shortCount(int count) {
        if (count < 1000) {
            return count == 1 ? "" : Integer.toString(count);
        }
        if (count < 100_000) {
            return String.format(Locale.ROOT, "%.1fk", count / 1000.0);
        }
        return count / 1000 + "k";
    }

    /** Clique no painel: devolve {@code true} se foi dentro dele (o jogo não deve tratar, nem soltar o item do cursor). */
    public boolean mouseClicked(double mx, double my, int button) {
        if (!active() || !inside(mx, my)) {
            return false;
        }
        int col = (int) ((mx - gridX()) / CELL);
        int row = (int) ((my - gridY()) / CELL);
        if (mx >= gridX() && my >= gridY() && col >= 0 && col < COLS && row >= 0 && row < ROWS && (button == 0 || button == 1)) {
            int index = (scrollRows + row) * COLS + col;
            List<BenchPoolSync.Entry> list = entries();
            if (index < list.size()) {
                BenchClient.request(menu, list.get(index), button == 1);
            }
        }
        return true;
    }

    public boolean mouseScrolled(double mx, double my, double scrollY) {
        if (!active() || !inside(mx, my)) {
            return false;
        }
        scrollRows -= (int) Math.signum(scrollY);
        entries();
        return true;
    }

    /** Teclas com a busca em foco: tudo vai para o campo e nunca para o jogo (senão E fecharia a tela). */
    public boolean onKey(KeyEvent event) {
        if (!active() || !search.isFocused() || event.isEscape()) {
            return false;
        }
        return search.keyPressed(event) || search.canConsumeInput();
    }
}
