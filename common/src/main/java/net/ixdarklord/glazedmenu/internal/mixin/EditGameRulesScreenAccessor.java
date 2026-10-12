package net.ixdarklord.glazedmenu.internal.mixin;

import net.minecraft.client.gui.screens.worldselection.EditGameRulesScreen;
import net.minecraft.world.level.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Optional;
import java.util.function.Consumer;

// The rules the game's screen is editing and where it hands them back, which it keeps to itself.
@Mixin(EditGameRulesScreen.class)
public interface EditGameRulesScreenAccessor {
    @Accessor("gameRules")
    GameRules glazedmenu$gameRules();

    @Accessor("exitCallback")
    Consumer<Optional<GameRules>> glazedmenu$exitCallback();
}
