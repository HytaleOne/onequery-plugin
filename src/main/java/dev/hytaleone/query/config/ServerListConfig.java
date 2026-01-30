package dev.hytaleone.query.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

import javax.annotation.Nullable;

/**
 * Configuration for hytale.one server list registration.
 *
 * Example:
 * {
 *   "ServerList": {
 *     "Enabled": true,
 *     "ServerId": "hytaleone_abc123..."
 *   }
 * }
 */
public class ServerListConfig {

    public static final BuilderCodec<ServerListConfig> CODEC = BuilderCodec.builder(ServerListConfig.class, ServerListConfig::new)
            .addField(new KeyedCodec<>("Enabled", Codec.BOOLEAN),
                    (o, v) -> o.enabled = v, o -> o.enabled)
            .addField(new KeyedCodec<>("ServerId", Codec.STRING),
                    (o, v) -> o.serverId = v, o -> o.serverId)
            .build();

    private boolean enabled = true;
    private String serverId = null;

    public ServerListConfig() {
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Nullable
    public String getServerId() {
        return serverId;
    }

    public void setServerId(@Nullable String serverId) {
        this.serverId = serverId;
    }
}
