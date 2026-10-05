package io.github.leoascenci0.stashlink.mixin;

import io.github.leoascenci0.stashlink.client.BenchClient;
import io.github.leoascenci0.stashlink.compat.mc.BenchCompat;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.GhostSlots;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import org.jspecify.annotations.Nullable;
import java.util.List;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.RecipeBookMenu;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Cliente (Item 16): o livro de receitas só sabe o que há na mochila, então pinta de vermelho o que dá para fazer
 * com os baús. Aqui ele soma o que o servidor disse que há no armazenamento, e se recalcula quando a lista muda.
 * Só aparência: quem coloca os ingredientes é o servidor, ao clicar na receita.
 */
@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentMixin {
    @Shadow
    @Final
    protected RecipeBookMenu menu;
    @Shadow
    @Final
    private StackedItemContents stackedContents;
    @Shadow
    private int timesInventoryChanged;

    @Shadow
    @Final
    private GhostSlots ghostSlots;
    @Shadow
    private ClientRecipeBook book;
    @Shadow
    protected Minecraft minecraft;

    @Shadow
    protected abstract boolean isCraftingSlot(Slot slot);

    @Shadow
    public abstract boolean isVisible();

    /**
     * Fornalhas (Item 16.3): com o painel do StashLink valendo, o livro de receitas do jogo fica sempre fechado (o painel
     * ocupa o lugar dele, com abas fixas e combustível). Sem o mod no servidor ou com a função desligada, nada muda.
     */
    @Inject(method = "isVisible", at = @At("HEAD"), cancellable = true)
    private void stashlink$closedWhenPanelReplacesIt(CallbackInfoReturnable<Boolean> cir) {
        if (BenchCompat.hidesRecipeBook(menu) && BenchClient.expectedFor(menu)) {
            cir.setReturnValue(false);
        }
    }

    @Shadow
    private boolean tryPlaceRecipe(RecipeCollection collection, RecipeDisplayId recipe, boolean useMaxItems) {
        throw new AssertionError();
    }

    @Unique
    private int stashlink$seenVersion = -1;

    /** O item que faltava e aparecia como "fantasma" no slot clicado (lido antes de o jogo limpar os fantasmas). */
    @Unique
    private ItemStack stashlink$missingIngredient = ItemStack.EMPTY;

    /** Antes: clicou num slot vazio da grade que mostra um ingrediente fantasma? Guarda qual item era. */
    @Inject(method = "slotClicked", at = @At("HEAD"))
    private void stashlink$rememberGhost(@Nullable Slot slot, CallbackInfo ci) {
        stashlink$missingIngredient = ItemStack.EMPTY;
        if (slot == null || slot.hasItem() || !isVisible() || !isCraftingSlot(slot)) {
            return;
        }
        Object ghost = ((GhostSlotsAccessor) ghostSlots).stashlink$ingredients().get(slot);
        if (ghost != null) {
            List<ItemStack> items = ((GhostSlotAccessor) ghost).stashlink$items();
            if (!items.isEmpty()) {
                stashlink$missingIngredient = items.get(0).copyWithCount(1);
            }
        }
    }

    /**
     * Depois (o jogo já limpou os fantasmas): se o jogador não tem esse item nem na mochila nem no armazenamento, vai
     * para a receita que o fabrica (só receitas que ele já descobriu), em vez de não fazer nada.
     */
    @Inject(method = "slotClicked", at = @At("TAIL"))
    private void stashlink$jumpToMissingIngredientRecipe(@Nullable Slot slot, CallbackInfo ci) {
        ItemStack wanted = stashlink$missingIngredient;
        stashlink$missingIngredient = ItemStack.EMPTY;
        // Só com a função ligada, sem cadeado do servidor e com o mod no servidor: senão o "armazenamento" que vimos
        // é uma lista velha/vazia e o salto levaria a uma receita que o servidor nem ajuda a montar.
        if (wanted.isEmpty() || !BenchClient.activeFor(menu) || minecraft.player.getInventory().countItem(wanted.getItem()) > 0
                || BenchClient.poolHas(wanted)) {
            return;
        }
        var context = SlotDisplayContext.fromLevel(minecraft.level);
        RecipeCollection bestCollection = null;
        RecipeDisplayId best = null;
        for (RecipeCollection collection : book.getCollections()) {
            for (RecipeDisplayEntry entry : collection.getRecipes()) {
                if (entry.resultItems(context).stream().anyMatch(r -> r.is(wanted.getItem()))
                        && (best == null || (collection.isCraftable(entry.id()) && !bestCollection.isCraftable(best)))) {
                    bestCollection = collection;
                    best = entry.id();
                }
            }
        }
        if (best != null) {
            tryPlaceRecipe(bestCollection, best, false);
        }
    }

    /** Chegou uma lista nova do servidor: força o livro a refazer a conta no próximo tick. */
    @Inject(method = "tick", at = @At("HEAD"))
    private void stashlink$refreshOnNewPool(CallbackInfo ci) {
        if (stashlink$seenVersion != BenchClient.version()) {
            stashlink$seenVersion = BenchClient.version();
            timesInventoryChanged = -1;
        }
    }

    /** Depois de contar a mochila e a grade, antes de marcar o que dá para fazer: soma o armazenamento. */
    // require = 0: se outro mod ou uma versão nova mudar o método, o livro só deixa de somar o armazenamento
    // (a montagem da receita continua no servidor); o jogo não cai ao abrir a bancada.
    @Inject(method = "updateStackedContents",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/recipebook/RecipeBookComponent;selectMatchingRecipes()V"),
            require = 0)
    private void stashlink$countStorage(CallbackInfo ci) {
        BenchClient.addTo(stackedContents, menu);
    }
}
