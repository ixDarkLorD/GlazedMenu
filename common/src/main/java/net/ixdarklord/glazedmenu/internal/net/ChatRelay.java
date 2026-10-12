package net.ixdarklord.glazedmenu.internal.net;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The server's part in Glazed Menu's chat: it passes on to the other players that one is typing, and puts the item a
 * player shows into the chat (as a message of the server's, with the item on its name's hover, so players without
 * Glazed Menu see it too). It runs on a server of its own and on the one inside the game alike.
 */
public final class ChatRelay {
    /** How the loader sends to a player, and whether that player's game knows what's sent. */
    public interface Transport {
        boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type);

        void send(ServerPlayer player, CustomPacketPayload payload);
    }

    // An item isn't shown again by the same player sooner than this.
    private static final long SHOWCASE_GAP_MILLIS = 3000;
    private static final Map<UUID, Long> lastShowcase = new ConcurrentHashMap<>();
    private static @Nullable Transport transport;

    private ChatRelay() {}

    public static void setTransport(Transport loader) {
        transport = loader;
    }

    /** A player began or stopped typing: every other player whose game has Glazed Menu is told. */
    public static void typing(ServerPlayer from, boolean typing) {
        Transport loader = transport;
        MinecraftServer server = from.level().getServer();
        if (loader == null || server == null) return;
        ChatPayloads.TypingOf payload = new ChatPayloads.TypingOf(from.getUUID(), typing);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player != from && loader.canSend(player, ChatPayloads.TypingOf.TYPE)) loader.send(player, payload);
        }
    }

    /** A player shows the item in their hand: it's said in the chat, the item on the hover of its name. */
    public static void showcase(ServerPlayer from) {
        MinecraftServer server = from.level().getServer();
        ItemStack stack = from.getMainHandItem();
        if (server == null || stack.isEmpty()) return;
        long now = System.currentTimeMillis();
        Long last = lastShowcase.get(from.getUUID());
        if (last != null && now - last < SHOWCASE_GAP_MILLIS) return;
        lastShowcase.put(from.getUUID(), now);
        Component item = stack.getCount() > 1 ? Component.empty().append(stack.getDisplayName()).append(" x" + stack.getCount()) : stack.getDisplayName();
        server.getPlayerList().broadcastSystemMessage(Component.translatableWithFallback("glazedmenu.chat.showcase", "%s is showing %s", from.getDisplayName(), item), false);
    }
}
