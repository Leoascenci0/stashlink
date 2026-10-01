package io.github.leoascenci0.stashlink;

import io.github.leoascenci0.stashlink.compat.mc.TestCompat;
import io.github.leoascenci0.stashlink.refill.HandWatcher;
import io.github.leoascenci0.stashlink.refill.RefillLogic;
import io.github.leoascenci0.stashlink.source.PlayerShulkerSource;
import io.github.leoascenci0.stashlink.storage.ShulkerStorage;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Item 5: nenhuma sequência de operações pode gerar nem perder item. Rodam no JVM puro (sem abrir o jogo):
 * a lógica está toda em {@code common}, então o que um GameTest provaria já dá para provar aqui.
 */
class RobustnessTest {
    @BeforeAll
    static void bootstrap() {
        MinecraftTestSetup.init();
    }

    private static ItemStack shulkerWith(ItemStack... contents) {
        ItemStack box = new ItemStack(Items.SHULKER_BOX);
        for (ItemStack s : contents) {
            assertTrue(ShulkerStorage.insert(box, s).isEmpty());
        }
        return box;
    }

    private static int total(List<ItemStack> inventory, ItemStack model) {
        int sum = 0;
        for (ItemStack s : inventory) {
            if (ShulkerStorage.isShulker(s)) {
                sum += ShulkerStorage.count(s, c -> ItemStack.isSameItemSameComponents(c, model));
            }
        }
        return sum;
    }

    // ---- shulker dentro de shulker ----

    @Test
    void shulkerCannotBeInsertedIntoShulker() {
        ItemStack outer = new ItemStack(Items.SHULKER_BOX);
        ItemStack inner = new ItemStack(TestCompat.dyedShulker(DyeColor.RED));
        ItemStack rest = ShulkerStorage.insert(outer, inner);
        assertEquals(1, rest.getCount(), "a shulker deve voltar inteira");
        assertEquals(0, ShulkerStorage.count(outer, s -> true));
    }

    @Test
    void shulkerCannotBeInsertedIntoItself() {
        ItemStack box = shulkerWith(new ItemStack(Items.DIRT, 5));
        ItemStack rest = ShulkerStorage.insert(box, box);
        assertEquals(1, rest.getCount());
        assertEquals(5, ShulkerStorage.count(box, s -> true));
    }

    @Test
    void refillingLastShulkerInHandDoesNotPullFromOtherShulkers() {
        // A última shulker da mão foi colocada; o estoque tem outra shulker, mas ela não pode conter shulkers.
        ItemStack other = shulkerWith(new ItemStack(Items.DIRT, 64));
        List<ItemStack> inv = new ArrayList<>(List.of(other));
        ItemStack got = RefillLogic.refill(new ItemStack(Items.SHULKER_BOX), new PlayerShulkerSource(inv));
        assertTrue(got.isEmpty());
        assertEquals(64, ShulkerStorage.count(other, s -> true));
    }

    @Test
    void giveNeverPutsShulkerInsideShulker() {
        ItemStack a = new ItemStack(Items.SHULKER_BOX);
        ItemStack b = new ItemStack(TestCompat.dyedShulker(DyeColor.BLUE));
        PlayerShulkerSource src = new PlayerShulkerSource(new ArrayList<>(List.of(a, b)));
        ItemStack rest = src.give(new ItemStack(TestCompat.dyedShulker(DyeColor.GREEN)));
        assertEquals(1, rest.getCount(), "sem lugar: devolve ao chamador, que o coloca no inventário");
        assertEquals(0, ShulkerStorage.count(a, s -> true));
        assertEquals(0, ShulkerStorage.count(b, s -> true));
    }

    // ---- encantamento e nome ----

