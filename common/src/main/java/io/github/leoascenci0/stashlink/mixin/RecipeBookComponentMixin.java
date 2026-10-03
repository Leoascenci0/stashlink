package io.github.leoascenci0.stashlink.mixin;

import io.github.leoascenci0.stashlink.client.BenchClient;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.RecipeBookMenu;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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

    @Unique
    private int stashlink$seenVersion = -1;

    /** Chegou uma lista nova do servidor: força o livro a refazer a conta no próximo tick. */
    @Inject(method = "tick", at = @At("HEAD"))
    private void stashlink$refreshOnNewPool(CallbackInfo ci) {
        if (stashlink$seenVersion != BenchClient.version()) {
            stashlink$seenVersion = BenchClient.version();
            timesInventoryChanged = -1;
        }
    }

    /** Depois de contar a mochila e a grade, antes de marcar o que dá para fazer: soma o armazenamento. */
    @Inject(method = "updateStackedContents",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/recipebook/RecipeBookComponent;selectMatchingRecipes()V"))
    private void stashlink$countStorage(CallbackInfo ci) {
        BenchClient.addTo(stackedContents, menu);
    }
}
