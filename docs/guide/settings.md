---
icon: material/cog-outline
description: Every option of Glazed Menu itself
---

# Settings

Glazed Menu's own settings are a config like any other mod's, edited in its own config screen.

## Where to find them

- In the mod list, select **Glazed Menu** and press **Settings**.
- Or type `/glazedmenu glazedmenu` in chat.

They are saved in `config/glazedmenu.json`.

![Glazed Menu's settings in its own config screen](../assets/shots/config-screen.jpg){ .gm-shot loading=lazy }

## Mod List

| Setting | Default | What it does |
|---|:-:|---|
| **Replace Other Mod Lists** | On | Opens Glazed Menu's mod list where Mod Menu's or NeoForge's would open. |
| **Mods Button** | On | Adds a **Mods** button to the title and pause screens when they have none. |
| **Show Loader Parts** | Off | Lists the loader's own parts (Java, the loader, MixinExtras) among the other mods too, not only under **Libraries**. |
| **Check for Updates** | On | Looks for newer versions of your mods in the update files they name, once the game has loaded, and marks the mods that have one. |
| **Mod List View** | List | Shows the mods as a grid of tiles or as a list. |

## Look

These apply to the mod list and to every mod's config screens. [Looks](looks.md) shows what they change.

| Setting | Default | What it does |
|---|:-:|---|
| **Theme Mode** | Dark | Whether the screens use their dark or light colors. The sun/moon button in a screen's top bar switches it too. |
| **Color Scheme** | Glazed | The colors of the mod list and of every config screen without a theme of its own. |
| **Backdrop Opacity** | 100% | Scales how strongly each mod's backdrop color covers the background, from 0% (removed) to 200% (doubled). |
| **Texture Opacity** | 100% | Scales the opacity of each mod's background texture. Below 100% the panorama or world shows through it. |
| **Panel Opacity** | 100% | How solid the panels and bars are, from 10% to 100%. Lower lets more of the background show through. |
| **Theme Effects** | On | Each mod's animated effects: by default soft glows and small falling stars behind the panels. |
| **Page Transitions** | On | Pages fading in and out as you open a screen or go back. |

## Other Config Screens

Two config libraries have screens of their own. Glazed Menu leaves those alone unless you turn these on.

| Setting | Default | What it does |
|---|:-:|---|
| **Replace MezzConfig GUI Screens** | Off | Replaces MezzConfig GUI's config screens, like JEI's, with Glazed Menu's. Without MezzConfig GUI installed, Glazed Menu's open either way. |
| **Replace FTB Library Screens** | Off | Replaces FTB Library's config editor, used by FTB mods' settings like FTB Ultimine's, with Glazed Menu's screens. |
