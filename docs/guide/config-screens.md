# Config Screens

One look for every mod's settings, whichever config library the mod uses.

![A config screen: a sidebar of categories and the settings beside it](../assets/shots/config-screen.jpg){ .gm-shot loading=lazy }

## What every screen has

- A **sidebar of categories** and **search** across all of them.
- **Undo / redo** and **reset**, per value or for everything.
- **Presets**, where the mod defines them.
- **Validation** as you type, and **restart notices** for values that need one.
- **List editing** and a **color picker**.

![The color picker](../assets/shots/color-picker.jpg){ .gm-shot loading=lazy }

## Each config as a card

A mod's configs are shown as cards: client, common, server and so on. Pick one to open its screen.

![A mod's configs as cards](../assets/shots/config-cards.jpg){ .gm-shot loading=lazy }

A single category can also open as a popup over whatever screen you're on.

![A category opened as a popup](../assets/shots/category-popup.jpg){ .gm-shot loading=lazy }

## Which configs it reads

| Library | Notes |
|---|---|
| **CoolCatLib: Core** | With the mod's own theme. |
| **NeoForge** (`ModConfigSpec`) | On Fabric through Forge Config API Port, which also covers Forge's `ForgeConfigSpec`. |
| **Cloth Config** (AutoConfig) | |
| **YACL** | |
| **MidnightLib** | |
| **MezzConfig** (JEI) | Optional, in Glazed Menu's settings. |
| **FTB Library** (FTB mods) | Optional, in Glazed Menu's settings. |

A mod with its own config screen gets a link to that screen instead.

![Configs from every mod in one place](../assets/shots/configs-all-mods.jpg){ .gm-shot loading=lazy }
