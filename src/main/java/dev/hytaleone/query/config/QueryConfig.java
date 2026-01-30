package dev.hytaleone.query.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

import javax.annotation.Nonnull;

/**
 * Main configuration for the HytaleOne Query plugin.
 *
 * Example:
 * {
 *   "Enabled": true,
 *   "LegacyProtocolEnabled": true,
 *   "ServerList": {
 *     "Enabled": true,
 *     "ServerId": "hytaleone_abc123..."
 *   },
 *   "Authentication": {
 *     "Public": {
 *       "Basic": true,
 *       "Players": false
 *     },
 *     "Tokens": {
 *       "my-secret-token": {
 *         "Basic": true,
 *         "Players": true
 *       }
 *     }
 *   }
 * }
 */
public class QueryConfig {

    public static final BuilderCodec<QueryConfig> CODEC = BuilderCodec.builder(QueryConfig.class, QueryConfig::new)
            .addField(new KeyedCodec<>("Enabled", Codec.BOOLEAN),
                    (o, v) -> o.enabled = v, o -> o.enabled)
            .addField(new KeyedCodec<>("LegacyProtocolEnabled", Codec.BOOLEAN),
                    (o, v) -> o.legacyProtocolEnabled = v, o -> o.legacyProtocolEnabled)
            .addField(new KeyedCodec<>("ServerList", ServerListConfig.CODEC),
                    (o, v) -> o.serverList = v != null ? v : new ServerListConfig(),
                    o -> o.serverList)
            .addField(new KeyedCodec<>("Authentication", AuthConfig.CODEC),
                    (o, v) -> o.authentication = v != null ? v : new AuthConfig(),
                    o -> o.authentication)
            .addField(new KeyedCodec<>("Network", NetworkConfig.CODEC),
                    (o, v) -> o.network = v != null ? v : new NetworkConfig(),
                    o -> o.network)
            .addField(new KeyedCodec<>("ServerInfo", ServerInfoConfig.CODEC),
                    (o, v) -> o.serverInfo = v != null ? v : new ServerInfoConfig(),
                    o -> o.serverInfo)
            .build();

    private boolean enabled = true;
    private boolean legacyProtocolEnabled = true;
    private ServerListConfig serverList = new ServerListConfig();
    private AuthConfig authentication = new AuthConfig();
    private NetworkConfig network = new NetworkConfig();
    private ServerInfoConfig serverInfo = new ServerInfoConfig();

    public QueryConfig() {
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isLegacyProtocolEnabled() {
        return legacyProtocolEnabled;
    }

    public void setLegacyProtocolEnabled(boolean enabled) {
        this.legacyProtocolEnabled = enabled;
    }

    @Nonnull
    public ServerListConfig getServerList() {
        return serverList;
    }

    public void setServerList(@Nonnull ServerListConfig serverList) {
        this.serverList = serverList;
    }

    @Nonnull
    public AuthConfig getAuthentication() {
        return authentication;
    }

    public void setAuthentication(@Nonnull AuthConfig authentication) {
        this.authentication = authentication;
    }

    @Nonnull
    public NetworkConfig getNetwork() {
        return network;
    }

    public void setNetwork(@Nonnull NetworkConfig network) {
        this.network = network;
    }

    @Nonnull
    public ServerInfoConfig getServerInfo() {
        return serverInfo;
    }

    public void setServerInfo(@Nonnull ServerInfoConfig serverInfo) {
        this.serverInfo = serverInfo;
    }
}
