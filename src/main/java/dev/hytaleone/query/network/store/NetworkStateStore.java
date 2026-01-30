package dev.hytaleone.query.network.store;

import dev.hytaleone.query.network.model.NetworkEvent;
import dev.hytaleone.query.network.model.NetworkSnapshot;
import dev.hytaleone.query.network.model.PlayerInfo;
import dev.hytaleone.query.network.model.ServerState;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Interface for network state storage.
 * All methods are non-blocking and return CompletableFuture.
 */
public interface NetworkStateStore {

    /**
     * Register this server with the network.
     * Should be called on startup.
     *
     * @param state Initial server state
     * @return Future that completes when registration is done
     */
    @Nonnull
    CompletableFuture<Void> registerServer(@Nonnull ServerState state);

    /**
     * Unregister this server from the network.
     * Should be called on shutdown.
     *
     * @param serverId ID of the server to unregister
     * @return Future that completes when unregistration is done
     */
    @Nonnull
    CompletableFuture<Void> unregisterServer(@Nonnull String serverId);

    /**
     * Handle a player joining this server.
     * For backends, this writes to the store.
     * For hubs, this updates local state only.
     *
     * @param player Player information
     * @return Future that completes when the update is persisted
     */
    @Nonnull
    CompletableFuture<Void> onPlayerJoin(@Nonnull PlayerInfo player);

    /**
     * Handle a player leaving this server.
     *
     * @param playerId UUID of the player
     * @param serverId ID of the server they left
     * @return Future that completes when the update is persisted
     */
    @Nonnull
    CompletableFuture<Void> onPlayerLeave(@Nonnull UUID playerId, @Nonnull String serverId);

    /**
     * Send a heartbeat to keep this server registered.
     * Should be called periodically (e.g., every 15 seconds).
     *
     * @param state Current server state
     * @return Future that completes when the heartbeat is sent
     */
    @Nonnull
    CompletableFuture<Void> heartbeat(@Nonnull ServerState state);

    /**
     * Get a snapshot of the entire network state.
     * For hubs, this returns cached data.
     * For backends, this returns local data only.
     *
     * @return Future with the network snapshot
     */
    @Nonnull
    CompletableFuture<NetworkSnapshot> getNetworkSnapshot();

    /**
     * Fetch a fresh snapshot from the store (always queries Redis).
     *
     * @return Future with the network snapshot
     */
    @Nonnull
    CompletableFuture<NetworkSnapshot> fetchNetworkSnapshot();

    /**
     * Get all players across the network.
     * For hubs, this returns aggregated player list.
     * For backends, this returns local players only.
     *
     * @return Future with the player list
     */
    @Nonnull
    CompletableFuture<List<PlayerInfo>> getAllPlayers();

    /**
     * Subscribe to network events.
     * Events are delivered on the Netty event loop thread.
     *
     * @param listener Callback for network events
     */
    void subscribe(@Nonnull Consumer<NetworkEvent> listener);

    /**
     * Unsubscribe from network events.
     *
     * @param listener Previously registered callback
     */
    void unsubscribe(@Nonnull Consumer<NetworkEvent> listener);

    /**
     * Start the store (connect to external services if needed).
     *
     * @return Future that completes when started
     */
    @Nonnull
    CompletableFuture<Void> start();

    /**
     * Stop the store and release resources.
     *
     * @return Future that completes when stopped
     */
    @Nonnull
    CompletableFuture<Void> stop();

    /**
     * Check if the store is connected and operational.
     */
    boolean isConnected();

    /**
     * Get the current local server state.
     *
     * @return Current server state, or null if not registered
     */
    @Nonnull
    ServerState getLocalServerState();
}
