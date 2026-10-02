package net.ixdarklord.glazedmenu.internal.source;

/** Where a config's edits go from this client. */
public enum Access {
    /** Changed and saved here. */
    LOCAL,
    /** Sent to the server, which applies and saves them. */
    REMOTE,
    /** The server's values, which this player may not change. */
    READ_ONLY,
    /** A world config with no world to hold it. */
    UNAVAILABLE
}
