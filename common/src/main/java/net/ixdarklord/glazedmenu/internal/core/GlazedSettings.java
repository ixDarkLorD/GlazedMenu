package net.ixdarklord.glazedmenu.internal.core;

import net.ixdarklord.glazedmenu.internal.style.GlazedBrand;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.ixdarklord.glazedmenu.api.config.ConfigScope;
import net.ixdarklord.glazedmenu.api.config.ConfigTheme;
import net.ixdarklord.glazedmenu.api.config.type.ConfigTypes;
import net.ixdarklord.glazedmenu.internal.source.Access;
import net.ixdarklord.glazedmenu.internal.source.external.ExternalConfig;
import net.ixdarklord.glazedmenu.internal.source.external.ExternalConfigBuilder;
import net.ixdarklord.glazedmenu.internal.source.external.ExternalSource;
import net.ixdarklord.glazedmenu.internal.source.external.ExternalValue;
import net.minecraft.client.GraphicsPreset;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Glazed Menu's own settings, in {@code config/glazedmenu.json}: the mod list's, and the player's choices for every styled
 * screen's look. They're edited in Glazed Menu's screens like any config (Glazed Menu's card in the config list).
 */
public final class GlazedSettings {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Values values = new Values();

    private GlazedSettings() {}

    /** How the mod list lays mods out. */
    public enum View {
        /** Tiles with large icons, a few to a row. */
        GRID,
        /** One mod per row, with its version and description. */
        LIST
    }

    /** How Glazed Menu's pause screen is laid out. */
    public enum PauseLayout {
        /** A bar across the top and a dock of tiles along the bottom, the world open between them. */
        DOCK,
        /** A frame in the middle of the screen, its buttons as cards. */
        CARDS,
        /** A panel down the left side, its buttons as a list, the world open beside it. */
        SIDEBAR
    }

    /** How Glazed Menu's title screen is laid out. */
    public enum TitleLayout {
        /** The logo at the top, and a shelf of cards along the bottom over a bar of the rest. */
        SHELF,
        /** A panel down the left side: the logo, then the menu as a list; the panorama open beside it. */
        SIDEBAR
    }

    // The file's contents; Gson fills them, keeping the defaults for anything missing.
    private static final class Values {
        boolean replaceModList = true;
        boolean modsButton = true;
        boolean showLibraries = false;
        boolean checkUpdates = true;
        boolean checkModrinth = true;
        View view = View.LIST;
        ConfigTheme.Mode themeMode = ConfigTheme.Mode.DARK;
        GlazedBrand.Scheme colorScheme = GlazedBrand.Scheme.GLAZED;
        int backgroundOpacity = 100;
        int textureOpacity = 100;
        int panelOpacity = 100;
        int starOpacity = 65;
        boolean themeEffects = true;
        boolean transitions = true;
        boolean replaceMezzConfigScreens = false;
        boolean replaceFtbLibraryScreens = false;
        boolean replaceKeyBinds = true;
        boolean gameOptions = true;
        boolean replaceControls = true;
        boolean replaceOptions = true;
        boolean replaceGameRules = true;
        boolean replaceLanguage = true;
        boolean replacePacks = true;
        boolean replaceShaders = true;
        boolean replacePause = true;
        PauseLayout pauseLayout = PauseLayout.DOCK;
        boolean replaceWorlds = true;
        boolean replaceServers = true;
        boolean replaceAdvancements = true;
        boolean replaceStats = true;
        boolean replaceTitle = true;
        TitleLayout titleLayout = TitleLayout.SHELF;
        boolean replaceCreateWorld = true;
        boolean replaceEditWorld = true;
        boolean replaceEditServer = true;
        boolean replaceDirectConnect = true;
        boolean replaceDisconnected = true;
        boolean replaceDeath = true;
        boolean replaceWarnings = true;
        boolean replaceSocial = true;
        boolean replaceChat = true;
        boolean chatTyping = true;
        boolean chatHideTyping = false;
        boolean chatShowcase = true;
        boolean deathSounds = true;
        boolean replaceLoading = true;
        boolean welcomePopup = true;
    }

    private static Path file() {
        return GlazedPlatform.get().configDir().resolve("glazedmenu.json");
    }

