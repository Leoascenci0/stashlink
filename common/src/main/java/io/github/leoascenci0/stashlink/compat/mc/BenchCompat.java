package io.github.leoascenci0.stashlink.compat.mc;

import io.netty.buffer.Unpooled;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
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
        return stationOf(menu) != Station.OTHER;
    }

    /** Os tipos de estação que o mod conhece; o resto do mod pergunta por aqui e nunca usa as classes de menu do jogo. */
    public enum Station {
        CRAFTING, FURNACE, STONECUTTER, LOOM, CARTOGRAPHY, GRINDSTONE, SMITHING, ANVIL, ENCHANTING, BREWING, OTHER
    }

    public static Station stationOf(AbstractContainerMenu menu) {
        if (menu instanceof CraftingMenu) {
            return Station.CRAFTING;
        } else if (menu instanceof AbstractFurnaceMenu) {
            return Station.FURNACE;
        } else if (menu instanceof StonecutterMenu) {
            return Station.STONECUTTER;
        } else if (menu instanceof LoomMenu) {
            return Station.LOOM;
        } else if (menu instanceof CartographyTableMenu) {
            return Station.CARTOGRAPHY;
        } else if (menu instanceof GrindstoneMenu) {
            return Station.GRINDSTONE;
        } else if (menu instanceof SmithingMenu) {
            return Station.SMITHING;
        } else if (menu instanceof AnvilMenu) {
            return Station.ANVIL;
        } else if (menu instanceof EnchantmentMenu) {
            return Station.ENCHANTING;
        } else if (menu instanceof BrewingStandMenu) {
            return Station.BREWING;
        }
        return Station.OTHER;
    }

    /** Abas do tear (ordem dos botões do painel e número que o servidor põe em {@code Entry.tab}). */
    public static final int LOOM_TAB_COLORS = 0;
    public static final int LOOM_TAB_BANNERS = 1;
    public static final int LOOM_TAB_MOLDS = 2;

    /** Abas da mesa de ferraria: moldes, armaduras, ferramentas e armas (subir para netherite), material. */
    public static final int SMITHING_TAB_TRIMS = 0;
    public static final int SMITHING_TAB_ARMOR = 1;
    public static final int SMITHING_TAB_TOOLS = 2;
    public static final int SMITHING_TAB_MATERIALS = 3;

    /** O que um item é para a bigorna; {@code tab} é a aba do painel. Um predicado só: o filtro e a aba nunca discordam. */
    private enum AnvilKind {
        BOOK(0), GEAR(1), MATERIAL(2);

        final int tab;

        AnvilKind(int tab) {
            this.tab = tab;
        }
    }

    private static AnvilKind anvilKind(ItemStack stack) {
        if (stack.is(Items.ENCHANTED_BOOK)) {
            return AnvilKind.BOOK;
        }
        return stack.isDamageableItem() || EnchantmentHelper.hasAnyEnchantments(stack) || stack.is(Items.NAME_TAG)
                ? AnvilKind.GEAR : AnvilKind.MATERIAL;
    }

    /**
     * Encantamento e suporte de poções calculam o próprio x como {@code (width - imageWidth) / 2} e ignoram
     * {@code leftPos} (fundo, clique, desenho); para o painel deslocar a estação, a largura que eles enxergam tem que
     * ser a que dá exatamente {@code leftPos}. As demais telas respeitam {@code leftPos} (a pedra de amolar só no
     * fundo; ver {@code GrindstoneScreenMixin}, que não dá para refatorar: o {@code ordinal} é constante de anotação).
     */
    public static boolean ignoresLeftPos(AbstractContainerMenu menu) {
        Station station = stationOf(menu);
        return station == Station.ENCHANTING || station == Station.BREWING;
    }

    /** A largura de janela que faz uma tela que ignora {@code leftPos} cair exatamente em {@code leftPos}. */
    public static int widthForLeftPos(int leftPos, int imageWidth) {
        return 2 * leftPos + imageWidth;
    }

    /** A bigorna cria o campo de nome (103 de largura, 62 à direita de {@code leftPos}) com o centro da janela. */
    public static final int ANVIL_NAME_FIELD_WIDTH = 103;
    public static final int ANVIL_NAME_FIELD_DX = 62;

    public static boolean isAnvil(AbstractContainerMenu menu) {
        return stationOf(menu) == Station.ANVIL;
    }

    /**
     * Aba de um item solto no painel: na mesa de ferraria, qual slot o aceita (molde, armadura, ferramenta/arma ou
     * material); no
     * encantamento, equipamento, livros ou lápis-lazúli; no suporte de poções, garrafas, ingrediente ou combustível;
     * na bigorna, livros, equipamento ou material de conserto; nas outras, 0.
     */
    public static int slotTab(AbstractContainerMenu menu, ItemStack stack) {
        switch (stationOf(menu)) {
            case SMITHING -> {
                // Slot 0 = molde, 1 = item a melhorar (armadura ou ferramenta/arma), 2 = material.
                if (menu.getSlot(0).mayPlace(stack)) {
                    return SMITHING_TAB_TRIMS;
                }
                if (menu.getSlot(1).mayPlace(stack)) {
                    return ItemKinds.isArmor(stack) ? SMITHING_TAB_ARMOR : SMITHING_TAB_TOOLS;
                }
                if (menu.getSlot(2).mayPlace(stack)) {
                    return SMITHING_TAB_MATERIALS;
                }
            }
            case ENCHANTING -> {
                // O slot 1 só aceita o lápis.
                if (menu.getSlot(1).mayPlace(stack)) {
                    return 2;
                }
                return stack.is(Items.BOOK) || stack.is(Items.ENCHANTED_BOOK) ? 1 : 0;
            }
            case BREWING -> {
                // Do combustível para as garrafas: o pó de blaze serve nos dois, e aqui conta como combustível.
                for (int i = 4; i >= 0; i--) {
                    if (menu.getSlot(i).mayPlace(stack)) {
                        return i < 3 ? 0 : i - 2;
                    }
                }
            }
            case ANVIL -> {
                return anvilKind(stack).tab;
            }
            default -> {
            }
        }
        return 0;
    }

    /**
     * Em que ordem testar os slots ao pôr um item solto: no encantamento o slot do item aceita qualquer coisa, então
     * o do lápis-lazúli tem que ser testado primeiro.
     */
    public static List<Slot> placementOrder(AbstractContainerMenu menu) {
        List<Slot> order = new ArrayList<>(menu.slots);
        if (stationOf(menu) == Station.ENCHANTING) {
            java.util.Collections.reverse(order.subList(0, 2));
        }
        return order;
    }

    /** Estação que tem livro de receitas (o jogo coloca os ingredientes sozinho). */
    public static boolean hasRecipeBook(AbstractContainerMenu menu) {
        return menu instanceof RecipeBookMenu && isStation(menu);
    }

    /**
     * O stack serve nesta estação? Há <b>três regras de "o que serve"</b> no mod, uma por tipo de lista:
     * <ol>
     * <li><b>Painel "Armazenamento"</b> (este método): só lista o que o jogador poderia pôr na estação. A regra vem
     * dos próprios slots ({@code mayPlace}), então acompanha o jogo e itens de outros mods. Só a bancada (qualquer
     * item pode ser ingrediente) aceita tudo; fornalha, encantamento e bigorna têm o slot de entrada livre no jogo
     * (aceita qualquer coisa), por isso a conta é feita aqui; o slot 1 de fornalha e encantamento (combustível,
     * lápis-lazúli) tem regra própria e é consultado direto. O cortador de pedra e o tear <b>não</b> passam por
     * aqui: listam resultados ({@code BenchResults.list}), não itens soltos.</li>
     * <li><b>Livro de receitas</b> ({@link #usableForCrafting}): só itens comuns, a regra do próprio jogo
     * ({@code Inventory.isUsableForCrafting}: sem dano, encantamento nem nome).</li>
     * <li><b>Resultados com receita</b> (cortador, tear): quem decide é {@code StationRecipes} (o que o jogador já
     * conheceu e o material que há).</li>
     * </ol>
     */
    public static boolean relevant(AbstractContainerMenu menu, Player player, ItemStack stack) {
        if (menu instanceof CraftingMenu) {
            return true;
        }
        if (menu instanceof AbstractFurnaceMenu) {
            return menu.slots.get(1).mayPlace(stack) || player.level().recipeAccess()
                    .propertySet(furnaceInput(menu)).test(stack);
        }
        if (menu instanceof EnchantmentMenu) {
            return menu.slots.get(1).mayPlace(stack) || stack.isEnchantable();
        }
        if (menu instanceof AnvilMenu) {
            // Livro, equipamento e etiqueta sempre servem; material só se conserta algo que o jogador carrega.
            return anvilKind(stack) != AnvilKind.MATERIAL || repairsSomethingOf(player, stack);
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

    /** Id do item no registro ("minecraft:stone"): igual em qualquer idioma e em qualquer máquina. */
    public static String itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    /** Hash de item + componentes: desempate estável entre stacks de mesmo nome (livros encantados, poções). */
    public static int componentsHash(ItemStack stack) {
        return ItemStack.hashItemAndComponents(stack);
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
    public static Map<Item, Integer> missingIngredients(Inventory inventory, AbstractContainerMenu openMenu,
                                                         List<Slot> inputSlots, RecipeHolder<?> holder,
                                                         boolean useMax, Map<Item, Integer> pool) {
        if (!(openMenu instanceof RecipeBookMenu menu)) {
            return Map.of();
        }
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

    /**
     * Combustíveis "de verdade" que o botão de combustível das fornalhas usa, na ordem de preferência. Só itens que
     * existem para queimar: o botão nunca escolhe sozinho tábua, tronco ou ferramenta de madeira do baú (o jogador
     * pode pôr um desses na mão, e aí o botão completa com o mesmo item).
     */
    private static final List<Item> FUELS = List.of(Items.COAL, Items.CHARCOAL, Items.COAL_BLOCK, Items.BLAZE_ROD,
            Items.DRIED_KELP_BLOCK, Items.LAVA_BUCKET);

    /** O slot de combustível da fornalha/defumador/alto-forno aberto, ou {@code null} se não é uma fornalha. */
    public static Slot fuelSlot(AbstractContainerMenu menu) {
        return menu instanceof AbstractFurnaceMenu ? menu.getSlot(1) : null;
    }

    /** O item queima neste slot? O slot do jogo também aceita balde vazio (sobra do balde de lava), que não é combustível. */
    public static boolean burnsIn(Slot slot, ItemStack stack) {
        return !stack.isEmpty() && !stack.is(Items.BUCKET) && slot.mayPlace(stack);
    }

    /**
     * Que combustível o botão põe no slot (um item, contagem 1), ou vazio se não há o que pôr. Slot com combustível:
     * completa com o mesmo item (se o armazenamento tem e ainda cabe). Slot vazio: o primeiro de {@link #FUELS} que o
     * armazenamento tem. A mesma regra no cliente (ícone e dica do botão) e no servidor (que decide de verdade).
     */
    public static ItemStack pickFuel(Slot slot, java.util.function.Predicate<ItemStack> inStorage) {
        ItemStack inside = slot.getItem();
        if (!inside.isEmpty()) {
            ItemStack same = inside.copyWithCount(1);
            boolean room = inside.getCount() < Math.min(inside.getMaxStackSize(), slot.getMaxStackSize(inside));
            return room && burnsIn(slot, same) && inStorage.test(same) ? same : ItemStack.EMPTY;
        }
        for (Item item : FUELS) {
            ItemStack candidate = new ItemStack(item);
            if (burnsIn(slot, candidate) && inStorage.test(candidate)) {
                return candidate;
            }
        }
        return ItemStack.EMPTY;
    }

    /** Fecha a tela aberta do jogador do lado do servidor (o jogo devolve a grade à mochila). */
    public static void closeMenu(ServerPlayer player) {
        player.doCloseContainer();
    }
}
