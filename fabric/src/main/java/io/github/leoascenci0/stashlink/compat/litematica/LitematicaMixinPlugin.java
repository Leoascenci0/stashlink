package io.github.leoascenci0.stashlink.compat.litematica;

import io.github.leoascenci0.stashlink.Constants;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

import java.util.List;
import java.util.Set;

/**
 * Liga os mixins do Litematica só se o Litematica estiver instalado. É isto que torna a dependência
 * <i>opcional</i>: sem o mod, esta configuração de mixin inteira é ignorada.
 *
 * <p>Com o Litematica instalado, confere antes se a função que o {@code InventoryUtilsMixin} enxerta ainda existe
 * com a mesma assinatura. Sem isto, uma versão nova do Litematica que mudasse a função desligava a integração em
 * silêncio ({@code require = 0}): nada travava, mas o Easy Place parava de puxar do armazenamento sem explicação.
 * Agora o log diz se a integração ligou ou por que não ligou.
 */
public final class LitematicaMixinPlugin implements IMixinConfigPlugin {
    private static final String HOOK_NAME = "schematicWorldPickBlock";
    /** Conferido com {@code javap} no Litematica 0.27.14 e 0.29.1. Mudou numa versão nova → atualizar o mixin junto. */
    private static final String HOOK_DESC = "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/core/BlockPos;"
            + "Lnet/minecraft/world/level/Level;Lnet/minecraft/client/Minecraft;)V";

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!FabricLoader.getInstance().isModLoaded("litematica")) {
            return false;
        }
        String version = FabricLoader.getInstance().getModContainer("litematica")
                .map(mod -> mod.getMetadata().getVersion().getFriendlyString())
                .orElse("?");
        ClassNode target;
        try {
            // Lê só os bytes da classe (antes dos mixins); não carrega a classe nem roda o Litematica.
            target = MixinService.getService().getBytecodeProvider().getClassNode(targetClassName);
        } catch (Exception e) {
            // Não deu para conferir: aplica como antes. Com require = 0 e "required": false, o pior caso é a
            // integração não funcionar, nunca o jogo travar.
            Constants.LOG.warn("Litematica {}: não consegui conferir {}; aplicando a integração mesmo assim",
                    version, targetClassName, e);
            return true;
        }
        if (!hasHook(target)) {
            Constants.LOG.warn("Litematica {}: {}.{} mudou ou sumiu. A integração com o Litematica (Easy Place e pick "
                    + "block puxando do armazenamento) fica desligada; o resto do StashLink funciona normalmente.",
                    version, targetClassName, HOOK_NAME);
            return false;
        }
        Constants.LOG.info("Integração com o Litematica {} ligada", version);
        return true;
    }

    /** A classe tem a função que o mixin enxerta, com a assinatura que ele espera. */
    private static boolean hasHook(ClassNode target) {
        for (MethodNode method : target.methods) {
            if (HOOK_NAME.equals(method.name) && HOOK_DESC.equals(method.desc)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
