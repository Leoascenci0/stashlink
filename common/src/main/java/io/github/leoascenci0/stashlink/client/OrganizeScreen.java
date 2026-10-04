package io.github.leoascenci0.stashlink.client;

import io.github.leoascenci0.stashlink.compat.mc.ClientCompat;
import io.github.leoascenci0.stashlink.network.OrganizeRequest;
import io.github.leoascenci0.stashlink.network.OrganizeSync;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A tela do Item 20 (tecla O ou botão "Sistema" no baú). Duas abas: <b>Buscar</b> (digitou "ferro", lista os baús que têm ferro
 * e quanto; clicar numa linha destaca o baú no mundo) e <b>Organizar</b> (mostra o que vai mover antes de mexer, com Aplicar e
 * Desfazer). É só vitrine: tudo o que aparece veio do servidor e todo pedido é revalidado lá.
 */
public class OrganizeScreen extends Screen {
    private static final int WIDTH = 320;
    private static final int ROW = 22;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int DIM = 0xFFAAAAAA;
    private static final int LEFT = 1;                         // no 26.3: esquerdo = 1, direito = 3

    private boolean searchTab = true;
    private EditBox search;
    private Button tabSearch;
    private Button tabOrganize;
    private Button previewButton;
    private Button applyButton;
    private Button undoButton;
    private Button highlightAll;
    private int scroll;
    private int searchDelay = -1;
    private String lastSent = "\u0000";

    public OrganizeScreen() {
        super(Component.translatableWithFallback("stashlink.organize.title", "Storage"));
    }

    private int left() {
        return this.width / 2 - WIDTH / 2;
    }

    private int top() {
        return 18;
    }

    private int listTop() {
        return top() + 64;
    }

    private int listBottom() {
        return this.height - 38;
    }

    private int visibleRows() {
        return Math.max(1, (listBottom() - listTop()) / ROW);
    }

    @Override
    protected void init() {
        OrganizeClient.opened(this);
        int x = left();
        int y = top();
        tabSearch = addRenderableWidget(Button.builder(Component.translatableWithFallback("stashlink.organize.tab_search", "Search"),
                b -> setTab(true)).bounds(x, y + 14, WIDTH / 2 - 2, 18).build());
        tabOrganize = addRenderableWidget(Button.builder(Component.translatableWithFallback("stashlink.organize.tab_organize", "Organize"),
                b -> setTab(false)).bounds(x + WIDTH / 2 + 2, y + 14, WIDTH / 2 - 2, 18).build());

        search = addRenderableWidget(new EditBox(this.font, x, y + 38, WIDTH, 18,
                Component.translatableWithFallback("stashlink.organize.search", "Search item")));
        search.setHint(Component.translatableWithFallback("stashlink.organize.search_hint", "Type an item (iron, oak, bread...)"));
        search.setMaxLength(OrganizeRequest.MAX_TEXT);
        search.setResponder(text -> searchDelay = 8);

        int third = (WIDTH - 8) / 3;
        previewButton = addRenderableWidget(Button.builder(Component.translatableWithFallback("stashlink.organize.preview", "Preview"),
                b -> OrganizeClient.send(OrganizeRequest.of(OrganizeRequest.PREVIEW))).bounds(x, y + 38, third, 18).build());
        applyButton = addRenderableWidget(Button.builder(Component.translatableWithFallback("stashlink.organize.apply", "Apply"),
                b -> OrganizeClient.send(OrganizeRequest.of(OrganizeRequest.APPLY))).bounds(x + third + 4, y + 38, third, 18).build());
        undoButton = addRenderableWidget(Button.builder(Component.translatableWithFallback("stashlink.organize.undo", "Undo"),
                b -> OrganizeClient.send(OrganizeRequest.of(OrganizeRequest.UNDO))).bounds(x + 2 * (third + 4), y + 38, third, 18).build());

        highlightAll = addRenderableWidget(Button.builder(Component.translatableWithFallback("stashlink.organize.highlight_all",
                "Highlight all"), b -> highlightAll()).bounds(x, this.height - 30, WIDTH / 2 - 2, 20).build());
        addRenderableWidget(Button.builder(net.minecraft.network.chat.CommonComponents.GUI_DONE, b -> onClose())
                .bounds(x + WIDTH / 2 + 2, this.height - 30, WIDTH / 2 - 2, 20).build());
        setTab(searchTab);
        setInitialFocus(search);
    }

    private void setTab(boolean searchTab) {
        this.searchTab = searchTab;
        scroll = 0;
        search.visible = searchTab;
        search.active = searchTab;
        highlightAll.visible = searchTab;
        highlightAll.active = searchTab;
        previewButton.visible = !searchTab;
        applyButton.visible = !searchTab;
        undoButton.visible = !searchTab;
        tabSearch.active = !searchTab;
        tabOrganize.active = searchTab;
        if (searchTab) {
            setFocused(search);
        }
        refresh();
    }

