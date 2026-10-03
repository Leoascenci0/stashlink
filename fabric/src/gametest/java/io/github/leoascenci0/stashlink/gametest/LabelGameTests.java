package io.github.leoascenci0.stashlink.gametest;

import io.github.leoascenci0.stashlink.label.EnderLabels;
import io.github.leoascenci0.stashlink.label.HologramService;
import io.github.leoascenci0.stashlink.label.Label;
import io.github.leoascenci0.stashlink.label.LabelService;
import io.github.leoascenci0.stashlink.label.LabelText;
import io.github.leoascenci0.stashlink.label.Labels;
import io.github.leoascenci0.stashlink.compat.mc.LabelCompat;
import io.github.leoascenci0.stashlink.network.LabelEditRequest;
import io.github.leoascenci0.stashlink.network.LabelEditorData;
import io.github.leoascenci0.stashlink.network.SetLabelRequest;
import io.github.leoascenci0.stashlink.platform.Services;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Display;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Item 14: nome/resumo do baú com holograma e ícones. Servidor de verdade, jogador em sobrevivência. O que
 * importa: o texto é limpo no servidor; persiste no bloco (e no mundo, para o End); some com baú/barril, fica na
 * shulker e no baú do End; o holograma existe só perto do bloco, nunca é gravado e nunca fica órfão; nenhum
 * item muda de lugar por causa de um rótulo.
 */
public class LabelGameTests {
    private static ServerPlayer player(Lab lab, double x, double y, double z) {
        return lab.player(x, y, z);
    }

    private static void check(GameTestHelper h, boolean ok, String msg) {
        h.assertTrue(ok, msg);
    }

    private static BlockEntity be(Lab lab, int x, int y, int z) {
        return lab.level.getBlockEntity(lab.helper.absolutePos(new BlockPos(x, y, z)));
    }

    private static BlockPos abs(Lab lab, int x, int y, int z) {
        return lab.helper.absolutePos(new BlockPos(x, y, z));
    }

    private static void sync(Lab lab) {
        HologramService.sync(lab.level.getServer());
    }

    private static List<Display.TextDisplay> holos(Lab lab, BlockPos around) {
        return lab.level.getEntitiesOfClass(Display.TextDisplay.class, new AABB(around).inflate(1.6, 3, 1.6),
                e -> e.entityTags().contains(LabelCompat.HOLOGRAM_TAG));
    }

