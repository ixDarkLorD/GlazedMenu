---
icon: material/creation-outline
description: Animations for config screens
---

# Effects

An effect is an animation drawn on config screens. Every theme uses the built-in starfall, soft glows in the accent with small stars falling, unless it names its own.

Two steps: register the effect under an id, then name that id in a theme.

## Write one

Implement `net.ixdarklord.glazedmenu.api.theme.ConfigEffect`. It can draw at three layers; every method is optional.

| Method | Draws |
|---|---|
| `extractBackground(graphics, context)` | Behind the panels, over the theme's background. |
| `extractForeground(graphics, context)` | Over the whole screen, panels and widgets included, but under tooltips. |
| `extractWidget(graphics, widget)` | Over one widget, just after it's drawn. |

```java title="PulseEffect.java"
public final class PulseEffect implements ConfigEffect {
    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, Context context) {
        // A band in the accent color, sweeping down the screen every four seconds.
        int y = (int) (context.time() % 4.0F / 4.0F * context.height());
        int color = (context.accent() & 0x00FFFFFF) | 0x30000000;
        graphics.fill(0, y, context.width(), y + 2, color);
    }

    @Override
    public void extractWidget(GuiGraphicsExtractor graphics, WidgetContext widget) {
        // A thin accent line under the button the mouse is on.
        if (widget.kind() != WidgetKind.BUTTON || !widget.hovered()) return;
        int bottom = widget.y() + widget.height();
        graphics.fill(widget.x(), bottom - 1, widget.x() + widget.width(), bottom, widget.screen().accent());
    }
}
```

!!! warning "Keep it cheap"

    Effects run every frame on the render thread. One that throws is logged once and switched off.

## Register it

During client setup:

```java
ConfigEffects.register(Identifier.fromNamespaceAndPath("mymod", "pulse"), new PulseEffect());
```

Registering an id twice throws `IllegalArgumentException`.

## Use it

Name the id in your theme, in code or in the [theme file](../authors/theming.md).

=== "In code"

    ```java
    ConfigTheme.setForMod("mymod", ConfigTheme.builder()
            .accent(0xFFFF8A3D)
            .effects(Identifier.fromNamespaceAndPath("mymod", "pulse"))
            .build());
    ```

=== "In the theme file"

    ```json title="assets/mymod/glazedmenu/config_theme.json"
    {
      "effects": ["mymod:pulse"]
    }
    ```

Effects are drawn in the order they are listed. To keep the starfall and add yours, list both:

```java
.effects(ConfigTheme.STARFALL, Identifier.fromNamespaceAndPath("mymod", "pulse"))
```

An empty list gives a still screen. A theme naming an id nobody registered skips it, and logs it once.

Players can turn every effect off in Glazed Menu's settings, and the Fast graphics preset always does. Never rely on an effect to show something the player needs.

## What an effect is told

### `Context`: the screen

| Field | Meaning |
|---|---|
| `width`, `height` | The screen's size, in GUI pixels. |
| `mouseX`, `mouseY` | The mouse, in GUI pixels. Both are `-1` while a popup redraws the page under it. |
| `time` | Seconds since the game started, for animating without keeping state. |
| `partialTick` | How far between ticks this frame is. |
| `theme`, `colors`, `mode` | The theme drawn, its colors for the current mode, and that mode. |
| `inWorld` | Whether a world is open: the screen shows it, blurred. |

`accent()` is the accent of the current mode, and `hasMouse()` says whether the mouse is over the screen.

### `WidgetContext`: one widget

| Field | Meaning |
|---|---|
| `kind` | Which kind of widget it is. |
| `x`, `y`, `width`, `height` | Its bounds as drawn, in GUI pixels. |
| `hovered`, `focused`, `active` | Its state. |
| `screen` | The screen's `Context`. |

| `WidgetKind` | The widget |
|---|---|
| `BUTTON` | A flat button, including icon buttons. |
| `TOGGLE` | An on/off switch. |
| `SLIDER` | A number slider. |
| `TEXT_FIELD` | A text or number field. |
| `CARD` | A navigation card on a mod's main config screen. |
| `POPUP` | A popup's panel: category popups, confirmations, the color picker. |