    /** Chegou resposta do servidor: atualiza botões e lista. */
    public void refresh() {
        if (applyButton == null) {
            return;
        }
        applyButton.active = OrganizeClient.canApply();
        undoButton.active = OrganizeClient.canUndo();
        scroll = Math.min(scroll, maxScroll());
    }

    private List<OrganizeSync.Row> rows() {
        return searchTab ? OrganizeClient.searchRows() : OrganizeClient.previewRows();
    }

    private int maxScroll() {
        return Math.max(0, rows().size() - visibleRows());
    }

    @Override
    public void tick() {
        super.tick();
        if (searchDelay >= 0 && --searchDelay < 0) {
            sendSearch(false);
        }
        refreshButtons();
    }

    private void refreshButtons() {
        if (applyButton != null) {
            applyButton.active = OrganizeClient.canApply();
            undoButton.active = OrganizeClient.canUndo();
        }
    }

    // ------------------------------------------------------------------------------------------------------ busca

    /** O cliente sabe os nomes no idioma do jogador; o servidor só os em inglês. Por isso quem traduz o texto em itens é este lado. */
    private List<Identifier> matchingItems(String query) {
        String needle = normalize(query);
        List<Identifier> out = new ArrayList<>();
        if (needle.isEmpty()) {
            return out;
        }
        for (Item item : BuiltInRegistries.ITEM) {
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            if (normalize(new ItemStack(item).getHoverName().getString()).contains(needle)
                    || normalize(id.getPath().replace('_', ' ')).contains(needle)) {
                out.add(id);
                if (out.size() >= OrganizeRequest.MAX_ITEMS) {
                    break;
                }
            }
        }
        return out;
    }

