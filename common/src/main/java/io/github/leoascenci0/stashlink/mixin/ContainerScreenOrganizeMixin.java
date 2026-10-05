package io.github.leoascenci0.stashlink.mixin;

import io.github.leoascenci0.stashlink.client.OrganizeClient;
import io.github.leoascenci0.stashlink.lootall.LootAllService;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cliente (Item 20): os botões "Organizar" e "Sistema" ao lado da tela de baú/barril/shulker. "Organizar" arruma este
 * container (20.1); "Sistema" abre a tela de busca e de organizar tudo (20.2/20.3). Só aparecem com o mod no servidor e a
 * função ligada. Mixin separado do {@code AbstractContainerScreenMixin} de propósito.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class ContainerScreenOrganizeMixin {
    @Shadow
    protected int leftPos;
    @Shadow
    protected int topPos;
    @Shadow
    @Final
    protected int imageWidth;

    @Inject(method = "init", at = @At("TAIL"))
    private void stashlink$addOrganizeButtons(CallbackInfo ci) {
        AbstractContainerScreen<?> self = (AbstractContainerScreen<?>) (Object) this;
        if (!LootAllService.isSupportedMenu(self.getMenu()) || !OrganizeClient.available()) {
            return;
        }
        int containerId = self.getMenu().containerId;
        // Janela estreita (GUI 4): encosta na borda direita em vez de sair da tela.
        int x = Math.max(0, Math.min(leftPos + imageWidth + 3, self.width - 56 - 2));
        ScreenInvoker invoker = (ScreenInvoker) (Object) this;
        Button organize = Button.builder(Component.translatableWithFallback("stashlink.organize.button", "Organize"),
                b -> OrganizeClient.organizeChest(containerId)).bounds(x, topPos + 4, 56, 14).build();
        organize.setTooltip(Tooltip.create(Component.translatableWithFallback("stashlink.organize.button_tip",
                "Merge stacks and sort this container by category and name")));
        invoker.stashlink$addRenderableWidget(organize);
        Button system = Button.builder(Component.translatableWithFallback("stashlink.organize.system_button", "System"),
                b -> {
                    // Fecha o baú de verdade (o servidor só organiza o sistema com o inventário comum aberto).
                    self.onClose();
                    OrganizeClient.openScreen();
                }).bounds(x, topPos + 20, 56, 14).build();
        system.setTooltip(Tooltip.create(Component.translatableWithFallback("stashlink.organize.system_button_tip",
                "Search items and organize all nearby storage")));
        invoker.stashlink$addRenderableWidget(system);
    }
}
