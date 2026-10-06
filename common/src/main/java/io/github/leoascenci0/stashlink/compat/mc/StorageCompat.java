package io.github.leoascenci0.stashlink.compat.mc;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Item 26: o que conta como baú ou gaveta de <b>outro mod</b>. Tags, registro de block entity e alcance do jogador são
 * API do Minecraft sujeita a mudar; por isso ficam aqui (ver docs/UPDATING.md).
 */
public final class StorageCompat {
    /**
     * Tag de blocos {@code stashlink:mod_storage}: a lista de quem é armazenamento. É uma lista de <b>permitidos</b> de
     * propósito: máquinas, controladores de gavetas e redes (AE2, Refined Storage) também oferecem a "tomada de itens",
     * e não podem virar armazenamento (o mod tiraria a entrada de uma máquina, ou contaria a mesma rede várias vezes).
     * Servidores e modpacks podem acrescentar blocos com um datapack.
     */
    public static final TagKey<Block> MOD_STORAGE = TagKey.create(Registries.BLOCK, McCompat.id("mod_storage"));

    private StorageCompat() {
    }

    /** Bloco de outro mod listado na tag. Blocos do jogo nunca: baú, barril e shulker já têm o caminho próprio. */
    public static boolean isModStorageBlock(BlockEntity be) {
        if (!be.getBlockState().is(MOD_STORAGE)) {
            return false;
        }
        Identifier type = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType());
        return type != null && !Identifier.DEFAULT_NAMESPACE.equals(type.getNamespace());
    }

    /**
     * {@code other} (com uma tela aberta) pode estar olhando o bloco de outro mod em {@code pos}? A tela de outro mod não
     * diz de que bloco ela é, então a resposta é conservadora: sim, se ele está perto o bastante (a mesma distância que
     * o jogo usa para fechar a tela de um bloco quando o jogador se afasta) e a tela é de outro mod, de tipo
     * desconhecido, ou mostra o próprio bloco. Uma tela do jogo (bancada, baú do jogo, fornalha...) não é a do bloco.
     */
    public static boolean couldBeViewing(ServerPlayer other, BlockPos pos) {
        if (!other.isWithinBlockInteractionRange(pos, 4.0)) {
            return false;
        }
        AbstractContainerMenu menu = other.containerMenu;
        BlockEntity be = other.level().getBlockEntity(pos);
        for (Slot slot : menu.slots) {
            if (be != null && slot.container == be) {
                return true;
            }
        }
        return !isVanillaMenu(menu);
    }

    private static boolean isVanillaMenu(AbstractContainerMenu menu) {
        try {
            Identifier id = BuiltInRegistries.MENU.getKey(menu.getType());
            return id != null && Identifier.DEFAULT_NAMESPACE.equals(id.getNamespace());
        } catch (UnsupportedOperationException e) {
            return false;   // tela sem tipo registrado: não dá para saber de quem é
        }
    }
}
