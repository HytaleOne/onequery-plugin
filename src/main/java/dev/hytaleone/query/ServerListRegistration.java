package dev.hytaleone.query;

import com.hypixel.hytale.common.util.java.ManifestUtil;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.ProtocolSettings;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.io.ServerManager;
import com.hypixel.hytale.server.core.universe.Universe;
import dev.hytaleone.query.config.ServerListConfig;
import org.bson.Document;

import javax.annotation.Nonnull;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Registers the server with a central server list service on startup.
 */
public final class ServerListRegistration {

    private static final String ENDPOINT = "https://hytale.one/api/plugin/query/register";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private ServerListRegistration() {
    }

    /**
     * Register the server with the server list service.
     * Runs asynchronously to not block server startup.
     */
    public static void register(@Nonnull HytaleLogger logger, @Nonnull ServerListConfig config, @Nonnull Runnable saveConfig) {
        // Generate server ID if missing
        if (config.getServerId() == null || config.getServerId().isBlank()) {
            String newId = "hytaleone_" + generateRandomHex(32);
            config.setServerId(newId);
            saveConfig.run();
            logger.at(Level.FINE).log("Generated new server ID: %s", newId);
        }

        final String serverId = config.getServerId();

        CompletableFuture.runAsync(() -> {
            try {
                String json = buildPayload(serverId);

                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(TIMEOUT)
                        .version(HttpClient.Version.HTTP_1_1)
                        .build();

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(ENDPOINT))
                        .header("Content-Type", "application/json")
                        .timeout(TIMEOUT)
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build();

                HttpResponse<String> response = client.send(request,
                        HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    handleSuccessResponse(logger, response.body());
                } else {
                    logger.at(Level.WARNING).log("Server list registration failed (status: %d)",
                            response.statusCode());
                }
            } catch (Exception e) {
                logger.at(Level.WARNING).log("Failed to register with server list: %s", e.getMessage());
            }
        });
    }

    /**
     * Handle successful registration response.
     */
    private static void handleSuccessResponse(@Nonnull HytaleLogger logger, @Nonnull String body) {
        String url = null;
        boolean claimed = false;

        try {
            Document doc = Document.parse(body);
            url = doc.getString("url");
            claimed = doc.getBoolean("claimed", false);
        } catch (Exception e) {
            // Response parsing failed, just log simple message
        }

        if (claimed) {
            printClaimedMessage(logger, url);
        } else if (url != null && !url.isBlank()) {
            printPromotion(logger, url);
        } else {
            logger.at(Level.INFO).log("Server registered with hytale.one");
        }
    }

    /**
     * Print message for claimed servers.
     */
    private static void printClaimedMessage(@Nonnull HytaleLogger logger, String url) {
        logger.at(Level.INFO).log("");
        logger.at(Level.INFO).log("Your server is listed on hytale.one server list!");
        if (url != null && !url.isBlank()) {
            logger.at(Level.INFO).log("Manage your server: %s", url);
        }
        logger.at(Level.INFO).log("");
    }

    /**
     * Print promotional message to encourage claiming.
     */
    private static void printPromotion(@Nonnull HytaleLogger logger, @Nonnull String url) {
        logger.at(Level.INFO).log("");
        logger.at(Level.INFO).log("╔═══════════════════════════════════════════════════════════════════════════╗");
        logger.at(Level.INFO).log("║  Get more players! Your server is waiting to be discovered.              ║");
        logger.at(Level.INFO).log("║                                                                           ║");
        logger.at(Level.INFO).log("║  Claim your server on hytale.one to:                                      ║");
        logger.at(Level.INFO).log("║    - Appear in the server list and attract new players                    ║");
        logger.at(Level.INFO).log("║    - Add banners, descriptions, and showcase your community               ║");
        logger.at(Level.INFO).log("║                                                                           ║");
        logger.at(Level.INFO).log("║  Claim now: %-63s ║", url);
        logger.at(Level.INFO).log("╚═══════════════════════════════════════════════════════════════════════════╝");
        logger.at(Level.INFO).log("");
    }

    /**
     * Generate random hex string for server ID.
     */
    private static String generateRandomHex(int length) {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[length / 2];
        random.nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Build the JSON payload with server information.
     */
    private static String buildPayload(@Nonnull String serverId) {
        var config = HytaleServer.get().getConfig();

        Document doc = new Document()
                .append("serverId", serverId)
                .append("pluginVersion", "v2")
                .append("serverName", config.getServerName())
                .append("motd", config.getMotd())
                .append("host", getHostAddress())
                .append("port", getHostPort())
                .append("maxPlayers", config.getMaxPlayers())
                .append("currentPlayers", Universe.get().getPlayerCount())
                .append("version", getVersion())
                .append("protocolVersion", ProtocolSettings.PROTOCOL_VERSION);

        return doc.toJson();
    }

    private static String getVersion() {
        String version = ManifestUtil.getImplementationVersion();
        return version != null ? version : "unknown";
    }

    private static String getHostAddress() {
        try {
            InetSocketAddress address = ServerManager.get().getNonLoopbackAddress();
            if (address != null && address.getAddress() != null) {
                return address.getAddress().getHostAddress();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static int getHostPort() {
        try {
            InetSocketAddress address = ServerManager.get().getNonLoopbackAddress();
            if (address != null) {
                return address.getPort();
            }
        } catch (Exception ignored) {
        }
        return 5520;
    }
}
