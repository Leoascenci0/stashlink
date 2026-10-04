package io.github.leoascenci0.stashlink.mixin;

import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import net.minecraft.client.gui.screens.recipebook.GhostSlots;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Cliente: lê os "fantasmas" que o livro de receitas mostra nos slots da grade (o ingrediente que falta). */
@Mixin(GhostSlots.class)
public interface GhostSlotsAccessor {
    @Accessor("ingredients")
    Reference2ObjectMap<Slot, Object> stashlink$ingredients();
}
