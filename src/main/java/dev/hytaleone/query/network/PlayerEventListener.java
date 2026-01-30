package dev.hytaleone.query.network;

import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Listens for player connect/disconnect events and updates the network state.
 */
public class PlayerEventListener {

    private final NetworkModule networkModule;

    public PlayerEventListener(@Nonnull NetworkModule networkModule) {
        this.networkModule = networkModule;
    }

    /**
     * Handle a player connecting to the server.
     */
    public void onPlayerConnect(@Nonnull PlayerConnectEvent event) {
        if (!networkModule.isEnabled()) {
            return;
        }

        PlayerRef playerRef = event.getPlayerRef();
        UUID uuid = playerRef.getUuid();
        String username = playerRef.getUsername();

        networkModule.onPlayerJoin(uuid, username);
    }

    /**
     * Handle a player disconnecting from the server.
     */
    public void onPlayerDisconnect(@Nonnull PlayerDisconnectEvent event) {
        if (!networkModule.isEnabled()) {
            return;
        }

        PlayerRef playerRef = event.getPlayerRef();
        UUID uuid = playerRef.getUuid();

        networkModule.onPlayerLeave(uuid);
    }
}
