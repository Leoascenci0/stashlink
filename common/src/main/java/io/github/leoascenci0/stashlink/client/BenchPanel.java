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
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
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
    // Mesma tela do livro de receitas do jogo (textures/gui/recipe_book.png, 147x166): busca no topo,
    // grade 5x4 de botões de 25 px e paginação embaixo.
    private static final int COLS = 5;
    private static final int ROWS = 4;
    private static final int CELL = 25;
    private static final int WIDTH = 147;
    private static final int HEIGHT = 166;
    private static final int GRID_X = 11;
    private static final int GRID_Y = 31;
    private static final int SEARCH_X = 25;
    private static final int SEARCH_Y = 13;
    private static final int SEARCH_W = 81;
    private static final int SEARCH_H = 14;
    private static final int PAGE_Y = 137;
    private static final int PAGE_BACK_X = 38;
    private static final int PAGE_FORWARD_X = 93;
    private static final int PAGE_W = 12;
    private static final int PAGE_H = 17;
    private static final int GAP = 6;

    private static final Identifier BOOK = Identifier.withDefaultNamespace("textures/gui/recipe_book.png");
    private static final Identifier SLOT = Identifier.withDefaultNamespace("recipe_book/slot_craftable");
    private static final Identifier FORWARD = Identifier.withDefaultNamespace("recipe_book/page_forward");
    private static final Identifier FORWARD_HOVER = Identifier.withDefaultNamespace("recipe_book/page_forward_highlighted");
    private static final Identifier BACKWARD = Identifier.withDefaultNamespace("recipe_book/page_backward");
    private static final Identifier BACKWARD_HOVER = Identifier.withDefaultNamespace("recipe_book/page_backward_highlighted");
    private static final int HOVER = 0x80FFFFFF;
    private static final int TEXT = 0xFF404040;

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
        search = new EditBox(mc.font, 0, 0, SEARCH_W, SEARCH_H,
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
        search.setX(x + SEARCH_X);
        search.setY(y + SEARCH_Y);
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
        return x + GRID_X;
    }

    private int gridY() {
        return y + GRID_Y;
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
        graphics.blit(RenderPipelines.GUI_TEXTURED, BOOK, x, y, 1.0F, 1.0F, WIDTH, HEIGHT, 256, 256);
        // O campo de busca já foi desenhado com os outros componentes da tela, mas o fundo do painel o cobriu.
        search.extractRenderState(graphics, mouseX, mouseY, delta);

        List<BenchPoolSync.Entry> list = entries();
        if (list.isEmpty()) {
            // Texto longo quebra em linhas dentro do painel (antes vazava para fora dele).
            int ty = gridY() + 2;
            for (var line : font.split(Component.translatableWithFallback("stashlink.bench.panel.empty",
                    "Nothing usable nearby"), WIDTH - GRID_X * 2)) {
                graphics.text(font, line, x + GRID_X, ty, TEXT, false);
                ty += font.lineHeight + 2;
            }
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
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT, cx, cy, CELL, CELL);
                if (index >= list.size()) {
                    continue;
                }
                BenchPoolSync.Entry entry = list.get(index);
                graphics.fakeItem(entry.item(), cx + 4, cy + 4);
                graphics.itemDecorations(font, entry.item(), cx + 4, cy + 4, shortCount(entry.count()));
                if (mouseX >= cx && mouseX < cx + CELL && mouseY >= cy && mouseY < cy + CELL) {
                    graphics.fill(cx + 4, cy + 4, cx + CELL - 4, cy + CELL - 4, HOVER);
                    hovered = entry;
                    hx = mouseX;
                    hy = mouseY;
                }
            }
        }
        int totalRows = (list.size() + COLS - 1) / COLS;
        if (totalRows > ROWS) {
            int pages = (totalRows + ROWS - 1) / ROWS;
            int page = scrollRows / ROWS + 1;
            graphics.centeredText(font, page + "/" + pages, x + WIDTH / 2 + 1, y + PAGE_Y + 5, TEXT);
            if (page > 1) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, over(mouseX, mouseY, PAGE_BACK_X) ? BACKWARD_HOVER : BACKWARD,
                        x + PAGE_BACK_X, y + PAGE_Y, PAGE_W, PAGE_H);
            }
            if (page < pages) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, over(mouseX, mouseY, PAGE_FORWARD_X) ? FORWARD_HOVER : FORWARD,
                        x + PAGE_FORWARD_X, y + PAGE_Y, PAGE_W, PAGE_H);
            }
        }
        if (hovered != null) {
            List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(Minecraft.getInstance(), hovered.item()));
            lines.add(Component.literal("x" + hovered.count()));
            lines.add(Component.translatableWithFallback("stashlink.bench.panel.tip",
                    "Click: take a stack. Right-click: take one."));
            graphics.setComponentTooltipForNextFrame(font, lines, hx, hy);
        }
    }

    private boolean over(double mx, double my, int arrowX) {
        return mx >= x + arrowX && mx < x + arrowX + PAGE_W && my >= y + PAGE_Y && my < y + PAGE_Y + PAGE_H;
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
        int totalRows = (entries().size() + COLS - 1) / COLS;
        if (totalRows > ROWS && button == 0) {
            if (over(mx, my, PAGE_BACK_X)) {
                scrollRows -= ROWS;
                entries();
                return true;
            }
            if (over(mx, my, PAGE_FORWARD_X)) {
                scrollRows += ROWS;
                entries();
                return true;
            }
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
