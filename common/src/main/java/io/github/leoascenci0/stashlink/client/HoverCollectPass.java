package io.github.leoascenci0.stashlink.client;

/**
 * A "memória" de uma passagem do mouse com Shift (Item 21): decide, slot a slot, se aquele slot ainda deve levar o
 * shift-clique. Não conhece o Minecraft (só números), para poder ser testada sem abrir o jogo.
 *
 * <p>Regras: um clique por passagem (só quando o mouse <i>entra</i> num slot diferente do anterior; ficar parado em
 * cima não repete); no máximo {@link #MAX_CLICKS_PER_TICK} cliques por tick; e depois de {@link #MAX_FAILURES}
 * cliques seguidos que não moveram nada (destino cheio) para até soltar o Shift.
 */
public final class HoverCollectPass {
    public static final int MAX_CLICKS_PER_TICK = 4;
    public static final int MAX_FAILURES = 3;

    private int lastSlot = -1;
    private long tick = Long.MIN_VALUE;
    private int clicksThisTick;
    private int failures;

    /** O mouse está sobre {@code slot} (-1 = fora de qualquer slot) neste {@code tick}. Pode clicar agora? */
    public boolean shouldClick(int slot, long currentTick) {
        if (slot != lastSlot) {
            lastSlot = slot;
            if (slot < 0) {
                return false;
            }
            if (currentTick != tick) {
                tick = currentTick;
                clicksThisTick = 0;
            }
            if (failures >= MAX_FAILURES || clicksThisTick >= MAX_CLICKS_PER_TICK) {
                return false;
            }
            clicksThisTick++;
            return true;
        }
        return false;
    }

    /** Resultado do clique que acabou de sair: moveu alguma coisa? Seguidas sem mover = destino cheio. */
    public void clickResult(boolean moved) {
        failures = moved ? 0 : failures + 1;
    }

    /** O botão acabou de ser apertado sobre {@code slot}: o clique do próprio jogo já tratou esse slot, não repetir. */
    public void arm(int slot) {
        lastSlot = slot;
    }

    /** Soltou o Shift, fechou a tela ou pegou algo no cursor: a próxima passagem começa do zero. */
    public void reset() {
        lastSlot = -1;
        failures = 0;
        clicksThisTick = 0;
    }
}
