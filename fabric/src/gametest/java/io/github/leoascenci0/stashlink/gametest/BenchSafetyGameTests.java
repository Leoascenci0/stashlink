package io.github.leoascenci0.stashlink.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** Item 31 no Fabric: os cenários de {@link BenchSafetyScenarios} (os mesmos que rodam no NeoForge). */
public class BenchSafetyGameTests {
    @GameTest
    public void deathWithBorrowedItemsReturnsThemToTheChest(GameTestHelper h) {
        BenchSafetyScenarios.deathWithBorrowedItemsReturnsThemToTheChest(h);
    }

    @GameTest
    public void changingDimensionReturnsBorrowedItems(GameTestHelper h) {
        BenchSafetyScenarios.changingDimensionReturnsBorrowedItems(h);
    }

    @GameTest
    public void logoutReturnsBorrowedAndKeepsOwnItems(GameTestHelper h) {
        BenchSafetyScenarios.logoutReturnsBorrowedAndKeepsOwnItems(h);
    }

    @GameTest
    public void unusedEnchantedBookGoesBackToTheChest(GameTestHelper h) {
        BenchSafetyScenarios.unusedEnchantedBookGoesBackToTheChest(h);
    }

    @GameTest
    public void crashJournalGivesBackExactlyWhatWasLent(GameTestHelper h) {
        BenchSafetyScenarios.crashJournalGivesBackExactlyWhatWasLent(h);
    }

    @GameTest
    public void closingNormallyLeavesTheJournalEmpty(GameTestHelper h) {
        BenchSafetyScenarios.closingNormallyLeavesTheJournalEmpty(h);
    }

    @GameTest
    public void cursorAndPlaceRequestsAreSpaced(GameTestHelper h) {
        BenchSafetyScenarios.cursorAndPlaceRequestsAreSpaced(h);
    }

    @GameTest
    public void placeUsesTheSameFilterAsTheCursor(GameTestHelper h) {
        BenchSafetyScenarios.placeUsesTheSameFilterAsTheCursor(h);
    }

    @GameTest(maxTicks = 500)
    public void benchFuzzWithDeathTravelAndCrash(GameTestHelper h) {
        BenchSafetyScenarios.benchFuzzWithDeathTravelAndCrash(h);
    }
}