    /** Reads the file (writing it with the defaults the first time). */
    static void init() {
        Path file = file();
        if (Files.exists(file)) {
            try {
                Values read = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), Values.class);
                if (read != null) values = read;
                if (values.view == null) values.view = View.LIST;
                if (values.themeMode == null) values.themeMode = ConfigTheme.Mode.DARK;
                if (values.colorScheme == null) values.colorScheme = GlazedBrand.Scheme.GLAZED;
                if (values.pauseLayout == null) values.pauseLayout = PauseLayout.DOCK;
                if (values.titleLayout == null) values.titleLayout = TitleLayout.SHELF;
            } catch (IOException | JsonParseException e) {
                GlazedMenu.LOGGER.warn("Couldn't read {}; using the defaults", file, e);
            }
        }
        save();
    }

    public static void save() {
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), GSON.toJson(values), StandardCharsets.UTF_8);
        } catch (IOException e) {
            GlazedMenu.LOGGER.error("Couldn't save {}", file(), e);
        }
    }

    // --- The mod list ---

    public static boolean replaceModList() {
        return values.replaceModList;
    }

    public static boolean modsButton() {
        return values.modsButton;
    }

    public static boolean showLibraries() {
        return values.showLibraries;
    }

    /** Whether Glazed Menu looks for newer versions of the mods (in the update files they name). */
    public static boolean checkUpdates() {
        return values.checkUpdates;
    }

    /** Whether the update check also asks Modrinth, by the hashes of the mods' files. */
    public static boolean checkModrinth() {
        return values.checkUpdates && values.checkModrinth;
    }

    public static View view() {
        return values.view;
    }

    /** Changes the view and saves it. */
    public static void setView(View view) {
        values.view = view;
        save();
    }

    // --- The screens' look ---

    public static ConfigTheme.Mode themeMode() {
        return values.themeMode;
    }

    /** The colors of Glazed Menu's look: its mod list and every config screen without a theme of its own. */
    public static GlazedBrand.Scheme colorScheme() {
        return values.colorScheme;
    }

    /** Switches between dark and light, and saves the choice. */
    public static void toggleThemeMode() {
        values.themeMode = values.themeMode == ConfigTheme.Mode.DARK ? ConfigTheme.Mode.LIGHT : ConfigTheme.Mode.DARK;
        save();
    }

    /** A theme's backdrop opacity, scaled by the player's choice. */
    public static float backgroundOpacity(float themeOpacity) {
        return Math.clamp(themeOpacity * values.backgroundOpacity / 100.0F, 0, 1);
    }

    /** A theme's texture opacity, scaled by the player's choice. */
    public static float textureOpacity(float themeOpacity) {
        return Math.clamp(themeOpacity * values.textureOpacity / 100.0F, 0, 1);
    }

    /** A panel's or bar's alpha, scaled by the player's panel opacity (100 keeps the theme's). */
    public static int panelAlpha(int themeAlpha) {
        return Math.clamp(Math.round(themeAlpha * values.panelOpacity / 100.0F), 0, 255);
    }

    /** Whether the screens draw their themes' effects: the player's choice, but never on Fast graphics. */
    public static boolean themeEffects() {
        return values.themeEffects && !fastGraphics();
    }

    /** Whether pages animate into place: the player's choice, but never on Fast graphics. */
    public static boolean transitions() {
        return values.transitions && !fastGraphics();
    }

    // --- Other config systems' screens ---

    /**
     * Whether Glazed Menu's screens replace MezzConfig GUI's for MezzConfig's configs (JEI's). Without MezzConfig GUI, Glazed Menu's
     * open either way, as nothing else can.
     */
    public static boolean replaceMezzConfigScreens() {
        return values.replaceMezzConfigScreens;
    }

    /** Whether Glazed Menu's screens replace FTB Library's config editor for FTB mods' configs. */
    public static boolean replaceFtbLibraryScreens() {
        return values.replaceFtbLibraryScreens;
    }

    /** Whether the game's own options are listed as a config, among the mods'. */
    public static boolean gameOptions() {
        return values.gameOptions;
    }

    /** Whether Glazed Menu's packs screen opens where the game's own would (resource packs, and a new world's data packs). */
    public static boolean replacePacks() {
        return values.replacePacks;
    }

    /** Whether Glazed Menu's shader packs screen opens where Iris's own would. */
    public static boolean replaceShaders() {
        return values.replaceShaders;
    }

    /** Whether Glazed Menu's language screen opens where the game's own would. */
    public static boolean replaceLanguage() {
        return values.replaceLanguage;
    }

    /** Whether a new world's game rules open in Glazed Menu's config screen. */
    public static boolean replaceGameRules() {
        return values.replaceGameRules;
    }

    /** Whether the game's options screen opens as Glazed Menu's hub of its options (which it needs listed). */
    public static boolean replaceOptions() {
        return values.replaceOptions && values.gameOptions;
    }

    /** Whether the game's controls screen opens as Glazed Menu's map of the keyboard. */
    public static boolean replaceControls() {
        return values.replaceControls;
    }

    /** Whether Glazed Menu's key binds screen opens where the game's own would. */
    public static boolean replaceKeyBinds() {
        return values.replaceKeyBinds;
    }

    /** Whether Glazed Menu's pause screen opens where the game's own would. */
    public static boolean replacePause() {
        return values.replacePause;
    }

    public static PauseLayout pauseLayout() {
        return values.pauseLayout;
    }

    /** Whether Glazed Menu's list of worlds opens where the game's singleplayer screen would. */
    public static boolean replaceWorlds() {
        return values.replaceWorlds;
    }

    /** Whether Glazed Menu's list of servers opens where the game's multiplayer screen would. */
    public static boolean replaceServers() {
        return values.replaceServers;
    }

    /** Whether Glazed Menu's advancements screen opens where the game's own would. */
    public static boolean replaceAdvancements() {
        return values.replaceAdvancements;
    }

    /** Whether Glazed Menu's statistics screen opens where the game's own would. */
    public static boolean replaceStats() {
        return values.replaceStats;
    }

    /** Whether the welcome is shown over the title screen the next time the game starts. */
    public static boolean welcomePopup() {
        return values.welcomePopup;
    }

    /** The welcome was read: it's turned off, and stays off until the player turns it on again. */
    public static void dismissWelcome() {
        values.welcomePopup = false;
        save();
    }

    /** Whether the screens shown while a world or server is joined or left are drawn in Glazed Menu's look. */
    public static boolean replaceLoading() {
        return values.replaceLoading;
    }

    /** How bright the falling stars behind the panels are, in percent. */
    public static int starOpacity() {
        return Math.clamp(values.starOpacity, 0, 100);
    }

    /** Whether Glazed Menu's chat screen opens where the game's own would. */
    public static boolean replaceChat() {
        return values.replaceChat;
    }

    /** Whether the chat says who else is typing, and tells the others when the player is (on a server with Glazed Menu). */
    public static boolean chatTyping() {
        return values.chatTyping;
    }

    public static void setChatTyping(boolean on) {
        values.chatTyping = on;
        save();
    }

    /** Whether the others aren't told the player is typing, while who else is typing is still shown. */
    public static boolean chatHideTyping() {
        return values.chatHideTyping;
    }

    public static void setChatHideTyping(boolean on) {
        values.chatHideTyping = on;
        save();
    }

    /** Whether the chat has the button showing the item held to everyone. */
    public static boolean chatShowcase() {
        return values.chatShowcase;
    }

    public static void setChatShowcase(boolean on) {
        values.chatShowcase = on;
        save();
    }

    /** Whether Glazed Menu's popup opens where the game's warnings about a world (experimental settings, a backup first) would. */
    public static boolean replaceWarnings() {
        return values.replaceWarnings;
    }

    /** Whether Glazed Menu's popup opens where the game's Social Interactions screen would. */
    public static boolean replaceSocial() {
        return values.replaceSocial;
    }

    /** Whether Glazed Menu's death screen opens where the game's own would. */
    public static boolean replaceDeath() {
        return values.replaceDeath;
    }

    /** Whether Glazed Menu's death screen plays a sound as each of its parts arrives. */
    public static boolean deathSounds() {
        return values.deathSounds;
    }

    /** Whether Glazed Menu's card for a connection that ended opens where the game's disconnected screen would. */
    public static boolean replaceDisconnected() {
        return values.replaceDisconnected;
    }

    /** Whether Glazed Menu's popup for connecting to an address opens where the game's Direct Connection screen would. */
    public static boolean replaceDirectConnect() {
        return values.replaceDirectConnect;
    }

    /** Whether Glazed Menu's screen for a server's name and address opens where the game's own would. */
    public static boolean replaceEditServer() {
        return values.replaceEditServer;
    }

    /** Whether Glazed Menu's screen for editing a world opens where the game's own would. */
    public static boolean replaceEditWorld() {
        return values.replaceEditWorld;
    }

    /** Whether Glazed Menu's screen for making a world opens where the game's own would. */
    public static boolean replaceCreateWorld() {
        return values.replaceCreateWorld;
    }

    public static TitleLayout titleLayout() {
        return values.titleLayout;
    }

    /** Whether Glazed Menu's title screen opens where the game's own would. */
    public static boolean replaceTitle() {
        return values.replaceTitle;
    }

    // The Fast graphics preset asks for the cheapest look, so it turns the screens' animations off.
    private static boolean fastGraphics() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null && minecraft.options.graphicsPreset().get() == GraphicsPreset.FAST;
    }

    // --- In Glazed Menu's screens ---

    /** The settings as a config for Glazed Menu's screens. */
    public static final ExternalSource SOURCE = new ExternalSource() {
        @Override
        public String name() {
            return "Glazed Menu";
        }

        @Override
        protected List<ExternalConfig> discover() {
            String prefix = "config.glazedmenu.client.";
            ExternalConfigBuilder builder = new ExternalConfigBuilder(GlazedMenu.MOD_ID, "client", ConfigScope.CLIENT)
                    .title(Component.translatableWithFallback(prefix + "title", "Glazed Menu"))
                    .file("glazedmenu.json", file());
            builder.push("general", prefix + "general", null, List.of("Glazed Menu itself"));
            bool(builder, prefix + "general.", "welcomePopup",
                    "Shows the welcome over the title screen the next time the game starts. It turns itself off once you press Continue.",
                    () -> values.welcomePopup, value -> values.welcomePopup = value);
            builder.pop();
            builder.push("modList", prefix + "modList", null, List.of("Glazed Menu's mod list"));
            bool(builder, prefix + "modList.", "replaceModList", "Opens Glazed Menu's mod list where Mod Menu's or NeoForge's would open.",
                    () -> values.replaceModList, value -> values.replaceModList = value);
            bool(builder, prefix + "modList.", "modsButton", "Adds a Mods button to the title and pause screens when they have none.",
                    () -> values.modsButton, value -> values.modsButton = value);
            bool(builder, prefix + "modList.", "showLibraries", false, "Lists the loader's own parts (Java, the loader, MixinExtras) among the other mods too, not only under Libraries.",
                    () -> values.showLibraries, value -> values.showLibraries = value);
            bool(builder, prefix + "modList.", "checkUpdates",
                    "Looks for newer versions of your mods in the update files they name, once the game has loaded, and marks the mods that have one.",
                    () -> values.checkUpdates, value -> values.checkUpdates = value);
            bool(builder, prefix + "modList.", "checkModrinth",
                    "With Check for Updates on, also asks Modrinth about the mods that name no update file. Only the hashes of your mod files, the game version and the loader are sent.",
                    () -> values.checkModrinth, value -> values.checkModrinth = value);
            builder.value("view", ConfigTypes.enumOf(View.class), View.LIST, binding(() -> values.view, value -> values.view = value))
                    .translation(prefix + "modList.view", null).comment(List.of("Shows the mods as a grid of tiles or as a list.")).add();
            builder.pop();
            builder.push("look", prefix + "look", null, List.of("Every styled screen's look, for every mod"));
            builder.value("themeMode", ConfigTypes.enumOf(ConfigTheme.Mode.class), ConfigTheme.Mode.DARK,
                            binding(() -> values.themeMode, value -> values.themeMode = value))
                    .translation(prefix + "look.themeMode", null)
                    .comment(List.of("Whether the screens use their dark or light colors (the sun/moon button in their top bar).")).add();
            builder.value("colorScheme", ConfigTypes.enumOf(GlazedBrand.Scheme.class), GlazedBrand.Scheme.GLAZED,
                            binding(() -> values.colorScheme, value -> values.colorScheme = value))
                    .translation(prefix + "look.colorScheme", null)
                    .comment(List.of("The colors of the mod list and of every config screen without a theme of its own: Glazed Menu's own, or another scheme.")).add();
            builder.value("backgroundOpacity", ConfigTypes.intRange(0, 200).withSlider(true), 100,
                            binding(() -> values.backgroundOpacity, value -> values.backgroundOpacity = value))
                    .translation(prefix + "look.backgroundOpacity", null)
                    .comment(List.of("Scales how strongly each mod's backdrop color covers the background, in percent: 0 removes it, 200 doubles it.")).add();
            builder.value("textureOpacity", ConfigTypes.intRange(0, 100).withSlider(true), 100,
                            binding(() -> values.textureOpacity, value -> values.textureOpacity = value))
                    .translation(prefix + "look.textureOpacity", null)
                    .comment(List.of("Scales the opacity of each mod's background texture, in percent; below 100 the panorama or world shows through it.")).add();
            builder.value("panelOpacity", ConfigTypes.intRange(10, 100).withSlider(true), 100,
                            binding(() -> values.panelOpacity, value -> values.panelOpacity = value))
                    .translation(prefix + "look.panelOpacity", null)
                    .comment(List.of("How solid the screens' panels and bars are, in percent: lower lets more of the background show through them.")).add();
            builder.value("starOpacity", ConfigTypes.intRange(0, 100).withSlider(true), 65,
                            binding(() -> values.starOpacity, value -> values.starOpacity = value))
                    .translation(prefix + "look.starOpacity", null)
                    .comment(List.of("How bright the small falling stars behind the panels are, in percent: 0 hides them.")).add();
            bool(builder, prefix + "look.", "themeEffects", "Each mod's animated effects: by default soft glows and small falling stars behind the panels.",
                    () -> values.themeEffects, value -> values.themeEffects = value);
            bool(builder, prefix + "look.", "transitions", "Pages fading in and out as you open a screen or go back.",
                    () -> values.transitions, value -> values.transitions = value);
            builder.pop();
            builder.push("otherScreens", prefix + "otherScreens", null, List.of("Config systems with screens of their own"));
            bool(builder, prefix + "otherScreens.", "replaceMezzConfigScreens", false,
                    "Turn on to replace MezzConfig GUI's config screens (like JEI's) with Glazed Menu's. Without MezzConfig GUI, Glazed Menu's open either way.",
                    () -> values.replaceMezzConfigScreens, value -> values.replaceMezzConfigScreens = value);
            bool(builder, prefix + "otherScreens.", "replaceFtbLibraryScreens", false,
                    "Turn on to replace FTB Library's config editor (FTB mods' settings, like FTB Ultimine's) with Glazed Menu's screens.",
                    () -> values.replaceFtbLibraryScreens, value -> values.replaceFtbLibraryScreens = value);
            builder.pop();
            builder.push("game", prefix + "game", null, List.of("The game's own screens"));
            bool(builder, prefix + "game.", "replaceKeyBinds",
                    "Opens Glazed Menu's key binds screen where the game's would open: categories, search, and the key binds in conflict or changed.",
                    () -> values.replaceKeyBinds, value -> values.replaceKeyBinds = value);
            bool(builder, prefix + "game.", "replaceOptions",
                    "Opens the game's Options screen in Glazed Menu's look: a ribbon of tabs for the categories of its options, each tab's settings as cards to change right there. The game's own screen stays under More. It needs the game's options listed.",
                    () -> values.replaceOptions, value -> values.replaceOptions = value);
            bool(builder, prefix + "game.", "replaceControls",
                    "Opens the game's Controls screen as a map of the keyboard (and of the mouse): keys tinted where something is bound, red where key binds are in conflict, with each key's binds and the control settings under it.",
                    () -> values.replaceControls, value -> values.replaceControls = value);
            bool(builder, prefix + "game.", "replacePacks",
                    "Opens Glazed Menu's packs screen where the game's would open, for resource packs and for a new world's data packs: the packs in use as a stack to reorder, beside the packs to choose from as tiles, with a search.",
                    () -> values.replacePacks, value -> values.replacePacks = value);
            bool(builder, prefix + "game.", "replaceShaders",
                    "With Iris: opens Glazed Menu's shader packs screen where Iris's would open. A pack's own settings stay in Iris's screen, a button away.",
                    () -> values.replaceShaders, value -> values.replaceShaders = value);
            bool(builder, prefix + "game.", "replaceLanguage",
                    "Opens Glazed Menu's language screen where the game's would open: a search by name, region or code, with the language in use marked.",
                    () -> values.replaceLanguage, value -> values.replaceLanguage = value);
            bool(builder, prefix + "game.", "replaceGameRules",
                    "Opens a new world's game rules in Glazed Menu's config screen: categories, search, and the rules changed from their defaults marked.",
                    () -> values.replaceGameRules, value -> values.replaceGameRules = value);
            bool(builder, prefix + "game.", "replaceTitle",
                    "Opens Glazed Menu's title screen where the game's would open: the logo at the top left, and along the bottom Continue (the world played last) beside Singleplayer, Multiplayer and Realms, over a bar of the options, the mods, the language, accessibility and Quit.",
                    () -> values.replaceTitle, value -> values.replaceTitle = value);
            builder.value("titleLayout", ConfigTypes.enumOf(TitleLayout.class), TitleLayout.SHELF, binding(() -> values.titleLayout, value -> values.titleLayout = value))
                    .translation(prefix + "game.titleLayout", null)
                    .comment(List.of("How Glazed Menu's title screen is laid out: Shelf (the logo at the top, and cards along the bottom over a bar of the rest) or Sidebar (the logo and the menu as a list down the left side, the panorama open beside it).")).add();
            bool(builder, prefix + "game.", "replacePause",
                    "Opens Glazed Menu's pause screen where the game's would open: Back to Game, the screens a pause leads to as tiles, a few quick settings, and Save and Quit set apart.",
                    () -> values.replacePause, value -> values.replacePause = value);
            builder.value("pauseLayout", ConfigTypes.enumOf(PauseLayout.class), PauseLayout.DOCK, binding(() -> values.pauseLayout, value -> values.pauseLayout = value))
                    .translation(prefix + "game.pauseLayout", null)
                    .comment(List.of("How Glazed Menu's pause screen is laid out: Dock (a bar across the top and tiles along the bottom, the world open between), Cards (a frame in the middle) or Sidebar (a list down the left side).")).add();
            bool(builder, prefix + "game.", "replaceWorlds",
                    "Opens Glazed Menu's list of worlds where the game's singleplayer screen would open: a search, sorting, and the chosen world's details beside the list.",
                    () -> values.replaceWorlds, value -> values.replaceWorlds = value);
            bool(builder, prefix + "game.", "replaceServers",
                    "Opens Glazed Menu's list of servers where the game's multiplayer screen would open: a search, the games on your network apart from the servers, and the chosen server's details beside the list.",
                    () -> values.replaceServers, value -> values.replaceServers = value);
            bool(builder, prefix + "game.", "replaceCreateWorld",
                    "Opens Glazed Menu's screen for making a world where the game's would open: its three tabs as a ribbon, the game mode as cards, and what the world will be made with beside them. The game's own screen stays a button away.",
                    () -> values.replaceCreateWorld, value -> values.replaceCreateWorld = value);
            bool(builder, prefix + "game.", "replaceEditWorld",
                    "Opens Glazed Menu's screen for editing a world where the game's would open: the world's name, its files, backups and upkeep as cards, and the world itself beside them. The game's own screen stays a button away.",
                    () -> values.replaceEditWorld, value -> values.replaceEditWorld = value);
            bool(builder, prefix + "game.", "replaceEditServer",
                    "Opens Glazed Menu's screen for adding or editing a server where the game's would open: the server's name, address and resource packs' choice, beside the server as it answers at the address typed. The game's own screen stays a button away.",
                    () -> values.replaceEditServer, value -> values.replaceEditServer = value);
            bool(builder, prefix + "game.", "replaceLoading",
                    "Draws the screens shown while a world or a server is joined or left (reading the world, connecting, loading the terrain, saving) in Glazed Menu's look: the world's icon in a ring of the steps, with each step named and timed under it, over the game's panorama.",
                    () -> values.replaceLoading, value -> values.replaceLoading = value);
            bool(builder, prefix + "game.", "replaceDirectConnect",
                    "Opens Glazed Menu's popup where the game's Direct Connection screen would open: the address, and the server at it as it answers, before you join.",
                    () -> values.replaceDirectConnect, value -> values.replaceDirectConnect = value);
            bool(builder, prefix + "game.", "replaceChat",
                    "Opens Glazed Menu's chat where the game's would open: every message on a row of its own with the sender's face, tabs for players' messages, the system's and the ones naming you, buttons for things done in one click, and the line in a bar at the bottom.",
                    () -> values.replaceChat, value -> values.replaceChat = value);
            bool(builder, prefix + "game.", "chatTyping",
                    "Shows in the chat who else is typing, and tells the others when you are. Only on a server that has Glazed Menu too (your own worlds always do).",
                    () -> values.chatTyping, value -> values.chatTyping = value);
            bool(builder, prefix + "game.", "chatHideTyping",
                    "Doesn't tell the others when you're typing; who else is typing is still shown to you.",
                    () -> values.chatHideTyping, value -> values.chatHideTyping = value);
            bool(builder, prefix + "game.", "chatShowcase",
                    "Adds a button to the chat that shows the item in your hand to everyone: its name in the chat, the item itself on its hover (on a server with Glazed Menu; elsewhere its name is written into your message).",
                    () -> values.chatShowcase, value -> values.chatShowcase = value);
            bool(builder, prefix + "game.", "replaceWarnings",
                    "Opens Glazed Menu's popup where the game's warnings about a world would open: that it uses experimental settings, and the question of a backup before one is opened that might not open well.",
                    () -> values.replaceWarnings, value -> values.replaceWarnings = value);
            bool(builder, prefix + "game.", "replaceSocial",
                    "Opens Glazed Menu's popup where the game's Social Interactions screen would open: the players with their faces and what they said last, tabs for the hidden and the blocked, a search, and buttons to write to a player, to hide their messages and to report them.",
                    () -> values.replaceSocial, value -> values.replaceSocial = value);
            bool(builder, prefix + "game.", "replaceDeath",
                    "Opens Glazed Menu's death screen where the game's would open: a band drawn across the reddened world with how you died and the ways on, and under it your score, the level lost, how long you lasted and where you died.",
                    () -> values.replaceDeath, value -> values.replaceDeath = value);
            bool(builder, prefix + "game.", "deathSounds",
                    "Plays a sound as each part of Glazed Menu's death screen arrives, and one as its buttons can be pressed.",
                    () -> values.deathSounds, value -> values.deathSounds = value);
            bool(builder, prefix + "game.", "replaceDisconnected",
                    "Opens Glazed Menu's card where the game's disconnected screen would open: what happened and why, the server as it answers now, how long you played, and buttons to copy the reason and to reconnect.",
                    () -> values.replaceDisconnected, value -> values.replaceDisconnected = value);
            bool(builder, prefix + "game.", "replaceAdvancements",
                    "Opens Glazed Menu's advancements screen where the game's would open: its tabs as a ribbon, the advancements as a list with their steps, a search, and the game's tree a button away.",
                    () -> values.replaceAdvancements, value -> values.replaceAdvancements = value);
            bool(builder, prefix + "game.", "replaceStats",
                    "Opens Glazed Menu's statistics screen where the game's would open: tabs for general numbers, blocks, items and mobs, each a table sorted by the column you click, with a search.",
                    () -> values.replaceStats, value -> values.replaceStats = value);
            bool(builder, prefix + "game.", "gameOptions",
                    "Lists the game's own options as a config among the mods', so the search, the changed settings and profiles cover them too. The game's options screens stay as they are.",
                    () -> values.gameOptions, value -> values.gameOptions = value);
            builder.pop();
            return List.of(builder.build(() -> Access.LOCAL, GlazedSettings::save));
        }
    };

    private static void bool(ExternalConfigBuilder builder, String prefix, String key, String comment, Supplier<Boolean> get, Consumer<Boolean> set) {
        bool(builder, prefix, key, true, comment, get, set);
    }

    private static void bool(ExternalConfigBuilder builder, String prefix, String key, boolean defaultValue, String comment,
                             Supplier<Boolean> get, Consumer<Boolean> set) {
        builder.value(key, ConfigTypes.BOOLEAN, defaultValue, binding(get, set)).translation(prefix + key, null).comment(List.of(comment)).add();
    }

    private static <T> ExternalValue.Binding<T> binding(Supplier<T> get, Consumer<T> set) {
        return new ExternalValue.Binding<>() {
            @Override
            public T get() {
                return get.get();
            }

            @Override
            public void set(T value) {
                set.accept(value);
            }
        };
    }
}
