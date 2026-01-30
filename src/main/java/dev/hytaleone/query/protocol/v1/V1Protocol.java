package dev.hytaleone.query.protocol.v1;

import java.nio.charset.StandardCharsets;

/**
 * Protocol constants for v1 (legacy) HYQUERY protocol.
 */
public final class V1Protocol {

    private V1Protocol() {
    }

    // Magic bytes
    public static final byte[] REQUEST_MAGIC = "HYQUERY\0".getBytes(StandardCharsets.US_ASCII);
    public static final byte[] RESPONSE_MAGIC = "HYREPLY\0".getBytes(StandardCharsets.US_ASCII);

    // Packet types
    public static final byte TYPE_BASIC = 0x00;
    public static final byte TYPE_FULL = 0x01;

    // Minimum request size
    public static final int MIN_REQUEST_SIZE = REQUEST_MAGIC.length + 1;

    // Capability flags (appended to end of V1 responses for V2-aware clients)
    public static final short CAP_V2_PROTOCOL = 0x01;
    public static final short CAP_NETWORK_MODE = 0x02;
}
