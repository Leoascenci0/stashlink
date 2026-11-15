package io.github.leoascenci0.stashlink.clientmode;

/**
 * Percebe quando o servidor recusa os nossos cliques (anticheat, limite de pacotes). Não conhece o Minecraft (só
 * números), para poder ser testada sem abrir o jogo.
 *
 * <p>Como funciona, em linguagem simples: o cliente aplica o clique na hora (previsão). Se o servidor recusa, ele
 * reenvia o conteúdo e o menu <b>volta</b> ao estado de antes. Então anotamos uma "impressão digital" do menu antes
 * e depois dos cliques; se no tick seguinte o menu estiver igual ao de antes (e o clique devia ter mudado algo),
 * foi recusado. Duas recusas seguidas na mesma situação = parar, em vez de insistir e arriscar punição.
 */
public final class ClickRefusals {
    /** Ticks seguidos com o menu no estado previsto para dar o clique por aceito. */
    public static final int QUIET_TICKS = 3;
    /** Recusas seguidas (mesma situação de partida) que mandam parar. */
    public static final int MAX_REFUSALS = 2;

    private boolean pending;
    private int base;
    private int predicted;
    private boolean hasPredicted;
    private int quiet;
    private int refusals;
    private boolean hasLastRefused;
    private int lastRefusedBase;

    /** Antes de clicar: se ainda não há uma foto de partida esperando resposta, guarda esta. */
    public void beforeClicks(int fingerprint) {
        if (!pending) {
            pending = true;
            base = fingerprint;
            hasPredicted = false;
            quiet = 0;
        }
    }

    /** Depois de clicar: o estado que o cliente previu (já com o clique aplicado). */
    public void afterClicks(int fingerprint) {
        if (pending) {
            predicted = fingerprint;
            hasPredicted = true;
            quiet = 0;
        }
    }

    /**
     * Chamar uma vez por tick, antes de clicar de novo.
     *
     * @return {@code true} quando o servidor já recusou {@link #MAX_REFUSALS} vezes seguidas: hora de parar
     */
    public boolean observe(int fingerprint) {
        if (pending && hasPredicted) {
            if (predicted == base) {
                // O clique não mudava nada (nada coube): não há como o servidor "desfazer" algo.
                pending = false;
            } else if (fingerprint == base) {
                refusals = hasLastRefused && lastRefusedBase == base ? refusals + 1 : 1;
                hasLastRefused = true;
                lastRefusedBase = base;
                pending = false;
            } else if (fingerprint == predicted) {
                if (++quiet >= QUIET_TICKS) {
                    pending = false;
                    refusals = 0;
                    hasLastRefused = false;
                }
            } else {
                quiet = 0;   // estado intermediário (o servidor aceitou só parte, ou outro jogador mexeu)
            }
        }
        return refusals >= MAX_REFUSALS;
    }

    /** Container novo ou botão solto: começa do zero. */
    public void reset() {
        pending = false;
        hasPredicted = false;
        quiet = 0;
        refusals = 0;
        hasLastRefused = false;
    }
}
