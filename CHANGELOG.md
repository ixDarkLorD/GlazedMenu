# Changelog
This file is for listing all the changes to this project

## v1.0 Release | 2026-10-03
First release, for Minecraft 26.3, on Fabric and NeoForge. Client side only; it needs no other mod.

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
