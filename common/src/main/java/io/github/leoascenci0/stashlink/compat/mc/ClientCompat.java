package io.github.leoascenci0.stashlink.compat.mc;

import io.github.leoascenci0.stashlink.clientmode.Click;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * API de cliente do Minecraft sujeita a mudar entre versões. Fora de {@code compat/}, ninguém chama
 * estas coisas direto (ver docs/UPDATING.md).
 */
public final class ClientCompat {
    private ClientCompat() {
    }

    /** Categoria própria na tela de controles. {@code KeyMapping.Category} existe desde 1.21.9. */
    public static KeyMapping.Category keyCategory(String path) {
        return KeyMapping.Category.register(McCompat.id(path));
    }

    /**
     * Há uma tela aberta? Em MC 26.3 o campo {@code Minecraft.screen} sumiu; agora é
     * {@code mc.gui.screen()}.
     */
    public static boolean hasScreenOpen(Minecraft mc) {
        return mc.gui.screen() != null;
    }

    /** Abre (ou fecha, com {@code null}) uma tela. Em MC 26.3 {@code Minecraft.setScreen} passou para {@code mc.gui.setScreen}. */
    public static void openScreen(Minecraft mc, net.minecraft.client.gui.screens.Screen screen) {
        mc.gui.setScreen(screen);
    }

    /** A tecla do evento é a deste KeyMapping? ({@link KeyEvent} existe desde 1.21.9.) */
    public static boolean keyMatches(KeyMapping key, KeyEvent event) {
        return key.matches(event);
    }

    // ------------------------------------------------------------------ modo cliente (clientmode/)

    /** O mundo atual roda num servidor integrado (mundo único / LAN que nós abrimos)? */
    public static boolean isIntegratedServer(Minecraft mc) {
        return mc.getSingleplayerServer() != null;
    }

    /** O menu que o jogador tem aberto agora ({@code containerMenu}; é o do inventário se nada está aberto). */
    public static AbstractContainerMenu menu(Player player) {
        return player.containerMenu;
    }

    /** O menu do inventário do próprio jogador (o "nenhum container aberto"). */
    public static AbstractContainerMenu inventoryMenu(Player player) {
        return player.inventoryMenu;
    }

    /** Menu da tela de container aberta no momento, ou {@code null} se a tela aberta não é de container. */
    public static AbstractContainerMenu screenMenu(Minecraft mc) {
        return mc.gui.screen() instanceof AbstractContainerScreen<?> s ? s.getMenu() : null;
    }

    /** Slot da hotbar selecionado (0-8). Em 26.1/26.2 o nome pode ser outro ({@code selected}); 26.3: {@code getSelectedSlot()}. */
    public static int selectedSlot(Player player) {
        return player.getInventory().getSelectedSlot();
    }

    public static ItemStack mainHand(Player player) {
        return player.getMainHandItem();
    }

    public static ItemStack offHand(Player player) {
        return player.getOffhandItem();
    }

    /** O jogador está agachado (shift)? Agachado, clicar num bloco não abre o container. */
    public static boolean isSneaking(Player player) {
        return player.isSecondaryUseActive();
    }

    /** Dentro do alcance normal de interação com blocos ({@code blockInteractionRange}, 4.5 por padrão)? */
    public static boolean withinReach(Player player, BlockPos pos) {
        return player.isWithinBlockInteractionRange(pos, 0.0);
    }

    /** Criativo: o item na mão não acaba, então não há o que reabastecer. */
    public static boolean isCreative(Player player) {
        return player.isCreative();
    }

    /** A tecla de soltar item (Q) está apertada? Usada para não confundir "joguei o item fora" com "acabou". */
    public static boolean isDropKeyDown(Minecraft mc) {
        return mc.options.keyDrop.isDown();
    }

    /** Bloco para o qual a mira aponta agora, ou {@code null}. */
    public static BlockPos lookedAtBlock(Minecraft mc) {
        return mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
                ? hit.getBlockPos() : null;
    }

    /** Mostra uma mensagem curta na barra de ação (overlay), como as mensagens do mod no servidor. */
    public static void overlay(Minecraft mc, Component message) {
        if (mc.player != null) {
            mc.player.sendOverlayMessage(message);
        }
    }

    /**
     * "Clica com o botão direito" no bloco, como um jogador: manda o pacote de uso de bloco pelo
     * {@code MultiPlayerGameMode}. O ponto de clique é o centro da face voltada para o jogador (o servidor
     * confere que o ponto está dentro do bloco e que o jogador alcança). Se o bloco for um container que
     * abre, o servidor responde abrindo o menu.
     */
    public static void useBlock(Minecraft mc, BlockPos pos) {
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null) {
            return;
        }
        Vec3 center = Vec3.atCenterOf(pos);
        Direction face = Direction.getApproximateNearest(player.getEyePosition().subtract(center));
        Vec3 hitPoint = center.add(face.getUnitVec3().scale(0.5));
        mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, new BlockHitResult(hitPoint, face, pos, false));
    }

    /**
     * Faz um clique de inventário no menu aberto. Em 26.3 é {@code handleContainerInput(containerId, slot,
     * button, ContainerInput, player)}; antes era {@code handleInventoryMouseClick(..., ClickType, ...)}.
     * O cliente aplica o clique localmente (previsão) e o servidor confirma ou corrige.
     */
    public static void click(Minecraft mc, AbstractContainerMenu menu, Click click) {
        if (mc.player == null || mc.gameMode == null) {
            return;
        }
        ContainerInput input = switch (click.kind()) {
            case PICKUP -> ContainerInput.PICKUP;
            case QUICK_MOVE -> ContainerInput.QUICK_MOVE;
            case SWAP -> ContainerInput.SWAP;
        };
        mc.gameMode.handleContainerInput(menu.containerId, click.slot(), click.button(), input, mc.player);
    }

    /** O jogador está com Shift (esquerdo ou direito) pressionado agora? */
    public static boolean isShiftDown(Minecraft mc) {
        return mc.hasShiftDown();
    }

    /**
     * O evento é do botão esquerdo? Em 26.3 o esquerdo é 1 (não 0). {@code MouseHandler.isLeftPressed} não serve: só
     * é atualizado quando não há tela aberta.
     */
    public static boolean isLeftButton(net.minecraft.client.input.MouseButtonEvent event) {
        return event.button() == 1;
    }

    /** Fecha o container aberto (manda o pacote de fechar e volta ao menu do inventário). */
    public static void closeContainer(Minecraft mc) {
        if (mc.player != null) {
            mc.player.closeContainer();
        }
    }
}
