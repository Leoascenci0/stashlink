package io.github.leoascenci0.stashlink.client;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.LoomMenu;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.DyeColor;
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
        if (menu instanceof LoomMenu) {
            return List.of(
                    new Tab(new ItemStack(Items.DYE.pick(DyeColor.RED)), "stashlink.bench.tab.colors", "Colors"),
                    new Tab(new ItemStack(Items.BANNER.pick(DyeColor.WHITE)), "stashlink.bench.tab.banners", "Banners"),
                    new Tab(new ItemStack(Items.FLOWER_BANNER_PATTERN), "stashlink.bench.tab.molds", "Patterns (molds)"));
        }
        if (menu instanceof EnchantmentMenu) {
            return List.of(
                    new Tab(new ItemStack(Items.DIAMOND_PICKAXE), "stashlink.bench.tab.gear", "Equipment"),
                    new Tab(new ItemStack(Items.ENCHANTED_BOOK), "stashlink.bench.tab.books", "Books"),
                    new Tab(new ItemStack(Items.LAPIS_LAZULI), "stashlink.bench.tab.lapis", "Lapis lazuli"));
        }
        if (menu instanceof BrewingStandMenu) {
            return List.of(
                    new Tab(new ItemStack(Items.POTION), "stashlink.bench.tab.bottles", "Bottles and potions"),
                    new Tab(new ItemStack(Items.NETHER_WART), "stashlink.bench.tab.ingredients", "Ingredients"),
                    new Tab(new ItemStack(Items.BLAZE_POWDER), "stashlink.bench.tab.fuel", "Fuel"));
        }
        if (menu instanceof AnvilMenu) {
            return List.of(
                    new Tab(new ItemStack(Items.ENCHANTED_BOOK), "stashlink.bench.tab.books", "Books"),
                    new Tab(new ItemStack(Items.IRON_CHESTPLATE), "stashlink.bench.tab.gear", "Equipment"),
                    new Tab(new ItemStack(Items.IRON_INGOT), "stashlink.bench.tab.materials", "Repair materials"));
        }
        if (menu instanceof SmithingMenu) {
            return List.of(
                    new Tab(new ItemStack(Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE), "stashlink.bench.tab.trims", "Trims"),
                    new Tab(new ItemStack(Items.DIAMOND_CHESTPLATE), "stashlink.bench.tab.gear", "Equipment"),
                    new Tab(new ItemStack(Items.NETHERITE_INGOT), "stashlink.bench.tab.ores", "Materials"));
        }
        return List.of();
    }
}
