package dev.hytaleone.query.protocol.v1;

import com.hypixel.hytale.server.core.plugin.PluginBase;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import dev.hytaleone.query.network.model.NetworkSnapshot;
import dev.hytaleone.query.network.model.PlayerInfo;
import dev.hytaleone.query.protocol.ServerDataProvider;
import dev.hytaleone.query.protocol.v2.V2Protocol;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;

import javax.annotation.Nonnull;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Builds v1 (legacy) HYQUERY protocol responses.
 */
public final class V1ResponseBuilder {

    private V1ResponseBuilder() {
    }

    @Nonnull
    public static ByteBuf buildBasicResponse(@Nonnull ByteBufAllocator alloc) {
        ByteBuf buf = alloc.buffer();

        buf.writeBytes(V1Protocol.RESPONSE_MAGIC);
        buf.writeByte(V1Protocol.TYPE_BASIC);

        writeServerInfo(buf);
        writeCapabilities(buf, V1Protocol.CAP_V2_PROTOCOL);

        return buf;
    }

    @Nonnull
    public static ByteBuf buildFullResponse(@Nonnull ByteBufAllocator alloc) {
        ByteBuf buf = alloc.buffer();

        buf.writeBytes(V1Protocol.RESPONSE_MAGIC);
        buf.writeByte(V1Protocol.TYPE_FULL);

        writeServerInfo(buf);
        writePlayerList(buf);
        writePluginList(buf);
        writeCapabilities(buf, V1Protocol.CAP_V2_PROTOCOL);

        return buf;
    }

    @Nonnull
    public static ByteBuf buildBasicResponse(@Nonnull ByteBufAllocator alloc, @Nonnull NetworkSnapshot snapshot) {
        ByteBuf buf = alloc.buffer();

        buf.writeBytes(V1Protocol.RESPONSE_MAGIC);
        buf.writeByte(V1Protocol.TYPE_BASIC);

        writeNetworkServerInfo(buf, snapshot);
        writeCapabilities(buf, (short) (V1Protocol.CAP_V2_PROTOCOL | V1Protocol.CAP_NETWORK_MODE));

        return buf;
    }

    @Nonnull
    public static ByteBuf buildFullResponse(@Nonnull ByteBufAllocator alloc, @Nonnull NetworkSnapshot snapshot) {
        ByteBuf buf = alloc.buffer();

        buf.writeBytes(V1Protocol.RESPONSE_MAGIC);
        buf.writeByte(V1Protocol.TYPE_FULL);

        writeNetworkServerInfo(buf, snapshot);
        writeNetworkPlayerList(buf, snapshot);
        writePluginList(buf);
        writeCapabilities(buf, (short) (V1Protocol.CAP_V2_PROTOCOL | V1Protocol.CAP_NETWORK_MODE));

        return buf;
    }

    private static void writeServerInfo(@Nonnull ByteBuf buf) {
        writeString(buf, ServerDataProvider.getServerName());
        writeString(buf, ServerDataProvider.getMotd());
        buf.writeIntLE(ServerDataProvider.getPlayerCount());
        buf.writeIntLE(ServerDataProvider.getMaxPlayers());
        buf.writeShortLE(ServerDataProvider.getHostPort());
        writeString(buf, ServerDataProvider.getVersion());
        buf.writeIntLE(ServerDataProvider.getProtocolVersion());
        writeString(buf, ServerDataProvider.getProtocolHash());
    }

    private static void writeNetworkServerInfo(@Nonnull ByteBuf buf, @Nonnull NetworkSnapshot snapshot) {
        writeString(buf, ServerDataProvider.getServerName());
        writeString(buf, ServerDataProvider.getMotd());
        buf.writeIntLE(snapshot.getTotalPlayerCount());
        buf.writeIntLE(snapshot.getTotalMaxPlayers());
        buf.writeShortLE(ServerDataProvider.getHostPort());
        writeString(buf, ServerDataProvider.getVersion());
        buf.writeIntLE(ServerDataProvider.getProtocolVersion());
        writeString(buf, ServerDataProvider.getProtocolHash());
    }

    private static void writePlayerList(@Nonnull ByteBuf buf) {
        List<PlayerRef> players = ServerDataProvider.getPlayers();

        buf.writeIntLE(players.size());
        for (PlayerRef player : players) {
            writeString(buf, player.getUsername());
            writeUUID(buf, player.getUuid());
        }
    }

    private static void writeNetworkPlayerList(@Nonnull ByteBuf buf, @Nonnull NetworkSnapshot snapshot) {
        List<PlayerInfo> players = snapshot.players();

        buf.writeIntLE(players.size());
        for (PlayerInfo player : players) {
            writeString(buf, player.username());
            writeUUID(buf, player.uuid());
        }
    }

    private static void writePluginList(@Nonnull ByteBuf buf) {
        List<PluginBase> plugins = ServerDataProvider.getPlugins();

        buf.writeIntLE(plugins.size());
        for (PluginBase plugin : plugins) {
            writeString(buf, ServerDataProvider.getPluginId(plugin));
            writeString(buf, ServerDataProvider.getPluginVersion(plugin));
            buf.writeBoolean(plugin.isEnabled());
        }
    }

    private static void writeString(@Nonnull ByteBuf buf, @Nonnull String str) {
        byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
        buf.writeShortLE(bytes.length);
        buf.writeBytes(bytes);
    }

    private static void writeUUID(@Nonnull ByteBuf buf, @Nonnull UUID uuid) {
        buf.writeLong(uuid.getMostSignificantBits());
        buf.writeLong(uuid.getLeastSignificantBits());
    }

    private static void writeCapabilities(@Nonnull ByteBuf buf, short capabilities) {
        buf.writeShortLE(capabilities);
        buf.writeByte(V2Protocol.VERSION);
    }
}
