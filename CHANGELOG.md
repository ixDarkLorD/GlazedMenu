# Changelog
This file is for listing all the changes to this project

## v1.2 Release | 2026-10-12

### ✨ New Features
- **World waits** ("Reading world data", "Preparing for world creation", "Saving world"): the world's icon inside a ring cut into one piece for each step, the world's name and the step under way, and the steps as a line of marks with how long each took. It follows the loading screens' setting (Game Screens).
- **Time played**: Glazed Menu counts how long each world and server is played. The pause screen shows this session and the total in every layout, and the worlds and servers lists show the last session and the time played in a world's or server's details.
- **Social interactions**, in place of the game's screen: a popup with the players' faces, how fast their connection is and what they said last, tabs for the hidden and the blocked, a search, and buttons to write to a player, to hide or show their messages and to report them. Hiding and reporting are done by the game itself. It can be turned off in Glazed Menu's settings (Game Screens).
- **Friends list**, in place of the game's: a popup with your profile name to copy, tabs for your friends and the requests (each with its count), a box to ask someone to be a friend, and a row for each friend with their face, their name and whether they're playing, and for each request with Accept and Decline, or Cancel request. Asking, accepting, declining and removing are done by the game itself (a friend is removed after its own question). It can be turned off in Glazed Menu's settings (Game Screens).
- **Open to LAN**, in place of the game's screen: a popup with whether the world is open to LAN as cards, the port, and what the players who join may do (their game mode as choices, commands as a switch). A setting the game locks is grayed, with the game's reason under the mouse; nothing changes until the game's Apply Changes. It can be turned off in Glazed Menu's settings (Game Screens).

### 🛠️ Changes
- Controls screen: the keyboard map's keys grow with the window, and a key's name is drawn smaller to fit its cap instead of being cut short.
- The loading screens no longer take or show a picture of where you last were: they are over the game's panorama, as the menus are. (Pictures taken before stay in the game's folder under `glazedmenu/snapshots`; they can be deleted.)
- The falling stars behind the panels are a little fainter, and a new setting (Look, Falling Stars Opacity) says how bright they are.
- The loading of a world's terrain and the connecting to a server are drawn as the waits before them are (the world's icon in a ring of the steps), so a world's opening is one screen from its first word to the world; lesser text is brighter and has a shadow on the dark look, and the line of the LAN heading in the servers list is brighter.
- Pause screen: Advancements says how many are made of how many there are, and Statistics the mobs killed.
- Pause screen: a quick setting is applied as its slider moves (the field of view and the volume follow the drag), and a name too long for its cell goes onto two lines or is drawn smaller instead of being cut short.
- Title screen: the friends list is on it too, after Realms in the Sidebar layout and at the end of the Shelf layout's bar.
- Pause screen: opening to LAN, player reporting and the friends list each have an entry of their own, in that order; player reporting and the friends list are grayed where the game turns its own buttons off.

## v1.1 Release | 2026-10-12

### ✨ New Features
- **Edit settings from the search**: searching on the Mod Configs screen lists the matching settings of every mod with their editors, so they're changed right there. Save or Done applies them to every config at once, and a config's heading opens it filtered the same way.
- `/glazedmenu search [words]` opens that search from the chat.
- **Changed**, on the Mod Configs screen: every setting that isn't at its default, across every mod, to edit or reset right there. Copy puts the list on the clipboard as text, and `/glazedmenu changed` opens it from the chat.
- **Key binds screen**, in place of the game's: categories in a sidebar with All, Conflicts, Changed and Unbound, and each key bind in conflict marked with what else uses its key.
  - Search by name, key, category or mod; `@mod` lists one mod's key binds, `=key` the ones on exactly that key, and By Key searches for the key pressed next.
  - Sorting by category, name or key.
  - Reset and Unbind act on the key binds shown: the chosen category or filter, or what the search found.
  - On NeoForge, a key held with Ctrl, Shift or Alt binds as it does on the game's screen.
  - It can be turned off in Glazed Menu's settings (Game Screens).
