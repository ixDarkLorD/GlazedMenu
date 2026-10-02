package net.ixdarklord.glazedmenu.api.config;

/**
 * Where a config lives, which side loads it, and whether the server's values reach the clients.
 */
public enum ConfigScope {
    /** Loaded only on clients, from the {@code config} folder. Never synced. */
    CLIENT("client", false),
    /** Loaded on both sides, from the {@code config} folder. Each side keeps its own values. */
    COMMON("common", false),
    /**
     * Loaded on both sides from the {@code config} folder; the server's values are sent to every client that joins
     * and whenever they change. Operators can edit them in-game.
     */
    SERVER("server", true),
    /**
     * Stored per world, in {@code <world>/serverconfig}; loaded while a server runs (defaults otherwise) and synced
     * like {@link #SERVER}. A copy in {@code defaultconfigs} seeds new worlds.
     */
    WORLD("world", true),
    /**
     * Loaded on both sides from the {@code config} folder as soon as it's built, before content is registered, so
     * its values can shape what a mod creates: which items exist, their durability, stack sizes... The values are
     * then fixed for the session: changes are saved but take effect after a restart.
     * <p>
     * Joining a server compares the values, per {@link StartupSync}: by default a client whose values differ from
     * the server's can't join, and is offered to adopt the server's values.
     */
    STARTUP("startup", true);

    private final String defaultName;
    private final boolean synced;

    ConfigScope(String defaultName, boolean synced) {
        this.defaultName = defaultName;
        this.synced = synced;
    }

    /** The config name used when the builder doesn't set one. */
    public String defaultName() {
        return this.defaultName;
    }

    /** Whether the server's values are sent to clients. */
    public boolean isSynced() {
        return this.synced;
    }
}
