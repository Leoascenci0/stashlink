package io.github.leoascenci0.stashlink.compat.mc;

import io.netty.buffer.Unpooled;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.BlastFurnaceMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.inventory.LoomMenu;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.inventory.SmokerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipePropertySet;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * O que as bancadas (Item 16) precisam do Minecraft e pode mudar entre versões: quais menus são estações, a conta
 * do livro de receitas ({@link StackedItemContents}) e a identidade de um stack. Quebrou numa atualização?
 * Conserte AQUI (ver docs/UPDATING.md).
 */
public final class BenchCompat {
    private BenchCompat() {
    }

    /**
     * O menu é uma estação onde o jogador usa itens dele? Bancada, fornalha/defumador/alto-forno, cortador de pedra,
     * tear, mesa de cartografia, pedra de amolar, mesa de ferreiro, bigorna, encantamento e suporte de poções.
     * Fora de propósito: a grade 2x2 da mochila ({@code InventoryMenu}, não é uma bancada) e o Crafter (bloco
     * automático, sem jogador no meio).
     */
    public static boolean isStation(AbstractContainerMenu menu) {
        return menu instanceof CraftingMenu || menu instanceof AbstractFurnaceMenu
                || menu instanceof StonecutterMenu || menu instanceof LoomMenu
                || menu instanceof CartographyTableMenu || menu instanceof GrindstoneMenu
                || menu instanceof SmithingMenu || menu instanceof AnvilMenu
                || menu instanceof EnchantmentMenu || menu instanceof BrewingStandMenu;
    }

    /** Estação que tem livro de receitas (o jogo coloca os ingredientes sozinho). */
    public static boolean hasRecipeBook(AbstractContainerMenu menu) {
        return menu instanceof RecipeBookMenu && isStation(menu);
    }

    /**
     * O stack serve nesta estação? O painel "Armazenamento" só lista o que o jogador poderia pôr nela: no tear,
     * banner, corante e molde; na pedra de amolar, o que tem dano ou encantamento; e assim por diante.
     * A regra vem dos próprios slots da estação ({@code mayPlace}), então acompanha o jogo e itens de outros mods.
     * Só a bancada (qualquer item pode ser ingrediente) aceita tudo; fornalha, cortador de pedra, encantamento
     * e bigorna têm o slot de entrada livre no jogo (aceita qualquer coisa), por isso a conta é feita aqui; o slot 1
     * dessas duas (combustível, lápis-lazúli) tem regra própria e é consultado direto.
     */
    public static boolean relevant(AbstractContainerMenu menu, Player player, ItemStack stack) {
        if (menu instanceof CraftingMenu) {
            return true;
        }
        if (menu instanceof AbstractFurnaceMenu) {
            return menu.slots.get(1).mayPlace(stack) || player.level().recipeAccess()
                    .propertySet(furnaceInput(menu)).test(stack);
        }
        if (menu instanceof StonecutterMenu) {
            return player.level().recipeAccess().stonecutterRecipes().acceptsInput(stack);
        }
        if (menu instanceof EnchantmentMenu) {
            return menu.slots.get(1).mayPlace(stack) || stack.isEnchantable();
        }
        if (menu instanceof AnvilMenu) {
            return stack.isDamageableItem() || stack.is(Items.ENCHANTED_BOOK) || stack.is(Items.NAME_TAG)
                    || EnchantmentHelper.hasAnyEnchantments(stack) || repairsSomethingOf(player, stack);
        }
        return acceptedBySlots(menu, player, stack);
    }

    /** Algum slot da estação (fora o inventário do jogador) aceita este stack? */
    private static boolean acceptedBySlots(AbstractContainerMenu menu, Player player, ItemStack stack) {
        for (Slot slot : menu.slots) {
            if (slot.container != player.getInventory() && slot.mayPlace(stack)) {
                return true;
            }
        }
        return false;
    }

    private static ResourceKey<RecipePropertySet> furnaceInput(AbstractContainerMenu menu) {
        if (menu instanceof SmokerMenu) {
            return RecipePropertySet.SMOKER_INPUT;
        }
        if (menu instanceof BlastFurnaceMenu) {
            return RecipePropertySet.BLAST_FURNACE_INPUT;
        }
        return RecipePropertySet.FURNACE_INPUT;
    }

    /** O stack é material de conserto de algo que o jogador carrega (ex.: diamante para a picareta)? */
    private static boolean repairsSomethingOf(Player player, ItemStack material) {
        for (ItemStack carried : player.getInventory().getNonEquipmentItems()) {
            if (!carried.isEmpty() && carried.isDamageableItem() && carried.isValidRepairItem(material)) {
                return true;
            }
        }
        return false;
    }

