package dev.hytaleone.query.auth;

import dev.hytaleone.query.config.AuthConfig;
import dev.hytaleone.query.config.Permissions;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;

public final class TokenValidator {

    private final AuthConfig config;

    public TokenValidator(@Nonnull AuthConfig config) {
        this.config = config;
    }

    public boolean isAccessAllowed(@Nonnull String endpoint, @Nullable byte[] token) {
        if (config.isPubliclyAccessible(endpoint)) {
            return true;
        }

        if (token == null || token.length == 0) {
            return false;
        }

        Permissions permissions = getTokenPermissions(token);
        if (permissions == null) {
            return false;
        }

        return permissions.isAllowed(endpoint);
    }

    public boolean isAuthRequired(@Nonnull String endpoint) {
        return !config.isPubliclyAccessible(endpoint);
    }

    @Nullable
    public Permissions getTokenPermissions(@Nullable byte[] token) {
        if (token == null || token.length == 0) {
            return null;
        }

        String tokenString = new String(token, StandardCharsets.UTF_8);
        return config.getToken(tokenString);
    }
}
