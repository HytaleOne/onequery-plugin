package dev.hytaleone.query.protocol.v1;

import io.netty.buffer.ByteBuf;

import javax.annotation.Nonnull;

/**
 * Parses incoming v1 HYQUERY protocol requests.
 */
public final class V1RequestParser {

    private V1RequestParser() {
    }

    /**
     * Check if the buffer contains a v1 HYQUERY request.
     */
    public static boolean isRequest(@Nonnull ByteBuf buf) {
        if (buf.readableBytes() < V1Protocol.MIN_REQUEST_SIZE) {
            return false;
        }

        int readerIndex = buf.readerIndex();
        for (int i = 0; i < V1Protocol.REQUEST_MAGIC.length; i++) {
            if (buf.getByte(readerIndex + i) != V1Protocol.REQUEST_MAGIC[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Get the query type from a v1 request.
     */
    public static byte getQueryType(@Nonnull ByteBuf buf) {
        return buf.getByte(buf.readerIndex() + V1Protocol.REQUEST_MAGIC.length);
    }
}
