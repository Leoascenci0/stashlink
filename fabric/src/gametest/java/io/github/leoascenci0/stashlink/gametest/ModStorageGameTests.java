package io.github.leoascenci0.stashlink.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** Item 26 no Fabric: os cenários de {@link ModStorageScenarios} (os mesmos que rodam no NeoForge). */
public class ModStorageGameTests {
    @GameTest
    public void quickStackStoresIntoTheDrawer(GameTestHelper h) {
        ModStorageScenarios.quickStackStoresIntoTheDrawer(h);
    }

    @GameTest
    public void refillTakesFromTheDrawer(GameTestHelper h) {
        ModStorageScenarios.refillTakesFromTheDrawer(h);
    }

    @GameTest
    public void middleClickPullsFromTheDrawer(GameTestHelper h) {
        ModStorageScenarios.middleClickPullsFromTheDrawer(h);
    }

    @GameTest
    public void benchUsesTheDrawerAndGivesBackOnClose(GameTestHelper h) {
        ModStorageScenarios.benchUsesTheDrawerAndGivesBackOnClose(h);
    }

    @GameTest
    public void featureOffIgnoresTheDrawer(GameTestHelper h) {
        ModStorageScenarios.featureOffIgnoresTheDrawer(h);
    }

    @GameTest
    public void anotherPlayersScreenNearbyKeepsTheDrawerUntouched(GameTestHelper h) {
        ModStorageScenarios.anotherPlayersScreenNearbyKeepsTheDrawerUntouched(h);
    }

    @GameTest
    public void vanillaChestIsCountedOnce(GameTestHelper h) {
        ModStorageScenarios.vanillaChestIsCountedOnce(h);
    }

    @GameTest
    public void randomRequestsNeverDuplicateOrLose(GameTestHelper h) {
        ModStorageScenarios.randomRequestsNeverDuplicateOrLose(h);
    }

    @GameTest(maxTicks = 600)
    public void loadWith289Drawers(GameTestHelper h) {
        ModStorageScenarios.loadWith289Drawers(h);
    }
}
