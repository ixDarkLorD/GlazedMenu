package net.ixdarklord.glazedmenu.internal.game;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * How long each world and server has been played: this session, the last one, and in all since Glazed Menu began
 * counting. Counted as the game is drawn while in a world (LoadingLook calls it each frame), kept in
 * {@code config/glazedmenu-game.json} by the world's folder or the server's address, and written every half minute
 * and as the world is left, so little is lost if the game ends without a word.
 */
public final class PlayTimes {
    // Time between two frames longer than this isn't play (the game was frozen, or the computer asleep).
    private static final long LONGEST_FRAME_MILLIS = 2000;
    private static final long WRITE_EVERY_MILLIS = 30_000;
    private static @Nullable String playing;
    private static long lastFrame;
    private static long session;
    private static long unwritten;

    private PlayTimes() {}

    /** A frame of the world or server of this key was drawn (null: of none that's known). */
    static void frame(@Nullable String key) {
        long now = System.currentTimeMillis();
        if (key == null || !key.equals(playing)) {
            flush();
            playing = key;
            session = 0;
            lastFrame = now;
            return;
        }
        long passed = now - lastFrame;
        lastFrame = now;
        if (passed <= 0 || passed > LONGEST_FRAME_MILLIS) return;
        session += passed;
        unwritten += passed;
        if (unwritten >= WRITE_EVERY_MILLIS) write();
    }

    private static void write() {
        if (playing != null && unwritten > 0) GameMarks.addPlayed(playing, unwritten, session);
        unwritten = 0;
    }

    /** The world was left: what was played of it is written, and nothing is being played. */
    static void flush() {
        write();
        playing = null;
        session = 0;
    }

    /** How long the world or server being played has been played this session, in milliseconds. */
    public static long session() {
        return playing == null ? 0 : session;
    }

    /** How long a world or server has been played in all, in milliseconds. */
    public static long total(String key) {
        return GameMarks.playedTotal(key) + (key.equals(playing) ? unwritten : 0);
    }

    /** How long its last session was (the one under way, for the one being played), in milliseconds. */
    public static long lastSession(String key) {
        return key.equals(playing) ? session : GameMarks.playedLast(key);
    }

    /** The world's own count of its play time (its statistics), where that's more than was counted here: it's taken. */
    public static void atLeast(String key, long totalMillis) {
        if (totalMillis > total(key)) GameMarks.setPlayedTotal(key, totalMillis - (key.equals(playing) ? unwritten : 0));
    }

    /** A length of time, to the second: "42 s", "34 min 5 s", "5 h 2 min 9 s". */
    public static Component format(long millis) {
        long seconds = Math.max(0, millis) / 1000;
        long minutes = seconds / 60;
        if (minutes < 1) return Component.translatableWithFallback("glazedmenu.time.seconds", "%s s", seconds);
        if (minutes < 60) return Component.translatableWithFallback("glazedmenu.time.minutes", "%s min %s s", minutes, seconds % 60);
        return Component.translatableWithFallback("glazedmenu.time.hours", "%s h %s min %s s", minutes / 60, minutes % 60, seconds % 60);
    }
}
