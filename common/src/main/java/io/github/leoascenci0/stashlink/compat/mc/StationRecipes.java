package io.github.leoascenci0.stashlink.compat.mc;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BannerPatternTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.LoomMenu;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.SelectableRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Receitas das estações que não têm livro de receitas (cortador de pedra e tear): lista o que dá para fazer, diz que
 * item entra em cada slot e escolhe a receita no menu. Só aparece o que o jogador já descobriu (pegou, fabricou ou
 * tem agora). API frágil do jogo fica só aqui.
 */
public final class StationRecipes {
    /**
     * Uma receita. {@code icon} é o resultado; {@code slots}/{@code needs} dizem que slot da estação recebe qual item
     * (um item que serve em cada); {@code entry} é a escolha no menu.
     */
    public record Option(ItemStack icon, int[] slots, List<Predicate<ItemStack>> needs, Object entry, int tab) {
    }

    private StationRecipes() {
    }

    public static boolean supports(AbstractContainerMenu menu) {
        return menu instanceof StonecutterMenu || menu instanceof LoomMenu;
    }

    /** O jogador já conheceu este item? Pegou, fabricou ou usou algum dia (estatísticas do servidor). */
    public static boolean discovered(ServerPlayer player, Item item) {
        var stats = player.getStats();
        return stats.getValue(Stats.ITEM_PICKED_UP, item) > 0 || stats.getValue(Stats.ITEM_CRAFTED, item) > 0
                || stats.getValue(Stats.ITEM_USED, item) > 0;
    }

    /**
     * As receitas <b>conhecidas</b> da estação, na ordem do jogo (o índice é o {@code id} do painel): as que o
     * jogador já descobriu ou que ele tem material à mão ({@code have}).
     */
    public static List<Option> options(ServerPlayer player, AbstractContainerMenu menu, List<ItemStack> have) {
        List<Option> out = new ArrayList<>();
        if (menu instanceof StonecutterMenu) {
            var context = SlotDisplayContext.fromLevel(player.level());
            for (SelectableRecipe.SingleInputEntry<StonecutterRecipe> entry
                    : player.level().recipeAccess().stonecutterRecipes().entries()) {
                boolean known = entry.input().items().anyMatch(h -> discovered(player, h.value()))
                        || have.stream().anyMatch(entry.input());
                if (known) {
                    out.add(new Option(entry.recipe().optionDisplay().resolveForFirstStack(context), new int[]{0},
                            List.of(entry.input()), entry, 0));
                }
            }
        } else if (menu instanceof LoomMenu) {
            loom(player, have, out);
        }
        return out;
    }

    private static boolean isBanner(ItemStack stack) {
        return stack.getItem() instanceof BannerItem;
    }

    public static boolean isDye(ItemStack stack) {
        return stack.is(ItemTags.LOOM_DYES) && stack.has(DataComponents.DYE);
    }

    /**
     * Tear: uma receita por padrão (aba 1 = sem molde, aba 2 = com molde). O ícone é o banner com o padrão em branco: o
     * painel pinta com a cor escolhida e o servidor usa o corante dessa cor ao montar ({@code BenchResults.craft}).
     */
    private static void loom(ServerPlayer player, List<ItemStack> have, List<Option> out) {
        var patterns = player.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN);
        ItemStack banner = have.stream().filter(StationRecipes::isBanner).findFirst().map(s -> s.copyWithCount(1))
                .orElse(new ItemStack(Items.BANNER.pick(DyeColor.WHITE)));
        Predicate<ItemStack> isThisBanner = s -> s.is(banner.getItem());
        // Padrões que não pedem item (listras, bordas...): sempre conhecidos.
        patterns.get(BannerPatternTags.NO_ITEM_REQUIRED).ifPresent(set -> {
            for (Holder<BannerPattern> pattern : set) {
                out.add(new Option(withPattern(banner, pattern, DyeColor.WHITE), new int[]{0, 1},
                        List.of(isThisBanner, StationRecipes::isDye), pattern, 1));
            }
        });
        // Padrões que vêm de um item (flor, creeper, caveira...): só os que o jogador já conheceu.
        for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(ItemTags.LOOM_PATTERNS)) {
            Item item = holder.value();
            HolderSet<BannerPattern> provided = new ItemStack(item).get(DataComponents.PROVIDES_BANNER_PATTERNS);
            if (provided == null || !(discovered(player, item) || have.stream().anyMatch(s -> s.is(item)))) {
                continue;
            }
            for (Holder<BannerPattern> pattern : provided) {
                out.add(new Option(withPattern(banner, pattern, DyeColor.WHITE), new int[]{0, 1, 2},
                        List.of(isThisBanner, StationRecipes::isDye, s -> s.is(item)), pattern, 2));
            }
        }
    }

    /** As cores de corante que o jogador conhece (descobriu ou tem à mão), em ordem; o item é o corante dessa cor. */
    public static List<DyeColor> knownDyeColors(ServerPlayer player, List<ItemStack> have) {
        List<DyeColor> out = new ArrayList<>();
        for (DyeColor color : DyeColor.values()) {
            Item dye = Items.DYE.pick(color);
            if (discovered(player, dye) || have.stream().anyMatch(s -> s.is(dye))) {
                out.add(color);
            }
        }
        return out;
    }

    /** O mesmo ícone com a última camada de padrão na cor {@code colorId} (o painel pinta o banner da cor escolhida). */
    public static ItemStack recolor(ItemStack icon, int colorId) {
        BannerPatternLayers layers = icon.get(DataComponents.BANNER_PATTERNS);
        if (layers == null || layers.layers().isEmpty()) {
            return icon;
        }
        List<BannerPatternLayers.Layer> list = new ArrayList<>(layers.layers());
        BannerPatternLayers.Layer last = list.remove(list.size() - 1);
        list.add(new BannerPatternLayers.Layer(last.pattern(), DyeColor.byId(colorId)));
        ItemStack out = icon.copy();
        out.set(DataComponents.BANNER_PATTERNS, new BannerPatternLayers(list));
        return out;
    }

    private static ItemStack withPattern(ItemStack banner, Holder<BannerPattern> pattern, DyeColor color) {
        ItemStack icon = banner.copyWithCount(1);
        icon.set(DataComponents.BANNER_PATTERNS, new BannerPatternLayers.Builder()
                .addAll(icon.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY))
                .add(pattern, color).build());
        return icon;
    }

    /** Escolhe {@code option} no menu (as entradas já estão nos slots). */
    @SuppressWarnings("unchecked")
    public static void select(AbstractContainerMenu menu, Player player, Option option) {
        if (menu instanceof StonecutterMenu stonecutter) {
            List<SelectableRecipe.SingleInputEntry<StonecutterRecipe>> visible = stonecutter.getVisibleRecipes().entries();
            int index = visible.indexOf((SelectableRecipe.SingleInputEntry<StonecutterRecipe>) option.entry());
            if (index >= 0) {
                stonecutter.clickMenuButton(player, index);
            }
        } else if (menu instanceof LoomMenu loom) {
            int index = loom.getSelectablePatterns().indexOf((Holder<BannerPattern>) option.entry());
            if (index >= 0) {
                loom.clickMenuButton(player, index);
            }
        }
    }
}
