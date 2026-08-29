package dev.hytaleone.query.network.store;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hypixel.hytale.logger.HytaleLogger;
import dev.hytaleone.query.config.NetworkConfig;
import dev.hytaleone.query.network.NetworkModule;
import dev.hytaleone.query.network.cache.LocalStateCache;
import dev.hytaleone.query.network.model.NetworkEvent;
import dev.hytaleone.query.network.model.NetworkSnapshot;
import dev.hytaleone.query.network.model.PlayerInfo;
import dev.hytaleone.query.network.model.ServerState;
import io.lettuce.core.*;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;

public class RedisStateStore implements NetworkStateStore {

    private static final Gson GSON = new GsonBuilder().create();
    private static final int STREAM_MAX_LEN = 1000;
    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    private static final String CONNECT_SCRIPT = """
            local playerKey = KEYS[1]
            local newPlayersKey = KEYS[2]
            local newServerKey = KEYS[3]
            local streamKey = KEYS[4]

            local uuid = ARGV[1]
            local newServerId = ARGV[2]
            local playerJson = ARGV[3]
            local ttlSeconds = tonumber(ARGV[4])
            local networkId = ARGV[5]
            local streamMaxLen = tonumber(ARGV[6])
            local username = ARGV[7]
            local serverName = ARGV[8]

            local oldServerId = redis.call('GET', playerKey)

            if oldServerId and oldServerId ~= newServerId then
                local oldPlayersKey = 'onequery:network:{' .. networkId .. '}:server:' .. oldServerId .. ':players'
                local oldServerKey = 'onequery:network:{' .. networkId .. '}:server:' .. oldServerId
                redis.call('HDEL', oldPlayersKey, uuid)
                local oldCount = redis.call('HGET', oldServerKey, 'playerCount')
                if oldCount and tonumber(oldCount) > 0 then
                    redis.call('HINCRBY', oldServerKey, 'playerCount', -1)
                end
            end

            redis.call('SET', playerKey, newServerId, 'EX', ttlSeconds)

            local alreadyOnServer = redis.call('HEXISTS', newPlayersKey, uuid)
            redis.call('HSET', newPlayersKey, uuid, playerJson)
            if alreadyOnServer == 0 then
                redis.call('HINCRBY', newServerKey, 'playerCount', 1)
            end

            local event = cjson.encode({type='join', uuid=uuid, server=newServerId, username=username, serverName=serverName})
            redis.call('XADD', streamKey, 'MAXLEN', '~', streamMaxLen, '*', 'data', event)

            return oldServerId or 'none'
            """;

    private static final String DISCONNECT_SCRIPT = """
            local playerKey = KEYS[1]
            local playersKey = KEYS[2]
            local serverKey = KEYS[3]
            local streamKey = KEYS[4]

            local serverId = ARGV[1]
            local uuid = ARGV[2]
            local streamMaxLen = tonumber(ARGV[3])

            local current = redis.call('GET', playerKey)
            if current == serverId then
                redis.call('DEL', playerKey)
                redis.call('HDEL', playersKey, uuid)
                local count = redis.call('HGET', serverKey, 'playerCount')
                if count and tonumber(count) > 0 then
                    redis.call('HINCRBY', serverKey, 'playerCount', -1)
                end
                local event = cjson.encode({type='leave', uuid=uuid, server=serverId})
                redis.call('XADD', streamKey, 'MAXLEN', '~', streamMaxLen, '*', 'data', event)
                return 1
            end
            return 0
            """;

    private static final String REGISTER_SCRIPT = """
            local serverKey = KEYS[1]
            local serversSetKey = KEYS[2]
            local streamKey = KEYS[3]

            local serverId = ARGV[1]
            local ttlSeconds = tonumber(ARGV[2])
            local streamMaxLen = tonumber(ARGV[3])

            for i = 4, #ARGV, 2 do
                redis.call('HSET', serverKey, ARGV[i], ARGV[i+1])
            end

            redis.call('EXPIRE', serverKey, ttlSeconds)
            redis.call('SADD', serversSetKey, serverId)

            local serverName = redis.call('HGET', serverKey, 'serverName') or serverId
            local event = cjson.encode({type='server_online', serverId=serverId, serverName=serverName})
            redis.call('XADD', streamKey, 'MAXLEN', '~', streamMaxLen, '*', 'data', event)

            return 'OK'
            """;

