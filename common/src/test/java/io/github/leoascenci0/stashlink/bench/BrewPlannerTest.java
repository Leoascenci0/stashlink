package io.github.leoascenci0.stashlink.bench;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** O mapa das poções em números: água 0, estranha 1, força 2, força II 3, rapidez 4, arremesso de força 5. */
class BrewPlannerTest {
    private static final int WATER = 0;
    private static final int AWKWARD = 1;
    private static final int STRENGTH = 2;
    private static final int STRENGTH_II = 3;
    private static final int SWIFT = 4;
    private static final int SPLASH_STRENGTH = 5;
    // ingredientes
    private static final int WART = 10;
    private static final int BLAZE = 11;
    private static final int GLOW = 12;
    private static final int SUGAR = 13;
    private static final int GUNPOWDER = 14;

    private static final List<BrewPlanner.Edge> EDGES = List.of(
            new BrewPlanner.Edge(WATER, WART, AWKWARD),
            new BrewPlanner.Edge(AWKWARD, BLAZE, STRENGTH),
            new BrewPlanner.Edge(STRENGTH, GLOW, STRENGTH_II),
            new BrewPlanner.Edge(AWKWARD, SUGAR, SWIFT),
            new BrewPlanner.Edge(STRENGTH, GUNPOWDER, SPLASH_STRENGTH));

    @Test
    void everythingIsReachableFromWater() {
        assertEquals(Set.of(AWKWARD, STRENGTH, STRENGTH_II, SWIFT, SPLASH_STRENGTH),
                BrewPlanner.reachable(EDGES, List.of(WATER)));
    }

    @Test
    void chainStartsWithTheFirstStepFromWater() {
        Map<Integer, BrewPlanner.Edge> first = BrewPlanner.firstSteps(EDGES, List.of(WATER), i -> true);
        // Força II a partir de água: o primeiro passo é água + verruga.
        assertEquals(new BrewPlanner.Edge(WATER, WART, AWKWARD), first.get(STRENGTH_II));
        assertEquals(new BrewPlanner.Edge(WATER, WART, AWKWARD), first.get(SPLASH_STRENGTH));
    }

    @Test
    void missingIngredientAnywhereOnThePathMakesItUnreachable() {
        Map<Integer, BrewPlanner.Edge> first = BrewPlanner.firstSteps(EDGES, List.of(WATER), i -> i != GLOW);
        assertNull(first.get(STRENGTH_II), "sem pó de pedra luminosa não há força II");
        assertTrue(first.containsKey(STRENGTH));
        Map<Integer, BrewPlanner.Edge> noWart = BrewPlanner.firstSteps(EDGES, List.of(WATER), i -> i != WART);
        assertTrue(noWart.isEmpty(), "sem verruga nada sai da água");
    }

    @Test
    void oneStepPerClickContinuesFromWhatIsInTheStand() {
        // Depois do 1º passo o suporte tem poção estranha: o próximo passo para força II é estranha + blaze.
        Map<Integer, BrewPlanner.Edge> first = BrewPlanner.firstSteps(EDGES, List.of(AWKWARD, WATER), i -> true);
        assertEquals(new BrewPlanner.Edge(AWKWARD, BLAZE, STRENGTH), first.get(STRENGTH_II));
        // E com força pronta no suporte: força + pedra luminosa.
        Map<Integer, BrewPlanner.Edge> then = BrewPlanner.firstSteps(EDGES, List.of(STRENGTH, WATER), i -> true);
        assertEquals(new BrewPlanner.Edge(STRENGTH, GLOW, STRENGTH_II), then.get(STRENGTH_II));
    }

    @Test
    void withoutWaterOrPotionsThereIsNoStep() {
        assertTrue(BrewPlanner.firstSteps(EDGES, List.of(), i -> true).isEmpty());
        assertFalse(BrewPlanner.firstSteps(EDGES, List.of(SWIFT), i -> true).containsKey(STRENGTH));
    }
}
