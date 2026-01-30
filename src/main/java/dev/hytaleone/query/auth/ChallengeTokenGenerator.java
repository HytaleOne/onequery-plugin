package dev.hytaleone.query.auth;

import dev.hytaleone.query.util.CryptoUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * Token format (32 bytes):
 * - Bytes 0-3:  Timestamp (30-second granularity, big-endian)
 * - Bytes 4-7:  Flags (reserved)
 * - Bytes 8-31: HMAC-SHA256 truncated to 24 bytes
 */
public final class ChallengeTokenGenerator {

    public static final int TOKEN_SIZE = 32;
    public static final int DEFAULT_SECRET_LENGTH = 32;
    public static final int DEFAULT_VALIDITY_SECONDS = 120;
    private static final int TIMESTAMP_GRANULARITY_SECONDS = 30;
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final byte[] serverSecret;
    private final SecretKeySpec secretKeySpec;
    private final ThreadLocal<Mac> threadLocalMac;
    private final int validityWindows;

    public ChallengeTokenGenerator(@Nonnull byte[] serverSecret) {
        this(serverSecret, DEFAULT_VALIDITY_SECONDS);
    }

    public ChallengeTokenGenerator(@Nonnull byte[] serverSecret, int validitySeconds) {
        this.serverSecret = serverSecret.clone();
        this.secretKeySpec = new SecretKeySpec(this.serverSecret, HMAC_ALGORITHM);
        this.threadLocalMac = ThreadLocal.withInitial(this::createMac);
        this.validityWindows = Math.max(1, (validitySeconds + TIMESTAMP_GRANULARITY_SECONDS - 1) / TIMESTAMP_GRANULARITY_SECONDS);
    }

    @Nonnull
    private Mac createMac() {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(secretKeySpec);
            return mac;
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("Failed to initialize HMAC", e);
        }
    }

    @Nonnull
    public byte[] generateToken(@Nonnull InetAddress clientAddress) {
        int timestamp = getCurrentTimestamp();
        return generateTokenForTimestamp(clientAddress, timestamp);
    }

    public boolean validateToken(@Nullable byte[] token, @Nonnull InetAddress clientAddress) {
        if (token == null || token.length != TOKEN_SIZE) {
            return false;
        }

        int tokenTimestamp = ByteBuffer.wrap(token, 0, 4).getInt();

        int currentTimestamp = getCurrentTimestamp();
        for (int i = 0; i < validityWindows; i++) {
            int expectedTimestamp = currentTimestamp - i;
            if (tokenTimestamp == expectedTimestamp) {
                byte[] expectedToken = generateTokenForTimestamp(clientAddress, tokenTimestamp);
                return CryptoUtils.constantTimeEquals(token, expectedToken);
            }
        }

        return false;
    }

    @Nonnull
    private byte[] generateTokenForTimestamp(@Nonnull InetAddress clientAddress, int timestamp) {
        byte[] token = new byte[TOKEN_SIZE];

        token[0] = (byte) (timestamp >> 24);
        token[1] = (byte) (timestamp >> 16);
        token[2] = (byte) (timestamp >> 8);
        token[3] = (byte) timestamp;
        token[4] = 0;
        token[5] = 0;
        token[6] = 0;
        token[7] = 0;

        byte[] hmac = computeHmac(timestamp, clientAddress);
        System.arraycopy(hmac, 0, token, 8, 24);

        return token;
    }

    @Nonnull
    private byte[] computeHmac(int timestamp, @Nonnull InetAddress clientAddress) {
        Mac mac = threadLocalMac.get();

        ByteBuffer input = ByteBuffer.allocate(4 + clientAddress.getAddress().length);
        input.putInt(timestamp);
        input.put(clientAddress.getAddress());

        return mac.doFinal(input.array());
    }

    private static int getCurrentTimestamp() {
        return (int) (System.currentTimeMillis() / 1000 / TIMESTAMP_GRANULARITY_SECONDS);
    }

    @Nonnull
    public static byte[] generateSecret() {
        return generateSecret(DEFAULT_SECRET_LENGTH);
    }

    @Nonnull
    public static byte[] generateSecret(int length) {
        byte[] secret = new byte[length];
        new SecureRandom().nextBytes(secret);
        return secret;
    }
}
