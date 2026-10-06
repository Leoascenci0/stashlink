package io.github.leoascenci0.stashlink.bench;

import io.github.leoascenci0.stashlink.compat.mc.BenchCompat;
import io.github.leoascenci0.stashlink.compat.mc.BrewingCompat;
import io.github.leoascenci0.stashlink.compat.mc.StationRecipes;
import io.github.leoascenci0.stashlink.config.Feature;
import io.github.leoascenci0.stashlink.config.FeatureGate;
import io.github.leoascenci0.stashlink.network.BenchPoolSync;
import io.github.leoascenci0.stashlink.source.StackListSink;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * O painel "Armazenamento" ao lado de uma estação. Em estações com receita própria (cortador de pedra, tear) lista os
 * <b>resultados</b> que o jogador já conhece (em vermelho os que faltam material, como o livro de receitas) e clicar
 * monta a receita: o servidor põe cada entrada no slot certo e escolhe a receita. Nas demais estações (bigorna,
 * amolar...) clicar num item o coloca direto no slot da estação que o aceita. Tudo é revalidado aqui.
 */
public final class BenchResults {
    /** {@code recipeId} do pedido: "ponha este item no slot da estação que o aceita" (em vez do cursor). */
    public static final int PLACE = -2;

    /** {@code recipeId} do pedido comum do painel: "ponha um stack deste item no meu cursor". */
    public static final int CURSOR = -1;

    /** {@code recipeId} dos botões de pagamento do sinalizador: "ponha 1 deste item no slot de pagamento". */
    public static final int PAY = -4;

    /** Só estes {@code recipeId} chegam do cliente de verdade ({@code >= 0} é uma receita); o resto é ignorado. */
    public static boolean validRequestId(int recipeId) {
        return recipeId >= 0 || recipeId == PLACE || recipeId == CURSOR || recipeId == PAY;
    }

    private BenchResults() {
    }

    public static boolean supports(AbstractContainerMenu menu) {
        return StationRecipes.supports(menu);
    }

    /** Os resultados conhecidos (e as cores do tear), na ordem única de {@link BenchOrder}: possíveis antes dos vermelhos. */
    public static List<BenchPoolSync.Entry> list(ServerPlayer player) {
        List<ItemStack> have = usable(BenchPool.of(player).atHand());
        List<StationRecipes.Option> options = StationRecipes.options(player, player.containerMenu, have);
        List<BenchPoolSync.Entry> ok = new ArrayList<>();
        List<BenchPoolSync.Entry> missing = new ArrayList<>();
        List<BenchPoolSync.Entry> colors = new ArrayList<>();
        // Aba "Cores" (tear): uma entrada por cor de corante conhecida (vermelha se não há corante dela agora).
        for (StationRecipes.DyePick pick : StationRecipes.dyePicks(player, player.containerMenu, have)) {
            colors.add(new BenchPoolSync.Entry(pick.dye(), 0, BenchPoolSync.Entry.COLOR_PICK, !pick.has(), 0, pick.color()));
        }
        java.util.Set<Integer> seenOk = new java.util.HashSet<>();
        java.util.Set<Integer> seenMissing = new java.util.HashSet<>();
        for (StationRecipes.Option option : options) {
            boolean can = option.needs().stream().allMatch(need -> have.stream().anyMatch(need));
            int key = key(option);
            // O mesmo resultado pode sair de mais de uma pedra (ex.: ardósia abissal talhada). Uma entrada só: a possível
            // vence a que falta material, e entre iguais fica a primeira.
            if (can ? !seenOk.add(key) : !seenMissing.add(key)) {
                continue;
            }
            (can ? ok : missing).add(new BenchPoolSync.Entry(option.icon(), 0, key, !can, option.tab(), -1));
        }
        missing.removeIf(entry -> seenOk.contains(entry.id()));
        colors.addAll(ok);
        colors.addAll(missing);
        // Ordem única (BenchOrder): disponível antes de faltante, depois por nome; o corte do teto vem DEPOIS de ordenar.
        return BenchOrder.sortedAndCapped(colors);
    }

    /**
     * Identidade estável de uma receita no pedido: o que ela produz (item + componentes), não a posição na lista. A
     * lista é refeita a cada clique e o snapshot do cliente chega a 5 s atrasado, então um índice podia apontar para
     * outra receita. Sempre {@code >= 0} (os negativos têm significado próprio).
     */
    static int key(StationRecipes.Option option) {
        return keyOf(option.icon());
    }

    /** A chave estável de um resultado pelo item que ele produz (também as poções do suporte). */
    static int keyOf(ItemStack result) {
        return ItemStack.hashItemAndComponents(result) & Integer.MAX_VALUE;
    }

