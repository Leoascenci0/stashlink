package io.github.leoascenci0.stashlink.client;

import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Os textos do painel das bancadas num lugar só (chaves {@code stashlink.bench.*} nos dois idiomas, sem texto de
 * reserva no código: se a chave faltar, o teste de idiomas acusa). Só montagem de texto, sem tela: dá para testar.
 */
final class BenchText {
    static final String EMPTY_RADIUS = "stashlink.bench.panel.empty.radius";
    static final String EMPTY_BACKPACK = "stashlink.bench.panel.empty.backpack";
    static final String EMPTY = "stashlink.bench.panel.empty";
    static final String PAYMENT_NONE_RADIUS = "stashlink.bench.beacon.none.radius";
    static final String PAYMENT_NONE_BACKPACK = "stashlink.bench.beacon.none.backpack";
    static final String PAYMENT_NONE = "stashlink.bench.beacon.none";
    static final String NO_MATCH = "stashlink.bench.panel.nomatch";
    static final String COUNT = "stashlink.bench.panel.count";
    static final String ITEM_TIP = "stashlink.bench.panel.tip";
    static final String RESULT_TIP = "stashlink.bench.panel.result.tip";
    static final String RESULT_MISSING = "stashlink.bench.panel.result.missing";
    static final String POTION_TIP = "stashlink.bench.panel.potion.tip";
    static final String POTION_MISSING = "stashlink.bench.panel.potion.missing";
    static final String COLOR_TIP = "stashlink.bench.panel.color.tip";
    static final String COLOR_NONE = "stashlink.bench.panel.color.none";
    static final String TITLE = "stashlink.bench.panel.title";

    private BenchText() {
    }

    /**
     * Lista vazia: com busca, "nenhum item combina"; sem busca, "nada na mochila nem no raio de N blocos" (o raio
     * efetivo que o servidor mandou, não o preferido do jogador). Raio 0 é "usar baús" desligado: só a mochila conta,
     * e a mensagem diz isso em vez de "raio de 0 blocos". Sem raio conhecido, a mensagem genérica.
     */
    static Component empty(boolean searching, int radius) {
        if (searching) {
            return Component.translatable(NO_MATCH);
        }
        return byRadius(radius, EMPTY_RADIUS, EMPTY_BACKPACK, EMPTY);
    }

    /** Ícone de pagamento do sinalizador que não há: mesma regra de {@link #empty} (mochila e raio, só mochila, genérica). */
    static Component paymentMissing(int radius) {
        return byRadius(radius, PAYMENT_NONE_RADIUS, PAYMENT_NONE_BACKPACK, PAYMENT_NONE);
    }

    private static Component byRadius(int radius, String withRadius, String backpackOnly, String unknown) {
        if (radius > 0) {
            return Component.translatable(withRadius, radius);
        }
        return Component.translatable(radius == 0 ? backpackOnly : unknown);
    }

    /** "Disponível: N": a contagem do item na mochila + armazenamento, no tooltip. Uma função só para todas as estações. */
    static Component inStorage(int count) {
        return Component.translatable(COUNT, count);
    }

    /** As linhas extras do tooltip de um item solto, de um resultado ou de uma cor. */
    static List<Component> itemLines(int count) {
        return List.of(inStorage(count), Component.translatable(ITEM_TIP));
    }

    static Component resultLine(boolean missing) {
        return Component.translatable(missing ? RESULT_MISSING : RESULT_TIP);
    }

    /** Poção do suporte: o clique monta só o próximo passo (garrafas + ingrediente + pó de blaze se faltar). */
    static Component potionLine(boolean missing) {
        return Component.translatable(missing ? POTION_MISSING : POTION_TIP);
    }

    static Component colorLine(boolean missing) {
        return Component.translatable(missing ? COLOR_NONE : COLOR_TIP);
    }

    /** Quanto o clique pede: esquerdo = uma pilha, direito = um, Shift+esquerdo = o máximo (que cabe). */
    enum Amount {
        ONE, STACK, MAX
    }

    /** Numeração de botão do 26.3 (ver {@code AbstractContainerScreen.getContainerClickButton}): esquerdo = 1, direito = 3. */
    static final int LEFT = 1;
    static final int RIGHT = 3;

    /** O que o botão pede, ou {@code null} se o painel não trata esse botão (o meio, por exemplo, segue para o jogo). */
    static Amount amountFor(int button, boolean shift) {
        if (button == RIGHT) {
            return Amount.ONE;
        }
        if (button == LEFT) {
            return shift ? Amount.MAX : Amount.STACK;
        }
        return null;
    }

    /**
     * No servidor só existe "um" ou "o que cabe": o teto de um slot ou do cursor é uma pilha, então pilha e máximo
     * dão o mesmo resultado (o Shift existe para o gesto ser o mesmo do resto do mod e do jogo).
     */
    static boolean one(Amount amount) {
        return amount == Amount.ONE;
    }
}
