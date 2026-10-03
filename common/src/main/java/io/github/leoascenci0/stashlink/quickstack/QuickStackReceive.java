package io.github.leoascenci0.stashlink.quickstack;

import io.github.leoascenci0.stashlink.mixin.CompoundContainerAccessor;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * O botão "recebe itens com a tecla N" de um container (Item 17). Só a tecla N consulta isto; guardar à mão, W,
 * funil, Litematica e reabastecer não são afetados. Container sem a memória (ender chest, mods) recebe sempre.
 *
 * <p>Baú duplo: o botão grava nas duas metades; para <b>receber</b>, as duas precisam aceitar (se um baú novo foi
 * colocado ao lado de um desligado, o conjunto continua desligado — o lado seguro).
 */
public final class QuickStackReceive {
    /** Chave no NBT do bloco (e dentro do CUSTOM_DATA do item da shulker). Só existe quando está DESLIGADO. */
    public static final String KEY = "stashlink_no_quick_stack";

    /** Lado cliente: o que o servidor contou sobre os containers do menu aberto (só aparência do botão). */
    private static final Map<Container, Boolean> CLIENT = new WeakHashMap<>();

    private QuickStackReceive() {
    }

    /** A tecla N pode colocar itens neste container? */
    public static boolean accepts(Container c) {
        List<ReceiveHolder> holders = holders(c);
        if (holders.isEmpty()) {
            return CLIENT.getOrDefault(c, true);
        }
        for (ReceiveHolder holder : holders) {
            if (!holder.stashlink$receivesQuickStack()) {
                return false;
            }
        }
        return true;
    }

    /** O container sabe guardar este botão? */
    public static boolean supports(Container c) {
        return !holders(c).isEmpty();
    }

    /** Liga/desliga o botão (nas duas metades de um baú duplo). Só metadado: nenhum item muda. */
    public static boolean set(Container c, boolean receives) {
        List<ReceiveHolder> holders = holders(c);
        for (ReceiveHolder holder : holders) {
            holder.stashlink$setReceivesQuickStack(receives);
            if (holder instanceof BlockEntity be) {
                be.setChanged();
            }
        }
        return !holders.isEmpty();
    }

    /** Cliente: guarda o que o servidor contou. */
    public static void setClient(Container c, boolean receives) {
        if (receives) {
            CLIENT.remove(c);
        } else {
            CLIENT.put(c, false);
        }
    }

    private static List<ReceiveHolder> holders(Container c) {
        List<ReceiveHolder> out = new ArrayList<>();
        collect(c, out);
        return out;
    }

    private static void collect(Container c, List<ReceiveHolder> out) {
        if (c instanceof CompoundContainerAccessor compound) {
            collect(compound.stashlink$first(), out);
            collect(compound.stashlink$second(), out);
        } else if (c instanceof ReceiveHolder holder) {
            out.add(holder);
        }
    }
}
