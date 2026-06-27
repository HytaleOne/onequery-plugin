package dev.hytaleone.query;

import com.hypixel.hytale.protocol.io.ServerListener;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.io.ServerManager;
import com.hypixel.hytale.server.core.io.netty.NettyUtil;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.util.Config;
import dev.hytaleone.query.api.OneQueryAPI;
import dev.hytaleone.query.auth.ChallengeTokenGenerator;
import dev.hytaleone.query.auth.TokenValidator;
import dev.hytaleone.query.config.AuthConfig;
import dev.hytaleone.query.config.ConfigMigration;
import dev.hytaleone.query.config.NetworkConfig;
import dev.hytaleone.query.config.Permissions;
import dev.hytaleone.query.config.QueryConfig;
import dev.hytaleone.query.config.ServerInfoConfig;
import dev.hytaleone.query.protocol.ServerDataProvider;
import dev.hytaleone.query.network.NetworkModule;
import dev.hytaleone.query.network.PlayerEventListener;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Level;

public class OneQueryPlugin extends JavaPlugin {

    private static final String HANDLER_NAME = "hytaleone-query";
    private static final String CONFIG_NAME = "config";

    private final Config<QueryConfig> configWrapper = withConfig(CONFIG_NAME, QueryConfig.CODEC);

    private QueryHandler queryHandler;
    private QueryConfig config;
    private ChallengeTokenGenerator challengeTokenGenerator;
    private TokenValidator tokenValidator;
    private NetworkModule networkModule;
    private PlayerEventListener playerEventListener;

    public OneQueryPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        migrateConfigIfNeeded();

        this.config = configWrapper.get();

        if (!config.isEnabled()) {
            getLogger().at(Level.INFO).log("ONEQUERY protocol is disabled in config");
            return;
        }

