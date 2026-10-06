package io.github.leoascenci0.stashlink.quickstack;

import io.github.leoascenci0.stashlink.compat.mc.McCompat;
import io.github.leoascenci0.stashlink.compat.mc.StorageCompat;
import io.github.leoascenci0.stashlink.Constants;
import io.github.leoascenci0.stashlink.config.Feature;
import io.github.leoascenci0.stashlink.config.FeatureGate;
import io.github.leoascenci0.stashlink.config.StashLinkConfig;
import io.github.leoascenci0.stashlink.config.PlayerPrefsStore;
import io.github.leoascenci0.stashlink.source.ContainerInsert;
import io.github.leoascenci0.stashlink.source.ContainerSource;
import io.github.leoascenci0.stashlink.source.ModStorageSource;
import io.github.leoascenci0.stashlink.source.NearbyContainers;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.IntPredicate;
import java.util.function.Predicate;

/**
 * Trata a tecla N no servidor. O cliente só <i>pede</i>; aqui o servidor revalida quem pede e com que
 * frequência, e cada container é conferido individualmente: dentro do raio, liberado pelos mods de proteção
 * (claim) e não aberto por outro jogador naquele momento.
 */
public final class QuickStackService {
    /** Chave por identidade do objeto do jogador: relogar cria outro objeto, e o antigo é coletado sozinho. */
    private static final Map<ServerPlayer, Long> LAST_REQUEST = new WeakHashMap<>();

    private QuickStackService() {
    }

    /** Roda na thread do servidor. */
    public static void handle(ServerPlayer player) {
        try {
            process(player);
        } catch (RuntimeException e) {
            // Pacote vindo da rede: um erro aqui nunca pode derrubar o servidor.
            Constants.LOG.error("Falha ao guardar itens de {}", player.getGameProfile().name(), e);
        }
    }

    private static void process(ServerPlayer player) {
        // Espectador não mexe em itens; com outro menu aberto o jogador já está mexendo no inventário.
        if (!player.isAlive() || player.isSpectator() || player.containerMenu != player.inventoryMenu) {
            return;
        }
        if (!FeatureGate.allow(player, Feature.QUICK_STACK)) {
            return;
        }
        long now = McCompat.gameTime(player);
        Long last = LAST_REQUEST.get(player);
        if (last != null && now >= last && now - last < StashLinkConfig.QUICK_STACK_COOLDOWN_TICKS) {
            return;
        }
        LAST_REQUEST.put(player, now);

        NearbyContainers.Stash found = NearbyContainers.findStash(player);
        List<ContainerSource.Entry> targets = new ArrayList<>();
        for (ContainerSource.Entry entry : found.containers()) {
            // Além da permissão (claim), ninguém pode estar com este container aberto agora: mexer por baixo
            // de quem está olhando o baú geraria confusão (e é a brecha clássica de duplicação).
            targets.add(new ContainerSource.Entry(entry.container(),
                    () -> !openedByAnother(player, entry.container()) && entry.allowed().getAsBoolean()));
        }

        Inventory inventory = player.getInventory();
        // Só a mochila e a hotbar (36 slots); armadura e mão secundária ficam fora. Hotbar e slots travados
        // nunca são esvaziados.
        IntPredicate skip = slot -> slot < Inventory.getSelectionSize() || PlayerPrefsStore.isSlotLocked(player, slot);
        Predicate<ItemStack> excluded = stack -> categoryOff(player, stack);
        QuickStackLogic.Result result = QuickStackLogic.stack(inventory.getNonEquipmentItems(), skip, excluded, targets);
        // Item 26: depois os baús e gavetas de outros mods, com a mesma regra ("o bloco já contém o item").
        int moved = result.itemsMoved();
        int used = result.containersUsed();
        for (ModStorageSource.Entry entry : guard(player, found.modStorage())) {
            int here = ModStorageSource.stashInto(entry, inventory.getNonEquipmentItems(), skip, excluded);
            if (here > 0) {
                moved += here;
                used++;
            }
        }

        if (moved > 0) {
            player.sendOverlayMessage(Component.translatableWithFallback("stashlink.quick_stack.done",
                    "%s items stored in %s containers", moved, used));
        } else {
            player.sendOverlayMessage(Component.translatableWithFallback("stashlink.quick_stack.nothing",
                    "Nothing to store nearby"));
        }
    }

    /**
     * O item é de uma categoria que este jogador desligou (ou que o servidor trancou)? Então a N nunca o guarda.
     * Item sem categoria (bloco comum etc.) nunca é filtrado.
     */
    public static boolean categoryOff(ServerPlayer player, ItemStack stack) {
        ItemCategory category = ItemCategory.of(stack);
        return category != null && !PlayerPrefsStore.featureEnabled(player, category.feature());
    }

    /**
     * Item 26: a tela de um bloco de outro mod não diz de que bloco ela é. Então, por segurança, o bloco fica de fora
     * se algum <b>outro</b> jogador está perto o bastante e com uma tela que pode ser a dele ({@link StorageCompat#couldBeViewing}).
     */
    public static boolean anotherScreenNear(ServerPlayer player, BlockPos pos) {
        for (ServerPlayer other : McCompat.playersOnServer(player)) {
            if (other != player && other.containerMenu != other.inventoryMenu && other.level() == player.level()
                    && StorageCompat.couldBeViewing(other, pos)) {
                return true;
            }
        }
        return false;
    }

    /** A mesma regra da N para blocos de outros mods: permissão (claims) e ninguém olhando. */
    public static List<ModStorageSource.Entry> guard(ServerPlayer player, List<ModStorageSource.Entry> entries) {
        List<ModStorageSource.Entry> out = new ArrayList<>(entries.size());
        for (ModStorageSource.Entry entry : entries) {
            out.add(new ModStorageSource.Entry(entry.storage(),
                    () -> entry.where().stream().noneMatch(pos -> anotherScreenNear(player, pos))
                            && entry.allowed().getAsBoolean(),
                    entry.where()));
        }
        return out;
    }

    /** {@code true} se algum <b>outro</b> jogador está com uma GUI aberta que mostra este container. */
    public static boolean openedByAnother(ServerPlayer player, Container container) {
        for (ServerPlayer other : McCompat.playersOnServer(player)) {
            if (other == player || other.containerMenu == other.inventoryMenu) {
                continue;
            }
            AbstractContainerMenu menu = other.containerMenu;
            for (Slot slot : menu.slots) {
                if (ContainerInsert.isOrContains(container, slot.container)) {
                    return true;
                }
            }
        }
        return false;
    }
}
