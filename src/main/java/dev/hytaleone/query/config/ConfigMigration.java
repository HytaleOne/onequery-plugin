package dev.hytaleone.query.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.util.Config;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;

/**
 * Handles migration from old config format (HytaleServer.json module) to new format (plugin data directory).
 */
public final class ConfigMigration {

    private static final String OLD_CONFIG_MODULE = "HytaleOneQuery";

    private static final BuilderCodec<OldConfig> OLD_CONFIG_CODEC = BuilderCodec.builder(OldConfig.class, OldConfig::new)
            .addField(new KeyedCodec<>("RegisterOnStartup", Codec.BOOLEAN),
                    (o, v) -> o.registerOnStartup = v, o -> o.registerOnStartup)
            .addField(new KeyedCodec<>("ServerIdDoNotChange", Codec.STRING),
                    (o, v) -> o.serverId = v, o -> o.serverId)
            .build();

    private ConfigMigration() {
    }

    /**
     * Attempts to migrate config from old location to new location.
     *
     * @return Migrated config if found, null otherwise
     */
    @Nullable
    public static QueryConfig migrateIfNeeded(@Nonnull HytaleLogger logger, @Nonnull Path newConfigPath) {
        if (Files.exists(newConfigPath)) {
            return null;
        }

        OldConfig oldConfig = loadOldConfig();
        if (oldConfig == null) {
            return null;
        }

        logger.at(Level.INFO).log("Migrating config from HytaleServer.json...");

        QueryConfig newConfig = migrateToNewFormat(oldConfig);
        removeOldConfig();

        logger.at(Level.INFO).log("Config migration complete (ServerList.Enabled=%s, ServerId=%s)",
                newConfig.getServerList().isEnabled(),
                newConfig.getServerList().getServerId() != null ? "present" : "none");

        return newConfig;
    }

    @Nullable
    private static OldConfig loadOldConfig() {
        try {
            var serverConfig = HytaleServer.get().getConfig();
            var module = serverConfig.getModule(OLD_CONFIG_MODULE);
            return module.decode(OLD_CONFIG_CODEC);
        } catch (Exception e) {
            return null;
        }
    }

    @Nonnull
    private static QueryConfig migrateToNewFormat(@Nonnull OldConfig oldConfig) {
        QueryConfig newConfig = new QueryConfig();

        ServerListConfig serverList = new ServerListConfig();
        serverList.setEnabled(oldConfig.registerOnStartup);
        serverList.setServerId(oldConfig.serverId);
        newConfig.setServerList(serverList);

        return newConfig;
    }

    private static void removeOldConfig() {
        try {
            var serverConfig = HytaleServer.get().getConfig();
            serverConfig.removeModule(OLD_CONFIG_MODULE);
            serverConfig.markChanged();
        } catch (Exception e) {
            // Ignore - old config removal is best-effort
        }
    }

    /**
     * Applies migrated config to the Config wrapper and saves it.
     */
    public static void applyMigratedConfig(@Nonnull Config<QueryConfig> configWrapper, @Nonnull QueryConfig migratedConfig) {
        try {
            var field = Config.class.getDeclaredField("config");
            field.setAccessible(true);
            field.set(configWrapper, migratedConfig);
            configWrapper.save().join();
        } catch (Exception e) {
            throw new RuntimeException("Failed to apply migrated config", e);
        }
    }

    private static class OldConfig {
        boolean registerOnStartup = true;
        String serverId = null;
    }
}