    /** Aba de um item solto (ver {@link BenchCompat#slotTab}). */
    public static int slotTab(AbstractContainerMenu menu, ServerPlayer player, ItemStack stack) {
        return BenchCompat.slotTab(menu, player, stack);
    }

    /**
     * Só os itens comuns (sem dano, encantamento nem nome: a regra do livro de receitas do jogo). Com a mochila na
     * lista, isso impede a receita de pegar a espada nomeada ou a pedra renomeada do jogador. Mantém a ordem de
     * {@link BenchPool#atHand()} (mochila antes do armazenamento).
     */
    private static List<ItemStack> usable(List<ItemStack> atHand) {
        List<ItemStack> out = new ArrayList<>(atHand.size());
        for (ItemStack stack : atHand) {
            if (BenchCompat.usableForCrafting(stack)) {
                out.add(stack);
            }
        }
        return out;
    }

    /** Monta a receita de chave {@code id} ({@link #key}): põe cada entrada no slot certo (mochila primeiro, o armazenamento completa) e a escolhe. */
    public static void craft(ServerPlayer player, AbstractContainerMenu menu, int id, ItemStack choice, boolean one) {
        if (BrewingCompat.isBrewing(menu)) {
            // Suporte de poções: a "receita" é a poção escolhida; monta o próximo passo do caminho até ela.
            if (FeatureGate.allow(player, Feature.BENCH_BREWING)) {
                BenchBrewing.brew(player, menu, id, one);
            }
            return;
        }
        // Uma varredura só por clique: o pool, a lista do que há e o que sai dos baús vêm todos da mesma passada.
        BenchPool pool = BenchPool.of(player);
        List<ItemStack> have = usable(pool.atHand());
        List<StationRecipes.Option> options = StationRecipes.options(player, menu, have);
        StationRecipes.Option option = null;
        for (StationRecipes.Option candidate : options) {
            if (key(candidate) != id) {
                continue;
            }
            // Mesmo resultado de várias pedras: usa a que tem material agora (a lista mostra uma só entrada por resultado).
            boolean can = candidate.needs().stream().allMatch(need -> have.stream().anyMatch(need));
            if (option == null || can) {
                option = candidate;
            }
            if (can) {
                break;
            }
        }
        if (option == null) {
            BenchSync.markDirty(player);   // a lista mudou desde o snapshot do cliente: manda a nova e não monta nada
            return;
        }
        List<java.util.function.Predicate<ItemStack>> needs = new ArrayList<>(option.needs());
        StationRecipes.applyChoice(menu, needs, choice);   // tear: o corante da cor escolhida no painel
        // Primeiro confere que dá para montar tudo: nunca deixa a estação pela metade.
        for (int i = 0; i < option.slots().length; i++) {
            Slot slot = menu.getSlot(option.slots()[i]);
            if (slot.hasItem() ? !needs.get(i).test(slot.getItem()) : have.stream().noneMatch(needs.get(i))) {
                BenchSync.markDirty(player);
                return;
            }
        }
        for (int i = 0; i < option.slots().length; i++) {
            Slot slot = menu.getSlot(option.slots()[i]);
            if (slot.hasItem()) {
                continue;   // já tem um item que serve (talvez de uma receita anterior)
            }
            if (!fillSlot(player, pool, have, slot, needs.get(i), one)) {
                BenchSync.markDirty(player);
                return;
            }
        }
        StationRecipes.select(menu, player, option);
        menu.broadcastChanges();
        BenchSync.markDirty(player);
    }

