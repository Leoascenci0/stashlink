package io.github.leoascenci0.stashlink.clientmode;

import io.github.leoascenci0.stashlink.Constants;
import io.github.leoascenci0.stashlink.compat.mc.ClientCompat;
import io.github.leoascenci0.stashlink.lootall.LootAllService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;

/**
 * A máquina de estados que "joga" como um jogador, <b>um tick de cada vez</b> (ela é movida pelo tick do cliente,
 * nunca bloqueia o jogo esperando o servidor):
 *
 * <pre>
 *  OPENING  -- clicou no bloco; espera o servidor abrir o menu (com tempo limite)
 *  SETTLING -- menu abriu; espera 2 ticks para o conteúdo (pacote separado) chegar
 *  WORKING  -- o Job manda cliques, poucos por tick, conferindo o menu a cada tick
 *  DELAY    -- fechou; espera uns ticks antes do próximo (não parecer rajada de pacotes)
 * </pre>
 *
 * <p><b>Segurança:</b> a cada tick confere que o jogador continua vivo, fora do espectador, perto do bloco e sem
 * outra tela por cima; se algo falhar, {@link #abort} <b>fecha o container que nós abrimos</b> — nunca fica um
 * container aberto sozinho. Container que não abre (trancado, bloqueado, claim) só é pulado.
 *
 * <p>Modo "attached" (tecla W): a tela já está aberta pelo jogador; não abrimos nem fechamos nada, só rodamos o Job.
 */
public final class ContainerSession {
    /** Quantos ticks esperar o servidor abrir o menu antes de desistir deste container (latência de Realms). */
    static final int OPEN_TIMEOUT_TICKS = 20;
    /** Ticks de espera depois de abrir, para o conteúdo chegar. */
    static final int SETTLE_TICKS = 2;
    /** Pausa entre um container e o próximo. */
    static final int DELAY_TICKS = 3;
    /** Teto de ticks trabalhando num container só. */
    static final int WORK_TIMEOUT_TICKS = 100;
    /** Ticks sem clicar antes de dar o container por terminado: dá tempo de o servidor corrigir a previsão. */
    static final int VERIFY_TICKS = 3;

    private enum State { OPENING, SETTLING, WORKING, DELAY, DONE }

    private final Job job;
    private final Deque<Candidate> queue;
    private final boolean attached;
    private final Consumer<Candidate> onOpenFailed;
    private final ClientLevel level;

    private State state;
    private Candidate current;
    private AbstractContainerMenu menu;
    private int ticks;
    private int sinceClick = 99;

    private ContainerSession(Minecraft mc, Job job, List<Candidate> candidates, boolean attached,
                             Consumer<Candidate> onOpenFailed) {
        this.job = job;
        this.queue = new ArrayDeque<>(candidates);
        this.attached = attached;
        this.onOpenFailed = onOpenFailed;
        this.level = mc.level;
    }

    /** Sessão que abre cada candidato, mexe e fecha. */
    public static ContainerSession opening(Minecraft mc, Job job, List<Candidate> candidates,
                                           Consumer<Candidate> onOpenFailed) {
        ContainerSession s = new ContainerSession(mc, job, candidates, false, onOpenFailed);
        s.openNext(mc);
        return s;
    }

    /** Sessão sobre o container que o jogador já abriu (não abre nem fecha). */
    public static ContainerSession attached(Minecraft mc, Job job) {
        ContainerSession s = new ContainerSession(mc, job, List.of(), true, c -> { });
        s.menu = ClientCompat.menu(mc.player);
        s.state = State.WORKING;
        s.ticks = 0;
        s.job.onOpened(mc, null, MenuSnapshot.of(s.menu, mc.player.getInventory()));
        return s;
    }

    public boolean isAttached() {
        return attached;
    }

    /** @return {@code true} quando a sessão acabou (a engine pode descartá-la) */
    public boolean tick(Minecraft mc) {
        if (state == State.DONE) {
            return true;
        }
        LocalPlayer player = mc.player;
        if (player == null || mc.level != level || !player.isAlive() || player.isSpectator()) {
            // Mundo trocado/jogador morto: não há mais o que fechar com segurança; só encerra.
            abort(mc, "player/world gone");
            return true;
        }
        try {
            switch (state) {
                case DELAY -> {
                    if (--ticks <= 0) {
                        openNext(mc);
                    }
                }
                case OPENING -> tickOpening(mc, player);
                case SETTLING -> tickSettling(mc, player);
                case WORKING -> tickWorking(mc, player);
                default -> {
                }
            }
        } catch (RuntimeException e) {
            // Qualquer erro inesperado: fecha o que abrimos e para (nunca derruba o jogo).
            Constants.LOG.error("Falha no modo cliente", e);
            abort(mc, "error");
        }
        return state == State.DONE;
    }

