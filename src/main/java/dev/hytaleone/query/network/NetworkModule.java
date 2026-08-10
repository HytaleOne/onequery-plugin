package dev.hytaleone.query.network;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.io.ServerManager;
import dev.hytaleone.query.config.NetworkConfig;
import dev.hytaleone.query.network.cache.LocalStateCache;
import dev.hytaleone.query.network.model.NetworkEvent;
import dev.hytaleone.query.network.model.NetworkSnapshot;
import dev.hytaleone.query.network.model.PlayerInfo;
import dev.hytaleone.query.network.model.ServerState;
import dev.hytaleone.query.network.store.NetworkStateStore;
import dev.hytaleone.query.network.store.RedisStateStore;
import dev.hytaleone.query.protocol.ServerDataProvider;
import io.lettuce.core.RedisURI;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Level;

public class NetworkModule {

    public static final int SERVER_TIMEOUT_SECONDS = 45;
    public static final long SERVER_TIMEOUT_MILLIS = SERVER_TIMEOUT_SECONDS * 1000L;

    private final HytaleLogger logger;
    private final NetworkConfig config;
    private final String serverId;
    private final String serverName;

    private volatile NetworkStateStore store;
    private volatile boolean initialized = false;

    public NetworkModule(@Nonnull HytaleLogger logger,
                         @Nonnull NetworkConfig config,
                         @Nonnull String serverId,
                         @Nonnull String serverName) {
        this.logger = logger;
        this.config = config;
        this.serverId = serverId;
        this.serverName = serverName;
    }

    @Nonnull
    public CompletableFuture<Void> start() {
        if (!config.isEnabled()) {
            logger.at(Level.FINE).log("Network mode disabled");
            return CompletableFuture.completedFuture(null);
        }

        NetworkStateStore newStore = createStore();
        store = newStore;

        return newStore.start()
                .thenCompose(v -> {
                    ServerState state = buildInitialServerState();
                    return newStore.registerServer(state);
                })
                .thenRun(() -> {
                    initialized = true;
                    logger.at(Level.FINE).log("Network module started (mode=%s, store=%s, network=%s)",
                            config.getMode(), config.getStore().getType(), config.getNetworkId());
                })
                .exceptionally(e -> {
                    store = null;
                    logger.at(Level.SEVERE).withCause(e).log("Failed to start network module");
                    throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
                });
    }

    @Nonnull
    public CompletableFuture<Void> stop() {
        NetworkStateStore currentStore = store;
        if (currentStore == null) {
            return CompletableFuture.completedFuture(null);
        }

        initialized = false;
        store = null;

        return currentStore.unregisterServer(serverId)
                .thenCompose(v -> currentStore.stop())
                .thenRun(() -> logger.at(Level.FINE).log("Network module stopped"));
    }

    public boolean isEnabled() {
        return config.isEnabled() && initialized && store != null;
    }

    public boolean shouldSync() {
        return config.shouldSync();
    }

    public boolean shouldAggregate() {
        return config.shouldAggregate();
    }

    @Nonnull
    public NetworkConfig getConfig() {
        return config;
    }

    @Nullable
    public NetworkStateStore getStore() {
        return store;
    }

    public void onPlayerJoin(@Nonnull UUID uuid, @Nonnull String username) {
        if (!isEnabled()) {
            return;
        }

        PlayerInfo player = PlayerInfo.of(uuid, username, serverId, serverName);
        store.onPlayerJoin(player)
                .exceptionally(e -> {
                    logger.at(Level.WARNING).withCause(e).log("Failed to record player join: %s", username);
                    return null;
                });
    }

    public void onPlayerLeave(@Nonnull UUID uuid) {
        if (!isEnabled()) {
            return;
        }

        store.onPlayerLeave(uuid, serverId)
                .exceptionally(e -> {
                    logger.at(Level.WARNING).withCause(e).log("Failed to record player leave: %s", uuid);
                    return null;
                });
    }

