package io.github.leoascenci0.stashlink.bench;

import io.github.leoascenci0.stashlink.Constants;
import io.github.leoascenci0.stashlink.compat.mc.BenchCompat;
import io.github.leoascenci0.stashlink.compat.mc.McCompat;
import io.github.leoascenci0.stashlink.config.Feature;
import io.github.leoascenci0.stashlink.config.FeatureGate;
import io.github.leoascenci0.stashlink.source.StackListSink;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Livro de receitas com armazenamento (Item 16). O jogo monta a receita só com o que há na <b>mochila</b>
 * ({@code ServerPlaceRecipe}); em vez de reescrever essa lógica (grade, receita sem forma, máximo com shift,
 * limpar a grade), o mod faz dois gestos em volta dela:
 *
 * <ol>
 *   <li>{@link #before}: calcula o que <i>falta</i> na mochila e traz do armazenamento para a mochila, só isso;</li>
 *   <li>o jogo roda normalmente, como se o jogador já carregasse tudo;</li>
 *   <li>{@link #after}: o que sobrou do que foi trazido volta para a origem.</li>
 * </ol>
 *
 * Tudo no servidor e na thread do servidor, sem item "no ar": cada item sai de um container e entra na mochila
 * no mesmo passo, e o que não couber na mochila volta ao container antes de o jogo olhar. O que o jogador
 * deixou em fornalha/estação nunca é tocado: só se tira de baú, barril e shulker.
 */
public final class BenchRecipe {
    /** O que foi trazido nesta operação, para {@link #after} desfazer a sobra. */
    private record Pending(ServerPlayer player, BenchPool pool, Map<Item, Integer> pulled) {
    }

    /** Uma operação por vez (a thread do servidor é uma só). Zerada em todo {@link #before}. */
    private static Pending pending;

    private BenchRecipe() {
    }

    public static void before(Inventory inventory, List<Slot> inputSlots, RecipeHolder<?> holder, boolean useMax,
                              boolean creative) {
        pending = null;
        try {
            if (inventory.player instanceof ServerPlayer player) {
                run(player, inventory, inputSlots, holder, useMax);
            }
        } catch (RuntimeException e) {
            // Um defeito aqui nunca pode impedir o jogo de montar a receita com a mochila.
            Constants.LOG.error("Falha ao trazer ingredientes do armazenamento", e);
        }
    }

    private static void run(ServerPlayer player, Inventory inventory, List<Slot> inputSlots, RecipeHolder<?> holder,
                            boolean useMax) {
        AbstractContainerMenu open = player.containerMenu;
        if (!player.isAlive() || player.isSpectator() || !BenchCompat.hasRecipeBook(open)
                || !open.stillValid(player) || !FeatureGate.allowSilently(player, Feature.BENCH)) {
            return;
        }
        // Trocou de receita: o que o mod pôs na grade antes e sobrou volta ao baú de origem, não à mochila.
        BenchLedger.returnFromGrid(player, inputSlots);
        BenchPool pool = BenchPool.of(player);
        Map<Item, Integer> missing = BenchCompat.missingIngredients(inventory, (RecipeBookMenu) open, inputSlots,
                holder, useMax, pool.plainCounts());
        if (missing.isEmpty()) {
            return;
        }
        Map<Item, Integer> pulled = new LinkedHashMap<>();
        for (Map.Entry<Item, Integer> want : missing.entrySet()) {
            int got = bring(player, pool, new ItemStack(want.getKey()), want.getValue());
            if (got > 0) {
                pulled.put(want.getKey(), got);
            }
        }
        if (!pulled.isEmpty()) {
            pending = new Pending(player, pool, pulled);
        }
    }

    /** Tira até {@code n} do armazenamento e põe na mochila; o que não couber volta. Devolve quantos ficaram. */
    private static int bring(ServerPlayer player, BenchPool pool, ItemStack model, int n) {
        int kept = 0;
        for (ItemStack taken : pool.source().take(model, n)) {
            ItemStack rest = taken.copy();
            // Guarda a mochila por conta própria (StackListSink), sem Inventory.add: ele descarta o que sobra quando o
            // jogador tem materiais infinitos, e aqui nada pode sumir.
            rest = new StackListSink(player.getInventory().getNonEquipmentItems()).give(rest);
            kept += taken.getCount() - rest.getCount();
            if (!rest.isEmpty()) {
                giveBack(player, pool, rest);
            }
        }
        return kept;
    }

    /** O jogo terminou de montar: devolve a sobra do que foi trazido e avisa quanto foi usado. */
    public static void after() {
        Pending p = pending;
        pending = null;
        if (p == null) {
            return;
        }
        try {
            int used = 0;
            Map<Item, Integer> inStation = new LinkedHashMap<>();
            for (Map.Entry<Item, Integer> e : p.pulled().entrySet()) {
                int stayed = e.getValue() - settle(p.player(), p.pool(), e.getKey(), e.getValue());
                used += stayed;
                if (stayed > 0) {
                    inStation.put(e.getKey(), stayed);
                }
            }
            BenchLedger.record(p.player(), inStation, p.pool().origin());
            if (used > 0) {
                p.player().sendOverlayMessage(Component.translatableWithFallback("stashlink.bench.used",
                        "%s items taken from storage", used));
            }
            BenchSync.markDirty(p.player());
        } catch (RuntimeException e) {
            Constants.LOG.error("Falha ao devolver a sobra ao armazenamento", e);
        }
    }

    /** Devolve à origem até {@code pulled} itens que ainda estão soltos na mochila. Retorna quantos devolveu. */
    private static int settle(ServerPlayer player, BenchPool pool, Item item, int pulled) {
        List<ItemStack> backpack = player.getInventory().getNonEquipmentItems();
        int returned = 0;
        for (int i = 0; i < backpack.size() && returned < pulled; i++) {
            ItemStack stack = backpack.get(i);
            if (!stack.is(item) || !BenchCompat.usableForCrafting(stack)) {
                continue;
            }
            int move = Math.min(stack.getCount(), pulled - returned);
            ItemStack out = stack.copyWithCount(move);
            stack.shrink(move);
            if (stack.isEmpty()) {
                backpack.set(i, ItemStack.EMPTY);
            }
            ItemStack rest = pool.source().give(out);
            if (!rest.isEmpty()) {
                // Ninguém quis (origem cheia?): fica na mochila, onde estava há um instante. Nunca no chão.
                backpack.set(i, out.copyWithCount(rest.getCount() + backpack.get(i).getCount()));
            }
            returned += move - rest.getCount();
        }
        return returned;
    }

    private static void giveBack(ServerPlayer player, BenchPool pool, ItemStack rest) {
        ItemStack left = pool.source().give(rest);
        if (!left.isEmpty()) {
            McCompat.placeBackInInventory(player, left);
        }
    }
}
