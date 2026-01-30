package dev.hytaleone.query.protocol.v2;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import dev.hytaleone.query.network.model.NetworkSnapshot;
import dev.hytaleone.query.network.model.PlayerInfo;
import dev.hytaleone.query.protocol.Protocol;
import dev.hytaleone.query.protocol.ServerDataProvider;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;

import javax.annotation.Nonnull;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

public final class V2ResponseBuilder {

    private static final int MAX_PAYLOAD_SIZE = Protocol.SAFE_MTU - V2Protocol.HEADER_SIZE - 50;
    private static final int TLV_HEADER_SIZE = 4;
    private static final int LIST_HEADER_SIZE = 12;

    private V2ResponseBuilder() {
    }

    @Nonnull
    public static ByteBuf buildChallengeResponse(@Nonnull ByteBufAllocator alloc, @Nonnull byte[] token) {
        ByteBuf buf = alloc.buffer(48);
        buf.writeBytes(V2Protocol.RESPONSE_MAGIC);
        buf.writeByte(V2Protocol.QueryType.CHALLENGE.code());
        buf.writeBytes(token);
        buf.writeZero(7);
        return buf;
    }

    @Nonnull
    public static ByteBuf buildBasicResponse(@Nonnull ByteBufAllocator alloc, int requestId, short flags) {
        ByteBuf payload = alloc.buffer();
        try {
            V2TLVWriter.writeTLV(payload, alloc, V2TLVWriter.TYPE_SERVER_INFO, V2ResponseBuilder::writeLocalServerInfo);
            return buildPacket(alloc, requestId, (short) (flags | getAddressFlag()), payload);
        } finally {
            payload.release();
        }
    }

    @Nonnull
    public static ByteBuf buildBasicResponse(@Nonnull ByteBufAllocator alloc, int requestId, @Nonnull NetworkSnapshot snapshot) {
        ByteBuf payload = alloc.buffer();
        try {
            V2TLVWriter.writeTLV(payload, alloc, V2TLVWriter.TYPE_SERVER_INFO, buf -> writeNetworkServerInfo(buf, snapshot));
            return buildPacket(alloc, requestId, (short) (V2Protocol.FLAG_RESPONSE_IS_NETWORK | getAddressFlag()), payload);
        } finally {
            payload.release();
        }
    }

    private static short getAddressFlag() {
        return ServerDataProvider.getHost() != null ? V2Protocol.FLAG_RESPONSE_HAS_ADDRESS : 0;
    }

    @Nonnull
    public static ByteBuf buildPlayersResponse(@Nonnull ByteBufAllocator alloc, int requestId, int offset) {
        List<PlayerRef> players = ServerDataProvider.getPlayers();
        return buildPlayersResponse(alloc, requestId, (short) 0, players, offset, PlayerRef::getUuid, PlayerRef::getUsername);
    }

    @Nonnull
    public static ByteBuf buildPlayersResponse(@Nonnull ByteBufAllocator alloc, int requestId, @Nonnull NetworkSnapshot snapshot, int offset) {
        List<PlayerInfo> players = snapshot.players();
        return buildPlayersResponse(alloc, requestId, V2Protocol.FLAG_RESPONSE_IS_NETWORK, players, offset, PlayerInfo::uuid, PlayerInfo::username);
    }

    @Nonnull
    public static ByteBuf buildAuthRequiredResponse(@Nonnull ByteBufAllocator alloc, int requestId) {
        return buildBasicResponse(alloc, requestId, V2Protocol.FLAG_RESPONSE_AUTH_REQUIRED);
    }

