---
icon: material/rocket-launch-outline
description: Set up, and what the API covers
---

# Java API

Everything on the [Mod Authors](../authors/index.md) pages is done with files. The Java API is for what files can't do: opening Glazed Menu's screens from your own buttons and keys, building a theme in code, drawing your own animated effects, and changing the widget that edits a value.

It lives in `net.ixdarklord.glazedmenu.api`. Everything under `net.ixdarklord.glazedmenu.internal` is Glazed Menu's own and can change in any release.

<div class="grid cards" markdown>

-   :material-monitor-dashboard:{ .lg .middle } **Screens**

    ---

    Open a mod's configs, one config, a single category as a popup, or the color picker.

    [:octicons-arrow-right-24: Screens](screens.md)

-   :material-palette-swatch-outline:{ .lg .middle } **Themes in code**

    ---

    Colors, background, icon and popup panel for your mod's config screens.

    [:octicons-arrow-right-24: Themes](themes.md)

-   :material-creation-outline:{ .lg .middle } **Effects**

    ---

    Animations behind the panels, over the screen, or over each widget.

    [:octicons-arrow-right-24: Effects](effects.md)

-   :material-form-textbox:{ .lg .middle } **Configs and editors**

    ---

    The shape Glazed Menu reads every config into, its value types, and the widgets that edit them.

    [:octicons-arrow-right-24: Configs and Editors](editors.md)

</div>

## Compile against Glazed Menu

Glazed Menu is a client-side mod that players may or may not have, so depend on it at compile time only and never bundle it.

Download the jar for your Minecraft version and loader from [Modrinth](https://modrinth.com/mod/glazedmenu) or [CurseForge](https://www.curseforge.com/minecraft/mc-mods/glazed-menu), put it in your project's `libs` folder, and add it as a compile-only dependency. The jars are named `glazedmenu-<loader>-<minecraft>+<version>.jar`.

=== "26.1 and newer"

    ```groovy title="build.gradle"
    dependencies {
        compileOnly files("libs/glazedmenu-fabric-26.1.2+1.0.jar")
    }
    ```

=== "1.20 – 1.21.1, with Loom"

    ```groovy title="build.gradle"
    dependencies {
        // Loom remaps the jar to your mappings.
        modCompileOnly files("libs/glazedmenu-fabric-1.21.1+1.0.jar")
    }
    ```

To try it in your dev runs too, add the same jar as a runtime-only dependency (`runtimeOnly`, or `modRuntimeOnly` on 1.20 – 1.21.1).

## Keep it optional

Call the API only when Glazed Menu is installed, from a class of its own, so the game never loads Glazed Menu's classes without it.

```java title="GlazedMenuCompat.java"
public final class GlazedMenuCompat {
    private GlazedMenuCompat() {}

    /** Call from your client setup, after checking the mod is loaded. */
    public static void setup() {
        ConfigTheme.setForMod("mymod", ConfigTheme.builder()
                .colors(ConfigColorScheme.tinted(0xFFFF8A3D))
                .build());
    }
}
```

=== "Fabric"

    ```java
    if (FabricLoader.getInstance().isModLoaded("glazedmenu")) {
        GlazedMenuCompat.setup();
    }
    ```

=== "NeoForge and Forge"

    ```java
    if (ModList.get().isLoaded("glazedmenu")) {
        GlazedMenuCompat.setup();
    }
    ```

!!! warning "Client only"

    Glazed Menu is never on a dedicated server. Keep every call on the client side of your mod.

## What you may use

| Marked | Meaning |
|---|---|
| Nothing | Yours to call. |
| `@ApiStatus.NonExtendable` | Read it, don't implement it. `Config`, `ConfigGroup`, `ConfigNode` and `ConfigValue` are made by Glazed Menu from the config systems it reads. |
| `@ApiStatus.Internal` | For Glazed Menu's own integrations. Don't call it. |

## Differences between Minecraft versions

The API is the same on every version branch, apart from the names Minecraft itself changed.

| | 26.1 and newer | 1.20 – 1.21.1 |
|---|---|---|
| Ids | `Identifier` | `ResourceLocation` |
| An effect's `graphics` | Minecraft's `GuiGraphicsExtractor` | Glazed Menu's own `GuiGraphicsExtractor` (in `internal.compat`), with the same drawing calls on top of `GuiGraphics` |

The examples on these pages use the 26.1 names.
