# Libraries and Updates

Two things Glazed Menu reads from your mod's metadata.

## List your mod under Libraries

Library mods are kept out of the way under the **Libraries** filter.

=== "Fabric"

    ```json title="fabric.mod.json"
    "custom": {
      "glazedmenu": { "library": true }
    }
    ```

=== "NeoForge and Forge"

    ```toml title="neoforge.mods.toml or mods.toml"
    [modproperties.yourmodid]
    library = true
    ```

## Update notices

=== "Fabric"

    Point Glazed Menu at an update file:

    ```json title="fabric.mod.json"
    "custom": {
      "glazedmenu": { "update_json": "https://example.com/updates/yourmodid.json" }
    }
    ```

=== "NeoForge and Forge"

    Glazed Menu uses your mod's usual `updateJSONURL`, so there is nothing to add.

### The update file

The file uses the Forge and NeoForge update JSON format:

```json
{
  "homepage": "https://modrinth.com/mod/yourmod",
  "promos": {
    "1.21.1-recommended": "1.2.0",
    "1.21.1-latest": "1.3.0-beta"
  }
}
```

Glazed Menu takes the `<minecraft>-recommended` version for the running game, or `<minecraft>-latest` when there is no recommended one, and shows a notice when it is newer than the installed version. `homepage` is where the notice sends the player.
