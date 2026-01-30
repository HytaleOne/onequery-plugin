package dev.hytaleone.query.protocol.v2;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;

public final class V2Protocol {

    private V2Protocol() {
    }

    public static final byte VERSION = 0x01;

    public static final byte[] REQUEST_MAGIC = "ONEQUERY".getBytes(StandardCharsets.US_ASCII);
    public static final byte[] RESPONSE_MAGIC = "ONEREPLY".getBytes(StandardCharsets.US_ASCII);

    public enum QueryType {
        CHALLENGE((byte) 0x00, null),
        BASIC((byte) 0x01, "basic"),
        PLAYERS((byte) 0x02, "players");

        private final byte code;
        private final String endpoint;

        QueryType(byte code, @Nullable String endpoint) {
            this.code = code;
            this.endpoint = endpoint;
        }

        public byte code() {
            return code;
        }

        @Nullable
        public String endpoint() {
            return endpoint;
        }

        @Nullable
        public static QueryType fromCode(byte code) {
            for (QueryType type : values()) {
                if (type.code == code) {
                    return type;
                }
            }
            return null;
        }
    }

    public static final short FLAG_REQUEST_HAS_AUTH_TOKEN = 0x0001;

    public static final short FLAG_RESPONSE_HAS_MORE_PLAYERS = 0x0001;
    public static final short FLAG_RESPONSE_AUTH_REQUIRED = 0x0002;
    public static final short FLAG_RESPONSE_IS_NETWORK = 0x0010;
    public static final short FLAG_RESPONSE_HAS_ADDRESS = 0x0020;

    public static final int CHALLENGE_TOKEN_SIZE = 32;
    public static final int REQUEST_ID_SIZE = 4;
    public static final int HEADER_SIZE = 17; // magic(8) + version(1) + flags(2) + requestId(4) + payloadLen(2)

    public static final int MIN_CHALLENGE_REQUEST_SIZE = REQUEST_MAGIC.length + 1;
    public static final int MIN_QUERY_REQUEST_SIZE = REQUEST_MAGIC.length + 1 + CHALLENGE_TOKEN_SIZE;

    public static final int OFFSET_TYPE = REQUEST_MAGIC.length;
    public static final int OFFSET_CHALLENGE_TOKEN = OFFSET_TYPE + 1;
    public static final int OFFSET_REQUEST_ID = OFFSET_CHALLENGE_TOKEN + CHALLENGE_TOKEN_SIZE;
    public static final int OFFSET_FLAGS = OFFSET_REQUEST_ID + REQUEST_ID_SIZE;
    public static final int OFFSET_PAGINATION = OFFSET_FLAGS + 2;
    public static final int OFFSET_OPTIONAL_DATA = OFFSET_PAGINATION + 4;
}
