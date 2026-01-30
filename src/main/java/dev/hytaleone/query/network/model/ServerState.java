package dev.hytaleone.query.network.model;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * State of a server in the network.
 *
 * @param serverId      Unique identifier for the server
 * @param serverName    Display name of the server
 * @param playerCount   Current number of players
 * @param maxPlayers    Maximum player capacity
 * @param host          Host address of the server
 * @param port          Port the server is listening on
 * @param lastHeartbeat Timestamp of last heartbeat (epoch millis)
 */
public record ServerState(
        @Nonnull String serverId,
        @Nonnull String serverName,
        int playerCount,
        int maxPlayers,
        @Nullable String host,
        int port,
        long lastHeartbeat
) {

    /**
     * Create a new ServerState with updated player count.
     */
    public ServerState withPlayerCount(int newCount) {
        return new ServerState(serverId, serverName, newCount, maxPlayers, host, port, lastHeartbeat);
    }

    /**
     * Create a new ServerState with updated heartbeat timestamp.
     */
    public ServerState withHeartbeat(long timestamp) {
        return new ServerState(serverId, serverName, playerCount, maxPlayers, host, port, timestamp);
    }

    /**
     * Check if this server is considered stale (no heartbeat within timeout).
     *
     * @param timeoutMillis Maximum time since last heartbeat
     * @return true if server is stale
     */
    public boolean isStale(long timeoutMillis) {
        return System.currentTimeMillis() - lastHeartbeat > timeoutMillis;
    }

    /**
     * Create a builder for constructing a ServerState.
     */
    public static Builder builder(@Nonnull String serverId) {
        return new Builder(serverId);
    }

    public static class Builder {
        private final String serverId;
        private String serverName = "";
        private int playerCount = 0;
        private int maxPlayers = 100;
        private String host = null;
        private int port = 5520;
        private long lastHeartbeat = System.currentTimeMillis();

        private Builder(@Nonnull String serverId) {
            this.serverId = serverId;
        }

        public Builder serverName(@Nonnull String serverName) {
            this.serverName = serverName;
            return this;
        }

        public Builder playerCount(int playerCount) {
            this.playerCount = playerCount;
            return this;
        }

        public Builder maxPlayers(int maxPlayers) {
            this.maxPlayers = maxPlayers;
            return this;
        }

        public Builder host(@Nullable String host) {
            this.host = host;
            return this;
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder lastHeartbeat(long lastHeartbeat) {
            this.lastHeartbeat = lastHeartbeat;
            return this;
        }

        public ServerState build() {
            return new ServerState(serverId, serverName, playerCount, maxPlayers, host, port, lastHeartbeat);
        }
    }
}
