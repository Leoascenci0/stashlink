package io.github.leoascenci0.stashlink.label;

import io.github.leoascenci0.stashlink.Constants;
import io.github.leoascenci0.stashlink.config.Feature;
import io.github.leoascenci0.stashlink.config.StashLinkConfig;
import io.github.leoascenci0.stashlink.compat.mc.LabelCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Mantém um holograma (entidade de texto, vista só de perto) sobre cada bloco com rótulo.
 *
 * <p><b>Por que assim.</b> O rótulo mora no bloco (ou no mundo, para o baú do End); o holograma é só um
 * <i>reflexo</i> dele e <b>nunca é gravado no disco</b> ({@code EntityMixin}): ao descarregar a área ou
 * reiniciar o servidor ele some, e é recriado quando o bloco volta. Assim não sobram hologramas órfãos
 * (quebrou o baú, apagou o holograma; crash, nada para limpar). A cada 10 ticks (0,5 s) compara o que
 * <i>deveria</i> existir com o que existe e corrige a diferença.
 */
public final class HologramService {
    private static final int INTERVAL = 10;

    private record Key(String dimension, long pos) {
    }

    private record Desired(ServerLevel level, Vec3 at, Label label) {
    }

    private record Holo(Entity entity, ServerLevel level, Vec3 at, Label label) {
    }

    /** Blocos com rótulo que o servidor conhece (carregados). Fraca: um bloco descartado sai sozinho. */
    private static final Set<BlockEntity> TRACKED = java.util.Collections.synchronizedSet(java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>()));
    private static final Map<Key, Holo> HOLOS = new HashMap<>();
    private static MinecraftServer owner;

    private HologramService() {
    }

    /** Avisa que este bloco tem (ou teve) rótulo; o próximo ciclo ajusta o holograma. */
    public static void track(BlockEntity be) {
        TRACKED.add(be);
    }

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % INTERVAL != 0) {
            return;
        }
        try {
            sync(server);
        } catch (RuntimeException e) {
            Constants.LOG.error("Falha ao atualizar hologramas de rótulo", e);
        }
    }

    /** Um ciclo completo. Público para os testes (que não querem esperar o relógio). */
    public static void sync(MinecraftServer server) {
        if (owner != null && owner != server) {
            // Outro mundo/servidor (mundo único reaberto): nada do anterior vale.
            HOLOS.values().forEach(h -> h.entity().discard());
            HOLOS.clear();
            TRACKED.clear();
        }
        if (owner != server) {
            owner = server;
        }
        trackEnderChests(server);

        // Função trancada pelo servidor: nenhum holograma aparece (os rótulos ficam gravados nos blocos e voltam
        // quando o cadeado abre).
        boolean shown = !StashLinkConfig.isFeatureLocked(Feature.LABEL);
        Map<Key, Desired> desired = new HashMap<>();
        synchronized (TRACKED) {
        for (Iterator<BlockEntity> it = TRACKED.iterator(); it.hasNext(); ) {
            BlockEntity be = it.next();
            if (be.isRemoved()) {
                it.remove();
                continue;
            }
            if (!(be.getLevel() instanceof ServerLevel level) || !level.isLoaded(be.getBlockPos())
                    || level.getBlockEntity(be.getBlockPos()) != be) {
                continue;
            }
            Label label = Labels.get(be);
            if (label.isEmpty()) {
                it.remove();
            } else if (shown && Labels.isAnchor(be)) {
                desired.put(new Key(level.dimension().identifier().toString(), be.getBlockPos().asLong()),
                        new Desired(level, Labels.hologramPos(be), label));
            }
        }

        }

        for (Iterator<Map.Entry<Key, Holo>> it = HOLOS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Key, Holo> entry = it.next();
            Holo holo = entry.getValue();
            Desired want = desired.get(entry.getKey());
            if (want == null || holo.entity().isRemoved() || want.level() != holo.level()
                    || !want.label().equals(holo.label()) || !want.at().equals(holo.at())) {
                holo.entity().discard();
                it.remove();
            }
        }
        for (Map.Entry<Key, Desired> entry : desired.entrySet()) {
            if (HOLOS.containsKey(entry.getKey())) {
                continue;
            }
            Desired want = entry.getValue();
            Component text = LabelText.hologram(want.label());
            Entity entity = text == null ? null
                    : LabelCompat.spawnHologram(want.level(), want.at().x, want.at().y, want.at().z, text);
            if (entity != null) {
                HOLOS.put(entry.getKey(), new Holo(entity, want.level(), want.at(), want.label()));
            }
        }
    }

    /** Baús do End carregados com rótulo gravado no mundo entram na lista. */
    private static void trackEnderChests(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        if (overworld == null) {
            return;
        }
        for (EnderLabels.Entry entry : EnderLabels.of(overworld).entries()) {
            for (ServerLevel level : server.getAllLevels()) {
                BlockPos pos = entry.pos();
                if (level.dimension().identifier().toString().equals(entry.dimension()) && level.isLoaded(pos)
                        && level.getBlockEntity(pos) instanceof EnderChestBlockEntity be) {
                    TRACKED.add(be);
                }
            }
        }
    }

    /** Quantos hologramas existem agora (para os testes). */
    public static int count() {
        return HOLOS.size();
    }
}
