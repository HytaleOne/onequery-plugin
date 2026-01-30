package dev.hytaleone.query.util;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Shared cryptographic utility methods.
 */
public final class CryptoUtils {

    private CryptoUtils() {
    }

    /**
     * Constant-time byte array comparison to prevent timing attacks.
     */
    public static boolean constantTimeEquals(@Nonnull byte[] a, @Nonnull byte[] b) {
        if (a.length != b.length) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length; i++) {
            result |= a[i] ^ b[i];
        }
        return result == 0;
    }

    /**
     * Convert bytes to hexadecimal string.
     */
    @Nonnull
    public static String bytesToHex(@Nonnull byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Convert hexadecimal string to bytes.
     *
     * @return byte array, or null if input is invalid
     */
    @Nullable
    public static byte[] hexToBytes(@Nullable String hex) {
        if (hex == null || hex.length() % 2 != 0) {
            return null;
        }
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            int index = i * 2;
            try {
                bytes[i] = (byte) Integer.parseInt(hex.substring(index, index + 2), 16);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return bytes;
    }
}
