package net.ixdarklord.glazedmenu.internal.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.nio.file.Path;
import java.util.List;

// What Glazed Menu's packs screen takes over from the game's: the packs it chooses among, their folder, the game's own
// copying of dropped files, and the end of its watch on the folder.
@Mixin(PackSelectionScreen.class)
public interface PackSelectionScreenAccessor {
    @Accessor("model")
    PackSelectionModel glazedmenu$model();

    @Accessor("packDir")
    Path glazedmenu$packDir();

    @Invoker("closeWatcher")
    void glazedmenu$closeWatcher();

    @Invoker("copyPacks")
    static void glazedmenu$copyPacks(Minecraft minecraft, List<Path> packs, Path outDir) {
        throw new AssertionError();
    }
}
