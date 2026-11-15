package io.github.leoascenci0.stashlink.clientmode;

/**
 * Guarda de onde veio a tela de container que acabou de abrir: de um bloco de armazenamento do mundo (baú, barril,
 * shulker) ou de outra coisa (loja/kit de plugin, que também é uma tela de baú, mas aberta por comando ou por
 * clique num NPC). Não conhece o Minecraft (só números), para poder ser testada sem abrir o jogo.
 *
 * <p>Ideia, em linguagem simples: o cliente anota "usei o bloco X no tick T, e ele deveria abrir uma tela de N
 * slots". Quando um menu novo aparece, só confiamos nele se a anotação existe, é recente e o tamanho bate. Uma
 * anotação vale para <b>uma</b> tela só: a página 2 de uma loja troca o menu sem novo clique, então não passa.
 *
 * <p>Tamanhos esperados: 0 = o bloco não é armazenamento; 27 = baú simples, barril, shulker, baú do End;
 * 54 = baú duplo.
 */
public final class ScreenOrigin {
    /** Quantos ticks, no máximo, entre usar o bloco e o menu aparecer (latência de Realms incluída). */
    public static final int WINDOW_TICKS = 30;

    private boolean used;
    private long tick;
    private int expectedSlots;

    /** O jogador (ou o mod) usou um bloco. O último uso vence: usar um bloco que não guarda nada grava 0. */
    public void blockUsed(long tick, int expectedSlots) {
        this.used = true;
        this.tick = tick;
        this.expectedSlots = expectedSlots;
    }

    /** Clicou numa entidade (lojas de NPC abrem assim): apaga o registro. */
    public void entityUsed() {
        clear();
    }

    /**
     * Um menu NOVO apareceu. Devolve se ele veio de um bloco de armazenamento e consome o registro.
     *
     * @param containerSlots slots do menu que não são do inventário do jogador
     */
    public boolean menuOpened(long tick, int containerSlots) {
        boolean verdict = fromStorage(used, expectedSlots, tick - this.tick, containerSlots);
        clear();
        return verdict;
    }

    /** Esquece tudo (troca de mundo). */
    public void clear() {
        used = false;
        tick = 0;
        expectedSlots = 0;
    }

    /** A regra pura: houve uso, o bloco é armazenamento, foi há pouco e o tamanho da tela é o esperado. */
    public static boolean fromStorage(boolean used, int expectedSlots, long ticksSince, int containerSlots) {
        return used && expectedSlots > 0 && ticksSince >= 0 && ticksSince <= WINDOW_TICKS
                && containerSlots == expectedSlots;
    }
}
