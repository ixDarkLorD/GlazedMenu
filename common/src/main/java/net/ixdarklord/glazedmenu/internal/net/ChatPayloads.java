package net.ixdarklord.glazedmenu.internal.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;
import java.util.function.Function;

/**
 * What Glazed Menu's chat says between a player's game and the server, where both have Glazed Menu: that a player is
 * typing, and that one wants the item in their hand shown to everyone. Nothing here is of the client alone, so a
 * server can load it.
 * <p>
 * Minecraft 1.20.1 has no payload types of its own: each of these is a channel by its id, and what's written into it.
 */
public final class ChatPayloads {
    private ChatPayloads() {}

    private static ResourceLocation id(String name) {
        return new ResourceLocation("glazedmenu", name);
    }

    /** What's sent: it names its channel and writes itself. */
    public interface Payload {
        Type<?> type();

        void write(FriendlyByteBuf buffer);
    }

    /** A kind of payload: its channel, and how one is read. */
    public record Type<T extends Payload>(ResourceLocation id, Function<FriendlyByteBuf, T> reader) {
        public T read(FriendlyByteBuf buffer) {
            return this.reader.apply(buffer);
        }
    }

    /** To the server: the player began or stopped typing a message. */
    public record Typing(boolean typing) implements Payload {
        public static final Type<Typing> TYPE = new Type<>(id("typing"), buffer -> new Typing(buffer.readBoolean()));

        @Override
        public Type<Typing> type() {
            return TYPE;
        }

        @Override
        public void write(FriendlyByteBuf buffer) {
            buffer.writeBoolean(this.typing);
        }
    }

    /** To every other player: one of them began or stopped typing. */
    public record TypingOf(UUID player, boolean typing) implements Payload {
        public static final Type<TypingOf> TYPE = new Type<>(id("typing_of"), buffer -> new TypingOf(buffer.readUUID(), buffer.readBoolean()));

        @Override
        public Type<TypingOf> type() {
            return TYPE;
        }

        @Override
        public void write(FriendlyByteBuf buffer) {
            buffer.writeUUID(this.player);
            buffer.writeBoolean(this.typing);
        }
    }

    /** To the server: show everyone the item in the player's hand (the server looks at the hand itself). */
    public record Showcase() implements Payload {
        public static final Type<Showcase> TYPE = new Type<>(id("showcase"), buffer -> new Showcase());

        @Override
        public Type<Showcase> type() {
            return TYPE;
        }

        @Override
        public void write(FriendlyByteBuf buffer) {}
    }
}
