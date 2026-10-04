package io.github.leoascenci0.stashlink.client;

import io.github.leoascenci0.stashlink.MinecraftTestSetup;
import io.github.leoascenci0.stashlink.network.BenchPoolSync;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BenchClientTest {
    @BeforeAll
    static void bootstrap() {
        MinecraftTestSetup.init();
    }

    @AfterEach
    void clean() {
        BenchClient.reset();
    }

    private static BenchPoolSync lists() {
        return new BenchPoolSync(7, List.of(
                new BenchPoolSync.Entry(new ItemStack(Items.TUFF), 12),
                new BenchPoolSync.Entry(new ItemStack(Items.BONE_MEAL), 0, BenchPoolSync.Entry.COLOR_PICK, false, 0, 14)));
    }

    /** Item 14: ao trocar de servidor/mundo, a lista e a estação da conexão anterior não sobram. */
    @Test
    void resetForgetsTheListAndTheStationOfTheOldConnection() {
        BenchClient.apply(lists());
        assertEquals(7, BenchClient.poolContainerId());
        assertEquals(2, BenchClient.pool().size());
        int version = BenchClient.version();

        BenchClient.reset();

        assertEquals(-1, BenchClient.poolContainerId(), "nenhuma estação associada");
        assertTrue(BenchClient.pool().isEmpty(), "lista vazia");
        assertNull(BenchClient.colorPick(14), "cores esquecidas");
        assertTrue(BenchClient.version() > version, "painel e livro se recalculam");
    }

    /** Item 12: a cor do tear vem de um mapa montado uma vez por lista, não de uma varredura por frame. */
    @Test
    void colorPicksAreLookedUpByColorId() {
        BenchClient.apply(lists());
        assertNotNull(BenchClient.colorPick(14));
        assertNull(BenchClient.colorPick(3), "cor que não veio na lista");
    }
}