    @Test
    void refillKeepsEnchantmentsAndNameMandatory() {
        ItemStack named = new ItemStack(Items.DIAMOND_PICKAXE);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Picareta do Eliel"));
        ItemStack plain = new ItemStack(Items.DIAMOND_PICKAXE);
        ItemStack box = shulkerWith(plain);
        List<ItemStack> inv = new ArrayList<>(List.of(box));

        // A picareta com nome NÃO é reposta por uma sem nome.
        assertTrue(RefillLogic.refill(named, new PlayerShulkerSource(inv)).isEmpty());
        assertEquals(1, ShulkerStorage.count(box, s -> true));

        // Uma picareta com o mesmo nome é reposta, mesmo a da mão estando gasta.
        ItemStack namedFresh = named.copy();
        assertTrue(ShulkerStorage.insert(box, namedFresh).isEmpty());
        ItemStack worn = named.copy();
        worn.set(DataComponents.DAMAGE, worn.getMaxDamage() - 1);
        ItemStack got = RefillLogic.refill(worn, new PlayerShulkerSource(inv));
        assertEquals(1, got.getCount());
        assertEquals("Picareta do Eliel", got.getHoverName().getString());
        assertEquals(0, got.getDamageValue());
        assertEquals(1, ShulkerStorage.count(box, s -> true), "só a picareta sem nome ficou");
    }

    @Test
    void namedStacksDoNotMergeWithPlainOnes() {
        ItemStack named = new ItemStack(Items.COBBLESTONE, 10);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Especial"));
        ItemStack box = shulkerWith(new ItemStack(Items.COBBLESTONE, 10), named);
        // Dois stacks separados, cada um com seus 10; nada foi somado.
        List<ItemStack> slots = ShulkerStorage.read(box);
        assertEquals(10, slots.get(0).getCount());
        assertEquals(10, slots.get(1).getCount());
    }

    // ---- mão / hotbar ----

    @Test
    void handRefillNeverExceedsMaxStack() {
        ItemStack box = shulkerWith(new ItemStack(Items.ENDER_PEARL, 16), new ItemStack(Items.ENDER_PEARL, 16));
        List<ItemStack> inv = new ArrayList<>(List.of(box));
        ItemStack got = RefillLogic.refill(new ItemStack(Items.ENDER_PEARL), new PlayerShulkerSource(inv));
        assertTrue(got.getCount() <= got.getMaxStackSize());
    }

    // ---- morte e desconexão ----

    @Test
    void deathDoesNotTriggerRefillOnRespawn() {
        HandWatcher w = new HandWatcher();
        w.observe(0, new ItemStack(Items.COBBLESTONE, 1), true);
        // Durante a morte/tela de respawn o jogador fica inativo: o watcher esquece o passado.
        assertTrue(w.observe(0, ItemStack.EMPTY, false).isEmpty());
        assertTrue(w.observe(0, ItemStack.EMPTY, true).isEmpty());
    }

    @Test
    void freshWatcherAfterReloginNeverFires() {
        // Relogar cria estado novo: mão vazia no primeiro tick não é "esgotou".
        HandWatcher w = new HandWatcher();
        assertTrue(w.observe(3, ItemStack.EMPTY, true).isEmpty());
    }

    // ---- sequências aleatórias: conservação de itens ----

    @Test
    void randomSequencesNeverCreateOrLoseItems() {
        for (long seed = 0; seed < 300; seed++) {
            Random rnd = new Random(seed);
            ItemStack a = shulkerWith(new ItemStack(Items.COBBLESTONE, 1 + rnd.nextInt(200)));
            ItemStack b = shulkerWith(new ItemStack(Items.COBBLESTONE, rnd.nextInt(100)),
                    new ItemStack(Items.DIRT, rnd.nextInt(64)));
            List<ItemStack> inv = new ArrayList<>(List.of(a, new ItemStack(Items.DIAMOND), b));
            PlayerShulkerSource src = new PlayerShulkerSource(inv);
            ItemStack cobble = new ItemStack(Items.COBBLESTONE);

            int initial = total(inv, cobble);
            int inHands = 0;
            for (int step = 0; step < 40; step++) {
                if (rnd.nextBoolean()) {
                    ItemStack got = RefillLogic.refill(new ItemStack(Items.COBBLESTONE), src);
                    inHands += got.getCount();
                    assertTrue(got.getCount() <= 64);
                } else if (inHands > 0) {
                    int back = 1 + rnd.nextInt(Math.min(inHands, 64));
                    ItemStack rest = src.give(new ItemStack(Items.COBBLESTONE, back));
                    inHands -= back - rest.getCount();
                }
                assertEquals(initial, total(inv, cobble) + inHands, "seed " + seed + " passo " + step);
            }
        }
    }
}
