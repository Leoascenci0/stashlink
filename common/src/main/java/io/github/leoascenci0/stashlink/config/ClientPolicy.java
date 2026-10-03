package io.github.leoascenci0.stashlink.config;

/**
 * O que o servidor atual contou ao cliente sobre as funções: quais estão trancadas e se este jogador pode mexer
 * nos cadeados. Fica em {@code config} (sem nada do cliente do jogo) para o código comum poder consultar
 * mesmo rodando num servidor dedicado, onde ele simplesmente nunca é preenchido.
 */
public final class ClientPolicy {
    private static volatile boolean known;
    private static volatile int lockedMask;
    private static volatile boolean canEdit;
    private static volatile int version;

    private ClientPolicy() {
    }

    /** Ao entrar num servidor: esquece o que sabia do anterior. */
    public static void reset() {
        known = false;
        lockedMask = 0;
        canEdit = false;
        version++;
    }

    public static void apply(int locked, boolean edit) {
        lockedMask = locked & Feature.ALL_MASK;
        canEdit = edit;
        known = true;
        version++;
    }

    /** O servidor com o mod já contou a política? (Sem isso, vale o que o próprio mundo local diz.) */
    public static boolean known() {
        return known;
    }

    public static boolean canEdit() {
        return canEdit;
    }

    /** Muda a cada atualização; a tela compara para se redesenhar quando o servidor responde. */
    public static int version() {
        return version;
    }

    /** Esta função está trancada onde o jogador está agora? */
    public static boolean locked(Feature feature) {
        return known ? (lockedMask & feature.bit()) != 0 : StashLinkConfig.isFeatureLocked(feature);
    }
}
