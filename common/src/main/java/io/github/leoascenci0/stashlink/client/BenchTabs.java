package io.github.leoascenci0.stashlink.client;

import io.github.leoascenci0.stashlink.compat.mc.BenchCompat;
import io.github.leoascenci0.stashlink.compat.mc.StationRecipes;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * As abas do painel (a coluna de botões à esquerda, como as categorias do livro de receitas): tear e mesa de ferraria
 * separam o que serve em seções. O servidor marca cada entrada com o número da aba ({@code Entry.tab}).
 */
final class BenchTabs {
    /**
     * Uma aba: o ícone do botão (um item, ou dois lado a lado como "machado + espada" no livro de receitas; {@code second}
     * vazio = um só) e o nome (chave de tradução, sem texto de reserva no código).
     */
    record Tab(ItemStack icon, ItemStack second, String key) {
        Tab(ItemStack icon, String key) {
            this(icon, ItemStack.EMPTY, key);
        }
    }

    private BenchTabs() {
    }

    /** O ícone de armadura, igual em todas as estações: o peitoral de ferro. */
    static ItemStack armorIcon() {
        return new ItemStack(Items.IRON_CHESTPLATE);
    }

    /** O ícone de ferramentas e armas: machado + espada, os mesmos dois itens da aba "Equipamento" do livro de receitas. */
    static ItemStack toolsIcon() {
        return new ItemStack(Items.IRON_AXE);
    }

    /** Segundo ícone, desenhado por cima do primeiro e deslocado (como as abas de dois itens do livro do jogo). */
    static ItemStack toolsIconSecond() {
        return new ItemStack(Items.GOLDEN_SWORD);
    }

    /** Abas da estação, ou lista vazia (uma lista só, sem abas). */
    static List<Tab> of(AbstractContainerMenu menu) {
        switch (BenchCompat.stationOf(menu)) {
            case LOOM:
                return List.of(
                        new Tab(StationRecipes.sampleDye(), "stashlink.bench.tab.colors"),
                        new Tab(StationRecipes.sampleBanner(), "stashlink.bench.tab.banners"),
                        new Tab(new ItemStack(Items.FLOWER_BANNER_PATTERN), "stashlink.bench.tab.molds"));
            case ENCHANTING:
                return List.of(
                        new Tab(new ItemStack(Items.DIAMOND_PICKAXE), "stashlink.bench.tab.gear"),
                        new Tab(new ItemStack(Items.ENCHANTED_BOOK), "stashlink.bench.tab.books"),
                        new Tab(new ItemStack(Items.LAPIS_LAZULI), "stashlink.bench.tab.lapis"));
            case BREWING:
                return List.of(
                        new Tab(new ItemStack(Items.POTION), "stashlink.bench.tab.bottles"),
                        new Tab(new ItemStack(Items.NETHER_WART), "stashlink.bench.tab.ingredients"),
                        new Tab(new ItemStack(Items.BLAZE_POWDER), "stashlink.bench.tab.fuel"));
            case ANVIL:
                return List.of(
                        new Tab(new ItemStack(Items.ENCHANTED_BOOK), "stashlink.bench.tab.books"),
                        new Tab(armorIcon(), "stashlink.bench.tab.gear"),
                        new Tab(new ItemStack(Items.IRON_INGOT), "stashlink.bench.tab.materials"));
            case SMITHING:
                return List.of(
                        // Mesma ordem de BenchCompat.SMITHING_TAB_*.
                        new Tab(new ItemStack(Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE), "stashlink.bench.tab.trims"),
                        new Tab(armorIcon(), "stashlink.bench.tab.armor"),
                        new Tab(toolsIcon(), toolsIconSecond(), "stashlink.bench.tab.tools_weapons"),
                        new Tab(new ItemStack(Items.NETHERITE_INGOT), "stashlink.bench.tab.smithing_materials"));
            default:
                return List.of();
        }
    }
}
