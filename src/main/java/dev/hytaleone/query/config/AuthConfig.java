package dev.hytaleone.query.config;

import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.map.MapCodec;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class AuthConfig {

    public static final BuilderCodec<AuthConfig> CODEC = BuilderCodec.builder(AuthConfig.class, AuthConfig::new)
            .addField(new KeyedCodec<>("Public", Permissions.CODEC),
                    (o, v) -> o.publicAccess = v != null ? v : new Permissions(),
                    o -> o.publicAccess)
            .addField(new KeyedCodec<>("Tokens", new MapCodec<>(Permissions.CODEC, HashMap::new)),
                    (o, v) -> o.tokens = v != null ? new HashMap<>(v) : new HashMap<>(),
                    o -> o.tokens)
            .build();

    private Permissions publicAccess = new Permissions();
    private Map<String, Permissions> tokens = new HashMap<>();

    public AuthConfig() {
    }

    @Nonnull
    public Permissions getPublicAccess() {
        return publicAccess;
    }

    public void setPublicAccess(@Nonnull Permissions publicAccess) {
        this.publicAccess = publicAccess;
    }

    @Nonnull
    public Map<String, Permissions> getTokens() {
        return Collections.unmodifiableMap(tokens);
    }

    public void setTokens(@Nonnull Map<String, Permissions> tokens) {
        this.tokens = new HashMap<>(tokens);
    }

    public void addToken(@Nonnull String secret, @Nonnull Permissions permissions) {
        this.tokens.put(secret, permissions);
    }

    @Nullable
    public Permissions removeToken(@Nonnull String secret) {
        return this.tokens.remove(secret);
    }

    @Nullable
    public Permissions getToken(@Nonnull String secret) {
        return tokens.get(secret);
    }

    public boolean hasTokens() {
        return !tokens.isEmpty();
    }

    public boolean isPubliclyAccessible(@Nonnull String endpoint) {
        return publicAccess.isAllowed(endpoint);
    }

    public boolean isAuthRequired() {
        return !publicAccess.isBasicAllowed() || !publicAccess.isPlayersAllowed();
    }
}