    private static final String HEARTBEAT_SCRIPT = """
            local serverKey = KEYS[1]
            local playersKey = KEYS[2]

            local ttlSeconds = tonumber(ARGV[1])
            local networkId = ARGV[2]

            for i = 3, #ARGV, 2 do
                if ARGV[i] ~= 'playerCount' then
                    redis.call('HSET', serverKey, ARGV[i], ARGV[i+1])
                end
            end

            redis.call('EXPIRE', serverKey, ttlSeconds)
            redis.call('EXPIRE', playersKey, ttlSeconds)

            local playerUuids = redis.call('HKEYS', playersKey)
            for _, uuid in ipairs(playerUuids) do
                redis.call('EXPIRE', 'onequery:network:{' .. networkId .. '}:player:' .. uuid, ttlSeconds)
            end

            return 'OK'
            """;

    private static final String UNREGISTER_SCRIPT = """
            local serverKey = KEYS[1]
            local playersKey = KEYS[2]
            local serversSetKey = KEYS[3]
            local streamKey = KEYS[4]

            local serverId = ARGV[1]
            local streamMaxLen = tonumber(ARGV[2])

            redis.call('DEL', serverKey, playersKey)
            redis.call('SREM', serversSetKey, serverId)

            local event = cjson.encode({type='server_offline', serverId=serverId})
            redis.call('XADD', streamKey, 'MAXLEN', '~', streamMaxLen, '*', 'data', event)

            return 'OK'
            """;

    private final HytaleLogger logger;
    private final String networkId;
    private final String serverId;
    private final String redisUri;
    private final NetworkConfig.TimingConfig timing;
    private final boolean subscribe;
    private final LocalStateCache cache;
    private final CopyOnWriteArrayList<Consumer<NetworkEvent>> listeners = new CopyOnWriteArrayList<>();

    private volatile String streamCursor = "0";

    private ExecutorService redisExecutor;
    private ClientResources clientResources;
    private RedisClient redisClient;
    private StatefulRedisConnection<String, String> connection;
    private RedisAsyncCommands<String, String> commands;
    private ScheduledExecutorService heartbeatScheduler;
    private ScheduledExecutorService hubScheduler;
    private ScheduledFuture<?> heartbeatTask;
    private ScheduledFuture<?> refreshTask;
    private ScheduledFuture<?> streamReaderTask;

    private volatile ServerState localServerState;
    private volatile boolean connected = false;
    private volatile String connectScriptSha;
    private volatile String disconnectScriptSha;
    private volatile String registerScriptSha;
    private volatile String heartbeatScriptSha;
    private volatile String unregisterScriptSha;

    public RedisStateStore(@Nonnull HytaleLogger logger,
                           @Nonnull String networkId,
                           @Nonnull String serverId,
                           @Nonnull String redisUri,
                           @Nonnull NetworkConfig.TimingConfig timing,
                           boolean subscribe) {
        this.logger = logger;
        this.networkId = networkId;
        this.serverId = serverId;
        this.redisUri = redisUri;
        this.timing = timing;
        this.subscribe = subscribe;
        this.cache = new LocalStateCache();
        this.cache.setServerTimeout(NetworkModule.SERVER_TIMEOUT_MILLIS);
    }

    public RedisStateStore(@Nonnull HytaleLogger logger,
                           @Nonnull String networkId,
                           @Nonnull String serverId,
                           @Nonnull RedisURI redisUri,
                           @Nonnull NetworkConfig.TimingConfig timing,
                           boolean subscribe) {
        this.logger = logger;
        this.networkId = networkId;
        this.serverId = serverId;
        this.redisUri = redisUri.toURI().toString();
        this.timing = timing;
        this.subscribe = subscribe;
        this.cache = new LocalStateCache();
        this.cache.setServerTimeout(NetworkModule.SERVER_TIMEOUT_MILLIS);
    }