    @Nonnull
    public CompletableFuture<NetworkSnapshot> getNetworkSnapshot() {
        if (!isEnabled()) {
            return CompletableFuture.completedFuture(NetworkSnapshot.empty());
        }
        return store.getNetworkSnapshot();
    }

    @Nonnull
    public CompletableFuture<NetworkSnapshot> fetchNetworkSnapshot() {
        if (!isEnabled()) {
            return CompletableFuture.completedFuture(NetworkSnapshot.empty());
        }
        return store.fetchNetworkSnapshot();
    }

    @Nonnull
    public NetworkSnapshot getNetworkSnapshotSync() {
        LocalStateCache cache = getSubscriberCache();
        if (cache != null) {
            return cache.getSnapshot();
        }
        ServerState localState = store != null ? store.getLocalServerState() : null;
        if (localState != null) {
            return NetworkSnapshot.of(List.of(localState), getLocalPlayers());
        }
        return NetworkSnapshot.empty();
    }

    @Nonnull
    public CompletableFuture<List<PlayerInfo>> getAllPlayers() {
        if (!isEnabled()) {
            return CompletableFuture.completedFuture(List.of());
        }
        return store.getAllPlayers();
    }

    public int getPlayerCount() {
        if (!isEnabled()) {
            return ServerDataProvider.getPlayerCount();
        }
        LocalStateCache cache = getSubscriberCache();
        return cache != null ? cache.getTotalPlayerCount() : ServerDataProvider.getPlayerCount();
    }

    @Nonnull
    public List<PlayerInfo> getPlayers() {
        if (!isEnabled()) {
            return getLocalPlayers();
        }
        LocalStateCache cache = getSubscriberCache();
        return cache != null ? cache.getPlayers() : getLocalPlayers();
    }

    public void subscribe(@Nonnull Consumer<NetworkEvent> listener) {
        if (isEnabled()) {
            store.subscribe(listener);
        }
    }

    public void unsubscribe(@Nonnull Consumer<NetworkEvent> listener) {
        if (isEnabled()) {
            store.unsubscribe(listener);
        }
    }

    public int getServerCount() {
        if (!isEnabled()) {
            return 1;
        }
        LocalStateCache cache = getSubscriberCache();
        return cache != null ? cache.getServerCount() : 1;
    }

    public int getOnlineServerCount() {
        if (!isEnabled()) {
            return 1;
        }
        LocalStateCache cache = getSubscriberCache();
        return cache != null ? cache.getOnlineServerCount() : 1;
    }

    @Nullable
    private LocalStateCache getSubscriberCache() {
        if (shouldSync() && store instanceof RedisStateStore redisStore) {
            return redisStore.getCache();
        }
        return null;
    }

    @Nonnull
    private List<PlayerInfo> getLocalPlayers() {
        return ServerDataProvider.getPlayers().stream()
                .map(ref -> PlayerInfo.of(ref.getUuid(), ref.getUsername(), serverId, serverName))
                .toList();
    }

    private NetworkStateStore createStore() {
        if (!config.getStore().isRedis()) {
            throw new IllegalStateException("Network mode requires Redis configuration. Set Store.Type to 'redis' and configure Redis connection.");
        }
        RedisURI redisUri = config.getStore().getRedis().toRedisURI();
        return new RedisStateStore(
                logger,
                config.getNetworkId(),
                serverId,
                redisUri,
                config.getTiming(),
                config.shouldSync()
        );
    }

    private ServerState buildInitialServerState() {
        String host = null;
        int port = 5520;
        try {
            InetSocketAddress address = ServerManager.get().getNonLoopbackAddress();
            if (address != null) {
                host = address.getHostString();
                port = address.getPort();
            }
        } catch (SocketException e) {
            logger.at(Level.FINE).log("Could not get server address: %s", e.getMessage());
        }

        return ServerState.builder(serverId)
                .serverName(serverName)
                .playerCount(ServerDataProvider.getPlayerCount())
                .maxPlayers(ServerDataProvider.getMaxPlayers())
                .host(host)
                .port(port)
                .build();
    }
}
