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
        add("bench_uses_the_drawer_and_gives_back_on_close", ModStorageScenarios::benchUsesTheDrawerAndGivesBackOnClose);
        add("feature_off_ignores_the_drawer", ModStorageScenarios::featureOffIgnoresTheDrawer);
        add("another_players_screen_nearby_keeps_the_drawer_untouched", ModStorageScenarios::anotherPlayersScreenNearbyKeepsTheDrawerUntouched);
        add("vanilla_chest_is_counted_once", ModStorageScenarios::vanillaChestIsCountedOnce);
        add("random_requests_never_duplicate_or_lose", ModStorageScenarios::randomRequestsNeverDuplicateOrLose);
        TESTS.put("load_with_289_drawers", new Test(ModStorageScenarios::loadWith289Drawers, 600));
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
