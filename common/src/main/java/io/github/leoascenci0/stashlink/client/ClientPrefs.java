package io.github.leoascenci0.stashlink.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import io.github.leoascenci0.stashlink.Constants;
import io.github.leoascenci0.stashlink.config.Feature;
import io.github.leoascenci0.stashlink.config.PlayerPrefs;
import io.github.leoascenci0.stashlink.config.StashLinkConfig;
import io.github.leoascenci0.stashlink.network.PlayerPrefsRequest;
import io.github.leoascenci0.stashlink.platform.Services;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.TreeSet;
import java.util.function.Consumer;

/**
 * Preferências pessoais deste jogador ({@code config/stashlink-client.json}), usadas quando ele está num
 * servidor/Realms onde não pode mexer na config do servidor. Vão ao servidor ao entrar e ao fechar a tela; o
 * servidor limita tudo. O envio em si é "cola" do loader ({@link #setSender}).
 */
public final class ClientPrefs {
    public static final String FILE_NAME = "stashlink-client.json";

    /** -1 = usar o padrão do servidor. */
    public static int radius = PlayerPrefs.UNSET;
    /** -1 = padrão do servidor, 0 = não, 1 = sim. */
    public static int chests = PlayerPrefs.UNSET;
    /** Raio das shulkers colocadas. -1 = padrão do servidor. */
    public static int shulkerRadius = PlayerPrefs.UNSET;
    public static TreeSet<Integer> lockedSlots = new TreeSet<>();
    /** Modo cliente: em servidor sem o mod, o cliente faz o trabalho sozinho. Ligado por padrão. */
    public static boolean clientModeEnabled = true;

    /** Funções que ESTE jogador desligou para si ({@link Feature#bit()}). Tudo ligado por padrão. */
    public static int disabledFeatures = 0;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Consumer<PlayerPrefsRequest> sender;

    private ClientPrefs() {
    }

    /** O loader diz como mandar o pacote (e só manda se o servidor conhecer o mod). */
    public static void setSender(Consumer<PlayerPrefsRequest> s) {
        sender = s;
    }

    public static PlayerPrefs toPrefs() {
        return new PlayerPrefs(radius, chests, List.copyOf(lockedSlots), disabledFeatures, shulkerRadius).sanitized();
    }

    /** O jogador deixou esta função ligada na tela de config? (Cadeado do servidor é outra conta: ClientFeatures.) */
    public static boolean isFeatureOn(Feature feature) {
        return (disabledFeatures & feature.bit()) == 0;
    }

    public static void setFeatureOn(Feature feature, boolean on) {
        disabledFeatures = on ? disabledFeatures & ~feature.bit() : disabledFeatures | feature.bit();
    }

    /** Manda as preferências ao servidor atual (não faz nada se o loader ainda não registrou o envio). */
    public static void sync() {
        if (sender != null) {
            sender.accept(new PlayerPrefsRequest(toPrefs()));
        }
    }

    private static final class Data {
        int radius = PlayerPrefs.UNSET;
        int chests = PlayerPrefs.UNSET;
        int shulkerRadius = PlayerPrefs.UNSET;
        int[] lockedSlots = new int[0];
        // Valor inicial true: arquivo antigo sem o campo continua com o modo cliente ligado.
        boolean clientModeEnabled = true;
        // Máscara de funções desligadas; arquivo antigo sem o campo = tudo ligado.
        int disabledFeatures = 0;
    }

    private static Path path() {
        return Services.PLATFORM.getConfigDir().resolve(FILE_NAME);
    }

    public static void load() {
        Path file = path();
        try {
            if (!Files.exists(file)) {
                return;
            }
            Data d = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), Data.class);
            if (d == null) {
                return;
            }
            radius = d.radius < 0 ? PlayerPrefs.UNSET : Math.min(d.radius, StashLinkConfig.HARD_MAX_RADIUS);
            shulkerRadius = d.shulkerRadius < 0 ? PlayerPrefs.UNSET
                    : Math.min(d.shulkerRadius, StashLinkConfig.HARD_MAX_SHULKER_RADIUS);
            chests = d.chests < 0 ? PlayerPrefs.UNSET : (d.chests == 0 ? 0 : 1);
            TreeSet<Integer> slots = new TreeSet<>();
            if (d.lockedSlots != null) {
                for (int s : d.lockedSlots) {
                    if (s >= 0 && s < StashLinkConfig.INVENTORY_SLOTS) {
                        slots.add(s);
                    }
                }
            }
            lockedSlots = slots;
            clientModeEnabled = d.clientModeEnabled;
            disabledFeatures = d.disabledFeatures & Feature.ALL_MASK;
        } catch (IOException | JsonSyntaxException e) {
            Constants.LOG.warn("Preferências {} ilegíveis, usando padrões: {}", file, e.toString());
        }
    }

    public static void save() {
        Path file = path();
        try {
            Data d = new Data();
            d.radius = radius;
            d.chests = chests;
            d.shulkerRadius = shulkerRadius;
            d.lockedSlots = lockedSlots.stream().mapToInt(Integer::intValue).toArray();
            d.clientModeEnabled = clientModeEnabled;
            d.disabledFeatures = disabledFeatures;
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(d) + System.lineSeparator(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Constants.LOG.warn("Não consegui gravar {}: {}", file, e.toString());
        }
    }
}
