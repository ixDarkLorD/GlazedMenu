package net.ixdarklord.glazedmenu.internal.mixin;

import net.ixdarklord.glazedmenu.internal.game.GameLanguageScreen;
import net.ixdarklord.glazedmenu.internal.game.GameOptionsScreen;
import net.ixdarklord.glazedmenu.internal.game.GamePacksScreen;
import net.ixdarklord.glazedmenu.internal.game.GameRulesSource;
import net.ixdarklord.glazedmenu.internal.game.GameShadersScreen;
import net.ixdarklord.glazedmenu.internal.game.GlazedAdvancementsScreen;
import net.ixdarklord.glazedmenu.internal.game.GlazedCreateWorldScreen;
import net.ixdarklord.glazedmenu.internal.game.GlazedChatScreen;
import net.ixdarklord.glazedmenu.internal.game.GlazedDeathScreen;
import net.ixdarklord.glazedmenu.internal.game.GlazedDirectConnectScreen;
import net.ixdarklord.glazedmenu.internal.game.GlazedDisconnectedScreen;
import net.ixdarklord.glazedmenu.internal.game.GlazedEditServerScreen;
import net.ixdarklord.glazedmenu.internal.game.GlazedEditWorldScreen;
import net.ixdarklord.glazedmenu.internal.game.GlazedFriendsScreen;
import net.ixdarklord.glazedmenu.internal.game.GlazedPauseScreen;
import net.ixdarklord.glazedmenu.internal.game.GlazedServersScreen;
import net.ixdarklord.glazedmenu.internal.game.GlazedSocialScreen;
import net.ixdarklord.glazedmenu.internal.game.GlazedStatsScreen;
import net.ixdarklord.glazedmenu.internal.game.GlazedTitleScreen;
import net.ixdarklord.glazedmenu.internal.game.GlazedWarningScreen;
import net.ixdarklord.glazedmenu.internal.game.GlazedWorldOptionsScreen;
import net.ixdarklord.glazedmenu.internal.game.GlazedWorldsScreen;
import net.ixdarklord.glazedmenu.internal.game.LoadingLook;
import net.ixdarklord.glazedmenu.internal.keys.GlazedKeysScreen;
import net.ixdarklord.glazedmenu.internal.modlist.ModListHooks;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

// Opens Glazed Menu's title screen in place of the game's, its mod list wherever Mod Menu's or NeoForge's would open, and its screens for the game's options,
// controls, key binds, languages, packs, a new world's game rules, the pause menu, the lists of worlds and servers, the advancements and the statistics, and for Iris's shader packs,
// and its popups for the game's friends list and its Open to LAN screen (screens are set on the Gui since 26.2).
@Mixin(Gui.class)
public abstract class MinecraftMixin {
    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen glazedmenu$replaceScreens(Screen screen) {
        return GlazedFriendsScreen.replace(GlazedWorldOptionsScreen.replace(GlazedSocialScreen.replace(GlazedWarningScreen.replace(GlazedChatScreen.replace(GlazedDeathScreen.replace(GlazedDisconnectedScreen.replace(GlazedDirectConnectScreen.replace(GlazedEditServerScreen.replace(GlazedEditWorldScreen.replace(GlazedCreateWorldScreen.replace(GlazedTitleScreen.replace(GlazedStatsScreen.replace(GlazedAdvancementsScreen.replace(GlazedServersScreen.replace(GlazedWorldsScreen.replace(GameShadersScreen.replace(GlazedPauseScreen.replace(GamePacksScreen.replace(GameLanguageScreen.replace(GameRulesSource.replace(GameOptionsScreen.replace(GlazedKeysScreen.replace(ModListHooks.replace(LoadingLook.watch(screen)))))))))))))))))))))))));
    }
}
