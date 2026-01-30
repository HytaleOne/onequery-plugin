package dev.hytaleone.query.protocol.v2;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;

import javax.annotation.Nonnull;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class V2TLVWriter {

    public static final short TYPE_SERVER_INFO = 0x0001;
    public static final short TYPE_PLAYER_LIST = 0x0002;

    private V2TLVWriter() {
    }

    public static void writeTLV(@Nonnull ByteBuf buf, short type, @Nonnull ByteBuf value) {
        buf.writeShortLE(type);
        buf.writeShortLE(value.readableBytes());
        buf.writeBytes(value);
    }

    public static void writeTLV(@Nonnull ByteBuf buf, @Nonnull ByteBufAllocator alloc,
                                 short type, @Nonnull ValueWriter valueWriter) {
        ByteBuf valueBuffer = alloc.buffer();
        try {
            valueWriter.write(valueBuffer);
            writeTLV(buf, type, valueBuffer);
        } finally {
            valueBuffer.release();
        }
    }

    public static void writeString(@Nonnull ByteBuf buf, @Nonnull String str) {
        byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
        buf.writeShortLE(bytes.length);
        buf.writeBytes(bytes);
    }

    public static void writeUUID(@Nonnull ByteBuf buf, @Nonnull UUID uuid) {
        buf.writeLong(uuid.getMostSignificantBits());
        buf.writeLong(uuid.getLeastSignificantBits());
    }

    public static void writeInt(@Nonnull ByteBuf buf, int value) {
        buf.writeIntLE(value);
    }

    public static void writeShort(@Nonnull ByteBuf buf, int value) {
        buf.writeShortLE(value);
    }

    @FunctionalInterface
    public interface ValueWriter {
        void write(@Nonnull ByteBuf buf);
    }
}
