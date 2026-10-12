package net.ixdarklord.glazedmenu.internal.compat;

import java.net.UnknownHostException;
import net.minecraft.client.multiplayer.resolver.ServerNameResolver;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.SharedConstants;
import net.minecraft.client.multiplayer.ServerData;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

// Minecraft 26.1 keeps how a server answered on the server's data; 1.20.1 only has its ping (below zero until the
// answer is in), so the state is kept here, beside the data.
public final class ServerStates {
    private static final Map<ServerData, State> STATES = Collections.synchronizedMap(new WeakHashMap<>());

    private ServerStates() {}

    public enum State {
        INITIAL,
        PINGING,
        UNREACHABLE,
        INCOMPATIBLE,
        SUCCESSFUL
    }

    /** How the server stands: one being asked whose ping has come has answered. */
    public static State of(ServerData server) {
        State state = STATES.getOrDefault(server, State.INITIAL);
        if (state == State.PINGING && server.ping >= 0) {
            state = server.protocol == SharedConstants.getCurrentVersion().getProtocolVersion() ? State.SUCCESSFUL : State.INCOMPATIBLE;
            STATES.put(server, state);
        }
        return state;
    }

    /**
     * Asks a server what it is. 1.20.1's pinger doesn't say an address couldn't be resolved (it only says it can't
     * connect), so it's resolved here first and thrown as newer versions throw it.
     */
    public static void ping(ServerStatusPinger pinger, ServerData server, Runnable onChange) throws UnknownHostException {
        if (ServerNameResolver.DEFAULT.resolveAddress(ServerAddress.parseString(server.ip)).isEmpty()) throw new UnknownHostException(server.ip);
        pinger.pingServer(server, onChange);
    }

    public static void set(ServerData server, State state) {
        if (state == State.PINGING) {
            server.pinged = true;
            server.ping = -2;
        }
        STATES.put(server, state);
    }
}
