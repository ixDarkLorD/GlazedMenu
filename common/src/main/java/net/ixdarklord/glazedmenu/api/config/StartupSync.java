package net.ixdarklord.glazedmenu.api.config;

/**
 * How a {@link ConfigScope#STARTUP} value is reconciled with the server's when a client joins. Values built with
 * {@code localOnly()} aren't compared at all: each side keeps its own.
 */
public enum StartupSync {
    /**
     * The client must have the server's value, because both sides built something from it (an item that exists or
     * not, a block's properties...). A client that differs is disconnected before joining, with a list of the
     * differences and the option to adopt the server's values for the next start. The default.
     */
    REQUIRE_MATCH,
    /**
     * The client uses the server's value while connected, and its own again afterwards. For values read while
     * playing rather than when content is created (a multiplier applied every tick, say).
     */
    USE_SERVER
}
