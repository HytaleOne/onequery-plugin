package dev.hytaleone.query.network.model;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * An event in the network, used for pub/sub notifications.
 */
public sealed interface NetworkEvent {

    /**
     * Get the type name of this event.
     */
    @Nonnull
    String type();

    /**
     * A player joined a server.
     */
    record PlayerJoin(
            @Nonnull UUID uuid,
            @Nonnull String username,
            @Nonnull String serverId,
            @Nonnull String serverName
    ) implements NetworkEvent {
        @Override
        public String type() {
            return "player_join";
        }
    }

    /**
     * A player left a server.
     */
    record PlayerLeave(
            @Nonnull UUID uuid,
            @Nonnull String serverId
    ) implements NetworkEvent {
        @Override
        public String type() {
            return "player_leave";
        }
    }

    /**
     * A server sent a heartbeat.
     */
    record ServerHeartbeat(
            @Nonnull String serverId,
            int playerCount,
            int maxPlayers
    ) implements NetworkEvent {
        @Override
        public String type() {
            return "server_heartbeat";
        }
    }

    /**
     * A server came online.
     */
    record ServerOnline(
            @Nonnull String serverId,
            @Nonnull String serverName
    ) implements NetworkEvent {
        @Override
        public String type() {
            return "server_online";
        }
    }

    /**
     * A server went offline (explicitly or via timeout).
     */
    record ServerOffline(
            @Nonnull String serverId,
            @Nullable String reason
    ) implements NetworkEvent {
        @Override
        public String type() {
            return "server_offline";
        }
    }
}
