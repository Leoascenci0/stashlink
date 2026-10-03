package io.github.leoascenci0.stashlink.client;

import io.github.leoascenci0.stashlink.compat.mc.ClientCompat;
import io.github.leoascenci0.stashlink.config.ClientPolicy;
import io.github.leoascenci0.stashlink.config.Feature;
import net.minecraft.client.Minecraft;
import io.github.leoascenci0.stashlink.network.SetFeatureLockRequest;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * "Esta função está valendo para mim agora?" no cliente: ligada por mim na tela de config <b>e</b> não trancada
 * pelo servidor onde estou. O servidor repete a conta e é quem decide; aqui é só para não mandar pedido à toa e
 * para dizer ao jogador por que nada aconteceu. Em servidor sem o mod (modo cliente) não existe cadeado: só vale
 * a minha escolha.
 */
public final class ClientFeatures {
    private static Consumer<SetFeatureLockRequest> lockSender = request -> { };

    private ClientFeatures() {
    }

    /** O loader diz como mandar o pedido de cadeado ao servidor (só chamado se ele conhece o pacote). */
    public static void setLockSender(Consumer<SetFeatureLockRequest> sender) {
        lockSender = sender;
    }

    /** Pede ao servidor para trancar/destrancar a função para todos. Ele confere se o jogador pode. */
    public static void requestLock(Feature feature, boolean locked) {
        lockSender.accept(new SetFeatureLockRequest(feature.bit(), locked));
    }

    /** O servidor atual tem esta função trancada? Só sabe quem recebeu a política do servidor. */
    public static boolean lockedByServer(Feature feature) {
        return ClientPolicy.known() && ClientPolicy.locked(feature);
    }

    public static boolean enabled(Feature feature) {
        return ClientPrefs.isFeatureOn(feature) && !lockedByServer(feature);
    }

    /** Como {@link #enabled}, e se não vale avisa na barra de ação o motivo (cadeado ou desligada por mim). */
    public static boolean allow(Minecraft mc, Feature feature) {
        if (enabled(feature)) {
            return true;
        }
        Component reason = lockedByServer(feature)
                ? Component.translatableWithFallback("stashlink.feature.locked_here",
                        "This feature is locked on this server")
                : Component.translatableWithFallback("stashlink.feature.disabled_by_you",
                        "This feature is turned off in the StashLink settings");
        ClientCompat.overlay(mc, reason);
        return false;
    }
}
