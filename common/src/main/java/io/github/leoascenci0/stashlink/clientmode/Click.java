package io.github.leoascenci0.stashlink.clientmode;

/**
 * Um clique de inventário que o modo cliente vai fazer. É um tipo nosso (e não o {@code ContainerInput} do
 * Minecraft) para a lógica pura não depender de API que muda entre versões; o {@code ClientCompat} traduz.
 *
 * @param slot   índice do slot no <b>menu aberto</b> (não no inventário do jogador)
 * @param button botão do mouse (0 = esquerdo) ou, no SWAP, o número da hotbar (0-8)
 */
public record Click(int slot, int button, Kind kind) {
    public enum Kind {
        /** Clique normal: pega/solta o stack no cursor. */
        PICKUP,
        /** Shift-clique: o próprio jogo escolhe o destino. */
        QUICK_MOVE,
        /** Tecla numérica: troca o slot clicado com um slot da hotbar. */
        SWAP
    }

    public static Click quickMove(int slot) {
        return new Click(slot, 0, Kind.QUICK_MOVE);
    }

    public static Click pickup(int slot) {
        return new Click(slot, 0, Kind.PICKUP);
    }

    public static Click swapToHotbar(int slot, int hotbarIndex) {
        return new Click(slot, hotbarIndex, Kind.SWAP);
    }
}
