package dev.hytaleone.query.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import io.lettuce.core.RedisURI;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Configuration for network mode.
 *
 * <p>Example configuration for a game server (publish only):
 * <pre>
 * {
 *   "Network": {
 *     "Enabled": true,
 *     "ServerId": "survival-0",
 *     "NetworkId": "my-network",
 *     "Mode": "PUBLISH",
 *     "Store": {
 *       "Type": "redis",
 *       "Redis": {
 *         "Host": "redis.example.com",
 *         "Port": 6379
 *       }
 *     }
 *   }
 * }
 * </pre>
 *
 * <p>Example configuration for a lobby/hub (aggregate mode):
 * <pre>
 * {
 *   "Network": {
 *     "Enabled": true,
 *     "ServerId": "lobby-0",
 *     "NetworkId": "my-network",
 *     "Mode": "AGGREGATE",
 *     "Store": {
 *       "Type": "redis",
 *       "Redis": {
 *         "Host": "redis.example.com",
 *         "Port": 6379
 *       }
 *     }
 *   }
 * }
 * </pre>
 *
 * @see NetworkMode
 */
public class NetworkConfig {

    public static final BuilderCodec<NetworkConfig> CODEC = BuilderCodec.builder(NetworkConfig.class, NetworkConfig::new)
            .addField(new KeyedCodec<>("Enabled", Codec.BOOLEAN),
                    (o, v) -> o.enabled = v, o -> o.enabled)
            .addField(new KeyedCodec<>("ServerId", Codec.STRING),
                    (o, v) -> o.serverId = v, o -> o.serverId)
            .addField(new KeyedCodec<>("NetworkId", Codec.STRING),
                    (o, v) -> o.networkId = v, o -> o.networkId)
            .addField(new KeyedCodec<>("Mode", Codec.STRING),
                    (o, v) -> o.mode = parseMode(v), o -> o.mode.name())
            .addField(new KeyedCodec<>("Store", StoreConfig.CODEC),
                    (o, v) -> o.store = v != null ? v : new StoreConfig(),
                    o -> o.store)
            .addField(new KeyedCodec<>("Timing", TimingConfig.CODEC),
                    (o, v) -> o.timing = v != null ? v : new TimingConfig(),
                    o -> o.timing)
            .build();

    private boolean enabled = false;
    private String serverId = "server-1";
    private String networkId = "default";
    private NetworkMode mode = NetworkMode.AGGREGATE;
    private StoreConfig store = new StoreConfig();
    private TimingConfig timing = new TimingConfig();

    private static NetworkMode parseMode(String value) {
        if (value == null || value.isBlank()) {
            return NetworkMode.AGGREGATE;
        }
        try {
            return NetworkMode.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return NetworkMode.AGGREGATE;
        }
    }

