package io.github.leoascenci0.stashlink.client;

import io.github.leoascenci0.stashlink.compat.mc.BenchCompat;
import io.github.leoascenci0.stashlink.compat.mc.StationRecipes;
import io.github.leoascenci0.stashlink.network.BenchPoolSync;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

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
    private static final int GAP = 1;
    /** Abas: botões de 35x27 à esquerda do painel (como as categorias do livro de receitas). */
    private static final int TAB_W = 35;
    private static final int TAB_H = 27;
    private static final int TAB_OVERLAP = 30;
    /** Numeração de botão do 26.3 (ver {@code AbstractContainerScreen.getContainerClickButton}). */
    private static final int LEFT = 1;
    private static final int RIGHT = 3;

    private static final Identifier BOOK = Identifier.withDefaultNamespace("textures/gui/recipe_book.png");
    private static final Identifier TAB = Identifier.withDefaultNamespace("recipe_book/tab");
    private static final Identifier TAB_SELECTED = Identifier.withDefaultNamespace("recipe_book/tab_selected");
    private static final Identifier SLOT = Identifier.withDefaultNamespace("recipe_book/slot_craftable");
    private static final Identifier SLOT_MISSING = Identifier.withDefaultNamespace("recipe_book/slot_uncraftable");
    private static final Identifier FORWARD = Identifier.withDefaultNamespace("recipe_book/page_forward");
    private static final Identifier FORWARD_HOVER = Identifier.withDefaultNamespace("recipe_book/page_forward_highlighted");
    private static final Identifier BACKWARD = Identifier.withDefaultNamespace("recipe_book/page_backward");
    private static final Identifier BACKWARD_HOVER = Identifier.withDefaultNamespace("recipe_book/page_backward_highlighted");
    private static final int HOVER = 0x80FFFFFF;
    /** Branco, como o texto do livro do jogo: o fundo do livro é escuro. */
    private static final int TEXT = 0xFFFFFFFF;

    private final AbstractContainerMenu menu;
    private final EditBox search;
    private final ImageButton back = new ImageButton(0, 0, PAGE_W, PAGE_H, new WidgetSprites(BACKWARD, BACKWARD_HOVER), b -> { },
            Component.translatable("gui.recipebook.previous_page"));
    private final ImageButton forward = new ImageButton(0, 0, PAGE_W, PAGE_H, new WidgetSprites(FORWARD, FORWARD_HOVER), b -> { },
            Component.translatable("gui.recipebook.next_page"));
    private final Screen screen;
    /** A estação é o tear (aba "Cores", banner pintado, corante escolhido). */
    private final boolean loom;
    private int x;
    private int y;

    private final List<BenchTabs.Tab> tabs;
    private int tab;
    /** Cor de corante escolhida na aba "Cores" do tear (id de {@code DyeColor}); -1 até o servidor mandar a lista. */
    private int selectedColor = -1;
    private int filteredTab = -2;
    private int scrollRows;
    private int filteredVersion = -1;
    private String filteredQuery = null;
    private List<BenchPoolSync.Entry> filtered = List.of();

    private BenchPanel(AbstractContainerMenu menu, Screen screen) {
        Minecraft mc = Minecraft.getInstance();
        this.menu = menu;
        this.screen = screen;
        this.loom = BenchCompat.stationOf(menu) == BenchCompat.Station.LOOM;
        this.tabs = BenchTabs.of(menu);
        search = new EditBox(mc.font, 0, 0, SEARCH_W, SEARCH_H,
                Component.translatableWithFallback("stashlink.bench.panel.title", "Storage"));
        search.setMaxLength(32);
        search.setTextColor(-1);
        search.setHint(Component.translatable("gui.recipebook.search_hint").withStyle(EditBox.SEARCH_HINT_STYLE));
        search.setVisible(false);
    }

    /** Cria o painel para a tela aberta, se o menu for uma estação; {@code null} caso contrário. */
    public static BenchPanel create(AbstractContainerMenu menu, int leftPos, int topPos, int imageWidth, Screen screen) {
        // Bancada e fornalhas já têm o livro de receitas do jogo (que usa os baús e pinta de vermelho o que falta):
        // duas telas iguais lado a lado só atrapalham.
        if (!BenchCompat.isStation(menu) || BenchCompat.hasRecipeBook(menu)) {
            return null;
        }
        BenchPanel panel = new BenchPanel(menu, screen);
        panel.layout(leftPos, topPos, imageWidth);
        return panel;
    }

    /**
     * Onde a estação fica quando o painel está ao lado: as duas telas viram um bloco só, centralizado (o painel à
     * esquerda, grudado, como o livro de receitas da fornalha). Sem espaço na janela, a estação não se move.
     */
    public static int stationLeft(int screenWidth, int imageWidth, int defaultLeft) {
        int start = (screenWidth - (WIDTH + GAP + imageWidth)) / 2;
        return start >= TAB_OVERLAP ? start + WIDTH + GAP : defaultLeft;
    }

    /** Posição do painel: à esquerda da estação, grudado nela e na mesma altura do livro do jogo. */
    public void layout(int leftPos, int topPos, int imageWidth) {
        int min = tabs.isEmpty() ? 2 : TAB_OVERLAP + 2;
        x = Math.max(min, leftPos - WIDTH - GAP);
        y = Math.max(2, (screen.height - HEIGHT) / 2);
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
        return (mx >= x && mx < x + WIDTH && my >= y && my < y + HEIGHT) || tabAt(mx, my) >= 0;
    }

    /** Qual aba está sob o ponto, ou -1. */
    private int tabAt(double mx, double my) {
        for (int i = 0; i < tabs.size(); i++) {
            int tx = x - TAB_OVERLAP;
            int ty = y + 3 + TAB_H * i;
            if (mx >= tx && mx < tx + TAB_W && my >= ty && my < ty + TAB_H) {
                return i;
            }
        }
        return -1;
    }

    /** O corante da cor escolhida (vem na lista do servidor); a entrada vermelha diz que não há dele agora. */
    private BenchPoolSync.Entry colorEntry() {
        return BenchClient.colorPick(selectedColor);
    }

    /** O resultado está em vermelho? Falta material, ou (tear) não há corante da cor escolhida. */
    private boolean red(BenchPoolSync.Entry e) {
        if (e.missing()) {
            return true;
        }
        if (loom && e.isResult()) {
            BenchPoolSync.Entry dye = colorEntry();
            return dye == null || dye.missing();
        }
        return false;
    }

    /** O ícone mostrado: no tear, o banner com o padrão na cor escolhida. */
    private ItemStack iconOf(BenchPoolSync.Entry e) {
        return loom && e.isResult() && selectedColor >= 0
                ? StationRecipes.recolor(e.item(), selectedColor) : e.item();
    }

    private int gridX() {
        return x + GRID_X;
    }

    private int gridY() {
        return y + GRID_Y;
    }

    /** A lista da aba atual depois do filtro da busca; só refaz quando a lista do servidor, a aba ou o texto mudou. */
    private List<BenchPoolSync.Entry> entries() {
        String query = search.getValue().trim().toLowerCase(Locale.ROOT);
        int tabNow = tabs.isEmpty() ? -1 : tab;
        if (filteredVersion != BenchClient.version() || !query.equals(filteredQuery) || filteredTab != tabNow) {
            filteredVersion = BenchClient.version();
            boolean same = query.equals(filteredQuery) && filteredTab == tabNow;
            filteredQuery = query;
            filteredTab = tabNow;
            // Cor padrão do tear: a primeira que tem corante (ou a primeira); mantém a escolhida enquanto ela existir.
            if (loom && colorEntry() == null) {
                selectedColor = -1;
                for (BenchPoolSync.Entry e : BenchClient.pool()) {
                    if (e.isColorPick() && (selectedColor < 0 || (!e.missing() && colorEntryMissing()))) {
                        selectedColor = e.color();
                    }
                }
            }
            List<BenchPoolSync.Entry> out = new ArrayList<>();
            for (BenchPoolSync.Entry entry : BenchClient.pool()) {
                if (tabNow >= 0 && entry.tab() != tabNow) {
                    continue;
                }
                if (query.isEmpty() || entry.item().getHoverName().getString().toLowerCase(Locale.ROOT).contains(query)) {
                    out.add(entry);
                }
            }
            filtered = out;
            // Lista nova do servidor com o mesmo conteúdo (ou só mais itens): a página em que o jogador está não volta ao início.
            if (!same) {
                scrollRows = 0;
            }
        }
        int pages = Math.max(1, ((filtered.size() + COLS - 1) / COLS + ROWS - 1) / ROWS);
        scrollRows = Math.max(0, Math.min(scrollRows / ROWS, pages - 1)) * ROWS;
        return filtered;
    }

    private boolean colorEntryMissing() {
        BenchPoolSync.Entry current = colorEntry();
        return current == null || current.missing();
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
        BenchTabs.Tab hoveredTab = null;
        for (int i = 0; i < tabs.size(); i++) {
            int tx = x - TAB_OVERLAP;
            int ty = y + 3 + TAB_H * i;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, i == tab ? TAB_SELECTED : TAB, tx, ty, TAB_W, TAB_H);
            graphics.fakeItem(tabs.get(i).icon(), tx + 9, ty + 5);
            if (tabAt(mouseX, mouseY) == i) {
                hoveredTab = tabs.get(i);
            }
        }
        if (hoveredTab != null) {
            graphics.setComponentTooltipForNextFrame(font, List.of(Component.translatableWithFallback(hoveredTab.key(),
                    hoveredTab.fallback())), mouseX, mouseY);
        }

        List<BenchPoolSync.Entry> list = entries();
        if (list.isEmpty()) {
            // Texto longo quebra em linhas dentro do painel (antes vazava para fora dele).
            int ty = gridY() + 4;
            for (var line : font.split(Component.translatableWithFallback(search.getValue().isBlank()
                    ? "stashlink.bench.panel.empty" : "stashlink.bench.panel.nomatch",
                    search.getValue().isBlank() ? "Nothing usable nearby" : "No item matches the search"), WIDTH - GRID_X * 2)) {
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
                if (index >= list.size()) {
                    continue;   // só os itens disponíveis: célula sem item fica sem nada, como no livro do jogo
                }
                BenchPoolSync.Entry entry = list.get(index);
                // Resultado sem material: slot vermelho, como no livro de receitas.
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, red(entry) ? SLOT_MISSING : SLOT, cx, cy, CELL, CELL);
                graphics.fakeItem(iconOf(entry), cx + 4, cy + 4);
                if (!entry.isResult() && !entry.isColorPick()) {
                    graphics.itemDecorations(font, entry.item(), cx + 4, cy + 4, shortCount(entry.count()));
                }
                if (entry.isColorPick() && entry.color() == selectedColor) {
                    // Cor escolhida: moldura branca por dentro do slot.
                    graphics.fill(cx + 1, cy + 1, cx + CELL - 1, cy + 3, 0xFFFFFFFF);
                    graphics.fill(cx + 1, cy + CELL - 3, cx + CELL - 1, cy + CELL - 1, 0xFFFFFFFF);
                    graphics.fill(cx + 1, cy + 3, cx + 3, cy + CELL - 3, 0xFFFFFFFF);
                    graphics.fill(cx + CELL - 3, cy + 3, cx + CELL - 1, cy + CELL - 3, 0xFFFFFFFF);
                }
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
            graphics.centeredText(font, Component.translatable("gui.recipebook.page", page, pages), x + 74, y + PAGE_Y + 4, TEXT);
            // Os mesmos botões do livro do jogo (ImageButton), com as duas setas: voltar na página 2 em diante.
            back.setPosition(x + PAGE_BACK_X, y + PAGE_Y);
            forward.setPosition(x + PAGE_FORWARD_X, y + PAGE_Y);
            back.visible = page > 1;
            forward.visible = page < pages;
            back.extractRenderState(graphics, mouseX, mouseY, delta);
            forward.extractRenderState(graphics, mouseX, mouseY, delta);
        }
        if (hovered != null) {
            List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(Minecraft.getInstance(), iconOf(hovered)));
            if (hovered.isColorPick()) {
                lines.add(hovered.missing()
                        ? Component.translatableWithFallback("stashlink.bench.panel.color.none", "None of this color in storage")
                        : Component.translatableWithFallback("stashlink.bench.panel.color.tip", "Click: use this color"));
            } else if (hovered.isResult()) {
                lines.add(red(hovered)
                        ? Component.translatableWithFallback("stashlink.bench.panel.result.missing", "Missing material")
                        : Component.translatableWithFallback("stashlink.bench.panel.result.tip",
                                "Click: set it up with items from storage."));
            } else {
                lines.add(Component.literal("x" + hovered.count()));
                lines.add(Component.translatableWithFallback("stashlink.bench.panel.tip",
                        "Click: put the stack in the station. Right-click: put one."));
            }
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
    public boolean mouseClicked(MouseButtonEvent event) {
        double mx = event.x();
        double my = event.y();
        int button = event.button();   // no 26.3: esquerdo = 1, direito = 3 (não 0 e 1 como no GLFW)
        if (!active() || !inside(mx, my)) {
            if (search.isFocused()) {
                screen.setFocused(null);
            }
            return false;
        }
        int clickedTab = tabAt(mx, my);
        if (clickedTab >= 0) {
            if (button == LEFT) {
                tab = clickedTab;
                scrollRows = 0;
                entries();
            }
            return true;
        }
        // Lupa + campo são uma coisa só (como no livro do jogo): clicar em qualquer um dá foco à busca.
        boolean magnifier = mx >= x + 8 && mx < x + SEARCH_X && my >= y + SEARCH_Y && my < y + SEARCH_Y + SEARCH_H;
        if (magnifier || search.mouseClicked(event, false)) {
            screen.setFocused(search);
            search.setFocused(true);
            return true;
        }
        if (search.isFocused()) {
            screen.setFocused(null);
        }
        int totalRows = (entries().size() + COLS - 1) / COLS;
        if (totalRows > ROWS && button == LEFT) {
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
        if (mx >= gridX() && my >= gridY() && col >= 0 && col < COLS && row >= 0 && row < ROWS && (button == LEFT || button == RIGHT)) {
            int index = (scrollRows + row) * COLS + col;
            List<BenchPoolSync.Entry> list = entries();
            if (index < list.size()) {
                click(list.get(index), button);
            }
        }
        return true;
    }

    private void click(BenchPoolSync.Entry entry, int button) {
        if (entry.isColorPick()) {
            if (button == LEFT) {
                selectedColor = entry.color();
                tab = 1;   // com a cor escolhida, mostra os estandartes dela
                scrollRows = 0;
                entries();
            }
        } else if (entry.isResult()) {
            if (button == LEFT && !red(entry)) {
                BenchPoolSync.Entry dye = loom ? colorEntry() : null;
                BenchClient.requestRecipe(menu, entry, dye != null ? dye.item() : entry.item());
            }
        } else {
            BenchClient.request(menu, entry, button == RIGHT);
        }
    }

    public boolean mouseScrolled(double mx, double my, double scrollY) {
        if (!active() || !inside(mx, my)) {
            return false;
        }
        // Sempre de página em página (como o livro do jogo): rolar uma linha só deixaria o "1/2" e as setas errados.
        scrollRows -= ROWS * (int) Math.signum(scrollY);
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