    /** Interrompe a sessão. Fecha o container se fomos nós que o abrimos. */
    public void abort(Minecraft mc, String why) {
        if (state == State.DONE) {
            return;
        }
        Constants.LOG.debug("Modo cliente interrompido: {}", why);
        closeIfOurs(mc);
        state = State.DONE;
        job.onFinish(mc, true);
    }

    // ------------------------------------------------------------------ estados

    private void openNext(Minecraft mc) {
        if (!job.wantsMore() || queue.isEmpty()) {
            finish(mc);
            return;
        }
        current = queue.poll();
        if (!ClientCompat.withinReach(mc.player, current.pos())) {
            // Jogador andou: este já não alcança; tenta o próximo.
            onOpenFailed.accept(current);
            openNext(mc);
            return;
        }
        ClientCompat.useBlock(mc, current.pos());
        state = State.OPENING;
        ticks = 0;
    }

    private void tickOpening(Minecraft mc, LocalPlayer player) {
        AbstractContainerMenu now = ClientCompat.menu(player);
        boolean opened = now != ClientCompat.inventoryMenu(player);
        // Outra tela por cima (o jogador abriu um menu, o jogo pausou...) enquanto nada abriu: cancela.
        if (!opened && ClientCompat.hasScreenOpen(mc)) {
            abort(mc, "another screen");
            return;
        }
        if (opened) {
            if (!LootAllService.isSupportedMenu(now)) {
                // Abriu algo que não é um baú/shulker (um bloco de outro mod...): fecha e pula.
                ClientCompat.closeContainer(mc);
                failCurrent(mc);
                return;
            }
            menu = now;
            state = State.SETTLING;
            ticks = SETTLE_TICKS;
            return;
        }
        if (!ClientCompat.withinReach(player, current.pos()) || ++ticks > OPEN_TIMEOUT_TICKS) {
            // Não abriu: trancado, bloqueado por bloco em cima, claim, lag... Pula para o próximo.
            failCurrent(mc);
        }
    }

    private void tickSettling(Minecraft mc, LocalPlayer player) {
        if (!stillOurMenu(mc, player)) {
            abort(mc, "menu changed");
            return;
        }
        if (--ticks <= 0) {
            job.onOpened(mc, current, MenuSnapshot.of(menu, player.getInventory()));
            state = State.WORKING;
            sinceClick = VERIFY_TICKS;
            ticks = 0;
        }
    }

    private void tickWorking(Minecraft mc, LocalPlayer player) {
        if (!stillOurMenu(mc, player)) {
            // O jogador fechou a tela (ESC) ou outra tela abriu: para, sem mexer em nada.
            abort(mc, "menu closed or replaced");
            return;
        }
        if (!attached && !ClientCompat.withinReach(player, current.pos())) {
            abort(mc, "out of reach");
            return;
        }
        List<MenuEntry> entries = MenuSnapshot.of(menu, player.getInventory());
        Job.Step step = job.step(mc, entries, menu.getCarried());
        for (Click click : step.clicks()) {
            ClientCompat.click(mc, menu, click);
        }
        sinceClick = step.clicks().isEmpty() ? sinceClick + 1 : 0;
        // "Acabou" só vale depois de uns ticks sem clicar: assim a foto final já tem a correção do servidor.
        if ((step.finished() && sinceClick >= VERIFY_TICKS) || ++ticks > WORK_TIMEOUT_TICKS) {
            job.onClosed(mc, current, entries);
            if (attached) {
                finish(mc);
                return;
            }
            ClientCompat.closeContainer(mc);
            menu = null;
            state = State.DELAY;
            ticks = DELAY_TICKS;
        }
    }

    // ------------------------------------------------------------------ apoio

    /** O menu que abrimos ainda é o do jogador E a tela aberta (se houver) é a dele? */
    private boolean stillOurMenu(Minecraft mc, LocalPlayer player) {
        if (menu == null || ClientCompat.menu(player) != menu) {
            return false;
        }
        // A tela do container aparece junto com o menu; se há tela, tem de ser a deste menu.
        return !ClientCompat.hasScreenOpen(mc) || ClientCompat.screenMenu(mc) == menu;
    }

    private void failCurrent(Minecraft mc) {
        onOpenFailed.accept(current);
        current = null;
        state = State.DELAY;
        ticks = DELAY_TICKS;
        if (!job.wantsMore() || queue.isEmpty()) {
            finish(mc);
        }
    }

    private void finish(Minecraft mc) {
        state = State.DONE;
        job.onFinish(mc, false);
    }

    /** Se um container que nós abrimos ainda está aberto, fecha. No modo attached o container é do jogador. */
    private void closeIfOurs(Minecraft mc) {
        if (!attached && mc.player != null && ClientCompat.menu(mc.player) != ClientCompat.inventoryMenu(mc.player)) {
            ClientCompat.closeContainer(mc);
        }
    }
}
