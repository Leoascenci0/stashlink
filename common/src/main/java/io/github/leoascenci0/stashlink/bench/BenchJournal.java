package io.github.leoascenci0.stashlink.bench;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.leoascenci0.stashlink.Constants;
import io.github.leoascenci0.stashlink.compat.mc.McCompat;
import io.github.leoascenci0.stashlink.source.ItemSource;
import io.github.leoascenci0.stashlink.source.Origin;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Diário das bancadas gravado no mundo (Item 31; arquivo {@code data/stashlink/bench_journal.dat}).
 *
 * <p>O problema: a grade da bancada e o cursor <b>não são gravados</b> pelo jogo (só existem na memória). Se o servidor
 * cair com a estação aberta e um item emprestado na grade, o baú já foi gravado sem ele, e o item some. O diário
 * anota, a cada save do mundo, o que estava emprestado naquele exato momento (cursor e grade) e de onde veio.
 *
 * <p>Por que é seguro (nunca duplica): o diário não é atualizado "de vez em quando"; ele é <b>calculado na hora do
 * save</b>, no mesmo save que grava baús e jogadores ({@link BenchLedger#outstanding}). Então ele sempre descreve o
 * mesmo instante que o resto do mundo gravado: o que está nele estava na grade/cursor (que se perdem), e não no baú
 * nem na mochila (que ficam gravados). Desligar o servidor do jeito normal devolve tudo antes do último save, e o
 * diário fica vazio.
 *
 * <p>Na volta: quando o jogador entra de novo, o que o diário guarda para ele volta ao baú de origem (se ainda estiver
 * ao alcance) ou, se não couber, para a mochila dele (nunca some). Depois disso a entrada é apagada.
 */
public final class BenchJournal extends SavedData {
    /** Uma estação que estava aberta no save: de quem, em que dimensão, de quais posições, e as pilhas emprestadas. */
    public record Record(UUID player, String dimension, List<BlockPos> positions, List<ItemStack> items) {
        static final Codec<Record> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("player").forGetter(Record::player),
                Codec.STRING.fieldOf("dimension").forGetter(Record::dimension),
                BlockPos.CODEC.listOf().fieldOf("positions").forGetter(Record::positions),
                ItemStack.CODEC.listOf().fieldOf("items").forGetter(Record::items)
        ).apply(i, Record::new));
    }

    private static final Codec<BenchJournal> CODEC = RecordCodecBuilder.create(i -> i.group(
            Record.CODEC.listOf().optionalFieldOf("pending", List.of()).forGetter(BenchJournal::toSave)
    ).apply(i, BenchJournal::new));

    private static final SavedDataType<BenchJournal> TYPE = new SavedDataType<>(
            McCompat.id("bench_journal"), BenchJournal::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    /** O que veio do disco (de uma queda) e ainda não foi devolvido, por jogador. */
    private final Map<UUID, List<Record>> pending = new LinkedHashMap<>();

    public BenchJournal() {
    }

    private BenchJournal(List<Record> records) {
        for (Record record : records) {
            if (!record.items().isEmpty()) {
                pending.computeIfAbsent(record.player(), k -> new ArrayList<>()).add(record);
            }
        }
    }

    /** Um único diário para o servidor todo (guardado no mundo principal; cada entrada diz a dimensão). */
    public static BenchJournal of(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /**
     * O caderno mudou (ou ainda tem algo): o próximo save grava o diário de novo. Barato (só marca); o conteúdo é
     * calculado no save. Nunca derruba quem chamou.
     */
    static void touch(ServerPlayer player) {
        try {
            MinecraftServer server = player.level().getServer();
            if (server != null) {
                of(server).setDirty();
            }
        } catch (RuntimeException e) {
            Constants.LOG.error("Falha ao marcar o diário das bancadas", e);
        }
    }

    /** O que vai ao disco: o que ainda não foi devolvido de uma queda anterior + o emprestado agora. */
    private List<Record> toSave() {
        List<Record> out = new ArrayList<>();
        pending.values().forEach(out::addAll);
        try {
            for (BenchLedger.Outstanding o : BenchLedger.outstanding()) {
                out.add(new Record(o.player(), McCompat.dimensionId(o.origin().dimension()),
                        List.copyOf(o.origin().positions()), o.items()));
            }
        } catch (RuntimeException e) {
            Constants.LOG.error("Falha ao montar o diário das bancadas (o save segue sem a parte de agora)", e);
        }
        return out;
    }

    /**
     * O jogador entrou: se o diário guarda algo dele (o servidor caiu com a estação aberta), devolve agora. Ao baú de
     * origem, se ainda estiver ao alcance; o que não couber vai para a mochila (ou para o chão, se ela estiver cheia).
     * Só a thread do servidor chama. Nunca devolve mais do que o diário anotou: a entrada sai antes de devolver.
     */
    public static void restore(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null || !server.isSameThread()) {
            return;
        }
        try {
            BenchJournal journal = of(server);
            List<Record> mine = journal.pending.remove(player.getUUID());
            if (mine == null) {
                return;
            }
            journal.setDirty();
            int total = 0;
            for (Record record : mine) {
                Object here = McCompat.dimensionOf(player);
                boolean sameDimension = record.dimension().equals(McCompat.dimensionId(here));
                Origin origin = new Origin(false, here,
                        sameDimension ? new HashSet<>(record.positions()) : java.util.Set.of());
                ItemSource target = BenchPool.returnTarget(player, origin);
                for (ItemStack stack : record.items()) {
                    total += stack.getCount();
                    ItemStack rest = target.give(stack.copy());
                    if (!rest.isEmpty()) {
                        McCompat.placeBackInInventory(player, rest);
                    }
                }
            }
            Constants.LOG.info("Devolvidos {} itens que estavam emprestados na bancada de {} quando o servidor caiu",
                    total, player.getGameProfile().name());
            BenchSync.markDirty(player);
        } catch (RuntimeException e) {
            Constants.LOG.error("Falha ao devolver o diário das bancadas de {}", player.getGameProfile().name(), e);
        }
    }

    /**
     * Testes: o diário exatamente como um save o gravaria agora, de ida e volta pelo mesmo codec e contexto de registros
     * (o que voltaria do disco depois de uma queda).
     */
    public static List<Record> saveForTest(MinecraftServer server) {
        RegistryOps<Tag> ops = server.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        Tag saved = CODEC.encodeStart(ops, of(server)).getOrThrow();
        List<Record> out = new ArrayList<>();
        CODEC.parse(ops, saved).getOrThrow().pending.values().forEach(out::addAll);
        return out;
    }

    /** Testes: põe no diário o que um save anterior gravou, como se o servidor tivesse acabado de voltar de uma queda. */
    public static void loadForTest(MinecraftServer server, List<Record> records) {
        BenchJournal journal = of(server);
        for (Record record : records) {
            journal.pending.computeIfAbsent(record.player(), k -> new ArrayList<>()).add(record);
        }
        journal.setDirty();
    }
}