    private static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
    }

    private void sendSearch(boolean force) {
        String text = search.getValue();
        if (!force && text.equals(lastSent)) {
            return;
        }
        lastSent = text;
        scroll = 0;
        OrganizeClient.send(new OrganizeRequest(OrganizeRequest.SEARCH, 0, text, matchingItems(text), BlockPos.ZERO));
    }

    private void highlightAll() {
        String text = search.getValue();
        if (text.isBlank()) {
            return;
        }
        OrganizeClient.send(new OrganizeRequest(OrganizeRequest.HIGHLIGHT_ALL, 0, text, matchingItems(text), BlockPos.ZERO));
        closeForHighlight();
    }

    private void closeForHighlight() {
        // Fecha para o jogador ver o mundo; o contorno dura alguns segundos.
        ClientCompat.overlay(Minecraft.getInstance(), Component.translatableWithFallback("stashlink.organize.highlighted",
                "Container highlighted for a few seconds"));
        onClose();
    }

    // -------------------------------------------------------------------------------------------------- desenho

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int x = left();
        graphics.centeredText(this.font, this.title, this.width / 2, top() + 2, TEXT);

        int top = listTop();
        graphics.fill(x - 2, top - 2, x + WIDTH + 2, listBottom() + 2, 0x80000000);
        List<OrganizeSync.Row> rows = rows();
        if (rows.isEmpty()) {
            Component empty = emptyText();
            int ty = top + 4;
            for (var line : this.font.split(empty, WIDTH - 12)) {
                graphics.text(this.font, line, x + 6, ty, DIM, false);
                ty += this.font.lineHeight + 2;
            }
        }
        int shown = Math.min(rows.size() - scroll, visibleRows());
        Component hovered = null;
        for (int i = 0; i < shown; i++) {
            OrganizeSync.Row row = rows.get(scroll + i);
            int y = top + i * ROW;
            boolean over = mouseX >= x && mouseX < x + WIDTH && mouseY >= y && mouseY < y + ROW;
            if (over) {
                graphics.fill(x, y, x + WIDTH, y + ROW - 1, 0x40FFFFFF);
            }
            graphics.fakeItem(row.item(), x + 3, y + 2);
            graphics.text(this.font, Component.literal(row.count() + " × ").append(row.item().getHoverName()), x + 24, y + 2, TEXT, false);
            graphics.text(this.font, searchTab ? where(row) : Component.literal(containerName(row.from(), row.fromName()) + " → "
                    + containerName(row.to(), row.toName())), x + 24, y + 12, DIM, false);
            if (over && searchTab) {
                hovered = Component.translatableWithFallback("stashlink.organize.click_to_highlight", "Click to highlight this container");
            }
        }
        if (rows.size() > visibleRows()) {
            int barHeight = listBottom() - top;
            int thumb = Math.max(12, barHeight * visibleRows() / rows.size());
            int thumbY = top + (barHeight - thumb) * scroll / Math.max(1, maxScroll());
            graphics.fill(x + WIDTH - 3, top, x + WIDTH, listBottom(), 0x60000000);
            graphics.fill(x + WIDTH - 3, thumbY, x + WIDTH, thumbY + thumb, 0xFFC0C0C0);
        }
        // Linha de resumo / mensagem do servidor, acima dos botões de baixo.
        Component status = statusLine();
        if (status != null) {
            graphics.text(this.font, status, x, this.height - 44 + 4 - 4, DIM, false);
        }
        if (hovered != null) {
            graphics.setComponentTooltipForNextFrame(this.font, List.of(hovered), mouseX, mouseY);
        }
    }

    private Component emptyText() {
        if (searchTab) {
            return Component.translatableWithFallback(search.getValue().isBlank() ? "stashlink.organize.search_empty" : "stashlink.organize.search_none",
                    search.getValue().isBlank() ? "Type an item to find which containers have it." : "No container nearby has that.");
        }
        if (OrganizeClient.previewed()) {
            return OrganizeClient.tidied() > 0
                    ? Component.translatableWithFallback("stashlink.organize.only_tidy", "Nothing to move between containers; %s containers will just be sorted.", OrganizeClient.tidied())
                    : Component.translatableWithFallback("stashlink.organize.preview_none", "Everything is already organized.");
        }
        return Component.translatableWithFallback("stashlink.organize.preview_help",
                "Preview shows what would move before anything changes. Items go to the container that already has the most of them (same rule as the N key); locked slots and containers another player has open are left alone.");
    }

    private Component statusLine() {
        int message = OrganizeClient.message();
        String key = switch (message) {
            case OrganizeSync.MSG_APPLIED -> "stashlink.organize.msg.applied";
            case OrganizeSync.MSG_UNDONE -> "stashlink.organize.msg.undone";
            case OrganizeSync.MSG_STALE -> "stashlink.organize.msg.stale";
            case OrganizeSync.MSG_UNDO_FAILED -> "stashlink.organize.msg.undo_failed";
            case OrganizeSync.MSG_TOO_MANY -> "stashlink.organize.msg.too_many";
            default -> null;
        };
        if (key != null) {
            return Component.translatableWithFallback(key, switch (message) {
                case OrganizeSync.MSG_APPLIED -> "Applied.";
                case OrganizeSync.MSG_UNDONE -> "Undone.";
                case OrganizeSync.MSG_STALE -> "Something changed: preview again.";
                case OrganizeSync.MSG_UNDO_FAILED -> "Cannot undo: containers changed since.";
                default -> "Showing the first results only.";
            });
        }
        if (!searchTab && OrganizeClient.previewed() && OrganizeClient.canApply()) {
            int items = 0;
            for (OrganizeSync.Row row : OrganizeClient.previewRows()) {
                items += row.count();
            }
            return Component.translatableWithFallback("stashlink.organize.summary", "%s items to move, %s containers to sort",
                    items, OrganizeClient.tidied());
        }
        return null;
    }

    /** "Baú de ferro (x, y, z) · 12 m" */
    private Component where(OrganizeSync.Row row) {
        Minecraft mc = Minecraft.getInstance();
        int distance = mc.player == null ? 0 : (int) Math.round(Math.sqrt(mc.player.distanceToSqr(
                row.from().getX() + 0.5, row.from().getY() + 0.5, row.from().getZ() + 0.5)));
        return Component.literal(containerName(row.from(), row.fromName()) + "  " + row.from().getX() + ", " + row.from().getY()
                + ", " + row.from().getZ() + "  ·  " + distance + " m");
    }

    /** O rótulo que o jogador deu (Item 14) ou o nome do bloco no idioma dele. */
    private String containerName(BlockPos pos, String label) {
        if (!label.isEmpty()) {
            return label;
        }
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? "?" : mc.level.getBlockState(pos).getBlock().getName().getString();
    }

    // --------------------------------------------------------------------------------------------------- entrada

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (searchTab && event.button() == LEFT) {
            int x = left();
            if (event.x() >= x && event.x() < x + WIDTH && event.y() >= listTop()) {
                int index = scroll + (int) ((event.y() - listTop()) / ROW);
                List<OrganizeSync.Row> rows = rows();
                if (index >= 0 && index < rows.size() && index < scroll + visibleRows()) {
                    OrganizeClient.send(new OrganizeRequest(OrganizeRequest.HIGHLIGHT, 0, "", List.of(), rows.get(index).from()));
                    closeForHighlight();
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (maxScroll() > 0) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(scrollY)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        // Mundo único: o servidor precisa continuar rodando para responder aos pedidos.
        return false;
    }

    @Override
    public void removed() {
        super.removed();
        OrganizeClient.opened(null);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            ClientCompat.openScreen(minecraft, null);
        }
    }
}
