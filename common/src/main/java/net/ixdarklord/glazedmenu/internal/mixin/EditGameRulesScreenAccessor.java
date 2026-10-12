package net.ixdarklord.glazedmenu.internal.mixin;

import net.minecraft.client.gui.screens.worldselection.EditGameRulesScreen;
import net.minecraft.world.level.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Optional;
import java.util.function.Consumer;

// The rules the game's screen of a new world's game rules edits, and what it hands them back to, for Glazed Menu's
// config screen to edit them in its place (GameRulesSource).
@Mixin(EditGameRulesScreen.class)
public interface EditGameRulesScreenAccessor {
    @Accessor("gameRules")
    GameRules glazedmenu$gameRules();

    @Accessor("exitCallback")
    Consumer<Optional<GameRules>> glazedmenu$exitCallback();
}
