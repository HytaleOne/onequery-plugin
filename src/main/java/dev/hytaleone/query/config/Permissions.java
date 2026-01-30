package dev.hytaleone.query.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/**
 * Defines which endpoints are accessible.
 * Used for both public access and per-token permissions.
 */
public class Permissions {

    public static final BuilderCodec<Permissions> CODEC = BuilderCodec.builder(Permissions.class, Permissions::new)
            .addField(new KeyedCodec<>("Basic", Codec.BOOLEAN),
                    (o, v) -> o.basic = v, o -> o.basic)
            .addField(new KeyedCodec<>("Players", Codec.BOOLEAN),
                    (o, v) -> o.players = v, o -> o.players)
            .build();

    private boolean basic = true;
    private boolean players = true;

    public Permissions() {
    }

    public Permissions(boolean basic, boolean players) {
        this.basic = basic;
        this.players = players;
    }

    public boolean isBasicAllowed() {
        return basic;
    }

    public void setBasic(boolean basic) {
        this.basic = basic;
    }

    public boolean isPlayersAllowed() {
        return players;
    }

    public void setPlayers(boolean players) {
        this.players = players;
    }

    /**
     * Check if access is allowed for the given endpoint.
     *
     * @param endpoint "basic" or "players"
     * @return true if access is allowed
     */
    public boolean isAllowed(String endpoint) {
        return switch (endpoint.toLowerCase()) {
            case "basic" -> basic;
            case "players" -> players;
            default -> false;
        };
    }

    public static Permissions allowAll() {
        return new Permissions(true, true);
    }

    public static Permissions denyAll() {
        return new Permissions(false, false);
    }
}
