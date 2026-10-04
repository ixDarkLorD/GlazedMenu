# For Mod Authors

Glazed Menu works with your mod as it is: it lists it, reads its configs if they use a [supported library](../guide/config-screens.md#which-configs-it-reads), and links to its own config screen if it has one.

Everything here is an optional extra. All of it is read from files, so your mod never depends on Glazed Menu. For what files can't do, there is a [Java API](../api/index.md).

<div class="grid cards" markdown>

-   :material-bookshelf:{ .lg .middle } **Libraries and updates**

    ---

    Mark your mod as a library, and tell Glazed Menu where its update file is.

    [:octicons-arrow-right-24: Libraries and Updates](metadata.md)

-   :material-brush-variant:{ .lg .middle } **Theming**

    ---

    Give your config screens your own colors, background and card artwork.

    [:octicons-arrow-right-24: Theming](theming.md)

-   :material-code-braces:{ .lg .middle } **Java API**

    ---

    Open Glazed Menu's screens from your own code, build themes, and draw your own effects.

    [:octicons-arrow-right-24: Java API](../api/index.md)

</div>

## Your own config screen

If your mod has its own config screen, its **Settings** button in the mod list opens it. Glazed Menu finds it through:

- Mod Menu's `ModMenuApi` on Fabric,
- NeoForge's `IConfigScreenFactory`,
- Forge's `ConfigScreenFactory`.
