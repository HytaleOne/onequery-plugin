package dev.hytaleone.query.api;

import dev.hytaleone.query.network.NetworkModule;
import dev.hytaleone.query.network.model.NetworkEvent;
import dev.hytaleone.query.network.model.NetworkSnapshot;
import dev.hytaleone.query.network.model.PlayerInfo;
import dev.hytaleone.query.network.model.ServerState;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Public API for querying network state from other plugins.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * if (!OneQueryAPI.isAvailable()) {
 *     return;
 * }
 *
 * OneQueryAPI api = OneQueryAPI.get();
 *
 * // Get all players
 * List<PlayerInfo> players = api.getPlayers();
 *
 * // Get players on survival servers
 * List<PlayerInfo> survivalPlayers = api.getPlayers("survival-*");
 *
 * // Get total player count
 * int total = api.getPlayerCount();
 *
 * // Subscribe to events
 * api.subscribe(event -> {
 *     if (event instanceof NetworkEvent.PlayerJoin join) {
 *         System.out.println(join.username() + " joined " + join.serverId());
 *     }
 * });
 * }</pre>
 */
public final class OneQueryAPI {

    private static volatile OneQueryAPI instance;
    private static volatile NetworkModule networkModule;

    private OneQueryAPI() {}

    /**
     * Check if the API is available (network mode enabled and connected).
     */
    public static boolean isAvailable() {
        return networkModule != null && networkModule.isEnabled();
    }

    /**
     * Get the API instance.
     *
     * @throws IllegalStateException if API is not available
     */
    @Nonnull
    public static OneQueryAPI get() {
        if (!isAvailable()) {
            throw new IllegalStateException(
                    "OneQueryAPI is not available. Network mode may be disabled or not yet initialized.");
        }
        return instance;
    }

    /**
     * Get the API instance or null if not available.
     */
    @Nullable
    public static OneQueryAPI getOrNull() {
        return isAvailable() ? instance : null;
    }

    // ========== Server Queries ==========

    /**
     * Get all servers in the network.
     */
    @Nonnull
    public List<ServerState> getServers() {
        return networkModule.getNetworkSnapshotSync().servers();
    }

    /**
     * Get servers matching a pattern.
     *
     * @param pattern Glob pattern (e.g., "survival-*", "lobby-?", "*-eu-*")
     */
    @Nonnull
    public List<ServerState> getServers(@Nonnull String pattern) {
        Predicate<String> matcher = globToMatcher(pattern);
        return getServers().stream()
                .filter(s -> matcher.test(s.serverId()) )
                .toList();
    }

    /**
     * Get a specific server by ID.
     */
    @Nonnull
    public Optional<ServerState> getServer(@Nonnull String serverId) {
        return getServers().stream()
                .filter(s -> s.serverId().equals(serverId))
                .findFirst();
    }

    /**
     * Get total server count.
     */
    public int getServerCount() {
        return networkModule.getServerCount();
    }

    // ========== Player Queries ==========

    /**
     * Get all players in the network.
     */
    @Nonnull
    public List<PlayerInfo> getPlayers() {
        return networkModule.getPlayers();
    }

    /**
     * Get players on servers matching a pattern.
     *
     * @param serverPattern Glob pattern for server ID/name (e.g., "survival-*")
     */
    @Nonnull
    public List<PlayerInfo> getPlayers(@Nonnull String serverPattern) {
        Predicate<String> matcher = globToMatcher(serverPattern);
        return getPlayers().stream()
                .filter(p -> matcher.test(p.serverId()) )
                .toList();
    }

    /**
     * Get a player by UUID.
     */
    @Nonnull
    public Optional<PlayerInfo> getPlayer(@Nonnull UUID uuid) {
        return getPlayers().stream()
                .filter(p -> p.uuid().equals(uuid))
                .findFirst();
    }

    /**
     * Get a player by username (case-insensitive).
     */
    @Nonnull
    public Optional<PlayerInfo> getPlayer(@Nonnull String username) {
        return getPlayers().stream()
                .filter(p -> p.username().equalsIgnoreCase(username))
                .findFirst();
    }

    /**
     * Check if a player is online anywhere in the network.
     */
    public boolean isPlayerOnline(@Nonnull UUID uuid) {
        return getPlayer(uuid).isPresent();
    }

    /**
     * Check if a player is online anywhere in the network.
     */
    public boolean isPlayerOnline(@Nonnull String username) {
        return getPlayer(username).isPresent();
    }

    /**
     * Get total player count across all servers.
     */
    public int getPlayerCount() {
        return networkModule.getPlayerCount();
    }

    /**
     * Get player count on servers matching a pattern.
     */
    public int getPlayerCount(@Nonnull String serverPattern) {
        return getPlayers(serverPattern).size();
    }

    // ========== Network Info ==========

    /**
     * Get the network ID this server belongs to.
     */
    @Nonnull
    public String getNetworkId() {
        return networkModule.getConfig().getNetworkId();
    }

    /**
     * Get the local server's ID.
     */
    @Nonnull
    public String getLocalServerId() {
        ServerState state = getLocalServerState();
        return state != null ? state.serverId() : "";
    }

    /**
     * Get the local server's state.
     */
    @Nullable
    public ServerState getLocalServerState() {
        var store = networkModule.getStore();
        return store != null ? store.getLocalServerState() : null;
    }

    /**
     * Check if this server syncs network state (SYNC or AGGREGATE mode).
     */
    public boolean isSyncing() {
        return networkModule.shouldSync();
    }

    /**
     * Check if this server aggregates network data in responses (AGGREGATE mode).
     */
    public boolean isAggregating() {
        return networkModule.shouldAggregate();
    }

    // ========== Events ==========

    /**
     * Subscribe to network events (player join/leave, server online/offline).
     */
    public void subscribe(@Nonnull Consumer<NetworkEvent> listener) {
        networkModule.subscribe(listener);
    }

    /**
     * Unsubscribe from network events.
     */
    public void unsubscribe(@Nonnull Consumer<NetworkEvent> listener) {
        networkModule.unsubscribe(listener);
    }

    // ========== Async Operations ==========

    /**
     * Fetch a fresh network snapshot from Redis (async).
     * Always queries Redis, regardless of subscriber mode.
     */
    @Nonnull
    public CompletableFuture<NetworkSnapshot> fetchSnapshot() {
        return networkModule.fetchNetworkSnapshot();
    }

    // ========== Internal ==========

    private static Predicate<String> globToMatcher(String pattern) {
        if (pattern == null || pattern.equals("*")) {
            return s -> true;
        }
        StringBuilder regex = new StringBuilder("^");
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            switch (c) {
                case '*' -> regex.append(".*");
                case '?' -> regex.append(".");
                case '.' -> regex.append("\\.");
                case '\\' -> regex.append("\\\\");
                case '[', ']', '(', ')', '{', '}', '^', '$', '|', '+' -> regex.append("\\").append(c);
                default -> regex.append(c);
            }
        }
        regex.append("$");
        Pattern compiled = Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE);
        return s -> s != null && compiled.matcher(s).matches();
    }

    // ========== Lifecycle (called by OneQueryPlugin) ==========

    public static void init(@Nonnull NetworkModule module) {
        networkModule = module;
        instance = new OneQueryAPI();
    }

    public static void shutdown() {
        instance = null;
        networkModule = null;
    }
}
