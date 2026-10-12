package net.ixdarklord.glazedmenu.internal.game;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.ixdarklord.glazedmenu.internal.net.ChatPayloads;
import net.minecraft.client.GuiMessage;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The game's side of what Glazed Menu's chat knows beyond the messages themselves: who is typing now (told by a server
 * that has Glazed Menu), and who sent each message of a player's (noted as the game shows it, ChatListenerMixin).
 */
public final class ChatLink {
    /** How the loader sends to the server, and whether the server knows what's sent. */
    public interface Transport {
        boolean canSend(ChatPayloads.Type<?> type);

        void send(ChatPayloads.Payload payload);
    }

    // A player is taken as typing for this long after the server last said so, in case it's never said they stopped.
    private static final long TYPING_MILLIS = 6000;
    private static final Map<UUID, Long> typing = new ConcurrentHashMap<>();
    private static final Map<GuiMessage, UUID> senders = Collections.synchronizedMap(new WeakHashMap<>());
    private static @Nullable Transport transport;

    private ChatLink() {}

    public static void setTransport(Transport loader) {
        transport = loader;
    }

    /** Whether the server joined takes this of Glazed Menu's (it has Glazed Menu too). */
    public static boolean supports(ChatPayloads.Type<?> type) {
        Transport loader = transport;
        if (loader == null || Minecraft.getInstance().getConnection() == null) return false;
        try {
            return loader.canSend(type);
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static void send(ChatPayloads.Payload payload) {
        Transport loader = transport;
        if (loader != null && supports(payload.type())) loader.send(payload);
    }

    /** The server says a player began or stopped typing. */
    public static void typing(UUID player, boolean now) {
        if (now) typing.put(player, System.currentTimeMillis() + TYPING_MILLIS);
        else typing.remove(player);
    }

    /** The names of the other players typing now. */
    public static List<String> typingNames(Minecraft minecraft) {
        List<String> names = new ArrayList<>();
        ClientPacketListener connection = minecraft.getConnection();
        if (connection == null || typing.isEmpty()) return names;
        long now = System.currentTimeMillis();
        typing.values().removeIf(until -> until < now);
        for (UUID id : typing.keySet()) {
            if (minecraft.player != null && id.equals(minecraft.player.getUUID())) continue;
            PlayerInfo info = connection.getPlayerInfo(id);
            if (info != null) names.add(info.getProfile().getName());
        }
        Collections.sort(names);
        return names;
    }

    /** Notes who sent a message of a player's, as the game shows it. */
    public static void noteSender(GuiMessage message, UUID sender) {
        senders.put(message, sender);
    }

    public static @Nullable UUID senderOf(GuiMessage message) {
        return senders.get(message);
    }
}