    /** Identidade de um stack: mesmo item e mesmos componentes (a quantidade não conta). */
    public record Key(Item item, DataComponentPatch components) {
    }

    public static Key keyOf(ItemStack stack) {
        return new Key(stack.getItem(), stack.getComponentsPatch());
    }

    /** O stack serve de ingrediente comum (sem dano, encantamento nem nome)? É a regra do próprio livro de receitas. */
    public static boolean usableForCrafting(ItemStack stack) {
        return Inventory.isUsableForCrafting(stack);
    }

    /** Soma, na conta do livro, os itens (comuns) que o armazenamento tem. */
    public static void account(StackedItemContents contents, ItemStack item, int count) {
        if (count > 0) {
            contents.accountSimpleStack(item.copyWithCount(count));
        }
    }

    /**
     * Quanto de cada ingrediente falta na mochila (e na grade) para o livro montar a receita, supondo que o
     * armazenamento ({@code pool}: item comum → quantidade) cubra o que faltar. Vazio se a mochila já basta (o
     * jogo faz sozinho) ou se nem com o armazenamento dá. Repete a conta de {@code ServerPlaceRecipe}: com
     * {@code useMax} (shift-clique) monta o máximo, senão uma receita.
     */
    public static Map<Item, Integer> missingIngredients(Inventory inventory, RecipeBookMenu menu,
                                                         List<Slot> inputSlots, RecipeHolder<?> holder,
                                                         boolean useMax, Map<Item, Integer> pool) {
        Recipe<?> recipe = holder.value();
        StackedItemContents have = new StackedItemContents();
        inventory.fillStackedContents(have);
        menu.fillCraftSlotsStackedContents(have);
        if (!useMax && have.canCraft(recipe, null)) {
            return Map.of();
        }
        StackedItemContents all = new StackedItemContents();
        inventory.fillStackedContents(all);
        menu.fillCraftSlotsStackedContents(all);
        for (Map.Entry<Item, Integer> e : pool.entrySet()) {
            account(all, new ItemStack(e.getKey()), e.getValue());
        }
        int crafts = useMax ? all.getBiggestCraftableStack(recipe, null) : 1;
        if (crafts <= 0) {
            return Map.of();
        }
        List<Holder<Item>> picked = new ArrayList<>();
        if (!all.canCraft(recipe, crafts, picked::add)) {
            return Map.of();
        }
        int limit = crafts;
        for (Holder<Item> h : picked) {
            limit = Math.min(limit, h.components().getOrDefault(DataComponents.MAX_STACK_SIZE, 64));
        }
        if (limit != crafts) {
            crafts = limit;
            picked.clear();
            if (!all.canCraft(recipe, crafts, picked::add)) {
                return Map.of();
            }
        }
        Map<Item, Integer> need = new LinkedHashMap<>();
        for (Holder<Item> h : picked) {
            need.merge(h.value(), crafts, Integer::sum);
        }
        Map<Item, Integer> missing = new LinkedHashMap<>();
        for (Map.Entry<Item, Integer> e : need.entrySet()) {
            int held = held(inventory, inputSlots, e.getKey());
            if (e.getValue() > held) {
                missing.put(e.getKey(), e.getValue() - held);
            }
        }
        return missing;
    }

    /** Quantos itens comuns deste tipo há na mochila e nos slots de entrada da estação. */
    private static int held(Inventory inventory, List<Slot> inputSlots, Item item) {
        int total = 0;
        for (ItemStack stack : inventory.getNonEquipmentItems()) {
            if (stack.is(item) && usableForCrafting(stack)) {
                total += stack.getCount();
            }
        }
        for (Slot slot : inputSlots) {
            ItemStack stack = slot.getItem();
            if (stack.is(item) && usableForCrafting(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** Bytes que o item ocupa num pacote. Sem componentes extras é um valor fixo pequeno (não precisa codificar). */
    public static int packetSize(Player player, ItemStack stack) {
        if (stack.getComponentsPatch().isEmpty()) {
            return 16;
        }
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), player.registryAccess());
        try {
            ItemStack.STREAM_CODEC.encode(buf, stack);
            return buf.readableBytes();
        } finally {
            buf.release();
        }
    }

    /**
     * Estações em que o que está nos slots <b>fica no bloco</b> ao fechar (fornalhas e suporte de poções): o item
     * emprestado que ficou lá vira do jogador e não volta ao baú; só o cursor ainda é "emprestado".
     */
    public static boolean keepsItemsInBlock(AbstractContainerMenu menu) {
        return menu instanceof AbstractFurnaceMenu || menu instanceof BrewingStandMenu;
    }

    /** Fecha a tela aberta do jogador do lado do servidor (o jogo devolve a grade à mochila). */
    public static void closeMenu(ServerPlayer player) {
        player.doCloseContainer();
    }
}