    public NetworkConfig() {
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Nonnull
    public String getServerId() {
        return serverId != null && !serverId.isBlank() ? serverId : "server-1";
    }

    public void setServerId(@Nullable String serverId) {
        this.serverId = serverId;
    }

    @Nonnull
    public String getNetworkId() {
        return networkId != null ? networkId : "default";
    }

    public void setNetworkId(@Nullable String networkId) {
        this.networkId = networkId;
    }

    @Nonnull
    public NetworkMode getMode() {
        return mode;
    }

    public void setMode(@Nonnull NetworkMode mode) {
        this.mode = mode;
    }

    /**
     * Check if this server subscribes to network events (SYNC or AGGREGATE mode).
     */
    public boolean shouldSync() {
        return mode == NetworkMode.SYNC || mode == NetworkMode.AGGREGATE;
    }

    /**
     * Check if this server serves aggregated data in responses (AGGREGATE mode).
     */
    public boolean shouldAggregate() {
        return mode == NetworkMode.AGGREGATE;
    }

    @Nonnull
    public StoreConfig getStore() {
        return store;
    }

    public void setStore(@Nonnull StoreConfig store) {
        this.store = store;
    }

    @Nonnull
    public TimingConfig getTiming() {
        return timing;
    }

    public void setTiming(@Nonnull TimingConfig timing) {
        this.timing = timing;
    }

    /**
     * Configuration for the state store.
     */
    public static class StoreConfig {

        public static final BuilderCodec<StoreConfig> CODEC = BuilderCodec.builder(StoreConfig.class, StoreConfig::new)
                .addField(new KeyedCodec<>("Type", Codec.STRING),
                        (o, v) -> o.type = v, o -> o.type)
                .addField(new KeyedCodec<>("Redis", RedisConfig.CODEC),
                        (o, v) -> o.redis = v != null ? v : new RedisConfig(),
                        o -> o.redis)
                .build();

        private String type = "redis";
        private RedisConfig redis = new RedisConfig();

        public StoreConfig() {
        }

        @Nonnull
        public String getType() {
            return type != null ? type : "redis";
        }

        public void setType(@Nullable String type) {
            this.type = type;
        }

        public boolean isRedis() {
            return "redis".equalsIgnoreCase(type) || type == null;
        }

        @Nonnull
        public RedisConfig getRedis() {
            return redis;
        }

        public void setRedis(@Nonnull RedisConfig redis) {
            this.redis = redis;
        }
    }

    /**
     * Redis connection configuration.
     */
    public static class RedisConfig {

        public static final BuilderCodec<RedisConfig> CODEC = BuilderCodec.builder(RedisConfig.class, RedisConfig::new)
                .addField(new KeyedCodec<>("Host", Codec.STRING),
                        (o, v) -> o.host = v, o -> o.host)
                .addField(new KeyedCodec<>("Port", Codec.INTEGER),
                        (o, v) -> o.port = v, o -> o.port)
                .addField(new KeyedCodec<>("Username", Codec.STRING),
                        (o, v) -> o.username = v, o -> o.username)
                .addField(new KeyedCodec<>("Password", Codec.STRING),
                        (o, v) -> o.password = v, o -> o.password)
                .addField(new KeyedCodec<>("Database", Codec.INTEGER),
                        (o, v) -> o.database = v, o -> o.database)
                .addField(new KeyedCodec<>("UseTLS", Codec.BOOLEAN),
                        (o, v) -> o.useTLS = v, o -> o.useTLS)
                .build();

        private String host = "localhost";
        private int port = 6379;
        private String username = null;
        private String password = null;
        private int database = 0;
        private boolean useTLS = false;

        public RedisConfig() {
        }

        @Nonnull
        public String getHost() {
            return host != null ? host : "localhost";
        }

        public void setHost(@Nullable String host) {
            this.host = host;
        }

        public int getPort() {
            return port > 0 ? port : 6379;
        }

        public void setPort(int port) {
            this.port = port;
        }

        @Nullable
        public String getUsername() {
            return username;
        }

        public void setUsername(@Nullable String username) {
            this.username = username;
        }

        @Nullable
        public String getPassword() {
            return password;
        }

        public void setPassword(@Nullable String password) {
            this.password = password;
        }

        public int getDatabase() {
            return database >= 0 ? database : 0;
        }

        public void setDatabase(int database) {
            this.database = database;
        }

        public boolean isUseTLS() {
            return useTLS;
        }

        public void setUseTLS(boolean useTLS) {
            this.useTLS = useTLS;
        }

        /**
         * Build a Redis URI string for Lettuce.
         */
        @Deprecated
        @Nonnull
        public String toRedisUri() {
            StringBuilder sb = new StringBuilder();
            sb.append(useTLS ? "rediss://" : "redis://");
            if (username != null && !username.isEmpty() && password != null && !password.isEmpty()) {
                sb.append(username).append(":").append(password).append("@");
            } else if (password != null && !password.isEmpty()) {
                sb.append(":").append(password).append("@");
            }
            sb.append(host).append(":").append(port);
            if (database > 0) {
                sb.append("/").append(database);
            }
            return sb.toString();
        }

        /**
         * Build a Redis URI for Lettuce connection.
         */
        @Nonnull
        public RedisURI toRedisURI() {
            RedisURI.Builder builder = RedisURI.builder()
                    .withHost(getHost())
                    .withPort(getPort())
                    .withDatabase(getDatabase());
            if (getUsername() != null && !getUsername().isEmpty()) {
                builder.withAuthentication(getUsername(), getPassword() != null ? getPassword().toCharArray() : new char[0]);
            } else if (getPassword() != null && !getPassword().isEmpty()) {
                builder.withPassword(getPassword().toCharArray());
            }
            if (isUseTLS()) {
                builder.withSsl(true);
            }
            return builder.build();
        }
    }

    /**
     * Timeout configuration.
     */
    public static class TimingConfig {

        public static final BuilderCodec<TimingConfig> CODEC = BuilderCodec.builder(TimingConfig.class, TimingConfig::new)
                .addField(new KeyedCodec<>("HeartbeatIntervalSeconds", Codec.INTEGER),
                        (o, v) -> o.heartbeatIntervalSeconds = v, o -> o.heartbeatIntervalSeconds)
                .addField(new KeyedCodec<>("CacheRefreshSeconds", Codec.INTEGER),
                        (o, v) -> o.cacheRefreshSeconds = v, o -> o.cacheRefreshSeconds)
                .build();

        private int heartbeatIntervalSeconds = 15;
        private int cacheRefreshSeconds = 60;

        public TimingConfig() {
        }

        public int getHeartbeatIntervalSeconds() {
            return Math.max(5, Math.min(heartbeatIntervalSeconds, 300));
        }

        public void setHeartbeatIntervalSeconds(int seconds) {
            this.heartbeatIntervalSeconds = seconds;
        }

        public long getHeartbeatIntervalMillis() {
            return getHeartbeatIntervalSeconds() * 1000L;
        }

        public int getCacheRefreshSeconds() {
            return Math.max(10, Math.min(cacheRefreshSeconds, 300));
        }

        public void setCacheRefreshSeconds(int seconds) {
            this.cacheRefreshSeconds = seconds;
        }

        public long getCacheRefreshMillis() {
            return getCacheRefreshSeconds() * 1000L;
        }
    }
}
