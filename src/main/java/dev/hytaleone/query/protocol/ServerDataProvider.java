package dev.hytaleone.query.protocol;

import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.common.util.java.ManifestUtil;
import com.hypixel.hytale.protocol.ProtocolSettings;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.io.ServerManager;
import com.hypixel.hytale.server.core.plugin.PluginBase;
import com.hypixel.hytale.server.core.plugin.PluginManager;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import dev.hytaleone.query.config.ServerInfoConfig;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.List;

public final class ServerDataProvider {

    private static final int DEFAULT_PORT = 5520;

    private static volatile ServerInfoConfig config;

    private ServerDataProvider() {
    }

    public static void setConfig(@Nullable ServerInfoConfig config) {
        ServerDataProvider.config = config;
    }

    @Nonnull
    public static String getServerName() {
        ServerInfoConfig cfg = config;
        if (cfg != null && cfg.getServerName() != null) {
            return cfg.getServerName();
        }
        return HytaleServer.get().getConfig().getServerName();
    }

    @Nonnull
    public static String getMotd() {
        ServerInfoConfig cfg = config;
        if (cfg != null && cfg.getMotd() != null) {
            return cfg.getMotd();
        }
        return HytaleServer.get().getConfig().getMotd();
    }

    public static int getPlayerCount() {
        return Universe.get().getPlayerCount();
    }

    public static int getMaxPlayers() {
        ServerInfoConfig cfg = config;
        if (cfg != null && cfg.getMaxPlayers() != null) {
            return cfg.getMaxPlayers();
        }
        return Math.max(HytaleServer.get().getConfig().getMaxPlayers(), 0);
    }

    @Nonnull
    public static List<PlayerRef> getPlayers() {
        return Universe.get().getPlayers();
    }

    @Nonnull
    public static List<PluginBase> getPlugins() {
        return PluginManager.get().getPlugins();
    }

    @Nullable
    public static String getHost() {
        ServerInfoConfig cfg = config;
        if (cfg != null && cfg.getHost() != null) {
            return cfg.getHost();
        }
        return null;
    }

    public static int getHostPort() {
        ServerInfoConfig cfg = config;
        if (cfg != null && cfg.getPort() != null) {
            return cfg.getPort();
        }
        try {
            InetSocketAddress address = ServerManager.get().getNonLoopbackAddress();
            if (address != null) {
                return address.getPort();
            }
        } catch (SocketException ignored) {
        }
        return DEFAULT_PORT;
    }

    @Nonnull
    public static String getVersion() {
        String version = ManifestUtil.getImplementationVersion();
        return version != null ? version : "unknown";
    }

    public static int getProtocolVersion() {
        return ProtocolSettings.PROTOCOL_VERSION;
    }

    @Nonnull
    public static String getProtocolHash() {
        return String.format("%08X", ProtocolSettings.PROTOCOL_CRC);
    }

    @Nonnull
    public static String getPluginId(@Nonnull PluginBase plugin) {
        PluginIdentifier id = plugin.getIdentifier();
        return id.toString();
    }

    @Nonnull
    public static String getPluginVersion(@Nonnull PluginBase plugin) {
        return plugin.getManifest().getVersion().toString();
    }
}
