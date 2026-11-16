package io.github.leoascenci0.stashlink.gametest;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Harness de GameTest do NeoForge (Item 26): mod só dos testes ({@code ./gradlew :neoforge:runGameTestServer}), nunca
 * entra no jar do StashLink. Registra a gaveta de teste e a capability dela, e os testes: os cenários de
 * {@link ModStorageScenarios} (os mesmos do Fabric) e os com mods de verdade ({@link ThirdPartyStorageTests}).
 */
@Mod(TestBlocks.NAMESPACE)
public final class NeoForgeTestMod {
    /** Estrutura vazia (8x8x8 de ar) onde cada teste roda: {@code data/stashlink_test/structure/empty.nbt}. */
    private static final Identifier EMPTY = Identifier.fromNamespaceAndPath(TestBlocks.NAMESPACE, "empty");

    /** Nome do teste → corpo, e quantos ticks ele pode levar. */
    private record Test(Consumer<GameTestHelper> body, int maxTicks) {
    }

    private static final Map<String, Test> TESTS = new LinkedHashMap<>();

    static {
        add("quick_stack_stores_into_the_drawer", ModStorageScenarios::quickStackStoresIntoTheDrawer);
        add("refill_takes_from_the_drawer", ModStorageScenarios::refillTakesFromTheDrawer);
        add("middle_click_pulls_from_the_drawer", ModStorageScenarios::middleClickPullsFromTheDrawer);
        add("litematica_swap_returns_to_the_drawer", ModStorageScenarios::litematicaSwapReturnsToTheDrawer);
        add("bench_uses_the_drawer_and_gives_back_on_close", ModStorageScenarios::benchUsesTheDrawerAndGivesBackOnClose);
        add("feature_off_ignores_the_drawer", ModStorageScenarios::featureOffIgnoresTheDrawer);
        add("another_players_screen_nearby_keeps_the_drawer_untouched", ModStorageScenarios::anotherPlayersScreenNearbyKeepsTheDrawerUntouched);
        add("vanilla_chest_is_counted_once", ModStorageScenarios::vanillaChestIsCountedOnce);
        add("random_requests_never_duplicate_or_lose", ModStorageScenarios::randomRequestsNeverDuplicateOrLose);
        TESTS.put("load_with_289_drawers", new Test(ModStorageScenarios::loadWith289Drawers, 600));
        // Item 31: caderno das bancadas à prova de morte, queda e troca de dimensão (os mesmos do Fabric).
        add("bench_death_returns_borrowed", BenchSafetyScenarios::deathWithBorrowedItemsReturnsThemToTheChest);
        add("bench_dimension_returns_borrowed", BenchSafetyScenarios::changingDimensionReturnsBorrowedItems);
        add("bench_logout_returns_borrowed_keeps_own", BenchSafetyScenarios::logoutReturnsBorrowedAndKeepsOwnItems);
        add("bench_unused_enchanted_book_back_to_chest", BenchSafetyScenarios::unusedEnchantedBookGoesBackToTheChest);
        add("bench_crash_journal_gives_back_what_was_lent", BenchSafetyScenarios::crashJournalGivesBackExactlyWhatWasLent);
        add("bench_closing_leaves_journal_empty", BenchSafetyScenarios::closingNormallyLeavesTheJournalEmpty);
        add("bench_cursor_and_place_are_spaced", BenchSafetyScenarios::cursorAndPlaceRequestsAreSpaced);
        add("bench_place_uses_cursor_filter", BenchSafetyScenarios::placeUsesTheSameFilterAsTheCursor);
        TESTS.put("bench_fuzz_death_travel_crash", new Test(BenchSafetyScenarios::benchFuzzWithDeathTravelAndCrash, 500));
        // Item 32: travas do jogador e reservas valendo em todas as funções (os mesmos do Fabric).
        add("lock_middle_click_never_uses_a_locked_slot", LockEverywhereScenarios::middleClickNeverUsesALockedSlot);
        add("lock_middle_click_with_every_hotbar_slot_locked_brings_nothing", LockEverywhereScenarios::middleClickWithEveryHotbarSlotLockedBringsNothing);
        add("lock_litematica_pull_never_uses_a_locked_slot", LockEverywhereScenarios::litematicaPullNeverUsesALockedSlot);
        add("lock_litematica_return_never_uses_a_locked_backpack_slot", LockEverywhereScenarios::litematicaReturnNeverUsesALockedBackpackSlot);
        add("lock_world_config_locked_slot_also_counts", LockEverywhereScenarios::worldConfigLockedSlotAlsoCounts);
        add("lock_refill_empty_container_skips_locked_slots", LockEverywhereScenarios::refillEmptyContainerSkipsLockedSlots);
        add("lock_refill_with_only_locked_room_keeps_the_empty_container_in_hand", LockEverywhereScenarios::refillWithOnlyLockedRoomKeepsTheEmptyContainerInHand);
        add("lock_place_back_skips_locked_slots", LockEverywhereScenarios::placeBackSkipsLockedSlots);
        add("lock_beacon_payment_return_skips_locked_slots", LockEverywhereScenarios::beaconPaymentReturnSkipsLockedSlots);
        add("lock_recipe_book_never_brings_into_a_locked_slot", LockEverywhereScenarios::recipeBookNeverBringsIntoALockedSlot);
        add("lock_ledger_settle_never_takes_from_a_locked_slot", LockEverywhereScenarios::ledgerSettleNeverTakesFromALockedSlot);
        add("lock_shift_click_never_completes_a_stack_in_a_reserved_slot_of_another_item", LockEverywhereScenarios::shiftClickNeverCompletesAStackInAReservedSlotOfAnotherItem);
        add("lock_number_key_and_double_click_respect_the_reservation", LockEverywhereScenarios::numberKeyAndDoubleClickRespectTheReservation);
        add("lock_reservation_created_after_the_preview_invalidates_apply", LockEverywhereScenarios::reservationCreatedAfterThePreviewInvalidatesApply);
        add("lock_button_n_changed_after_the_preview_invalidates_apply", LockEverywhereScenarios::buttonNChangedAfterThePreviewInvalidatesApply);
        add("lock_category_turned_off_after_the_preview_invalidates_apply", LockEverywhereScenarios::categoryTurnedOffAfterThePreviewInvalidatesApply);
        add("lock_reservation_created_after_apply_invalidates_undo", LockEverywhereScenarios::reservationCreatedAfterApplyInvalidatesUndo);
        add("lock_button_n_changed_after_apply_invalidates_undo", LockEverywhereScenarios::buttonNChangedAfterApplyInvalidatesUndo);
        add("lock_category_turned_off_after_apply_invalidates_undo", LockEverywhereScenarios::categoryTurnedOffAfterApplyInvalidatesUndo);
        ThirdPartyStorageTests.register(TESTS::put, (name, body) -> new Test(body, 100));
    }

