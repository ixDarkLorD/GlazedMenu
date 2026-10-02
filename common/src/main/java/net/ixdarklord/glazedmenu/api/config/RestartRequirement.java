package net.ixdarklord.glazedmenu.api.config;

/**
 * What must restart before a changed value takes effect. The config screen and commands tell the player.
 */
public enum RestartRequirement {
    NONE,
    /** Rejoin the world (or restart the server). */
    WORLD,
    /** Restart the game. */
    GAME;

    /** The stricter of the two. */
    public RestartRequirement max(RestartRequirement other) {
        return this.ordinal() >= other.ordinal() ? this : other;
    }
}