    /**
     * Põe em {@code slot} um item que satisfaz {@code need}. {@code have} vem com a mochila antes do armazenamento,
     * então o tipo escolhido é o da mochila quando ela tem; e desse tipo sai da mochila primeiro, o armazenamento só
     * completa a pilha. Só a parte do armazenamento entra no caderno (a da mochila nunca volta a baú).
     */
    private static boolean fillSlot(ServerPlayer player, BenchPool pool, List<ItemStack> have, Slot slot,
                                    java.util.function.Predicate<ItemStack> need, boolean one) {
        for (ItemStack model : have) {
            if (!need.test(model) || !slot.mayPlace(model)) {
                continue;
            }
            int want = one ? 1 : Math.min(model.getMaxStackSize(), slot.getMaxStackSize(model));
            BenchPool.Taken taken = pool.take(model, want);
            if (taken.total() > 0) {
                slot.set(model.copyWithCount(taken.total()));
                if (taken.storage() > 0) {
                    BenchLedger.record(player, Map.of(model.getItem(), taken.storage()), pool.origin());
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Botão de pagamento do sinalizador: 1 do item pedido vai para o slot de pagamento (o slot só guarda 1), da
     * mochila primeiro e do armazenamento se ela não tiver. Só item que o slot aceita (minério de pagamento); outro
     * pedido é ignorado. Trocar de ferro para esmeralda: o pagamento anterior volta para onde é dele — ao baú se veio
     * do armazenamento, à mochila se é do jogador (decisão do Eliel, Item 16.5); sem lugar para ele, não troca. Nunca
     * vai item do jogador para um baú. O que veio do armazenamento vai para o caderno: se o efeito não for
     * confirmado, volta ao baú ao fechar (o jogo jogaria o item no chão).
     */
    public static void pay(ServerPlayer player, AbstractContainerMenu menu, ItemStack requested) {
        Slot slot = BenchCompat.beaconPaymentSlot(menu);
        ItemStack model = requested.copyWithCount(1);
        if (slot == null || !slot.mayPlace(model) || !FeatureGate.allow(player, Feature.BENCH_BEACON)) {
            return;
        }
        // Só troca se o novo pagamento existe na mochila ou no armazenamento: senão o slot ficaria vazio à toa.
        boolean available = BenchPool.of(player).listing().stream()
                .anyMatch(s -> s.count() > 0 && ItemStack.isSameItemSameComponents(s.item(), model));
        if (!available) {
            BenchSync.markDirty(player);
            return;
        }
        if (slot.hasItem() && !ItemStack.isSameItemSameComponents(slot.getItem(), model)) {
            BenchLedger.returnFromGrid(player, List.of(slot));   // o que veio do armazenamento volta ao baú
            if (slot.hasItem() && (BenchLedger.owes(player, slot.getItem()) || !backToBackpack(player, slot))) {
                menu.broadcastChanges();
                return;   // emprestado que o baú não aceitou, ou mochila sem lugar: não troca
            }
        }
        place(player, menu, model, true);
    }

    /**
     * Pagamento do próprio jogador (não está no caderno): volta para a mochila dele, nunca para baú nem chão. O que
     * não couber fica no slot. Devolve {@code true} se o slot ficou vazio.
     */
    private static boolean backToBackpack(ServerPlayer player, Slot slot) {
        ItemStack rest = new StackListSink(player.getInventory().getNonEquipmentItems()).give(slot.getItem());
        slot.set(rest);
        return rest.isEmpty();
    }

    /**
     * Clicou num item solto do painel: vai para o primeiro slot <b>da estação</b> que o aceita (corante no slot do
     * corante, banner no do banner...), da mochila primeiro e do armazenamento o que faltar. Um stack, ou um só com
     * o botão direito.
     */
    public static void place(ServerPlayer player, AbstractContainerMenu menu, ItemStack requested, boolean one) {
        ItemStack model = requested.copyWithCount(1);
        BenchPool pool = BenchPool.of(player);
        for (Slot slot : BenchCompat.placementOrder(menu, player, model)) {
            if (slot.container == player.getInventory() || !slot.mayPlace(model)) {
                continue;
            }
            // Aba Combustível das fornalhas: função própria, com cadeado próprio.
            if (BenchCompat.isFuelSlot(menu, slot) && !FeatureGate.allow(player, Feature.BENCH_FUEL)) {
                break;
            }
            // Pagamento do sinalizador: mesmo cadeado do botão de pagamento, também para pedido "pôr no slot".
            if (slot == BenchCompat.beaconPaymentSlot(menu) && !FeatureGate.allow(player, Feature.BENCH_BEACON)) {
                break;
            }
            ItemStack inside = slot.getItem();
            if (!inside.isEmpty() && !ItemStack.isSameItemSameComponents(inside, model)) {
                continue;
            }
            int room = Math.min(model.getMaxStackSize(), slot.getMaxStackSize(model)) - inside.getCount();
            if (room <= 0) {
                continue;
            }
            // Mochila primeiro (item do jogador, fora do caderno); o armazenamento só completa o que faltar.
            BenchPool.Taken taken = pool.take(model, one ? 1 : room);
            if (taken.total() <= 0) {
                break;   // não há na mochila nem no armazenamento (a lista vai se atualizar)
            }
            slot.set(model.copyWithCount(inside.getCount() + taken.total()));
            if (taken.storage() > 0) {
                BenchLedger.record(player, Map.of(model.getItem(), taken.storage()), pool.origin());
            }
            menu.broadcastChanges();
            break;
        }
        BenchSync.markDirty(player);
    }
}