    private static <T> ByteBuf buildPlayersResponse(@Nonnull ByteBufAllocator alloc, int requestId, short baseFlags,
                                                     @Nonnull List<T> players, int offset,
                                                     @Nonnull Function<T, UUID> uuidFn, @Nonnull Function<T, String> usernameFn) {
        int totalPlayers = players.size();
        int startIndex = Math.min(offset, totalPlayers);
        short flags = baseFlags;

        ByteBuf tlvValue = alloc.buffer();
        try {
            int count = 0;
            int remaining = MAX_PAYLOAD_SIZE - TLV_HEADER_SIZE - LIST_HEADER_SIZE;

            tlvValue.writeIntLE(totalPlayers);
            int countPosition = tlvValue.writerIndex();
            tlvValue.writeIntLE(0);
            tlvValue.writeIntLE(startIndex);

            for (int i = startIndex; i < players.size(); i++) {
                T player = players.get(i);
                String username = usernameFn.apply(player);
                int entrySize = 2 + username.getBytes(StandardCharsets.UTF_8).length + 16;

                if (remaining < entrySize) {
                    flags |= V2Protocol.FLAG_RESPONSE_HAS_MORE_PLAYERS;
                    break;
                }

                writePlayerEntry(tlvValue, uuidFn.apply(player), username);
                remaining -= entrySize;
                count++;
            }

            tlvValue.setIntLE(countPosition, count);

            ByteBuf payload = alloc.buffer();
            try {
                V2TLVWriter.writeTLV(payload, V2TLVWriter.TYPE_PLAYER_LIST, tlvValue);
                return buildPacket(alloc, requestId, flags, payload);
            } finally {
                payload.release();
            }
        } finally {
            tlvValue.release();
        }
    }

    private static void writePlayerEntry(@Nonnull ByteBuf buf, @Nonnull UUID uuid, @Nonnull String username) {
        byte[] nameBytes = username.getBytes(StandardCharsets.UTF_8);
        buf.writeShortLE(nameBytes.length);
        buf.writeBytes(nameBytes);
        buf.writeLong(uuid.getMostSignificantBits());
        buf.writeLong(uuid.getLeastSignificantBits());
    }

    private static ByteBuf buildPacket(@Nonnull ByteBufAllocator alloc, int requestId, short flags, @Nonnull ByteBuf payload) {
        ByteBuf buf = alloc.buffer(V2Protocol.HEADER_SIZE + payload.readableBytes());
        buf.writeBytes(V2Protocol.RESPONSE_MAGIC);
        buf.writeByte(V2Protocol.VERSION);
        buf.writeShortLE(flags);
        buf.writeIntLE(requestId);
        buf.writeShortLE(payload.readableBytes());
        buf.writeBytes(payload);
        return buf;
    }

    private static void writeLocalServerInfo(@Nonnull ByteBuf buf) {
        V2TLVWriter.writeString(buf, ServerDataProvider.getServerName());
        V2TLVWriter.writeString(buf, ServerDataProvider.getMotd());
        V2TLVWriter.writeInt(buf, ServerDataProvider.getPlayerCount());
        V2TLVWriter.writeInt(buf, ServerDataProvider.getMaxPlayers());
        V2TLVWriter.writeString(buf, ServerDataProvider.getVersion());
        V2TLVWriter.writeInt(buf, ServerDataProvider.getProtocolVersion());
        V2TLVWriter.writeString(buf, ServerDataProvider.getProtocolHash());
        writeOptionalAddress(buf);
    }

    private static void writeNetworkServerInfo(@Nonnull ByteBuf buf, @Nonnull NetworkSnapshot snapshot) {
        V2TLVWriter.writeString(buf, ServerDataProvider.getServerName());
        V2TLVWriter.writeString(buf, ServerDataProvider.getMotd());
        V2TLVWriter.writeInt(buf, snapshot.getTotalPlayerCount());
        V2TLVWriter.writeInt(buf, snapshot.getTotalMaxPlayers());
        V2TLVWriter.writeString(buf, ServerDataProvider.getVersion());
        V2TLVWriter.writeInt(buf, ServerDataProvider.getProtocolVersion());
        V2TLVWriter.writeString(buf, ServerDataProvider.getProtocolHash());
        writeOptionalAddress(buf);
    }

    private static void writeOptionalAddress(@Nonnull ByteBuf buf) {
        String host = ServerDataProvider.getHost();
        if (host != null) {
            V2TLVWriter.writeString(buf, host);
            V2TLVWriter.writeShort(buf, ServerDataProvider.getHostPort());
        }
    }
}
