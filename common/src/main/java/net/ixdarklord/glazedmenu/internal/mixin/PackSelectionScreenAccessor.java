package net.ixdarklord.glazedmenu.internal.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.nio.file.Path;
import java.util.List;

// What the game's pack selection screen chooses among and where its packs are kept, and its own copying of dropped
// files, for Glazed Menu's screen to stand in its place (GamePacksScreen).
@Mixin(PackSelectionScreen.class)
public interface PackSelectionScreenAccessor {
    @Accessor("model")
    PackSelectionModel glazedmenu$model();

    @Accessor("packDir")
    Path glazedmenu$packDir();

    @Invoker("copyPacks")
    static void glazedmenu$copyPacks(Minecraft minecraft, List<Path> packs, Path outDir) {
        throw new AssertionError();
    }
}