    private static void add(String name, Consumer<GameTestHelper> body) {
        TESTS.put(name, new Test(body, 100));
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(TestBlocks.NAMESPACE, path);
    }

    public NeoForgeTestMod(IEventBus modBus) {
        // Jogador simulado como um cliente NeoForge com todos os canais: mods (Sophisticated Storage) mandam pacotes a ele.
        Lab.MOCK_CONNECTION = NetworkRegistry::configureMockConnection;
        modBus.addListener(NeoForgeTestMod::register);
        modBus.addListener(NeoForgeTestMod::capabilities);
        modBus.addListener(NeoForgeTestMod::gameTests);
    }

    private static void register(RegisterEvent event) {
        TestBlocks.create();
        event.register(Registries.BLOCK, TestBlocks.API_DRAWER_ID, () -> TestBlocks.API_DRAWER);
        event.register(Registries.BLOCK_ENTITY_TYPE, TestBlocks.API_DRAWER_ID, () -> TestBlocks.API_DRAWER_TYPE);
        for (Map.Entry<String, Test> test : TESTS.entrySet()) {
            event.register(Registries.TEST_FUNCTION, id(test.getKey()), () -> test.getValue().body());
        }
    }

    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Item.BLOCK, TestBlocks.API_DRAWER_TYPE, (be, side) -> new ApiDrawerHandler(be));
    }

    private static void gameTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(id("default"));
        for (Map.Entry<String, Test> test : TESTS.entrySet()) {
            Identifier name = id(test.getKey());
            event.registerTest(name, new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION, name),
                    new TestData<>(environment, EMPTY, test.getValue().maxTicks(), 0, true)));
        }
    }
}
