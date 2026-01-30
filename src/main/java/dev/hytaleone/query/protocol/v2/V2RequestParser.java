package dev.hytaleone.query.protocol.v2;

import io.netty.buffer.ByteBuf;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class V2RequestParser {

    private V2RequestParser() {
    }

    public static boolean isRequest(@Nonnull ByteBuf buf) {
        if (buf.readableBytes() < V2Protocol.MIN_CHALLENGE_REQUEST_SIZE) {
            return false;
        }

        int readerIndex = buf.readerIndex();
        for (int i = 0; i < V2Protocol.REQUEST_MAGIC.length; i++) {
            if (buf.getByte(readerIndex + i) != V2Protocol.REQUEST_MAGIC[i]) {
                return false;
            }
        }
        return true;
    }

    public static byte getQueryType(@Nonnull ByteBuf buf) {
        return buf.getByte(buf.readerIndex() + V2Protocol.OFFSET_TYPE);
    }

    public static int getRequestId(@Nonnull ByteBuf buf) {
        if (buf.readableBytes() < V2Protocol.OFFSET_REQUEST_ID + V2Protocol.REQUEST_ID_SIZE) {
            return 0;
        }
        return buf.getIntLE(buf.readerIndex() + V2Protocol.OFFSET_REQUEST_ID);
    }

    public static int getOffset(@Nonnull ByteBuf buf) {
        if (buf.readableBytes() < V2Protocol.OFFSET_PAGINATION + 4) {
            return 0;
        }
        return buf.getIntLE(buf.readerIndex() + V2Protocol.OFFSET_PAGINATION);
    }

    @Nullable
    public static byte[] extractChallengeToken(@Nonnull ByteBuf buf) {
        if (buf.readableBytes() < V2Protocol.OFFSET_CHALLENGE_TOKEN + V2Protocol.CHALLENGE_TOKEN_SIZE) {
            return null;
        }

        byte[] token = new byte[V2Protocol.CHALLENGE_TOKEN_SIZE];
        buf.getBytes(buf.readerIndex() + V2Protocol.OFFSET_CHALLENGE_TOKEN, token);
        return token;
    }

    @Nullable
    public static byte[] extractAuthToken(@Nonnull ByteBuf buf) {
        if (buf.readableBytes() < V2Protocol.OFFSET_FLAGS + 2) {
            return null;
        }

        short flags = buf.getShortLE(buf.readerIndex() + V2Protocol.OFFSET_FLAGS);
        if ((flags & V2Protocol.FLAG_REQUEST_HAS_AUTH_TOKEN) == 0) {
            return null;
        }

        if (buf.readableBytes() < V2Protocol.OFFSET_OPTIONAL_DATA + 2) {
            return null;
        }

        int authLength = buf.getShortLE(buf.readerIndex() + V2Protocol.OFFSET_OPTIONAL_DATA) & 0xFFFF;
        if (buf.readableBytes() < V2Protocol.OFFSET_OPTIONAL_DATA + 2 + authLength) {
            return null;
        }

        byte[] authToken = new byte[authLength];
        buf.getBytes(buf.readerIndex() + V2Protocol.OFFSET_OPTIONAL_DATA + 2, authToken);
        return authToken;
    }

}
