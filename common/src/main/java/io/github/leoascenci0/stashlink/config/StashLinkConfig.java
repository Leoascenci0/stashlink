package io.github.leoascenci0.stashlink.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import io.github.leoascenci0.stashlink.Constants;
import io.github.leoascenci0.stashlink.platform.Services;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * Configuração do mod, guardada em {@code config/stashlink.json} (JSON, igual nos dois loaders).
 *
 * <p>Quem consome só usa os métodos daqui. O <b>servidor é a autoridade</b>: o arquivo lido é o do lado que roda
 * a lógica (servidor dedicado ou mundo local); {@link #maxRadius} é o teto definido pelo dono do servidor e nem
 * comando nem tela passam dele, e {@link #HARD_MAX_RADIUS} é o teto do código, que nem o arquivo passa.
 */
public final class StashLinkConfig {
    /**
     * Teto duro de baús, barris e bancadas, imposto pelo código: nenhum arquivo, comando ou pedido de cliente passa
     * disto (performance). Item 15 (Eliel, 2026-10-05): padrão 16, a tela sobe até 32, sem exigir conduíte.
     */
    public static final int HARD_MAX_RADIUS = 32;

    /** Raio padrão de baús, barris e bancadas. */
    public static final int DEFAULT_RADIUS = 16;

    /**
     * Versão do formato do arquivo. 2 (Item 15): o teto passou de 16 para 32 e o padrão de 8 para 16; arquivos
     * antigos que ainda têm os valores padrão de antes são atualizados ao carregar (ver {@link #fromJson}).
     */
    static final int CONFIG_VERSION = 2;

    /** Teto duro do raio das shulkers colocadas (maior que o de baús: são poucas e não pesam). */
    public static final int HARD_MAX_SHULKER_RADIUS = 64;

    /** Nome do arquivo dentro da pasta de config do loader. */
    public static final String FILE_NAME = "stashlink.json";

    /** Quantidade de slots do inventário principal (0-8 hotbar, 9-35 mochila). */
    public static final int INVENTORY_SLOTS = 36;

    /** Raio (em blocos) em volta do jogador onde containers colocados servem de fonte. 0 desliga. */
    public static int sourceRadius = DEFAULT_RADIUS;

    /** Teto do raio definido pelo servidor: só editável no arquivo, nunca por comando/tela. */
    public static int maxRadius = HARD_MAX_RADIUS;

    /** Raio (em blocos) das shulkers colocadas. Padrão 32; a tela deixa subir até {@link #shulkerCap()}. */
    public static int shulkerRadius = 32;

    /** Teto do raio de shulkers definido pelo servidor (só no arquivo), nunca acima de {@link #HARD_MAX_SHULKER_RADIUS}. */
    public static int maxShulkerRadius = HARD_MAX_SHULKER_RADIUS;

    /** Se baús e barris (além de shulkers colocadas) servem de fonte. Desligado por padrão. */
    public static boolean includeChests = false;

    /**
     * Slots do inventário (0-8 hotbar, 9-35 mochila) que a tecla N nunca esvazia e a tecla W nunca preenche.
     * Na tecla N a hotbar é sempre ignorada; na W ela recebe itens (por último).
     */
    public static Set<Integer> lockedSlots = new TreeSet<>();

    /**
     * Funções trancadas (cadeado) pelo dono do servidor: não funcionam para ninguém aqui, mesmo que o jogador
     * as tenha ligadas. Padrão: nenhuma trancada.
     */
    public static Set<Feature> lockedFeatures = EnumSet.noneOf(Feature.class);

    /** Tempo mínimo entre duas execuções da tecla N do mesmo jogador (impede spam de pacotes). */
    public static final int QUICK_STACK_COOLDOWN_TICKS = 10;

    /** Tempo mínimo entre duas execuções da tecla W do mesmo jogador (impede spam de pacotes). */
    public static final int LOOT_ALL_COOLDOWN_TICKS = 5;

    /** Tempo mínimo entre dois pedidos de travar/destravar slot do mesmo jogador (anti-flood). */
    public static final int SLOT_LOCK_COOLDOWN_TICKS = 3;

    /** Tempo mínimo entre dois cliques no botão "recebe itens com a N" do mesmo jogador (anti-flood). */
    public static final int RECEIVES_COOLDOWN_TICKS = 3;

    /** Tempo mínimo entre dois pedidos de rótulo (abrir o editor, ou gravar) do mesmo jogador (anti-flood). */
    public static final int LABEL_COOLDOWN_TICKS = 4;

    /** Tempo mínimo entre duas aplicações das preferências do mesmo jogador; no intervalo vale só a última. */
    public static final int PLAYER_PREFS_COOLDOWN_TICKS = 20;

    /** Tempo mínimo entre dois pedidos da bancada que varrem o raio (montar receita, pagar): 3 ticks. */
    public static final int BENCH_SWEEP_COOLDOWN_TICKS = 3;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Arquivo que o último {@link #load} achou quebrado ou de versão futura: o próximo {@link #save} nele guarda antes
     * uma cópia {@code .bak}, para o jogador não perder o que tinha escrito.
     */
    private static Path backupBeforeSave;

    private StashLinkConfig() {
    }

    /** Vale {@code true} se a tecla N deve deixar este slot do inventário em paz. */
    public static boolean isSlotLocked(int inventorySlot) {
        return lockedSlots.contains(inventorySlot);
    }

    /** A função está trancada pelo servidor? */
    public static boolean isFeatureLocked(Feature feature) {
        return lockedFeatures.contains(feature);
    }

    /** Tranca/destranca uma função (o chamador cuida de gravar e de avisar os jogadores). */
    public static void setFeatureLocked(Feature feature, boolean locked) {
        Set<Feature> copy = EnumSet.noneOf(Feature.class);
        copy.addAll(lockedFeatures);
        if (locked) {
            copy.add(feature);
        } else {
            copy.remove(feature);
        }
        lockedFeatures = copy;
    }

    /** Teto real do raio: o do servidor, mas nunca acima do teto do código. */
    public static int radiusCap() {
        return Math.max(0, Math.min(maxRadius, HARD_MAX_RADIUS));
    }

    /** Teto real do raio de shulkers: o do servidor, mas nunca acima do teto do código. */
    public static int shulkerCap() {
        return Math.max(0, Math.min(maxShulkerRadius, HARD_MAX_SHULKER_RADIUS));
    }

    /** Raio de shulkers realmente usado: sempre entre 0 e {@link #shulkerCap()}. */
    public static int effectiveShulkerRadius() {
        return Math.max(0, Math.min(shulkerRadius, shulkerCap()));
    }

    /** Raio realmente usado: sempre entre 0 e {@link #radiusCap()}. */
    public static int effectiveRadius() {
        return Math.max(0, Math.min(sourceRadius, radiusCap()));
    }

    /**
     * Muda o raio se {@code radius} estiver entre 0 e o teto.
     *
     * @return {@code true} se aplicou; {@code false} se o valor estava fora do permitido (nada muda)
     */
    public static boolean trySetRadius(int radius) {
        if (radius < 0 || radius > radiusCap()) {
            return false;
        }
        sourceRadius = radius;
        return true;
    }

    // ---- persistência ----

    /** Formato do arquivo. Campos ausentes/inválidos caem no padrão em {@link #apply}. */
    private static final class Data {
        /** Ausente = arquivo de antes do Item 15 (versão 1). */
        int configVersion = 1;
        int sourceRadius = DEFAULT_RADIUS;
        int maxRadius = HARD_MAX_RADIUS;
        int shulkerRadius = 32;
        int maxShulkerRadius = HARD_MAX_SHULKER_RADIUS;
        boolean includeChests = false;
        int[] lockedSlots = new int[0];
        String[] lockedFeatures = new String[0];
    }

    /** Texto JSON com os valores atuais. */
    public static String toJson() {
        Data d = new Data();
        d.configVersion = CONFIG_VERSION;
        d.sourceRadius = sourceRadius;
        d.maxRadius = maxRadius;
        d.shulkerRadius = shulkerRadius;
        d.maxShulkerRadius = maxShulkerRadius;
        d.includeChests = includeChests;
        d.lockedSlots = lockedSlots.stream().mapToInt(Integer::intValue).toArray();
        d.lockedFeatures = lockedFeatures.stream().map(Feature::id).toArray(String[]::new);
        return GSON.toJson(d);
    }

    /**
     * Aplica um JSON às variáveis. Valores fora da faixa são corrigidos (raio 0..teto, slots 0..35), nunca
     * rejeitados; JSON inválido lança {@link JsonSyntaxException} sem alterar nada.
     */
    public static void fromJson(String json) {
        apply(json);
    }

    /** Aplica o JSON e devolve o {@code configVersion} que estava escrito nele. */
    private static int apply(String json) {
        Data d = GSON.fromJson(json, Data.class);
        if (d == null) {
            throw new JsonSyntaxException("arquivo vazio");
        }
        if (d.configVersion < 2) {
            // Antes do Item 15 o teto era 16 e o padrão 8, e o arquivo grava os padrões. Quem não mexeu neles
            // ganha os novos (32 e 16); um valor escolhido pelo dono do servidor (qualquer outro) fica como está.
            if (d.maxRadius == 16) {
                d.maxRadius = HARD_MAX_RADIUS;
            }
            if (d.sourceRadius == 8) {
                d.sourceRadius = DEFAULT_RADIUS;
            }
        }
        maxRadius = clamp(d.maxRadius, 0, HARD_MAX_RADIUS);
        sourceRadius = clamp(d.sourceRadius, 0, maxRadius);
        maxShulkerRadius = clamp(d.maxShulkerRadius, 0, HARD_MAX_SHULKER_RADIUS);
        shulkerRadius = clamp(d.shulkerRadius, 0, maxShulkerRadius);
        includeChests = d.includeChests;
        Set<Integer> slots = new TreeSet<>();
        if (d.lockedSlots != null) {
            for (int s : d.lockedSlots) {
                if (s >= 0 && s < INVENTORY_SLOTS) {
                    slots.add(s);
                }
            }
        }
        lockedSlots = slots;
        Set<Feature> features = EnumSet.noneOf(Feature.class);
        if (d.lockedFeatures != null) {
            for (String id : d.lockedFeatures) {
                // Nome desconhecido (erro de digitação, função de versão futura): ignora, nunca rejeita o arquivo.
                Feature.byId(id).ifPresent(features::add);
            }
        }
        lockedFeatures = features;
        return d.configVersion;
    }

    /** Caminho do arquivo no loader atual. */
    public static Path defaultPath() {
        return Services.PLATFORM.getConfigDir().resolve(FILE_NAME);
    }

    /** Carrega do caminho padrão. Veja {@link #load(Path)}. */
    public static boolean load() {
        return load(defaultPath());
    }

    /** Grava no caminho padrão. Veja {@link #save(Path)}. */
    public static boolean save() {
        return save(defaultPath());
    }

    /**
     * Lê o arquivo. Se não existe, cria com os padrões. Se está quebrado, mantém os valores atuais e avisa no log
     * (o arquivo do jogador não é sobrescrito aqui; e o primeiro {@link #save} depois guarda um {@code .bak} dele).
     * Se vem de uma versão futura do mod, usa o que entende, avisa e <b>não</b> regrava (não rebaixa o arquivo).
     *
     * @return {@code true} se o arquivo foi lido (ou criado) sem erro
     */
    public static boolean load(Path file) {
        try {
            if (!Files.exists(file)) {
                backupBeforeSave = null;
                return save(file);
            }
            int version = apply(Files.readString(file, StandardCharsets.UTF_8));
            if (version > CONFIG_VERSION) {
                backupBeforeSave = file;
                Constants.LOG.warn("Config {} é da versão {} (este mod entende até a {}); não vou regravar o arquivo "
                        + "sozinho. Se algo for salvo por comando ou tela, fica uma cópia .bak.", file, version, CONFIG_VERSION);
                return true;
            }
            // Regrava já normalizado (completa campos novos, corrige valores fora da faixa).
            backupBeforeSave = null;
            return save(file);
        } catch (IOException | JsonSyntaxException e) {
            backupBeforeSave = file;
            Constants.LOG.warn("Config {} ilegível, mantendo valores atuais: {}", file, e.toString());
            return false;
        }
    }

    /**
     * Grava o arquivo (cria a pasta se preciso). Erro de disco só vai para o log. Se o arquivo estava quebrado ou era de
     * versão futura (ver {@link #load}), guarda antes uma cópia {@code .bak}; se a cópia falha, não sobrescreve.
     */
    public static boolean save(Path file) {
        try {
            Files.createDirectories(file.getParent());
            if (file.equals(backupBeforeSave) && Files.exists(file)) {
                Path bak = backupPath(file);
                Files.copy(file, bak);
                Constants.LOG.warn("Guardei a config anterior em {} antes de sobrescrever {}", bak, file);
            }
            backupBeforeSave = null;
            Files.writeString(file, toJson() + System.lineSeparator(), StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            Constants.LOG.warn("Não consegui gravar {}: {}", file, e.toString());
            return false;
        }
    }

    /** {@code stashlink.json.bak}; se já existe um (de um conserto anterior), um com a data no nome, nunca por cima. */
    private static Path backupPath(Path file) {
        Path bak = file.resolveSibling(file.getFileName() + ".bak");
        return Files.exists(bak) ? file.resolveSibling(file.getFileName() + "." + System.currentTimeMillis() + ".bak") : bak;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(v, hi));
    }
}
