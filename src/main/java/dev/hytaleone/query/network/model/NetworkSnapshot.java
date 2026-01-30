package dev.hytaleone.query.network.model;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * A snapshot of the entire network state at a point in time.
 * Used by hubs to build aggregated query responses.
 */
public record NetworkSnapshot(
        @Nonnull List<ServerState> servers,
        @Nonnull List<PlayerInfo> players,
        long snapshotTime
) {

    /**
     * Create an empty snapshot.
     */
    public static NetworkSnapshot empty() {
        return new NetworkSnapshot(Collections.emptyList(), Collections.emptyList(), System.currentTimeMillis());
    }

    /**
     * Create a snapshot from collections.
     */
    public static NetworkSnapshot of(@Nonnull Collection<ServerState> servers,
                                     @Nonnull Collection<PlayerInfo> players) {
        return new NetworkSnapshot(
                List.copyOf(servers),
                List.copyOf(players),
                System.currentTimeMillis()
        );
    }

    /**
     * Get total player count across all servers.
     */
    public int getTotalPlayerCount() {
        return servers.stream().mapToInt(ServerState::playerCount).sum();
    }

    /**
     * Get total max players across all servers.
     */
    public int getTotalMaxPlayers() {
        return servers.stream().mapToInt(ServerState::maxPlayers).sum();
    }

    /**
     * Get total server count.
     */
    public int getServerCount() {
        return servers.size();
    }

    /**
     * Get count of online (non-stale) servers.
     *
     * @param timeoutMillis Maximum time since last heartbeat
     */
    public int getOnlineServerCount(long timeoutMillis) {
        return (int) servers.stream().filter(s -> !s.isStale(timeoutMillis)).count();
    }

}
