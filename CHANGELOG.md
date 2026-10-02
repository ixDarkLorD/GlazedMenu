# Changelog
This file is for listing all the changes to this project

## v1.0 Release | Unreleased (Minecraft 26.1.2)
### ✨ New Features
- First release: a mod list and CoolCatLib's config screens, moved out of CoolCatLib: Core, in their own client-only mod (package `net.ixdarklord.glazedmenu`). It needs no other mod: it has its own config model, styled widgets, effects and settings (`config/glazedmenu.json`, editable in its own screens).
- With CoolCatLib: Core installed, Core's configs, the themes and enum names mods set with Core's API, the server's synced values and the startup mismatch screen work as before; with CoolCatLib: Canvas, the config screen effects registered with Canvas draw in Glazed Menu's screens too. Resource packs' `config_theme.json` is read from `assets/<modid>/glazedmenu/` (and the old `coolcatcore/`).
- Every config screen CoolCatLib had: the list of every mod's configs with cross-config search, each mod's cards, the config page (sidebar, search, undo/redo, reset, presets, validation, dependencies, restart notices, list editing, the color picker), category popups and the startup mismatch screen; themes, dark/light mode and effects.
- Other config systems, shown and edited in the same screens (saving writes through each system):
  - NeoForge's `ModConfigSpec` configs, on NeoForge and, with Forge Config API Port, on Fabric, where Forge's `ForgeConfigSpec` configs show too. Server configs are edited by the host while in a world and shown read-only to other players.
  - Cloth Config's AutoConfig classes: categories, collapsible and transitive objects, bounded sliders, color pickers, tooltips, excluded fields and restart notices.
  - YACL's config classes (`ConfigClassHandler`): the fields YACL's generated screen shows, by category and group, with its sliders, bounds and color fields.
  - MidnightLib's configs: entries by category, with bounds, sliders and colors.
  - Names and descriptions use each system's translation keys; values it has no matching type for are left to the mod's own screen.
- Config buttons: every mod with a config Glazed Menu reads gets one in NeoForge's mod list and Mod Menu. A mod's own screen is kept; one generated for any mod (NeoForge's generic screen, Forge Config API Port's, Configured's, MidnightLib's) gives way to Glazed Menu's. Glazed Menu's own button opens every mod's configs.
- Mods whose configs Glazed Menu can't read but which have their own config screen get a card opening it in the list of every mod's configs.
- A mod list, in place of Mod Menu's and NeoForge's (they open Glazed Menu's instead): every mod as a glass tile in a drawer, and the chosen one in a pane whose banner glows in the mod's own color (its theme's, or taken from its icon), with its version, authors, side and badges, a Settings button and its website, issues and source links, its description, details, the mods it needs and the ones it bundles (each a chip that opens it).
  - A grid of tiles or a list (name, version and a line of description), switched beside the sort order and remembered.
  - It fades in and out. A mod's Settings button shows only when it has configs Glazed Menu reads or a screen of its own that actually opens.
  - Filters (All, With Settings, Libraries) with counts, search across names, ids, authors and descriptions, A–Z / Z–A, and keys: arrows move between tiles, Enter opens settings, Ctrl+F searches. It remembers the filter, order and chosen mod.
  - Where no mod list put one, a Mods button on the title screen (sharing the Realms button's row) and the pause screen (in Report Bugs' place).
  - Settings: replace other mod lists, the Mods button, whether libraries show under All, and the view; plus the screens' look (dark/light mode, backdrop and texture opacity, theme effects, page transitions).
- `/glazedmenu` opens every mod's configs, `/glazedmenu <mod>` one mod's, `/glazedmenu <mod> <config>/<category>` one category in a small window, and `/glazedmenu mods` the mod list.