    private static String textOf(Lab lab, Display.TextDisplay d) {
        TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, lab.level.registryAccess());
        d.saveWithoutId(out);
        CompoundTag tag = out.buildResult();
        return String.valueOf(tag.get("text"));
    }

    private static boolean set(ServerPlayer p, BlockPos pos, String name, String note) {
        return LabelService.apply(p, pos, name, note);
    }

    // ---------------------------------------------------------------- limpeza do texto

    @GameTest
    public void textIsSanitizedOnTheServer(GameTestHelper h) {
        check(h, LabelText.sanitize("§cRed §lBold", 32).equals("cRed lBold"), "§ nunca passa");
        check(h, LabelText.sanitize("a‮b​c\u0007d", 32).equals("abcd"), "direção, invisível e controle saem");
        check(h, LabelText.sanitize("  muitos     espaços\n\tlinha  ", 32).equals("muitos espaços linha"), "espaços juntos e sem quebra de linha");
        check(h, LabelText.sanitize("caixa 📦 ok ❤️", 32).equals("caixa ok ❤"),
                "emoji colorido sai (a fonte não tem), ❤ da fonte fica: " + LabelText.sanitize("caixa 📦 ok ❤️", 32));
        check(h, LabelText.sanitize("x".repeat(500), LabelText.MAX_NAME).length() == LabelText.MAX_NAME, "corta no limite do nome");
        check(h, LabelText.sanitize("", 32).isEmpty(), "uso privado sai");
        check(h, LabelText.sanitize(null, 32).isEmpty(), "nulo vira vazio");
        // o corte nunca parte um par substituto no meio (sobra texto válido)
        String cut = LabelText.sanitize("𝐀".repeat(40), 5);
        check(h, cut.codePointCount(0, cut.length()) == 5, "corte conta caracteres, não unidades UTF-16");
        h.succeed();
    }

    @GameTest
    public void shortcodesBecomeIconsAndSymbols(GameTestHelper h) {
        check(h, LabelText.knowsShortcode("apple") && LabelText.knowsShortcode("diamond_pickaxe")
                && LabelText.knowsShortcode("oak_log") && LabelText.knowsShortcode("heart"), "atalhos básicos existem");
        check(h, !LabelText.knowsShortcode("nao_existe_isto"), "atalho inventado não existe");
        String apple = LabelText.toComponent("a :apple: b").toString();
        check(h, apple.contains("atlas") || apple.contains("AtlasSprite") || apple.contains("object"), "maçã vira objeto de ícone: " + apple);
        check(h, LabelText.toComponent(":heart:").getString().equals("❤"), "heart vira ❤");
        check(h, LabelText.toComponent(":nao_existe_isto: x").getString().equals(":nao_existe_isto: x"), "desconhecido fica como texto");
        h.succeed();
    }

    // ---------------------------------------------------------------- gravar, persistir, holograma

    @GameTest
    public void chestLabelPersistsAndShowsHologramAndMovesNoItems(GameTestHelper h) {
        Lab lab = new Lab(h);
        Container chest = lab.chest(2, 2, 2);
        Lab.fill(chest, 3, Items.COBBLESTONE, 10);
        ServerPlayer p = player(lab, 4, 2, 4);
        BlockPos pos = abs(lab, 2, 2, 2);

        check(h, set(p, pos, "Pedras :cobblestone:", "tudo de construção ❤"), "gravou");
        check(h, Lab.count(chest, Items.COBBLESTONE) == 10, "rótulo não mexe em itens");
        Label label = Labels.get(be(lab, 2, 2, 2));
        check(h, label.name().equals("Pedras :cobblestone:") && label.note().equals("tudo de construção ❤"), "texto guardado: " + label);

        // vai ao disco e volta (o mesmo caminho de reiniciar o servidor)
        BlockEntity be = be(lab, 2, 2, 2);
        CompoundTag tag = be.saveWithFullMetadata(lab.level.registryAccess());
        BlockEntity loaded = BlockEntity.loadStatic(pos, be.getBlockState(), tag, lab.level.registryAccess());
        check(h, Labels.get(loaded).equals(label), "o rótulo voltou do disco");

        // holograma: um, perto do baú, com o tag do mod, e que NUNCA é gravado
        sync(lab);
        List<Display.TextDisplay> list = holos(lab, pos);
        check(h, list.size() == 1, "um holograma, achei " + list.size() + " count=" + HologramService.count() + " all=" + lab.level.getEntitiesOfClass(Display.TextDisplay.class, new AABB(pos).inflate(50)).stream().map(e -> e.position() + "/" + e.entityTags() + "/removed=" + e.isRemoved()).toList() + " pos=" + pos);
        Display.TextDisplay holo = list.get(0);
        check(h, !holo.shouldBeSaved(), "holograma nunca vai para o disco");
        check(h, Math.abs(holo.getX() - (pos.getX() + 0.5)) < 1e-6 && Math.abs(holo.getZ() - (pos.getZ() + 0.5 - 0.56)) < 1e-6 && holo.getY() > pos.getY() + 0.5 && holo.getY() < pos.getY() + 1.0,
                "holograma na frente do baú (norte): " + holo.position());
        String text = textOf(lab, holo);
        check(h, text.contains("Pedras") && text.contains("construção"), "texto no holograma: " + text);
        check(h, text.contains("cobblestone"), "o ícone entrou no texto: " + text);

        // mudar o texto troca o holograma; esvaziar remove
        check(h, set(p, pos, "Outro", ""), "gravou de novo");
        sync(lab);
        check(h, holos(lab, pos).size() == 1 && textOf(lab, holos(lab, pos).get(0)).contains("Outro"), "holograma atualizado");
        check(h, set(p, pos, "", ""), "limpou");
        sync(lab);
        check(h, holos(lab, pos).isEmpty(), "sem rótulo, sem holograma");
        lab.cleanup();
        sync(lab);
        h.succeed();
    }

    @GameTest
    public void chestAndBarrelLoseTheLabelWhenBroken(GameTestHelper h) {
        Lab lab = new Lab(h);
        lab.chest(2, 2, 2);
        lab.block(Blocks.BARREL, 6, 2, 2);
        ServerPlayer p = player(lab, 4, 2, 4);
        BlockPos chestPos = abs(lab, 2, 2, 2);
        BlockPos barrelPos = abs(lab, 6, 2, 2);
        check(h, set(p, chestPos, "Baú", "x") && set(p, barrelPos, "Barril", "y"), "gravou nos dois");
        sync(lab);
        check(h, holos(lab, chestPos).size() == 1 && holos(lab, barrelPos).size() == 1, "dois hologramas: " + holos(lab, chestPos).size() + "/" + holos(lab, barrelPos).size() + " total " + HologramService.count() + " " + Labels.get(be(lab, 6, 2, 2)));

        lab.level.removeBlock(chestPos, false);
        lab.level.removeBlock(barrelPos, false);
        sync(lab);
        check(h, holos(lab, chestPos).isEmpty() && holos(lab, barrelPos).isEmpty(), "quebrou: holograma some");
        lab.block(Blocks.CHEST, 2, 2, 2);                                   // outro no mesmo lugar
        lab.block(Blocks.BARREL, 6, 2, 2);
        check(h, Labels.get(be(lab, 2, 2, 2)).isEmpty() && Labels.get(be(lab, 6, 2, 2)).isEmpty(), "o novo não herda o rótulo");
        sync(lab);
        check(h, holos(lab, chestPos).isEmpty() && holos(lab, barrelPos).isEmpty(), "e não aparece holograma");
        lab.cleanup();
        h.succeed();
    }

    @GameTest
    public void shulkerKeepsTheLabelInItsItem(GameTestHelper h) {
        Lab lab = new Lab(h);
        lab.block(Blocks.SHULKER_BOX, 2, 2, 2);
        ServerPlayer p = player(lab, 4, 2, 4);
        BlockPos pos = abs(lab, 2, 2, 2);
        check(h, set(p, pos, "Shulker :diamond:", "minérios"), "gravou");

        BlockEntity old = be(lab, 2, 2, 2);
        DataComponentMap components = old.collectComponents();               // o que o item solto leva
        lab.level.removeBlock(pos, false);
        lab.block(Blocks.SHULKER_BOX, 7, 2, 2);
        BlockEntity fresh = be(lab, 7, 2, 2);
        check(h, Labels.get(fresh).isEmpty(), "shulker nova começa sem rótulo");
        fresh.applyComponents(components, net.minecraft.core.component.DataComponentPatch.EMPTY);
        check(h, Labels.get(fresh).name().equals("Shulker :diamond:") && Labels.get(fresh).note().equals("minérios"),
                "o rótulo voltou ao colocar: " + Labels.get(fresh));
        sync(lab);
        check(h, holos(lab, abs(lab, 7, 2, 2)).size() == 1, "e o holograma voltou");
        lab.cleanup();
        sync(lab);
        h.succeed();
    }

    @GameTest
    public void enderChestKeepsTheLabelByPosition(GameTestHelper h) {
        Lab lab = new Lab(h);
        BlockPos pos = abs(lab, 2, 2, 2);
        lab.level.setBlock(pos, Blocks.ENDER_CHEST.defaultBlockState(), 3);
        ServerPlayer p = player(lab, 4, 2, 4);
        check(h, set(p, pos, "End", "tesouros"), "baú do End aceita rótulo");
        sync(lab);
        check(h, holos(lab, pos).size() == 1, "holograma no baú do End");

        lab.level.removeBlock(pos, false);
        sync(lab);
        check(h, holos(lab, pos).isEmpty(), "quebrou: holograma some");
        check(h, EnderLabels.of(lab.level).get(lab.level, pos).name().equals("End"), "o nome continua gravado no mundo");
        lab.level.setBlock(pos, Blocks.ENDER_CHEST.defaultBlockState(), 3);   // colocar de novo
        sync(lab);
        check(h, holos(lab, pos).size() == 1 && Labels.get(lab.level.getBlockEntity(pos)).name().equals("End"),
                "recolocado, o nome e o holograma voltam");
        // limpar de verdade
        set(p, pos, "", "");
        sync(lab);
        check(h, holos(lab, pos).isEmpty() && EnderLabels.of(lab.level).get(lab.level, pos).isEmpty(), "limpar apaga do mundo");
        lab.level.removeBlock(pos, false);
        sync(lab);
        h.succeed();
    }

    @GameTest
    public void doubleChestIsOneSystemWithOneHologram(GameTestHelper h) {
        Lab lab = new Lab(h);
        lab.doubleChest(2, 2, 2);
        ServerPlayer p = player(lab, 4, 2, 4);
        BlockPos left = abs(lab, 2, 2, 2);
        BlockPos right = abs(lab, 3, 2, 2);
        check(h, set(p, right, "Duplo", "dois baús"), "gravou pela metade direita");
        check(h, Labels.get(lab.level.getBlockEntity(left)).name().equals("Duplo")
                && Labels.get(lab.level.getBlockEntity(right)).name().equals("Duplo"), "as duas metades têm o nome");
        sync(lab);
        List<Display.TextDisplay> list = holos(lab, left);
        check(h, list.size() == 1, "um só holograma, achei " + list.size());
        check(h, Math.abs(list.get(0).getX() - (left.getX() + 1.0)) < 1e-6, "no meio das duas metades: x=" + list.get(0).getX());

        lab.level.removeBlock(left, false);                                  // sobra uma metade (vira baú simples)
        sync(lab);
        check(h, holos(lab, right).size() == 1, "continua um holograma");
        lab.cleanup();
        sync(lab);
        check(h, holos(lab, right).isEmpty(), "limpo no fim");
        h.succeed();
    }


    @GameTest
    public void hologramIsShownOnlyWithin32Blocks(GameTestHelper h) {
        Lab lab = new Lab(h);
        lab.chest(2, 2, 2);
        ServerPlayer near = player(lab, 4, 2, 4);
        ServerPlayer far = player(lab, 4, 2, 4);
        BlockPos pos = abs(lab, 2, 2, 2);
        check(h, set(near, pos, "Longe e perto", ""), "gravou");
        sync(lab);
        Display.TextDisplay holo = holos(lab, pos).get(0);
        check(h, holo.broadcastToPlayer(near), "jogador perto vê o holograma");
        far.setPos(lab.helper.absoluteVec(new Vec3(4, 2, 4)).add(0, 0, 40));      // 40 blocos
        check(h, !holo.broadcastToPlayer(far), "a 40 blocos não vê");
        far.setPos(lab.helper.absoluteVec(new Vec3(4, 2, 4)).add(0, 0, 25));      // 25 blocos
        check(h, holo.broadcastToPlayer(far), "a 25 blocos vê");
        lab.cleanup();
        sync(lab);
        h.succeed();
    }

    @GameTest
    public void hologramStaysVisibleWhenAChestIsStackedAbove(GameTestHelper h) {
        Lab lab = new Lab(h);
        lab.chest(2, 2, 2);
        lab.chest(2, 3, 2);                                                 // outro baú por cima
        ServerPlayer p = player(lab, 4, 2, 4);
        BlockPos low = abs(lab, 2, 2, 2);
        check(h, set(p, low, "Embaixo", ""), "gravou");
        sync(lab);
        Display.TextDisplay holo = holos(lab, low).get(0);
        BlockPos above = abs(lab, 2, 3, 2);
        // o texto não fica dentro do bloco do baú de cima nem dentro do de baixo
        BlockPos at = BlockPos.containing(holo.position());
        check(h, !at.equals(above) && !at.equals(low), "o texto está fora dos dois blocos de baú: " + holo.position());
        check(h, lab.level.getBlockState(at).isAir(), "e num bloco de ar, onde se enxerga: " + at);
        lab.cleanup();
        sync(lab);
        h.succeed();
    }
    // ---------------------------------------------------------------- validação

    @GameTest
    public void serverRejectsBadRequests(GameTestHelper h) {
        Lab lab = new Lab(h);
        lab.chest(2, 2, 2);
        lab.level.setBlock(abs(lab, 8, 2, 2), Blocks.STONE.defaultBlockState(), 3);
        ServerPlayer near = player(lab, 4, 2, 4);
        ServerPlayer far = player(lab, 40, 2, 40);
        BlockPos chest = abs(lab, 2, 2, 2);

        check(h, !set(far, chest, "x", "y") && Labels.get(be(lab, 2, 2, 2)).isEmpty(), "longe demais: recusa");
        check(h, !set(near, abs(lab, 8, 2, 2), "x", "y"), "pedra não aceita rótulo");
        check(h, !set(near, chest.above(20), "x", "y"), "ar não aceita rótulo");
        check(h, set(near, chest, "§4" + "A".repeat(500), "‮" + "B".repeat(500) + "📦"), "texto enorme é aceito, mas cortado");
        Label l = Labels.get(be(lab, 2, 2, 2));
        check(h, l.name().length() == LabelText.MAX_NAME && !l.name().contains("§"), "nome cortado e sem §: " + l.name());
        check(h, l.note().length() == LabelText.MAX_NOTE && !l.note().contains("‮"), "nota cortada e sem direção: " + l.note().length());

        // pedido pela rede: o handler nunca derruba, mesmo com lixo
        LabelService.handleSet(near, new SetLabelRequest(chest, "ok", "ok"));
        LabelService.handleSet(near, new SetLabelRequest(new BlockPos(0, -1000, 0), "ok", "ok"));
        LabelService.handleEdit(far, new LabelEditRequest(chest));
        check(h, Labels.get(be(lab, 2, 2, 2)).name().equals("ok"), "pedido válido gravou");
        lab.cleanup();
        lab.level.removeBlock(abs(lab, 8, 2, 2), false);
        sync(lab);
        h.succeed();
    }

    @GameTest
    public void packetsRoundTripAndVanillaClientsGetNothing(GameTestHelper h) {
        Lab lab = new Lab(h);
        lab.chest(2, 2, 2);
        ServerPlayer p = player(lab, 4, 2, 4);
        BlockPos pos = abs(lab, 2, 2, 2);
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), lab.level.registryAccess());
        SetLabelRequest.STREAM_CODEC.encode(buf, new SetLabelRequest(pos, "não", "nota ❤"));
        SetLabelRequest back = SetLabelRequest.STREAM_CODEC.decode(buf);
        check(h, back.pos().equals(pos) && back.name().equals("não") && back.note().equals("nota ❤"), "SetLabelRequest ida e volta");
        buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), lab.level.registryAccess());
        LabelEditorData.STREAM_CODEC.encode(buf, new LabelEditorData(pos, "a", "b"));
        check(h, LabelEditorData.STREAM_CODEC.decode(buf).note().equals("b"), "LabelEditorData ida e volta");
        // cliente sem o mod (o jogador simulado não registrou o canal): nunca recebe o editor
        check(h, !Services.PLATFORM.sendIfSupported(p, new LabelEditorData(pos, "a", "b")), "vanilla não recebe pacote do mod");
        // e um texto grande demais é recusado na própria leitura do pacote
        RegistryFriendlyByteBuf big = new RegistryFriendlyByteBuf(Unpooled.buffer(), lab.level.registryAccess());
        try {
            SetLabelRequest.STREAM_CODEC.encode(big, new SetLabelRequest(pos, "x".repeat(300), ""));
            check(h, false, "devia recusar texto de 300 caracteres no pacote");
        } catch (RuntimeException expected) {
            // ok: limite do pacote
        }
        lab.cleanup();
        h.succeed();
    }
}
