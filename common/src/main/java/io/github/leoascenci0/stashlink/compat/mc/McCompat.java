package io.github.leoascenci0.stashlink.compat.mc;

import io.github.leoascenci0.stashlink.Constants;
import java.util.List;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * Único lugar do lado comum/servidor que fala com API do Minecraft sujeita a mudar entre versões.
 * Regra do projeto: nada fora de {@code compat/} usa estas chamadas direto. Quebrou numa atualização?
 * Conserte AQUI (ver docs/UPDATING.md).
 */
public final class McCompat {
    private McCompat() {
    }

    /**
     * Identificador do mod ("stashlink:path"). A classe {@code ResourceLocation} virou {@link Identifier}
     * (renomeada; vale em 26.1+).
     */
    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Constants.MOD_ID, path);
    }

    /** Tipo de um pacote próprio, com o id do mod. API de payload (CustomPacketPayload.Type) estável desde 1.20.5. */
    public static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String path) {
        return new CustomPacketPayload.Type<>(id(path));
    }

    /** Tick de jogo do mundo do jogador. {@code Entity.level()} é método desde 1.20 (antes era campo). */
    public static long gameTime(ServerPlayer player) {
        return player.level().getGameTime();
    }

    /** Lista de jogadores do servidor onde {@code player} está. */
    public static List<ServerPlayer> playersOnServer(ServerPlayer player) {
        return player.level().getServer().getPlayerList().getPlayers();
    }

    /** Quantas vezes o jogador soltou itens (tecla Q). Estatística "drop" do vanilla. */
    public static int dropCount(ServerPlayer player) {
        return player.getStats().getValue(Stats.CUSTOM.get(Stats.DROP));
    }

    /**
     * Devolve o item ao inventário (ou solta no chão se não couber). Em MC 26.3 passou a exigir um
     * {@link Prediction} (aqui SERVER_ONLY: ação do servidor, sem previsão do cliente).
     */
    public static void placeBackInInventory(ServerPlayer player, ItemStack stack) {
        player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
    }

    /** Manda ao cliente qual slot da hotbar está selecionado. */
    public static void sendHeldSlot(ServerPlayer player, int slot) {
        player.connection.send(new ClientboundSetHeldSlotPacket(slot));
    }

    /** Conteúdo (27 slots) guardado no componente CONTAINER de uma shulker. Devolve cópias. */
    public static NonNullList<ItemStack> readContainerComponent(ItemStack shulker, int size) {
        NonNullList<ItemStack> slots = NonNullList.withSize(size, ItemStack.EMPTY);
        shulker.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(slots);
        return slots;
    }

    /** Grava o conteúdo no componente CONTAINER; lista vazia volta ao padrão (EMPTY). */
    public static void writeContainerComponent(ItemStack shulker, List<ItemStack> slots, boolean empty) {
        shulker.set(DataComponents.CONTAINER,
                empty ? ItemContainerContents.EMPTY : ItemContainerContents.fromItems(slots));
    }

    /** Zera o dano (durabilidade gasta) do stack, se ele tiver o componente DAMAGE. */
    public static void resetDamage(ItemStack stack) {
        if (stack.has(DataComponents.DAMAGE)) {
            stack.set(DataComponents.DAMAGE, 0);
        }
    }
}
