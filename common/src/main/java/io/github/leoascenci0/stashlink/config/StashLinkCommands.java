package io.github.leoascenci0.stashlink.config;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * Comandos {@code /stashlink radius [n]}, {@code /stashlink feature [nome lock|unlock]} e {@code /stashlink reload}. Só operadores (nível "gamemaster").
 * Os loaders só chamam {@link #register}; tudo o mais mora aqui.
 */
public final class StashLinkCommands {
    private StashLinkCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("stashlink")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("radius")
                        .executes(StashLinkCommands::showRadius)
                        // O teto vai na própria sintaxe: valor fora da faixa nem chega a executar.
                        .then(Commands.argument("blocks", IntegerArgumentType.integer(0, StashLinkConfig.HARD_MAX_RADIUS))
                                .executes(StashLinkCommands::setRadius)))
                .then(Commands.literal("feature")
                        .executes(StashLinkCommands::listFeatures)
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    for (Feature f : Feature.values()) {
                                        builder.suggest(f.id());
                                    }
                                    return builder.buildFuture();
                                })
                                .then(Commands.literal("lock").executes(ctx -> setFeatureLock(ctx, true)))
                                .then(Commands.literal("unlock").executes(ctx -> setFeatureLock(ctx, false)))))
                .then(Commands.literal("reload").executes(StashLinkCommands::reload)));
    }

    private static int showRadius(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.translatableWithFallback("stashlink.command.radius.show",
                "Source radius: %s blocks (server limit: %s)",
                StashLinkConfig.effectiveRadius(), StashLinkConfig.radiusCap()), false);
        return StashLinkConfig.effectiveRadius();
    }

    private static int setRadius(CommandContext<CommandSourceStack> ctx) {
        int blocks = IntegerArgumentType.getInteger(ctx, "blocks");
        CommandSourceStack source = ctx.getSource();
        // O teto configurável (maxRadius) pode ser menor que o teto da sintaxe; o servidor é quem manda.
        if (!StashLinkConfig.trySetRadius(blocks)) {
            source.sendFailure(Component.translatableWithFallback("stashlink.command.radius.too_big",
                    "Radius %s is above the server limit of %s", blocks, StashLinkConfig.radiusCap()));
            return 0;
        }
        StashLinkConfig.save();
        source.sendSuccess(() -> Component.translatableWithFallback("stashlink.command.radius.set",
                "Source radius set to %s blocks", blocks), true);
        return blocks;
    }

    private static int listFeatures(CommandContext<CommandSourceStack> ctx) {
        StringBuilder locked = new StringBuilder();
        for (Feature f : StashLinkConfig.lockedFeatures) {
            locked.append(locked.length() == 0 ? "" : ", ").append(f.id());
        }
        String list = locked.length() == 0 ? "-" : locked.toString();
        ctx.getSource().sendSuccess(() -> Component.translatableWithFallback("stashlink.command.feature.list",
                "Locked features: %s", list), false);
        return StashLinkConfig.lockedFeatures.size();
    }

    private static int setFeatureLock(CommandContext<CommandSourceStack> ctx, boolean locked) {
        String name = StringArgumentType.getString(ctx, "name");
        CommandSourceStack source = ctx.getSource();
        Feature feature = Feature.byId(name).orElse(null);
        if (feature == null) {
            source.sendFailure(Component.translatableWithFallback("stashlink.command.feature.unknown",
                    "Unknown feature: %s", name));
            return 0;
        }
        FeaturePolicyService.apply(source.getServer(), feature, locked);
        source.sendSuccess(() -> Component.translatableWithFallback(locked
                        ? "stashlink.command.feature.locked" : "stashlink.command.feature.unlocked",
                locked ? "Feature %s locked" : "Feature %s unlocked", feature.id()), true);
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!StashLinkConfig.load()) {
            source.sendFailure(Component.translatableWithFallback("stashlink.command.reload.fail",
                    "Could not read stashlink.json; keeping current values (see log)"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatableWithFallback("stashlink.command.reload.ok",
                "StashLink config reloaded (radius %s)", StashLinkConfig.effectiveRadius()), true);
        // O arquivo pode ter mudado os cadeados: avisa os jogadores.
        FeaturePolicyService.broadcast(source.getServer());
        return 1;
    }
}