- **The game's options as a config**: Minecraft's own options (video, controls, mouse, chat, sounds, accessibility...) are listed among the mods' configs, so the search, the changed settings and profiles cover them too. The game's options screens stay as they are, and the list can be turned off in Glazed Menu's settings (Game Screens).
- **Options screen**, in place of the game's: a ribbon of tabs across the top (Quick, each category of the game's options in its own color, and More), and under it the chosen tab's settings as cards in two columns, changed right there and saved with Save or Done. Quick holds the options most reached for and every option changed from its default; More holds the controls, the language, the resource packs, the skin and the game's own screen. The search lists the matching settings of every category. It can be turned off in Glazed Menu's settings (Game Screens).
- **Game rules**, when making a world: the rules open in Glazed Menu's config screen, their categories in the sidebar, with search, reset, undo, and the rules changed from their defaults marked. It can be turned off in Glazed Menu's settings (Game Screens).
- **Packs screen**, in place of the game's, for resource packs and for a new world's data packs: the packs in use as a stack, reordered by dragging or with arrows, beside the packs to choose from as tiles with their icons, a search, and a warning on packs made for another version. Files dropped on the window are copied into the packs' folder. It can be turned off in Glazed Menu's settings (Game Screens).
- **Shader packs screen** (with Iris), in place of Iris's list of packs: whether shaders are on and the chosen pack on one side, the packs in the folder as tiles on the other, with a search; shader packs dropped on the window are copied into the folder. A pack's own settings stay in Iris's screen, a button away. It can be turned off in Glazed Menu's settings (Game Screens).
- **Sodium and Iris in the options**: with Sodium, the Video tab leads with a card opening Sodium's video settings; with Iris, with one opening the shader packs. Their own video screens are left as they are.
- **Language screen**, in place of the game's: a search by a language's name, region or code, the language in use marked, and the font settings a button away. It can be turned off in Glazed Menu's settings (Game Screens).
- **Controls screen**, in place of the game's: a map of the keyboard (and of the mouse's buttons), keys tinted where something is bound and red where key binds are in conflict. Picking a key lists what's bound to it, to rebind (by pressing a key or clicking one on the map) or reset, beside the control settings; List opens every key bind as a list. It can be turned off in Glazed Menu's settings (Game Screens).
- **Title screen**, in place of the game's, with Continue: the world played last, opened at a click. It's laid out one of two ways, chosen in Glazed Menu's settings (Game Screens, Title Screen Layout):
  - Shelf (the default): the logo at the top, and along the bottom Continue beside Singleplayer, Multiplayer and Realms as cards, over a bar of the options, the mods, the language, accessibility, the credits and Quit.
  - Sidebar: a panel down the left side with the logo and the menu as a list (Singleplayer, Multiplayer and Realms, then the options, the mods, the language and accessibility) over Quit, the panorama open beside it, and Continue at the bottom right.
  - It can be turned off in Glazed Menu's settings (Game Screens).
- **Create World screen**, in place of the game's: its three tabs as a ribbon, the game mode as cards and the difficulty as a row of choices, and beside them what the world will be made with, whichever tab is open, over Create and Cancel. The game's own screen is a button away, with what was set carried over. It can be turned off in Glazed Menu's settings (Game Screens).
- **Welcome**, the first time the game runs with Glazed Menu: a popup over the title screen saying which of the game's screens it replaces, with a button to the settings that turn each back. Continue turns it off (Welcome Popup, in Glazed Menu's settings under General); turned back on, it's shown again the next time the game starts.
- **Pause screen**, in place of the game's: Back to Game, the screens a pause leads to (advancements, statistics, the options, the mods, opening to LAN or player reporting, feedback, and what a server adds) each in its own color, a few quick settings applied as they're changed, and Save and Quit set apart from the rest. It's laid out one of three ways, chosen in Glazed Menu's settings (Game Screens, Pause Screen Layout):
  - Dock (the default): a bar across the top and a dock of tiles along the bottom, the world open between them.
  - Cards: a frame in the middle of the screen, its buttons as cards.
  - Sidebar: a list down the left side, with the world's name, day, position and biome beside it.
  - It can be turned off in Glazed Menu's settings (Game Screens).
