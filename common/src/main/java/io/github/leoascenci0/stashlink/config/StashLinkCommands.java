package io.github.leoascenci0.stashlink.config;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * Comandos {@code /stashlink radius [n]} e {@code /stashlink reload}. Só operadores (nível "gamemaster").
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

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!StashLinkConfig.load()) {
            source.sendFailure(Component.translatableWithFallback("stashlink.command.reload.fail",
                    "Could not read stashlink.json; keeping current values (see log)"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatableWithFallback("stashlink.command.reload.ok",
                "StashLink config reloaded (radius %s)", StashLinkConfig.effectiveRadius()), true);
        return 1;
    }
}
