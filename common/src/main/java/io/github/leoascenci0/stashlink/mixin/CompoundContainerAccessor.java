package io.github.leoascenci0.stashlink.mixin;

import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Abre as duas metades do baú duplo (campos privados), para guardar a trava na metade certa. */
@Mixin(CompoundContainer.class)
public interface CompoundContainerAccessor {
    @Accessor("container1")
    Container stashlink$first();

    @Accessor("container2")
    Container stashlink$second();
}
