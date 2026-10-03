package io.github.leoascenci0.stashlink.slotlock;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.leoascenci0.stashlink.config.ClientPolicy;
import io.github.leoascenci0.stashlink.config.Feature;
import io.github.leoascenci0.stashlink.mixin.CompoundContainerAccessor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Regras do slot travado ("reservado") de um container. A prévia é só <b>metadado</b>: o slot continua
 * vazio de verdade, então não existe item falso para duplicar, tirar com funil ou com a tecla W.
 *
 * <p>Reservar um slot para um item faz duas coisas: (1) nenhum <b>outro</b> item entra nele (clique, shift-clique,
 * tecla N, devolução do Litematica...), e (2) a tecla N e o guardar passam a preferir esse slot.
 * Não bloqueia funil (hopper) nem tira nada: itens que já estão no slot continuam podendo sair.
 *
 * <p>Funciona em container que é (ou, no baú duplo, junta) block entities de container — todas implementam
 * {@link SlotLockHolder} por mixin. No cliente o container do menu não é block entity: aí vale o que o servidor
 * contou ({@link ClientSlotLocks}).
 */
public final class SlotLocks {
    /** Chave no NBT do bloco. */
    public static final String KEY = "stashlink_slot_locks";

    /** Uma trava gravada no disco. */
    public record Saved(int slot, Item item) {
    }

    public static final Codec<List<Saved>> CODEC = RecordCodecBuilder.<Saved>create(i -> i.group(
            Codec.INT.fieldOf("slot").forGetter(Saved::slot),
            BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(Saved::item)
    ).apply(i, Saved::new)).listOf();

    /** Resultado de {@link #toggle}. */
    public enum Result { LOCKED, UNLOCKED, NOTHING_TO_LOCK, UNSUPPORTED }

    /** Onde, de verdade, mora a trava do slot {@code slot} de um container. */
    private record Located(SlotLockHolder holder, int local) {
    }

    private SlotLocks() {
    }

    private static Located locate(Container c, int slot) {
        if (slot < 0 || slot >= c.getContainerSize()) {
            return null;
        }
        if (c instanceof CompoundContainerAccessor compound) {
            Container first = compound.stashlink$first();
            if (slot < first.getContainerSize()) {
                return locate(first, slot);
            }
            return locate(compound.stashlink$second(), slot - first.getContainerSize());
        }
        return c instanceof SlotLockHolder holder ? new Located(holder, slot) : null;
    }

    /** Este container sabe guardar travas no slot dado? */
    public static boolean supports(Container c, int slot) {
        return locate(c, slot) != null;
    }

    /** O item reservado para o slot, ou {@code null}. */
    public static Item lockedItem(Container c, int slot) {
        // Função trancada pelo servidor: as reservas ficam guardadas no bloco, mas deixam de valer (e voltam
        // a valer se o cadeado for aberto).
        if (ClientPolicy.locked(Feature.SLOT_LOCK)) {
            return null;
        }
        Located at = locate(c, slot);
        if (at != null) {
            return at.holder().stashlink$locks().get(at.local());
        }
        return ClientSlotLocks.lockedItem(c, slot);
    }

    /** O slot aceita {@code stack}? Falso só se está reservado para <b>outro</b> item. */
    public static boolean mayPlace(Container c, int slot, ItemStack stack) {
        Item locked = lockedItem(c, slot);
        return locked == null || stack.is(locked);
    }

    /** O slot está reservado exatamente para o item de {@code stack}? */
    public static boolean reservedFor(Container c, int slot, ItemStack stack) {
        Item locked = lockedItem(c, slot);
        return locked != null && stack.is(locked);
    }

    /** Algum slot do container está reservado para o item de {@code stack}? */
    public static boolean reserves(Container c, ItemStack stack) {
        for (int slot = 0; slot < c.getContainerSize(); slot++) {
            if (reservedFor(c, slot, stack)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Trava ou destrava. Slot já travado: destrava. Senão trava para {@code candidate} (o item do slot, ou o do
     * cursor se o slot está vazio); {@code candidate} vazio = nada a travar. Só mexe em metadado.
     */
    public static Result toggle(Container c, int slot, ItemStack candidate) {
        Located at = locate(c, slot);
        if (at == null) {
            return Result.UNSUPPORTED;
        }
        Map<Integer, Item> locks = at.holder().stashlink$locks();
        if (locks.remove(at.local()) != null) {
            changed(at);
            return Result.UNLOCKED;
        }
        if (candidate.isEmpty()) {
            return Result.NOTHING_TO_LOCK;
        }
        locks.put(at.local(), candidate.getItem());
        changed(at);
        return Result.LOCKED;
    }

    private static void changed(Located at) {
        if (at.holder() instanceof BlockEntity be) {
            be.setChanged();
        }
    }

    /** Todas as travas visíveis num container, por índice (do container inteiro, baú duplo = 54). */
    public static Map<Integer, Item> snapshot(Container c) {
        Map<Integer, Item> out = new java.util.TreeMap<>();
        for (int slot = 0; slot < c.getContainerSize(); slot++) {
            Item item = lockedItem(c, slot);
            if (item != null) {
                out.put(slot, item);
            }
        }
        return out;
    }

    /** Posições (block entities) que guardam as travas deste container; para checar claims. */
    public static List<BlockEntity> holders(Container c) {
        List<BlockEntity> out = new ArrayList<>();
        collect(c, out);
        return out;
    }

    private static void collect(Container c, List<BlockEntity> out) {
        if (c instanceof CompoundContainerAccessor compound) {
            collect(compound.stashlink$first(), out);
            collect(compound.stashlink$second(), out);
        } else if (c instanceof SlotLockHolder && c instanceof BlockEntity be) {
            out.add(be);
        }
    }
}
