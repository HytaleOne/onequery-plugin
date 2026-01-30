package dev.hytaleone.query.network.model;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Information about a player in the network.
 *
 * @param uuid       Player's unique identifier
 * @param username   Player's display name
 * @param serverId   ID of the server the player is on
 * @param serverName Display name of the server
 * @param joinTime   Timestamp when player joined (epoch millis)
 */
public record PlayerInfo(
        @Nonnull UUID uuid,
        @Nonnull String username,
        @Nonnull String serverId,
        @Nonnull String serverName,
        long joinTime
) {

    /**
     * Create a PlayerInfo for a player on the local server.
     */
    public static PlayerInfo of(@Nonnull UUID uuid, @Nonnull String username,
                                @Nonnull String serverId, @Nonnull String serverName) {
        return new PlayerInfo(uuid, username, serverId, serverName, System.currentTimeMillis());
    }
}
