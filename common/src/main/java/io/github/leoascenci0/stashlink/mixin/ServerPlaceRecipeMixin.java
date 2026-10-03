package io.github.leoascenci0.stashlink.mixin;

import io.github.leoascenci0.stashlink.bench.BenchRecipe;
import net.minecraft.recipebook.ServerPlaceRecipe;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Livro de receitas com armazenamento (Item 16). {@code ServerPlaceRecipe.placeRecipe} é o ponto único por onde o
 * jogo coloca os ingredientes de uma receita escolhida no livro: bancada, fornalha, defumador e alto-forno passam
 * por ele. Antes: o mod traz da caixa o que falta na mochila; depois: devolve a sobra. O jogo no meio não muda.
 */
@Mixin(ServerPlaceRecipe.class)
public abstract class ServerPlaceRecipeMixin {
    private static final String PLACE = "placeRecipe(Lnet/minecraft/recipebook/ServerPlaceRecipe$CraftingMenuAccess;"
            + "IILjava/util/List;Ljava/util/List;Lnet/minecraft/world/entity/player/Inventory;"
            + "Lnet/minecraft/world/item/crafting/RecipeHolder;ZZ)"
            + "Lnet/minecraft/world/inventory/RecipeBookMenu$PostPlaceAction;";

    @Inject(method = PLACE, at = @At("HEAD"))
    private static void stashlink$bringFromStorage(ServerPlaceRecipe.CraftingMenuAccess<?> access, int width, int height,
                                                   List<Slot> inputGridSlots, List<Slot> slotsToClear,
                                                   Inventory inventory, RecipeHolder<?> recipe, boolean useMaxItems,
                                                   boolean isCreative,
                                                   CallbackInfoReturnable<RecipeBookMenu.PostPlaceAction> cir) {
        BenchRecipe.before(inventory, inputGridSlots, recipe, useMaxItems, isCreative);
    }

    @Inject(method = PLACE, at = @At("RETURN"))
    private static void stashlink$returnLeftovers(ServerPlaceRecipe.CraftingMenuAccess<?> access, int width,
                                                  int height, List<Slot> inputGridSlots, List<Slot> slotsToClear,
                                                  Inventory inventory, RecipeHolder<?> recipe, boolean useMaxItems,
                                                  boolean isCreative,
                                                  CallbackInfoReturnable<RecipeBookMenu.PostPlaceAction> cir) {
        BenchRecipe.after();
    }
}
