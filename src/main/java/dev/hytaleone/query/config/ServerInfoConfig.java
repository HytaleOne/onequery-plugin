package dev.hytaleone.query.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

import javax.annotation.Nullable;

/**
 * Optional overrides for server info returned in query responses.
 * When a field is null, the actual server value is used.
 *
 * Example:
 * {
 *   "ServerInfo": {
 *     "ServerName": "My Network",
 *     "Motd": "Welcome to my server!",
 *     "Host": "play.example.com",
 *     "Port": 25565,
 *     "MaxPlayers": 1000
 *   }
 * }
 */
public class ServerInfoConfig {

    public static final BuilderCodec<ServerInfoConfig> CODEC = BuilderCodec.builder(ServerInfoConfig.class, ServerInfoConfig::new)
            .addField(new KeyedCodec<>("ServerName", Codec.STRING),
                    (o, v) -> o.serverName = v, o -> o.serverName)
            .addField(new KeyedCodec<>("Motd", Codec.STRING),
                    (o, v) -> o.motd = v, o -> o.motd)
            .addField(new KeyedCodec<>("Host", Codec.STRING),
                    (o, v) -> o.host = v, o -> o.host)
            .addField(new KeyedCodec<>("Port", Codec.INTEGER),
                    (o, v) -> o.port = v, o -> o.port)
            .addField(new KeyedCodec<>("MaxPlayers", Codec.INTEGER),
                    (o, v) -> o.maxPlayers = v, o -> o.maxPlayers)
            .build();

    private String serverName = null;
    private String motd = null;
    private String host = null;
    private Integer port = null;
    private Integer maxPlayers = null;

    public ServerInfoConfig() {
    }

    @Nullable
    public String getServerName() {
        return blankToNull(serverName);
    }

    public void setServerName(@Nullable String serverName) {
        this.serverName = serverName;
    }

    @Nullable
    public String getMotd() {
        return blankToNull(motd);
    }

    public void setMotd(@Nullable String motd) {
        this.motd = motd;
    }

    @Nullable
    public String getHost() {
        return blankToNull(host);
    }

    public void setHost(@Nullable String host) {
        this.host = host;
    }

    @Nullable
    public Integer getPort() {
        return port;
    }

    public void setPort(@Nullable Integer port) {
        this.port = port;
    }

    @Nullable
    public Integer getMaxPlayers() {
        return maxPlayers;
    }

    public void setMaxPlayers(@Nullable Integer maxPlayers) {
        this.maxPlayers = maxPlayers;
    }

    public boolean hasOverrides() {
        return getServerName() != null || getMotd() != null || getHost() != null || port != null || maxPlayers != null;
    }

    @Nullable
    private static String blankToNull(@Nullable String value) {
        return value != null && !value.isBlank() ? value : null;
    }
}