        initializeServerInfoConfig();
        initializeChallengeTokenGenerator();
        initializeTokenValidator();
        initializeNetworkModule();
    }

    private void initializeServerInfoConfig() {
        ServerInfoConfig serverInfo = config.getServerInfo();
        ServerDataProvider.setConfig(serverInfo);
        if (serverInfo.hasOverrides()) {
            getLogger().at(Level.FINE).log("Server info overrides configured");
        }
    }

    @Override
    protected void start() {
        if (!config.isEnabled()) {
            return;
        }

        ServerManager.get().waitForBindComplete();
        startNetworkModule();

        this.queryHandler = new QueryHandler(
                getLogger(),
                challengeTokenGenerator,
                tokenValidator,
                networkModule,
                config.isLegacyProtocolEnabled()
        );

        int registered = 0;
        for (ServerListener listener : ServerManager.get().getListeners()) {
            Channel channel = getListenerChannel(listener);
            if (channel == null) {
                getLogger().at(Level.WARNING).log("Cannot register query handler on non-Netty listener %s", listener.localAddress());
                continue;
            }

            try {
                ChannelPipeline pipeline = channel.pipeline();
                pipeline.addFirst(HANDLER_NAME, queryHandler);
                registered++;
                getLogger().at(Level.FINE).log("Registered query handler on %s", channel.localAddress());
            } catch (Exception e) {
                getLogger().at(Level.WARNING).withCause(e).log(
                        "Failed to register query handler on %s", channel.localAddress());
            }
        }

        getLogger().at(Level.INFO).log("ONEQUERY v2 protocol enabled on %d listener(s)", registered);

        logAccessConfig();

        if (config.getServerList().isEnabled()) {
            ServerListRegistration.register(getLogger(), config.getServerList(), this::saveConfig);
        }
    }

    @Override
    protected void shutdown() {
        stopNetworkModule();

        if (queryHandler == null) {
            return;
        }

        int removed = 0;
        for (ServerListener listener : ServerManager.get().getListeners()) {
            Channel channel = getListenerChannel(listener);
            if (channel == null) {
                continue;
            }

            try {
                ChannelPipeline pipeline = channel.pipeline();
                if (pipeline.get(HANDLER_NAME) != null) {
                    pipeline.remove(HANDLER_NAME);
                    removed++;
                }
            } catch (Exception e) {
                getLogger().at(Level.FINE).log("Handler already removed from %s", channel.localAddress());
            }
        }

        getLogger().at(Level.INFO).log("Query protocol disabled, removed from %d listener(s)", removed);
        this.queryHandler = null;
    }

    @Nullable
    private Channel getListenerChannel(@Nonnull ServerListener listener) {
        if (listener instanceof NettyUtil.NettyChannelServerListener nettyListener) {
            return nettyListener.channel();
        }
        return null;
    }

    private void migrateConfigIfNeeded() {
        Path configPath = getDataDirectory().resolve(CONFIG_NAME + ".json");
        QueryConfig migratedConfig = ConfigMigration.migrateIfNeeded(getLogger(), configPath);

        if (migratedConfig != null) {
            ConfigMigration.applyMigratedConfig(configWrapper, migratedConfig);
        }
    }

    private void initializeChallengeTokenGenerator() {
        byte[] secret = ChallengeTokenGenerator.generateSecret();
        this.challengeTokenGenerator = new ChallengeTokenGenerator(secret);
        getLogger().at(Level.FINE).log("Challenge token generator initialized");
    }

    private void initializeTokenValidator() {
        AuthConfig authConfig = config.getAuthentication();
        this.tokenValidator = new TokenValidator(authConfig);
    }

    private void initializeNetworkModule() {
        NetworkConfig networkConfig = config.getNetwork();
        if (!networkConfig.isEnabled()) {
            getLogger().at(Level.FINE).log("Network mode disabled");
            return;
        }

        String serverId = networkConfig.getServerId();
        String serverName = HytaleServer.get().getConfig().getServerName();

        this.networkModule = new NetworkModule(
                getLogger(),
                networkConfig,
                serverId,
                serverName
        );

        this.playerEventListener = new PlayerEventListener(networkModule);

        getLogger().at(Level.FINE).log("Network module initialized (serverId=%s, mode=%s, network=%s)",
                serverId, networkConfig.getMode(), networkConfig.getNetworkId());
    }

    private void startNetworkModule() {
        if (networkModule == null) {
            return;
        }

        try {
            networkModule.start().join();

            getEventRegistry().register(PlayerConnectEvent.class, playerEventListener::onPlayerConnect);
            getEventRegistry().register(PlayerDisconnectEvent.class, playerEventListener::onPlayerDisconnect);

            OneQueryAPI.init(networkModule);

            getLogger().at(Level.INFO).log("Network module started");
        } catch (Exception e) {
            getLogger().at(Level.SEVERE).withCause(e).log("Failed to start network module");
            networkModule = null;
            playerEventListener = null;
        }
    }

    private void stopNetworkModule() {
        if (networkModule == null) {
            return;
        }

        OneQueryAPI.shutdown();

        try {
            networkModule.stop().join();
            getLogger().at(Level.INFO).log("Network module stopped");
        } catch (Exception e) {
            getLogger().at(Level.WARNING).withCause(e).log("Error stopping network module");
        }

        networkModule = null;
        playerEventListener = null;
    }

    private void logAccessConfig() {
        Permissions publicAccess = config.getAuthentication().getPublicAccess();
        int tokenCount = config.getAuthentication().getTokens().size();

        getLogger().at(Level.FINE).log("Access: basic=%s, players=%s, tokens=%d",
                publicAccess.isBasicAllowed() ? "public" : "auth",
                publicAccess.isPlayersAllowed() ? "public" : "auth",
                tokenCount);

        if (config.isLegacyProtocolEnabled()) {
            getLogger().at(Level.FINE).log("Legacy v1 HYQUERY protocol enabled (no access control)");
        }
    }

    private void saveConfig() {
        configWrapper.save().join();
    }

    @Nonnull
    public QueryConfig getConfig() {
        return config;
    }

    @Nullable
    public ChallengeTokenGenerator getChallengeTokenGenerator() {
        return challengeTokenGenerator;
    }

    @Nullable
    public TokenValidator getTokenValidator() {
        return tokenValidator;
    }

    @Nullable
    public NetworkModule getNetworkModule() {
        return networkModule;
    }
}
