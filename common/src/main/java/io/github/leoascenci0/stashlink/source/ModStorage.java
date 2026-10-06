package io.github.leoascenci0.stashlink.source;

import net.minecraft.world.item.ItemStack;

import java.util.function.ObjLongConsumer;

/**
 * Baú ou gaveta de <b>outro mod</b> (Item 26), visto pela "tomada padrão" de itens do loader: a mesma que funis e canos
 * usam (no NeoForge a capability de itens do bloco, no Fabric a Transfer API). Cada loader implementa esta interface
 * só como "cola" ({@code IPlatformHelper#modStorageAt}); a regra do mod fica em {@link ModStorageSource}.
 *
 * <p>Toda operação roda numa <b>transação</b> do loader: simular = fazer e desistir no fim (nada muda); de verdade =
 * fazer e confirmar. O número devolvido é exatamente o que entrou ou saiu.
 *
 * <p>Quantidades são {@code long}: uma gaveta guarda milhares de um item num "slot". Nada aqui supõe stack ≤ 64.
 */
public interface ModStorage {
    /**
     * Entrega cada item guardado (modelo com quantidade 1) e quanto há dele. O mesmo tipo pode vir mais de uma vez
     * (vários slots); quem chama soma.
     */
    void forEach(ObjLongConsumer<ItemStack> sink);

    /** Quantos itens iguais ao modelo (mesmo item e componentes) o bloco tem. */
    long count(ItemStack model);

    /** Tira até {@code amount} itens iguais ao modelo; com {@code simulate} só diz quanto sairia. */
    long extract(ItemStack model, long amount, boolean simulate);

    /** Guarda até {@code amount} itens iguais ao modelo; com {@code simulate} só diz quanto caberia. */
    long insert(ItemStack model, long amount, boolean simulate);

    /**
     * O objeto do loader por trás deste bloco. Duas posições que devolvem o <b>mesmo</b> objeto (baú duplo de outro mod
     * que entrega o inventário inteiro nas duas metades) são um armazenamento só e só contam uma vez.
     */
    Object identity();
}
