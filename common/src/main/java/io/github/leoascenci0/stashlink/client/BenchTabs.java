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
    /** Uma aba: o ícone do botão, o nome (chave de tradução) e o texto de reserva. */
    record Tab(ItemStack icon, String key, String fallback) {
    }

    private BenchTabs() {
    }

    /** Abas da estação, ou lista vazia (uma lista só, sem abas). */
    static List<Tab> of(AbstractContainerMenu menu) {
        switch (BenchCompat.stationOf(menu)) {
            case LOOM:
                return List.of(
                        new Tab(StationRecipes.sampleDye(), "stashlink.bench.tab.colors", "Colors"),
                        new Tab(StationRecipes.sampleBanner(), "stashlink.bench.tab.banners", "Banners"),
                        new Tab(new ItemStack(Items.FLOWER_BANNER_PATTERN), "stashlink.bench.tab.molds", "Patterns (molds)"));
            case ENCHANTING:
                return List.of(
                        new Tab(new ItemStack(Items.DIAMOND_PICKAXE), "stashlink.bench.tab.gear", "Equipment"),
                        new Tab(new ItemStack(Items.ENCHANTED_BOOK), "stashlink.bench.tab.books", "Books"),
                        new Tab(new ItemStack(Items.LAPIS_LAZULI), "stashlink.bench.tab.lapis", "Lapis lazuli"));
            case BREWING:
                return List.of(
                        new Tab(new ItemStack(Items.POTION), "stashlink.bench.tab.bottles", "Bottles and potions"),
                        new Tab(new ItemStack(Items.NETHER_WART), "stashlink.bench.tab.ingredients", "Ingredients"),
                        new Tab(new ItemStack(Items.BLAZE_POWDER), "stashlink.bench.tab.fuel", "Fuel"));
            case ANVIL:
                return List.of(
                        new Tab(new ItemStack(Items.ENCHANTED_BOOK), "stashlink.bench.tab.books", "Books"),
                        new Tab(new ItemStack(Items.IRON_CHESTPLATE), "stashlink.bench.tab.gear", "Equipment"),
                        new Tab(new ItemStack(Items.IRON_INGOT), "stashlink.bench.tab.materials", "Repair materials"));
            case SMITHING:
                return List.of(
                        new Tab(new ItemStack(Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE), "stashlink.bench.tab.trims", "Trims"),
                        new Tab(new ItemStack(Items.DIAMOND_CHESTPLATE), "stashlink.bench.tab.gear", "Equipment"),
                        new Tab(new ItemStack(Items.NETHERITE_INGOT), "stashlink.bench.tab.ores", "Materials"));
            default:
                return List.of();
        }
    }
}
