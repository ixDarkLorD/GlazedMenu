package net.ixdarklord.glazedmenu.internal.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Adds Glazed Menu's mixins into other mods' classes only when those mods are installed, so Mixin doesn't look for their
// targets otherwise. Used by both mixin configs: the common one and Fabric's.
public final class GlazedMixinPlugin implements IMixinConfigPlugin {
    // Mixin -> the class file of its target.
    private static final Map<String, String> OPTIONAL = Map.of(
            "YaclConfigClassHandlerMixin", "dev/isxander/yacl3/config/v2/impl/ConfigClassHandlerImpl.class",
            "ModMenuMixin", "com/terraformersmc/modmenu/ModMenu.class",
            "FtbConfigManagerClientMixin", "dev/ftb/mods/ftblibrary/config/manager/ConfigManagerClient.class");
    private String mixinPackage = "";

    @Override
    public void onLoad(String mixinPackage) {
        this.mixinPackage = mixinPackage;
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        List<String> mixins = new ArrayList<>();
        boolean fabric = this.mixinPackage.endsWith(".fabric");
        OPTIONAL.forEach((mixin, target) -> {
            if (mixin.equals("ModMenuMixin") == fabric && exists(target)) mixins.add(mixin);
        });
        return mixins;
    }

    private static boolean exists(String resource) {
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        return GlazedMixinPlugin.class.getClassLoader().getResource(resource) != null || context != null && context.getResource(resource) != null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
