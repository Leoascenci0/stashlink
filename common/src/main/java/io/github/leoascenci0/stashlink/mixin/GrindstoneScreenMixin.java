package io.github.leoascenci0.stashlink.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.GrindstoneScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.GrindstoneMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Cliente: a pedra de amolar desenha o fundo no centro da janela e ignora {@code leftPos} (os slots usam {@code leftPos}),
 * então quando o painel desloca a estação o fundo ficava para trás. Aqui o fundo passa a seguir {@code leftPos}, que é o
 * mesmo valor do centro quando ninguém desloca a tela: sem o mod no servidor nada muda.
 * Estende a superclasse só para ler o campo protegido {@code leftPos} (o Mixin não "sombreia" campo herdado).
 */
@Mixin(GrindstoneScreen.class)
public abstract class GrindstoneScreenMixin extends AbstractContainerScreen<GrindstoneMenu> {
    protected GrindstoneScreenMixin(GrindstoneMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    /**
     * {@code ordinal = 2}: os inteiros do método são, em ordem, mouseX (0), mouseY (1) e {@code xo} (2). Com 0 nenhuma
     * escrita era encontrada e o jogo caía ao carregar a tela.
     */
    @ModifyVariable(method = "extractBackground", at = @At("STORE"), ordinal = 2, require = 0)
    private int stashlink$backgroundFollowsLeftPos(int centered) {
        return this.leftPos;
    }
}
