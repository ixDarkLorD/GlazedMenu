package net.ixdarklord.glazedmenu.internal.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

/**
 * What Glazed Menu's chat says between a player's game and the server, where both have Glazed Menu: that a player is
 * typing, and that one wants the item in their hand shown to everyone. Nothing here is of the client alone, so a
 * server can load it.
 */
public final class ChatPayloads {
    private ChatPayloads() {}

    private static Identifier id(String name) {
        return Identifier.fromNamespaceAndPath("glazedmenu", name);
    }

    /** To the server: the player began or stopped typing a message. */
    public record Typing(boolean typing) implements CustomPacketPayload {
        public static final Type<Typing> TYPE = new Type<>(id("typing"));
        public static final StreamCodec<ByteBuf, Typing> CODEC = StreamCodec.composite(ByteBufCodecs.BOOL, Typing::typing, Typing::new);

        @Override
        public Type<Typing> type() {
            return TYPE;
        }
    }

    /** To every other player: one of them began or stopped typing. */
    public record TypingOf(UUID player, boolean typing) implements CustomPacketPayload {
        public static final Type<TypingOf> TYPE = new Type<>(id("typing_of"));
        public static final StreamCodec<ByteBuf, TypingOf> CODEC = StreamCodec.composite(UUIDUtil.STREAM_CODEC, TypingOf::player,
                ByteBufCodecs.BOOL, TypingOf::typing, TypingOf::new);

        @Override
        public Type<TypingOf> type() {
            return TYPE;
        }
    }

    /** To the server: show everyone the item in the player's hand (the server looks at the hand itself). */
    public record Showcase() implements CustomPacketPayload {
        public static final Type<Showcase> TYPE = new Type<>(id("showcase"));
        public static final StreamCodec<ByteBuf, Showcase> CODEC = StreamCodec.unit(new Showcase());

        @Override
        public Type<Showcase> type() {
            return TYPE;
        }
    }
}