    @Override
    @Nonnull
    public CompletableFuture<Void> start() {
        redisExecutor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "onequery-redis-io");
            t.setDaemon(true);
            return t;
        });

        return CompletableFuture.runAsync(() -> {
            try {
                clientResources = DefaultClientResources.create();

                SocketOptions socketOptions = SocketOptions.builder()
                        .connectTimeout(CONNECT_TIMEOUT)
                        .keepAlive(true)
                        .build();

                ClientOptions clientOptions = ClientOptions.builder()
                        .socketOptions(socketOptions)
                        .timeoutOptions(TimeoutOptions.enabled(COMMAND_TIMEOUT))
                        .autoReconnect(true)
                        .build();

                redisClient = RedisClient.create(clientResources, redisUri);
                redisClient.setOptions(clientOptions);

                connection = redisClient.connect();
                commands = connection.async();

                loadScriptsSync();

                heartbeatScheduler = new ScheduledThreadPoolExecutor(1, r -> {
                    Thread t = new Thread(r, "onequery-heartbeat");
                    t.setDaemon(true);
                    return t;
                });

                if (subscribe) {
                    hubScheduler = new ScheduledThreadPoolExecutor(2, r -> {
                        Thread t = new Thread(r, "onequery-hub");
                        t.setDaemon(true);
                        return t;
                    });
                    startStreamReader();
                    startCacheRefresh();
                }

                connected = true;
                logger.at(Level.FINE).log("Redis connected to %s", redisUri);

            } catch (Exception e) {
                logger.at(Level.SEVERE).withCause(e).log("Failed to connect to Redis");
                throw new RuntimeException("Failed to connect to Redis", e);
            }
        }, redisExecutor);
    }

    @Override
    @Nonnull
    public CompletableFuture<Void> stop() {
        connected = false;

        return CompletableFuture.runAsync(() -> {
            try {
                if (heartbeatTask != null) heartbeatTask.cancel(false);
                if (refreshTask != null) refreshTask.cancel(false);
                if (streamReaderTask != null) streamReaderTask.cancel(false);

                if (heartbeatScheduler != null) heartbeatScheduler.shutdownNow();
                if (hubScheduler != null) hubScheduler.shutdownNow();

                if (connection != null) connection.close();
                if (redisClient != null) redisClient.shutdown();
                if (clientResources != null) clientResources.shutdown();
                if (redisExecutor != null) redisExecutor.shutdownNow();

                cache.clear();
                listeners.clear();

                logger.at(Level.FINE).log("Redis disconnected");
            } catch (Exception e) {
                logger.at(Level.WARNING).withCause(e).log("Error during Redis shutdown");
            }
        }, redisExecutor != null ? redisExecutor : Runnable::run);
    }

    @Override
    public boolean isConnected() {
        return connected && connection != null && connection.isOpen();
    }

    @Override
    @Nonnull
    public ServerState getLocalServerState() {
        return localServerState;
    }

    @Override
    @Nonnull
    public CompletableFuture<Void> registerServer(@Nonnull ServerState state) {
        this.localServerState = state;

        List<String> args = new ArrayList<>();
        args.add(serverId);
        args.add(String.valueOf(NetworkModule.SERVER_TIMEOUT_SECONDS * 2));
        args.add(String.valueOf(STREAM_MAX_LEN));

        Map<String, String> fields = serverStateToMap(state);
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            args.add(entry.getKey());
            args.add(entry.getValue());
        }

        String[] keys = {
                key("server", serverId),
                key("servers"),
                key("events")
        };

        return evalWithRetry(() -> registerScriptSha, REGISTER_SCRIPT, ScriptOutputType.VALUE,
                keys, args.toArray(new String[0]))
                .thenCompose(v -> {
                    startHeartbeat();
                    return CompletableFuture.completedFuture(null);
                })
                .thenAccept(v -> {
                    logger.at(Level.FINE).log("Registered server %s with network %s", serverId, networkId);
                    notifyListeners(new NetworkEvent.ServerOnline(state.serverId(), state.serverName()));
                });
    }

    @Override
    @Nonnull
    public CompletableFuture<Void> unregisterServer(@Nonnull String serverId) {
        String[] keys = {
                key("server", serverId),
                key("server", serverId, "players"),
                key("servers"),
                key("events")
        };
        String[] args = {
                serverId,
                String.valueOf(STREAM_MAX_LEN)
        };

        return evalWithRetry(() -> unregisterScriptSha, UNREGISTER_SCRIPT, ScriptOutputType.VALUE, keys, args)
                .thenAccept(v -> {
                    logger.at(Level.FINE).log("Unregistered server %s from network %s", serverId, networkId);
                    notifyListeners(new NetworkEvent.ServerOffline(serverId, "shutdown"));
                });
    }

    @Override
    @Nonnull
    public CompletableFuture<Void> onPlayerJoin(@Nonnull PlayerInfo player) {
        String playerJson = GSON.toJson(new PlayerData(player.username(), player.joinTime()));
        int ttlSeconds = NetworkModule.SERVER_TIMEOUT_SECONDS * 2;

        String[] keys = {
                key("player", player.uuid().toString()),
                key("server", serverId, "players"),
                key("server", serverId),
                key("events")
        };

        String serverName = localServerState != null ? localServerState.serverName() : serverId;
        String[] args = {
                player.uuid().toString(),
                serverId,
                playerJson,
                String.valueOf(ttlSeconds),
                networkId,
                String.valueOf(STREAM_MAX_LEN),
                player.username(),
                serverName
        };

        return evalWithRetry(() -> connectScriptSha, CONNECT_SCRIPT, ScriptOutputType.VALUE, keys, args)
                .thenAccept(result -> {
                    logger.at(Level.FINE).log("Player %s joined %s (was on: %s)",
                            player.username(), serverId, result);
                });
    }

    @Override
    @Nonnull
    public CompletableFuture<Void> onPlayerLeave(@Nonnull UUID playerId, @Nonnull String serverId) {
        String[] keys = {
                key("player", playerId.toString()),
                key("server", serverId, "players"),
                key("server", serverId),
                key("events")
        };
        String[] args = {
                serverId,
                playerId.toString(),
                String.valueOf(STREAM_MAX_LEN)
        };

        return evalWithRetry(() -> disconnectScriptSha, DISCONNECT_SCRIPT, ScriptOutputType.INTEGER, keys, args)
                .thenAccept(result -> {
                    long deleted = (result instanceof Number) ? ((Number) result).longValue() : 0;
                    logger.at(Level.FINE).log("Player %s left %s (deleted=%d)", playerId, serverId, deleted);
                });
    }

    @Override
    @Nonnull
    public CompletableFuture<Void> heartbeat(@Nonnull ServerState state) {
        this.localServerState = state;

        List<String> args = new ArrayList<>();
        args.add(String.valueOf(NetworkModule.SERVER_TIMEOUT_SECONDS * 2));
        args.add(networkId);

        Map<String, String> fields = serverStateToMap(state);
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            args.add(entry.getKey());
            args.add(entry.getValue());
        }

        String[] keys = {
                key("server", serverId),
                key("server", serverId, "players")
        };

        return evalWithRetry(() -> heartbeatScriptSha, HEARTBEAT_SCRIPT, ScriptOutputType.VALUE,
                keys, args.toArray(new String[0]))
                .thenAccept(v -> {
                    logger.at(Level.FINE).log("Heartbeat sent for %s", serverId);
                });
    }

    @Override
    @Nonnull
    public CompletableFuture<NetworkSnapshot> getNetworkSnapshot() {
        if (subscribe) {
            return CompletableFuture.completedFuture(cache.getSnapshot());
        } else {
            return getAllPlayers().thenApply(players ->
                    NetworkSnapshot.of(List.of(localServerState), players));
        }
    }

    @Override
    @Nonnull
    public CompletableFuture<NetworkSnapshot> fetchNetworkSnapshot() {
        return fetchFullSnapshot();
    }

    @Override
    @Nonnull
    public CompletableFuture<List<PlayerInfo>> getAllPlayers() {
        if (subscribe) {
            return CompletableFuture.completedFuture(cache.getPlayers());
        } else {
            return commands.hgetall(key("server", serverId, "players"))
                    .thenApply(this::parsePlayerMap)
                    .toCompletableFuture();
        }
    }

    @Override
    public void subscribe(@Nonnull Consumer<NetworkEvent> listener) {
        listeners.add(listener);
    }

    @Override
    public void unsubscribe(@Nonnull Consumer<NetworkEvent> listener) {
        listeners.remove(listener);
    }

    @Nonnull
    public LocalStateCache getCache() {
        return cache;
    }

    private String key(String... parts) {
        return "onequery:network:{" + networkId + "}:" + String.join(":", parts);
    }

    private void loadScriptsSync() {
        try {
            CompletableFuture.allOf(
                    commands.scriptLoad(CONNECT_SCRIPT).thenAccept(sha -> connectScriptSha = sha).toCompletableFuture(),
                    commands.scriptLoad(DISCONNECT_SCRIPT).thenAccept(sha -> disconnectScriptSha = sha).toCompletableFuture(),
                    commands.scriptLoad(REGISTER_SCRIPT).thenAccept(sha -> registerScriptSha = sha).toCompletableFuture(),
                    commands.scriptLoad(HEARTBEAT_SCRIPT).thenAccept(sha -> heartbeatScriptSha = sha).toCompletableFuture(),
                    commands.scriptLoad(UNREGISTER_SCRIPT).thenAccept(sha -> unregisterScriptSha = sha).toCompletableFuture()
            ).get(COMMAND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            logger.at(Level.FINE).log("Loaded Lua scripts");
        } catch (Exception e) {
            throw new RuntimeException("Failed to load Lua scripts", e);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> CompletableFuture<T> evalWithRetry(Supplier<String> shaSupplier, String script,
                                                    ScriptOutputType outputType, String[] keys, String[] args) {
        CompletionStage<T> stage = commands.evalsha(shaSupplier.get(), outputType, keys, args)
                .handle((result, ex) -> {
                    if (ex != null) {
                        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                        if (cause instanceof RedisCommandExecutionException rce
                                && rce.getMessage() != null
                                && rce.getMessage().contains("NOSCRIPT")) {
                            logger.at(Level.FINE).log("NOSCRIPT -> EVAL fallback");
                            return (CompletionStage<T>) commands.eval(script, outputType, keys, args);
                        }
                        return CompletableFuture.<T>failedFuture(cause);
                    }
                    return CompletableFuture.completedFuture((T) result);
                })
                .thenCompose(s -> s);

        return stage.toCompletableFuture();
    }

    private void startStreamReader() {
        streamReaderTask = hubScheduler.scheduleWithFixedDelay(() -> {
            if (!connected) return;
            try {
                readStreamEvents();
            } catch (Exception e) {
                if (!(e instanceof TimeoutException)) {
                    logger.at(Level.WARNING).withCause(e).log("Error reading stream events");
                }
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
    }

    private void readStreamEvents() {
        try {
            String cursor = streamCursor;
            if (cursor == null || cursor.isEmpty()) {
                logger.at(Level.WARNING).log("Stream cursor unexpectedly null, skipping poll");
                return;
            }

            List<StreamMessage<String, String>> messages = commands.xread(
                    XReadArgs.Builder.count(100).block(50),
                    XReadArgs.StreamOffset.from(key("events"), cursor)
            ).get(200, TimeUnit.MILLISECONDS);

            if (messages == null || messages.isEmpty()) return;

            String lastId = null;
            for (StreamMessage<String, String> message : messages) {
                lastId = message.getId();
                String data = message.getBody().get("data");
                if (data != null) {
                    handleStreamMessage(data);
                }
            }

            if (lastId != null) {
                streamCursor = lastId;
            }
        } catch (TimeoutException e) {
        } catch (Exception e) {
            logger.at(Level.FINE).log("Stream read: %s", e.getMessage());
        }
    }

    private void handleStreamMessage(@Nonnull String data) {
        try {
            StreamEvent event = GSON.fromJson(data, StreamEvent.class);
            if (event == null || event.type == null) return;

            switch (event.type) {
                case "join" -> {
                    if (event.uuid != null && event.server != null) {
                        UUID uuid = UUID.fromString(event.uuid);
                        String username = event.username != null ? event.username : "";
                        String serverName = event.serverName != null ? event.serverName : event.server;
                        cache.updatePlayer(new PlayerInfo(uuid, username, event.server, serverName, System.currentTimeMillis()));
                        notifyListeners(new NetworkEvent.PlayerJoin(uuid, username, event.server, serverName));
                    }
                }
                case "leave" -> {
                    if (event.uuid != null) {
                        UUID uuid = UUID.fromString(event.uuid);
                        cache.removePlayer(uuid);
                        notifyListeners(new NetworkEvent.PlayerLeave(uuid, event.server));
                    }
                }
                case "server_online" -> {
                    if (event.serverId != null) {
                        notifyListeners(new NetworkEvent.ServerOnline(
                                event.serverId,
                                event.serverName != null ? event.serverName : event.serverId));
                    }
                }
                case "server_offline" -> {
                    if (event.serverId != null) {
                        cache.removeServer(event.serverId);
                        notifyListeners(new NetworkEvent.ServerOffline(event.serverId, null));
                    }
                }
            }
        } catch (Exception e) {
            logger.at(Level.FINE).log("Failed to parse stream message: %s", data);
        }
    }

    private void startHeartbeat() {
        heartbeatTask = heartbeatScheduler.scheduleAtFixedRate(() -> {
            if (connected && localServerState != null) {
                heartbeat(localServerState.withHeartbeat(System.currentTimeMillis()))
                        .exceptionally(e -> {
                            logger.at(Level.WARNING).withCause(e).log("Heartbeat failed");
                            return null;
                        });
            }
        }, timing.getHeartbeatIntervalSeconds(), timing.getHeartbeatIntervalSeconds(), TimeUnit.SECONDS);
    }

    private void startCacheRefresh() {
        refreshCache();

        refreshTask = hubScheduler.scheduleAtFixedRate(() -> {
            if (connected) {
                refreshCache();
            }
        }, timing.getCacheRefreshSeconds(), timing.getCacheRefreshSeconds(), TimeUnit.SECONDS);
    }

    private void refreshCache() {
        fetchFullSnapshot()
                .thenAccept(snapshot -> {
                    cache.refresh(snapshot);
                    logger.at(Level.FINE).log("Cache refreshed: %d servers, %d players",
                            snapshot.getServerCount(), snapshot.getTotalPlayerCount());
                })
                .exceptionally(e -> {
                    logger.at(Level.WARNING).withCause(e).log("Cache refresh failed");
                    return null;
                });
    }

    private CompletableFuture<NetworkSnapshot> fetchFullSnapshot() {
        return commands.smembers(key("servers"))
                .thenCompose(serverIds -> {
                    if (serverIds.isEmpty()) {
                        return CompletableFuture.completedFuture(NetworkSnapshot.empty());
                    }

                    List<CompletableFuture<ServerWithPlayers>> futures = new ArrayList<>();
                    for (String sid : serverIds) {
                        futures.add(fetchServerWithPlayers(sid));
                    }

                    return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                            .thenApply(v -> {
                                List<ServerState> servers = new ArrayList<>();
                                List<PlayerInfo> players = new ArrayList<>();
                                for (CompletableFuture<ServerWithPlayers> f : futures) {
                                    ServerWithPlayers swp = f.join();
                                    if (swp != null && swp.server != null) {
                                        servers.add(swp.server);
                                        players.addAll(swp.players);
                                    }
                                }
                                return NetworkSnapshot.of(servers, players);
                            });
                })
                .toCompletableFuture();
    }

    private CompletableFuture<ServerWithPlayers> fetchServerWithPlayers(@Nonnull String serverId) {
        var serverFuture = commands.hgetall(key("server", serverId));
        var playersFuture = commands.hgetall(key("server", serverId, "players"));

        return serverFuture.thenCombine(playersFuture, (serverMap, playersMap) -> {
            if (serverMap.isEmpty()) {
                commands.srem(key("servers"), serverId)
                        .thenCompose(removed -> {
                            if (removed > 0) {
                                logger.at(Level.FINE).log("Removed orphaned server from set: %s", serverId);
                                String event = GSON.toJson(Map.of("type", "server_offline", "serverId", serverId));
                                return commands.xadd(key("events"), "data", event);
                            }
                            return CompletableFuture.completedFuture(null);
                        });
                return null;
            }
            ServerState server = parseServerState(serverId, serverMap);
            List<PlayerInfo> players = parsePlayerMapWithServer(playersMap, serverId, server.serverName());
            return new ServerWithPlayers(server, players);
        }).toCompletableFuture();
    }

    private record ServerWithPlayers(ServerState server, List<PlayerInfo> players) {}

    private ServerState parseServerState(@Nonnull String serverId, @Nonnull Map<String, String> map) {
        return ServerState.builder(serverId)
                .serverName(map.getOrDefault("serverName", serverId))
                .playerCount(parseIntOrDefault(map.get("playerCount"), 0))
                .maxPlayers(parseIntOrDefault(map.get("maxPlayers"), 100))
                .host(map.get("host"))
                .port(parseIntOrDefault(map.get("port"), 5520))
                .lastHeartbeat(parseLongOrDefault(map.get("lastHeartbeat"), System.currentTimeMillis()))
                .build();
    }

    private List<PlayerInfo> parsePlayerMap(@Nonnull Map<String, String> map) {
        return parsePlayerMapWithServer(map, serverId, localServerState != null ? localServerState.serverName() : "");
    }

    private List<PlayerInfo> parsePlayerMapWithServer(@Nonnull Map<String, String> map,
                                                       @Nonnull String serverId,
                                                       @Nonnull String serverName) {
        List<PlayerInfo> players = new ArrayList<>();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            try {
                UUID uuid = UUID.fromString(entry.getKey());
                PlayerData data = GSON.fromJson(entry.getValue(), PlayerData.class);
                players.add(new PlayerInfo(uuid, data.username, serverId, serverName, data.joinTime));
            } catch (Exception e) {
                logger.at(Level.FINE).log("Failed to parse player entry: %s", entry.getKey());
            }
        }
        return players;
    }

    private Map<String, String> serverStateToMap(@Nonnull ServerState state) {
        Map<String, String> map = new HashMap<>();
        map.put("serverName", state.serverName());
        map.put("playerCount", String.valueOf(state.playerCount()));
        map.put("maxPlayers", String.valueOf(state.maxPlayers()));
        if (state.host() != null) {
            map.put("host", state.host());
        }
        map.put("port", String.valueOf(state.port()));
        map.put("lastHeartbeat", String.valueOf(state.lastHeartbeat()));
        return map;
    }

    private void notifyListeners(@Nonnull NetworkEvent event) {
        for (Consumer<NetworkEvent> listener : listeners) {
            try {
                listener.accept(event);
            } catch (Exception e) {
                logger.at(Level.WARNING).withCause(e).log("Error in event listener");
            }
        }
    }

    private static int parseIntOrDefault(@Nullable String value, int defaultValue) {
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static long parseLongOrDefault(@Nullable String value, long defaultValue) {
        if (value == null) return defaultValue;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private record PlayerData(String username, long joinTime) {}

    private static class StreamEvent {
        String type;
        String uuid;
        String server;
        String serverId;
        String serverName;
        String username;
    }
}
