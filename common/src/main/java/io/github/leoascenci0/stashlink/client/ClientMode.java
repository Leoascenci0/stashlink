package io.github.leoascenci0.stashlink.client;

import io.github.leoascenci0.stashlink.clientmode.ClientModeEngine;
import io.github.leoascenci0.stashlink.compat.mc.ClientCompat;
import net.minecraft.client.Minecraft;

import java.util.function.BooleanSupplier;

/**
 * "Modo cliente": o StashLink só existe no PC do jogador (Realms, servidor vanilla) e por isso o servidor ignora
 * os nossos pacotes. Nesse caso o mod age como um jogador de verdade — abre o container, clica nos slots,
 * fecha (como o Litematica faz). Aqui fica só a decisão "este é o caso?" e os pontos de entrada que a cola
 * dos loaders chama; o motor está em {@code clientmode/}.
 *
 * <p>Por que o loader entra na história: "o servidor conhece o nosso pacote?" se pergunta de jeito diferente
 * em cada loader (Fabric: {@code ClientPlayNetworking.canSend}; NeoForge: {@code hasChannel}). Cada loader
 * registra a sua resposta em {@link #setServerHasModCheck}.
 */
public final class ClientMode {
    private static BooleanSupplier serverHasMod = () -> false;

    private ClientMode() {
    }

    /** O loader diz como saber se o servidor atual conhece o pacote do StashLink. */
    public static void setServerHasModCheck(BooleanSupplier check) {
        serverHasMod = check;
    }

    /** O servidor atual tem o StashLink? (só faz sentido com uma conexão aberta.) */
    public static boolean serverHasMod() {
        Minecraft mc = Minecraft.getInstance();
        return mc.getConnection() != null && serverHasMod.getAsBoolean();
    }

    /**
     * {@code true} quando o modo cliente deve assumir: há um mundo conectado a um servidor remoto (não é mundo
     * único nem LAN aberta por nós — esses seguem o caminho normal, onde o servidor local tem o mod), a opção
     * está ligada e o servidor <b>não</b> conhece o StashLink.
     */
    public static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null
                && mc.player != null
                && mc.getConnection() != null
                && !ClientCompat.isIntegratedServer(mc)
                && ClientPrefs.clientModeEnabled
                && !serverHasMod.getAsBoolean();
    }

    /** Tecla N sem o mod no servidor: a cola chama isto no lugar de mandar o pacote. */
    public static void onQuickStackKey() {
        if (active()) {
            ClientModeEngine.startQuickStack(Minecraft.getInstance());
        }
    }

    /** Tecla W (tela de container aberta) sem o mod no servidor. */
    public static void onLootAllKey() {
        if (active()) {
            ClientModeEngine.startLootAll(Minecraft.getInstance());
        }
    }

    /** A cola chama uma vez por tick do cliente. Não faz nada fora do modo cliente. */
    public static void tick(Minecraft mc) {
        ClientModeEngine.tick(mc, active());
    }
}
