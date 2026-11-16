package io.github.leoascenci0.stashlink.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** Item 32 no Fabric: os cenários de {@link LockEverywhereScenarios} (os mesmos que rodam no NeoForge). */
public class LockEverywhereGameTests {
    @GameTest
    public void middleClickNeverUsesALockedSlot(GameTestHelper h) {
        LockEverywhereScenarios.middleClickNeverUsesALockedSlot(h);
    }

    @GameTest
    public void middleClickWithEveryHotbarSlotLockedBringsNothing(GameTestHelper h) {
        LockEverywhereScenarios.middleClickWithEveryHotbarSlotLockedBringsNothing(h);
    }

    @GameTest(maxTicks = 100)
    public void litematicaPullNeverUsesALockedSlot(GameTestHelper h) {
        LockEverywhereScenarios.litematicaPullNeverUsesALockedSlot(h);
    }

    @GameTest(maxTicks = 100)
    public void litematicaReturnNeverUsesALockedBackpackSlot(GameTestHelper h) {
        LockEverywhereScenarios.litematicaReturnNeverUsesALockedBackpackSlot(h);
    }

    @GameTest
    public void worldConfigLockedSlotAlsoCounts(GameTestHelper h) {
        LockEverywhereScenarios.worldConfigLockedSlotAlsoCounts(h);
    }

    @GameTest
    public void refillEmptyContainerSkipsLockedSlots(GameTestHelper h) {
        LockEverywhereScenarios.refillEmptyContainerSkipsLockedSlots(h);
    }

    @GameTest
    public void refillWithOnlyLockedRoomKeepsTheEmptyContainerInHand(GameTestHelper h) {
        LockEverywhereScenarios.refillWithOnlyLockedRoomKeepsTheEmptyContainerInHand(h);
    }

    @GameTest
    public void placeBackSkipsLockedSlots(GameTestHelper h) {
        LockEverywhereScenarios.placeBackSkipsLockedSlots(h);
    }

    @GameTest(maxTicks = 100)
    public void beaconPaymentReturnSkipsLockedSlots(GameTestHelper h) {
        LockEverywhereScenarios.beaconPaymentReturnSkipsLockedSlots(h);
    }

    @GameTest
    public void recipeBookNeverBringsIntoALockedSlot(GameTestHelper h) {
        LockEverywhereScenarios.recipeBookNeverBringsIntoALockedSlot(h);
    }

    @GameTest
    public void ledgerSettleNeverTakesFromALockedSlot(GameTestHelper h) {
        LockEverywhereScenarios.ledgerSettleNeverTakesFromALockedSlot(h);
    }

    @GameTest
    public void shiftClickNeverCompletesAStackInAReservedSlotOfAnotherItem(GameTestHelper h) {
        LockEverywhereScenarios.shiftClickNeverCompletesAStackInAReservedSlotOfAnotherItem(h);
    }

    @GameTest
    public void numberKeyAndDoubleClickRespectTheReservation(GameTestHelper h) {
        LockEverywhereScenarios.numberKeyAndDoubleClickRespectTheReservation(h);
    }

    @GameTest(maxTicks = 100)
    public void reservationCreatedAfterThePreviewInvalidatesApply(GameTestHelper h) {
        LockEverywhereScenarios.reservationCreatedAfterThePreviewInvalidatesApply(h);
    }

    @GameTest
    public void buttonNChangedAfterThePreviewInvalidatesApply(GameTestHelper h) {
        LockEverywhereScenarios.buttonNChangedAfterThePreviewInvalidatesApply(h);
    }

    @GameTest
    public void categoryTurnedOffAfterThePreviewInvalidatesApply(GameTestHelper h) {
        LockEverywhereScenarios.categoryTurnedOffAfterThePreviewInvalidatesApply(h);
    }

    @GameTest
    public void reservationCreatedAfterApplyInvalidatesUndo(GameTestHelper h) {
        LockEverywhereScenarios.reservationCreatedAfterApplyInvalidatesUndo(h);
    }

    @GameTest
    public void buttonNChangedAfterApplyInvalidatesUndo(GameTestHelper h) {
        LockEverywhereScenarios.buttonNChangedAfterApplyInvalidatesUndo(h);
    }

    @GameTest
    public void categoryTurnedOffAfterApplyInvalidatesUndo(GameTestHelper h) {
        LockEverywhereScenarios.categoryTurnedOffAfterApplyInvalidatesUndo(h);
    }
}
