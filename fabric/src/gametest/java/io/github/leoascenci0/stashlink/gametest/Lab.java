package io.github.leoascenci0.stashlink.gametest;

import io.github.leoascenci0.stashlink.config.PlayerPrefs;
import io.github.leoascenci0.stashlink.config.PlayerPrefsStore;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Ferramentas comuns dos cenários: monta o "laboratório" (baús, jogadores, contagem de itens) no mundo real do teste. */
final class Lab {
    final GameTestHelper helper;
    final ServerLevel level;
    /** Tudo o que este teste colocou no mundo; é removido no fim para não atrapalhar os outros testes. */
    private final List<BlockPos> placed = new ArrayList<>();

    Lab(GameTestHelper helper) {
        this.helper = helper;
        this.level = helper.getLevel();
    }

    /**
     * Jogador simulado em modo SOBREVIVÊNCIA. O do próprio GameTest é sempre criativo, e o mod (de propósito)
     * não reabastece nem puxa item para criativo, então não serve para testar essas partes.
     */
    ServerPlayer player(double x, double y, double z) {
        MinecraftServer server = level.getServer();
        GameProfile profile = new GameProfile(UUID.randomUUID(), "survival-mock");
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        ServerPlayer p = new ServerPlayer(server, level, cookie.gameProfile(), cookie.clientInformation()) {
            @Override
            public GameType gameMode() {
                return GameType.SURVIVAL;
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, p, cookie);
        p.setPos(helper.absoluteVec(new Vec3(x, y, z)));
        return p;
    }

    static void prefs(ServerPlayer p, int radius, boolean chests) {
        PlayerPrefsStore.set(p.getUUID(), new PlayerPrefs(radius, chests ? 1 : 0, List.of()));
    }

    /** Coloca um bloco-container e devolve o container (a block entity). */
    Container block(Block block, int x, int y, int z) {
        BlockPos pos = helper.absolutePos(new BlockPos(x, y, z));
        level.setBlock(pos, block.defaultBlockState(), 3);
        placed.add(pos);
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof Container c)) {
            throw new IllegalStateException("sem container em " + pos);
        }
        return c;
    }

    /** Coloca um bloco comum na posição absoluta {@code pos} e o remove no fim do teste. */
    BlockPos bareAt(Block block, BlockPos pos) {
        level.setBlock(pos, block.defaultBlockState(), 3);
        placed.add(pos);
        return pos;
    }

    /** Coloca um bloco comum (sem container), por exemplo uma bancada, e o remove no fim do teste. */
    BlockPos bare(Block block, int x, int y, int z) {
        BlockPos pos = helper.absolutePos(new BlockPos(x, y, z));
        level.setBlock(pos, block.defaultBlockState(), 3);
        placed.add(pos);
        return pos;
    }

    Container chest(int x, int y, int z) {
        return block(Blocks.CHEST, x, y, z);
    }

    /** Baú duplo (duas metades lado a lado em x e x+1) e o container de 54 slots que o jogo usa. */
    Container doubleChest(int x, int y, int z) {
        BlockState base = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH);
        BlockPos left = helper.absolutePos(new BlockPos(x, y, z));
        BlockPos right = helper.absolutePos(new BlockPos(x + 1, y, z));
        level.setBlock(left, base.setValue(ChestBlock.TYPE, ChestType.LEFT), 3);
        level.setBlock(right, base.setValue(ChestBlock.TYPE, ChestType.RIGHT), 3);
        placed.add(left);
        placed.add(right);
        Container l = (Container) level.getBlockEntity(left);
        Container r = (Container) level.getBlockEntity(right);
        return new CompoundContainer(l, r);
    }

    void cleanup() {
        for (BlockPos pos : placed) {
            level.removeBlock(pos, false);
        }
        placed.clear();
    }

    static ChestMenu open(ServerPlayer p, Container c, int id) {
        ChestMenu menu = c.getContainerSize() > 27
                ? ChestMenu.sixRows(id, p.getInventory(), c)
                : ChestMenu.threeRows(id, p.getInventory(), c);
        p.containerMenu = menu;
        return menu;
    }

    static void close(ServerPlayer p) {
        p.containerMenu = p.inventoryMenu;
    }

    static int count(Container c, Item item) {
        return c.countItem(item);
    }

    /** Tudo o que o jogador carrega: inventário completo (com armadura e mão secundária) e item no cursor. */
    static int carried(ServerPlayer p, Item item) {
        Inventory inv = p.getInventory();
        int total = inv.countItem(item);
        ItemStack cursor = p.containerMenu.getCarried();
        if (cursor.is(item)) {
            total += cursor.getCount();
        }
        return total;
    }

    static void give(ServerPlayer p, int slot, Item item, int n) {
        p.getInventory().setItem(slot, new ItemStack(item, n));
    }

    static void fill(Container c, int slot, Item item, int n) {
        c.setItem(slot, new ItemStack(item, n));
    }
}