- **Singleplayer screen**, in place of the game's: the worlds as a list, searched by name or folder and sorted by when they were last played or by name, with the chosen world's details beside it: its picture, game mode and difficulty, its version, its size on disk, and what stands in the way of playing it. A star makes a world a favorite, listed first. It can be turned off in Glazed Menu's settings (Game Screens).
- **Multiplayer screen**, in place of the game's: the servers as a list with a search, the games on your network under a heading of their own, and the chosen server's details beside it: its picture and message, whether it answers and how fast, its version, its players, and what it does about resource packs. A star makes a server a favorite, listed first; Shift with the up or down arrow moves a server in the list, and F5 asks the servers again. It can be turned off in Glazed Menu's settings (Game Screens).
- **Edit World screen**, in place of the game's: the world's name, and its files, backups and upkeep as cards under headings, with the world itself (its picture, game mode, version and size) beside them, over Save and Cancel. The game's own screen is a button away. It can be turned off in Glazed Menu's settings (Game Screens).
- **Edit Server popup** (adding a server too), in place of the game's screen, over the list of servers: the server's name and address, and what's done about its resource packs as three cards, beside the server as it answers at the address typed, so a wrong address shows before it's saved. The game's own screen is a button away. It can be turned off in Glazed Menu's settings (Game Screens).
- **Direct Connection popup**, in place of the game's screen, over the list of servers: the address (the one typed last), and the server at it as it answers, before you join. It can be turned off in Glazed Menu's settings (Game Screens).
- **Chat screen**: every message on a row of its own with the sender's face, tabs for All, Players, System and Mentions, one-click buttons (show the item you hold, share your coordinates, your last commands), and the line in a bar at the bottom with a counter and Send. The game's command suggestions open over the bar in a styled popup, and the parts arrive one after another as the chat opens. On a server that has Glazed Menu too (your own worlds always do), the chat says who is typing and the shown item appears in chat with its tooltip; a gear in the bar lets you hide that you are typing. Each of these can be turned off in Glazed Menu's settings (Game Screens).
- Glazed Menu can now also be installed on a server (optional): there it only relays the chat's typing indicators and shown items.
- **World warnings**: the game's warning that a world uses experimental settings, and its question of a backup before opening one, as a popup in Glazed Menu's look. It can be turned off in Glazed Menu's settings (Game Screens).
- The colors that tell the game screens' cards, tabs and categories apart follow the chosen color scheme (Glazed Menu's own scheme keeps them as they were).
- **Death screen**: over the reddened world, a line is drawn across the screen and opens into a band with how you died and the buttons to respawn or leave; under it, your score, the level lost, how long you lasted and where you died (click to copy the coordinates). Each part arrives in its turn with a sound, the buttons unlock as a bar fills, and the band closes as you respawn. It and its sounds can be turned off in Glazed Menu's settings (Game Screens); with the interface transitions off it appears at once.
- **Disconnected screen**: a card over the last picture of the place, with what happened and the reason (scrolled when it is long), the server as it answers now, how long you played, and buttons to copy the reason, go back, and reconnect. It can be turned off in Glazed Menu's settings (Game Screens).
- **Loading screens**, while a world or a server is joined (connecting, and loading the terrain): behind them, a picture of where you were the last time you were there, taken as you open the pause menu, and also a few seconds after you enter and every minute while you play (so there is one however you leave); along the bottom, a panel naming the world or server and saying what's being done, with the progress. When the world is ready, the picture and the panel fade off it instead of cutting to it (not with the interface transitions turned off). The pictures are kept in the game's folder under `glazedmenu/snapshots`. It can be turned off in Glazed Menu's settings (Game Screens), and then no pictures are taken.
- **Advancements screen**, in place of the game's: its tabs as a ribbon, each saying how many of its advancements are done, and the chosen tab's advancements as a list, each set in under the one it comes after, with a bar where it takes several steps. Beside the list, the chosen advancement: what it asks for, its steps ticked off as they're done, and what it comes after and leads to. The list can be narrowed to the ones done or under way, the search looks through every tab, and Tree View opens the game's own screen. It can be turned off in Glazed Menu's settings (Game Screens).
- **Statistics screen**, in place of the game's: tabs for General, Blocks, Items and Mobs, four of the numbers most looked for as cards (time played, distance traveled, mob kills, deaths), and the chosen tab as a table sorted by the column clicked, with a search. It can be turned off in Glazed Menu's settings (Game Screens).
- **Profiles**, on the Mod Configs screen: named sets of settings across any mods. One is saved from the settings the list shows (the changed ones, or what a search narrowed them to); using one puts its settings in as unsaved changes to look over and save, and `/glazedmenu profile <name>` sets and saves them at once.
- **Update notices from Modrinth**: mods that name no update file are looked up on Modrinth by the hashes of their files, so most mods get update notices. Only the hashes, the game version and the loader are sent, and it can be turned off in Glazed Menu's settings (Mod List).
- **Mod list**:
  - Favorites: the star in a mod's details lists it first, and under a Favorites filter.
  - Hidden mods: the eye hides a mod from the list; it stays under a Hidden filter, to show it again.
  - Ignore this version, in a mod's update notice: nothing more is said of that version, until a newer one.
  - Sorting by Recent: the mods installed or updated last come first.

### 🔧 Changes
- A config's own search also finds settings by the name of the group they're in and by their value, as the search across configs does.

## v1.0 Release | 2026-10-03
First release, for Minecraft 26.2, on Fabric and NeoForge. Client side only; it needs no other mod.

### ✨ New Features
- **Mod list**, in place of Mod Menu's and NeoForge's: a grid or a list, filters for All, With Settings and Libraries, search and A–Z sorting.
  - Mods that bundle others open and close as one group, under All and under Libraries.
  - Every mod in its own colors, with a details pane: description, authors, license, what it needs and what it bundles, and Settings, Website, Issues and Source buttons.
  - Update notices for mods that publish an update file.
  - A Mods button on the title and pause screens where no mod list added one, and a button to open the mods folder.
- **Config screens** for the configs most mods use: every mod's configs in one place as cards, and one editor with categories, search, undo and redo, reset, presets, validation and restart notices.
  - Toggles, sliders, dropdowns, list editing and a color picker.
  - Save, Done and Cancel; server configs are edited by the host in a world and read-only for other players.
  - A mod with its own config screen keeps it.
- **Look**: dark and light modes, seven color schemes, panel opacity, glass and sun ray effects, and fading page transitions.
- **Commands**: `/glazedmenu` opens every mod's configs, `/glazedmenu <mod>` one mod's, and `/glazedmenu mods` the mod list.
