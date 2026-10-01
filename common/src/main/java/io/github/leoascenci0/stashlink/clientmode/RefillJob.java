package io.github.leoascenci0.stashlink.clientmode;

import io.github.leoascenci0.stashlink.compat.mc.ClientCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reabastecer a mão no modo cliente (Item 10.3). Dentro do container aberto, procura o item que acabou e o leva
 * para o slot da hotbar selecionado com <b>um clique SWAP</b>; depois olha o menu para ver se a mão realmente
 * recebeu o item (nunca assume que deu certo).
 *
 * <p>Segurança: se o slot da mão voltou a ter outro item (o jogador colocou algo), ou se o jogador trocou de slot
 * da hotbar, <b>não clica</b> — não sobrescreve nada. Só repõe o mesmo item que acabou (não faz a troca
 * balde/tigela/garrafa do modo servidor: aqui a mão vazia é o único caso).
 */
public final class RefillJob implements Job {
    /** Ticks esperando o servidor confirmar o clique antes de dar o slot por recusado e tentar outro. */
    static final int CONFIRM_TICKS = 4;

    private final ItemStack model;
    private final int hotbar;
    private final ContentsCache cache;
    private final Set<Integer> tried = new HashSet<>();
    private int pending = -1;
    private int waited;
    private boolean refilled;
    private boolean stop;
    private boolean openedAny;

    /**
     * @param model  o que estava na mão (só serve de modelo: item e componentes)
     * @param hotbar slot da hotbar (0-8) que estava selecionado quando a mão esvaziou
     */
    public RefillJob(ItemStack model, int hotbar, ContentsCache cache) {
        this.model = model.copyWithCount(1);
        this.hotbar = hotbar;
        this.cache = cache;
    }

    @Override
    public void onOpened(Minecraft mc, Candidate candidate, List<MenuEntry> entries) {
        openedAny = true;
        tried.clear();
        pending = -1;
        waited = 0;
    }

    @Override
    public Step step(Minecraft mc, List<MenuEntry> entries, ItemStack carried) {
        if (refilled || stop || !carried.isEmpty()) {
            return Step.finish();
        }
        // O jogador mexeu na hotbar (rolou o mouse) enquanto abríamos: o slot de destino já não é a mão dele.
        if (mc.player == null || ClientCompat.selectedSlot(mc.player) != hotbar) {
            stop = true;
            return Step.finish();
        }
        ClientMoveLogic.HandState hand = ClientMoveLogic.handState(entries, model, hotbar);
        if (hand == null || hand == ClientMoveLogic.HandState.OCCUPIED) {
            // Outro item na mão: não sobrescrevemos. (Se o nosso SWAP deu certo seria REFILLED.)
            stop = true;
            return Step.finish();
        }
        if (hand == ClientMoveLogic.HandState.REFILLED) {
            refilled = true;
            return Step.finish();
        }
        // Mão ainda vazia. Se acabamos de clicar, dá um tempo para o servidor confirmar (ou corrigir).
        if (pending >= 0 && ++waited <= CONFIRM_TICKS) {
            return Step.clicks(List.of());
        }
        if (pending >= 0) {
            tried.add(pending); // recusado/ignorado: não insiste neste slot, tenta outro igual
            pending = -1;
        }
        int src = ClientMoveLogic.refillSource(entries, model, tried);
        if (src < 0) {
            return Step.finish(); // este container não tem (mais) o item
        }
        pending = src;
        waited = 0;
        return Step.clicks(List.of(ClientMoveLogic.refillClick(src, hotbar)));
    }

    @Override
    public void onClosed(Minecraft mc, Candidate candidate, List<MenuEntry> entries) {
        if (candidate != null) {
            // Aprende o que vimos (já sem o stack que levamos): ajuda a ordenar na próxima vez.
            cache.put(candidate.keys(), MenuSnapshot.containerStacks(entries));
        }
    }

    @Override
    public boolean wantsMore() {
        return !refilled && !stop;
    }

    @Override
    public void onFinish(Minecraft mc, boolean aborted) {
        ClientModeEngine.refillEnded(mc);
        if (aborted) {
            ClientModeEngine.say(mc, Component.translatableWithFallback("stashlink.client_mode.aborted",
                    "StashLink: cancelled"));
        } else if (refilled) {
            ClientModeEngine.say(mc, Component.translatableWithFallback("stashlink.client_mode.refill.done",
                    "Refilled from a nearby container"));
        } else if (openedAny && !stop) {
            ClientModeEngine.say(mc, Component.translatableWithFallback("stashlink.client_mode.refill.none",
                    "Nothing to refill with nearby"));
        }
    }
}
