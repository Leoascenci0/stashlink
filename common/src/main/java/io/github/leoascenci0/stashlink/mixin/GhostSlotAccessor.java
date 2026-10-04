package io.github.leoascenci0.stashlink.mixin;

import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/** Cliente: os itens que um fantasma do livro de receitas pode mostrar (classe interna privada do jogo). */
@Mixin(targets = "net.minecraft.client.gui.screens.recipebook.GhostSlots$GhostSlot")
public interface GhostSlotAccessor {
    @Accessor("items")
    List<ItemStack> stashlink$items();
}
