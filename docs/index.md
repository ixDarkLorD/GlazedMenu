---
title: Home
hide:
  - navigation
  - toc
---

<div class="gm-hero" markdown>

<div class="gm-hero__logo">
  <img src="assets/logo.png" alt="Glazed Menu">
</div>

# Glazed Menu { .gm-hero__title }

<p class="gm-hero__tagline">A glassy mod list and config screens for every mod.</p>

<p class="gm-hero__lead">Client side, for Fabric, NeoForge and Forge, on Minecraft 1.20 to 26.3. It needs no other mod.</p>

[:simple-modrinth: Modrinth](https://modrinth.com/mod/glazedmenu){ .md-button .md-button--primary }
[:simple-curseforge: CurseForge](https://www.curseforge.com/minecraft/mc-mods/glazed-menu){ .md-button }
[:fontawesome-brands-github: Source](https://github.com/ixDarkLorD/GlazedMenu){ .md-button }

<p class="gm-hero__badges">
  <img alt="Minecraft 1.20 to 26.3" src="https://img.shields.io/badge/Minecraft-1.20%20%E2%80%93%2026.3-5fe1d0?style=flat-square">
  <img alt="Fabric, NeoForge and Forge" src="https://img.shields.io/badge/Loaders-Fabric%20%7C%20NeoForge%20%7C%20Forge-9d8cff?style=flat-square">
  <img alt="Client side" src="https://img.shields.io/badge/Side-Client-ff8fc7?style=flat-square">
  <a href="https://github.com/ixDarkLorD/GlazedMenu/blob/main/LICENSE"><img alt="License: MPL-2.0" src="https://img.shields.io/badge/License-MPL--2.0-8b93a8?style=flat-square"></a>
</p>

<div class="gm-hero__shot">
  <img src="assets/shots/mod-list.jpg" alt="Glazed Menu's mod list">
</div>

</div>

## What it does { .gm-heading }

<p class="gm-lead">Glazed Menu replaces Mod Menu's, NeoForge's and Forge's mod lists with its own, and gives the configs of almost any mod one clean, themed screen.</p>

<div class="grid cards" markdown>

-   :material-view-grid-outline:{ .lg .middle } **A mod list worth opening**

    ---

    Grid or list, filters, search and sorting. Every mod in its own colors, with a details pane and update notices.

    [:octicons-arrow-right-24: The mod list](guide/mod-list.md)

-   :material-tune-variant:{ .lg .middle } **One config screen for every mod**

    ---

    Categories, search, undo and redo, presets and validation, for configs from CoolCatLib, NeoForge, Cloth Config, YACL and more.

    [:octicons-arrow-right-24: Config screens](guide/config-screens.md)

-   :material-palette-outline:{ .lg .middle } **Your look**

    ---

    Dark and light modes, seven color schemes, panel opacity, background effects and page transitions.

    [:octicons-arrow-right-24: Looks](guide/looks.md)

-   :material-file-cog-outline:{ .lg .middle } **Friendly to mod authors**

    ---

    It works with your mod as it is. A few optional files mark a library, announce updates or theme your screens.

    [:octicons-arrow-right-24: For mod authors](authors/index.md)

-   :material-code-braces:{ .lg .middle } **A Java API**

    ---

    Open Glazed Menu's screens from your own code, build a theme, draw your own effects and change how a value is edited.

    [:octicons-arrow-right-24: Java API](api/index.md)

-   :material-history:{ .lg .middle } **What's new**

    ---

    Every release, for each Minecraft version, straight from the version branches.

    [:octicons-arrow-right-24: Changelog](changelog.md)

</div>

## Get started { .gm-heading }

<p class="gm-lead">Three steps, and no other mod to install.</p>

<div class="grid cards gm-steps" markdown>

-   <span class="gm-step">1</span> **Download**

    ---

    Get the jar for your loader and Minecraft version from [Modrinth](https://modrinth.com/mod/glazedmenu) or [CurseForge](https://www.curseforge.com/minecraft/mc-mods/glazed-menu).

-   <span class="gm-step">2</span> **Drop it in**

    ---

    Put it in your `mods` folder. It is client side, so servers don't need it.

-   <span class="gm-step">3</span> **Open it**

    ---

    Press the **Mods** button on the title or pause screen, or type `/glazedmenu mods` in chat.

</div>

## A closer look { .gm-heading }

<p class="gm-lead">The same glass, on every screen.</p>

<div class="gm-gallery" markdown>

<figure markdown="span">
  [![The config screen: categories on the left, the settings on the right](assets/shots/config-screen.jpg){ loading=lazy }](assets/shots/config-screen.jpg){ target=_blank }
  <figcaption>One config screen for every mod</figcaption>
</figure>

<figure markdown="span">
  [![A mod's configs as cards](assets/shots/config-cards.jpg){ loading=lazy }](assets/shots/config-cards.jpg){ target=_blank }
  <figcaption>Each config as a card</figcaption>
</figure>

<figure markdown="span">
  [![The mod list in grid view](assets/shots/mod-list-grid.jpg){ loading=lazy }](assets/shots/mod-list-grid.jpg){ target=_blank }
  <figcaption>The mod list as a grid</figcaption>
</figure>

<figure markdown="span">
  [![The mod list in light mode](assets/shots/mod-list-light.jpg){ loading=lazy }](assets/shots/mod-list-light.jpg){ target=_blank }
  <figcaption>Light mode, complete</figcaption>
</figure>

</div>

## Versions and loaders { .gm-heading }

<p class="gm-lead">Each Minecraft version has its own build and its own branch.</p>

| Minecraft | Fabric | NeoForge | Forge | Source |
|---|:-:|:-:|:-:|---|
| **26.3** | :material-check: | :material-check: | – | [`26.3`](https://github.com/ixDarkLorD/GlazedMenu/tree/26.3) |
| **26.2** | :material-check: | :material-check: | – | [`26.2`](https://github.com/ixDarkLorD/GlazedMenu/tree/26.2) |
| **26.1 – 26.1.2** | :material-check: | :material-check: | – | [`main`](https://github.com/ixDarkLorD/GlazedMenu/tree/main) |
| **1.21 – 1.21.1** | :material-check: | :material-check: | :material-check: | [`1.21-1.21.1`](https://github.com/ixDarkLorD/GlazedMenu/tree/1.21-1.21.1) |
| **1.20 – 1.20.1** | :material-check: | – | :material-check: | [`1.20-1.20.1`](https://github.com/ixDarkLorD/GlazedMenu/tree/1.20-1.20.1) |

<div class="gm-cta" markdown>

**Ready to try it?**

[:simple-modrinth: Get it on Modrinth](https://modrinth.com/mod/glazedmenu){ .md-button .md-button--primary }
[:simple-curseforge: Get it on CurseForge](https://www.curseforge.com/minecraft/mc-mods/glazed-menu){ .md-button }

</div>
