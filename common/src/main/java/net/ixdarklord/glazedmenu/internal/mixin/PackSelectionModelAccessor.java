package net.ixdarklord.glazedmenu.internal.mixin;

import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import net.minecraft.server.packs.repository.PackRepository;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.function.Consumer;

// The packs the game's pack selection chooses among and where the choice is handed, for Glazed Menu's screen to choose
// among the same and hand its choice to the same place (GamePacksScreen).
@Mixin(PackSelectionModel.class)
public interface PackSelectionModelAccessor {
    @Accessor("repository")
    PackRepository glazedmenu$repository();

    @Accessor("output")
    Consumer<PackRepository> glazedmenu$output();
}
