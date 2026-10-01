package io.github.leoascenci0.stashlink.clientmode;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * O "o que fazer" dentro de um container aberto; o {@link ContainerSession} cuida do "como abrir, esperar e
 * fechar". Cada tecla (N, W) é um Job.
 */
public interface Job {
    /** Resultado de um passo: os cliques deste tick e se acabou neste container. */
    record Step(List<Click> clicks, boolean finished) {
        public static Step finish() {
            return new Step(List.of(), true);
        }

        public static Step clicks(List<Click> clicks) {
            return new Step(clicks, false);
        }
    }

    /** O container acabou de abrir e o conteúdo já chegou (ou o tempo de espera passou). */
    default void onOpened(Minecraft mc, Candidate candidate, List<MenuEntry> entries) {
    }

    /**
     * Chamado uma vez por tick enquanto o container está aberto. Recebe a foto <b>atual</b> do menu (já com
     * qualquer correção do servidor) e o que está no cursor. Devolve os cliques (poucos por tick).
     */
    Step step(Minecraft mc, List<MenuEntry> entries, ItemStack carried);

    /** Terminou neste container (ou foi interrompido); {@code entries} é a última foto do menu. */
    default void onClosed(Minecraft mc, Candidate candidate, List<MenuEntry> entries) {
    }

    /** Vale a pena abrir o próximo container? */
    boolean wantsMore();

    /** Acabou a sessão inteira; mostra a mensagem final. {@code aborted} = foi interrompida. */
    void onFinish(Minecraft mc, boolean aborted);
}
