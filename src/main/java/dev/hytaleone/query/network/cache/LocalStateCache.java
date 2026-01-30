package dev.hytaleone.query.network.cache;

import dev.hytaleone.query.network.NetworkModule;
import dev.hytaleone.query.network.model.NetworkSnapshot;
import dev.hytaleone.query.network.model.PlayerInfo;
import dev.hytaleone.query.network.model.ServerState;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class LocalStateCache {

    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    private final HashMap<String, ServerState> servers = new HashMap<>();
    private final HashMap<UUID, PlayerInfo> players = new HashMap<>();
    private final HashMap<UUID, String> playerToServer = new HashMap<>();

    private long lastRefreshTime = 0;
    private long serverTimeoutMillis = NetworkModule.SERVER_TIMEOUT_MILLIS;

    public LocalStateCache() {
    }

    public void setServerTimeout(long timeoutMillis) {
        lock.writeLock().lock();
        try {
            this.serverTimeoutMillis = timeoutMillis;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void updateServer(@Nonnull ServerState state) {
        lock.writeLock().lock();
        try {
            servers.put(state.serverId(), state);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void removeServer(@Nonnull String serverId) {
        lock.writeLock().lock();
        try {
            servers.remove(serverId);
            List<UUID> toRemove = new ArrayList<>();
            playerToServer.forEach((uuid, sid) -> {
                if (serverId.equals(sid)) {
                    toRemove.add(uuid);
                }
            });
            for (UUID uuid : toRemove) {
                players.remove(uuid);
                playerToServer.remove(uuid);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void updatePlayer(@Nonnull PlayerInfo player) {
        lock.writeLock().lock();
        try {
            String oldServerId = playerToServer.put(player.uuid(), player.serverId());
            if (oldServerId != null && !oldServerId.equals(player.serverId())) {
                updateServerPlayerCount(oldServerId);
            }
            players.put(player.uuid(), player);
            updateServerPlayerCount(player.serverId());
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void removePlayer(@Nonnull UUID playerId) {
        lock.writeLock().lock();
        try {
            PlayerInfo removed = players.remove(playerId);
            String serverId = playerToServer.remove(playerId);
            if (removed != null && serverId != null) {
                updateServerPlayerCount(serverId);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Nullable
    public PlayerInfo getPlayer(@Nonnull UUID playerId) {
        lock.readLock().lock();
        try {
            return players.get(playerId);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Nullable
    public ServerState getServer(@Nonnull String serverId) {
        lock.readLock().lock();
        try {
            return servers.get(serverId);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Nonnull
    public NetworkSnapshot getSnapshot() {
        lock.readLock().lock();
        try {
            return NetworkSnapshot.of(
                    List.copyOf(servers.values()),
                    List.copyOf(players.values())
            );
        } finally {
            lock.readLock().unlock();
        }
    }

    @Nonnull
    public List<ServerState> getServers() {
        lock.readLock().lock();
        try {
            return List.copyOf(servers.values());
        } finally {
            lock.readLock().unlock();
        }
    }

    @Nonnull
    public List<PlayerInfo> getPlayers() {
        lock.readLock().lock();
        try {
            return List.copyOf(players.values());
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getTotalPlayerCount() {
        lock.readLock().lock();
        try {
            return players.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getServerCount() {
        lock.readLock().lock();
        try {
            return servers.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getOnlineServerCount() {
        lock.readLock().lock();
        try {
            return (int) servers.values().stream()
                    .filter(s -> !s.isStale(serverTimeoutMillis))
                    .count();
        } finally {
            lock.readLock().unlock();
        }
    }

    public boolean hasStaleServers() {
        lock.readLock().lock();
        try {
            return servers.values().stream().anyMatch(s -> s.isStale(serverTimeoutMillis));
        } finally {
            lock.readLock().unlock();
        }
    }

    public void clear() {
        lock.writeLock().lock();
        try {
            servers.clear();
            players.clear();
            playerToServer.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void refresh(@Nonnull NetworkSnapshot snapshot) {
        lock.writeLock().lock();
        try {
            servers.clear();
            players.clear();
            playerToServer.clear();

            for (ServerState server : snapshot.servers()) {
                servers.put(server.serverId(), server);
            }

            for (PlayerInfo player : snapshot.players()) {
                players.put(player.uuid(), player);
                playerToServer.put(player.uuid(), player.serverId());
            }

            this.lastRefreshTime = System.currentTimeMillis();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public long getLastRefreshTime() {
        lock.readLock().lock();
        try {
            return lastRefreshTime;
        } finally {
            lock.readLock().unlock();
        }
    }

    public int pruneStaleServers() {
        lock.writeLock().lock();
        try {
            List<String> staleServers = new ArrayList<>();
            servers.forEach((id, state) -> {
                if (state.isStale(serverTimeoutMillis)) {
                    staleServers.add(id);
                }
            });

            for (String serverId : staleServers) {
                servers.remove(serverId);
                List<UUID> toRemove = new ArrayList<>();
                playerToServer.forEach((uuid, sid) -> {
                    if (serverId.equals(sid)) {
                        toRemove.add(uuid);
                    }
                });
                for (UUID uuid : toRemove) {
                    players.remove(uuid);
                    playerToServer.remove(uuid);
                }
            }

            return staleServers.size();
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void updateServerPlayerCount(@Nonnull String serverId) {
        int count = 0;
        for (Map.Entry<UUID, String> entry : playerToServer.entrySet()) {
            if (serverId.equals(entry.getValue())) {
                count++;
            }
        }

        ServerState current = servers.get(serverId);
        if (current != null) {
            servers.put(serverId, current.withPlayerCount(count));
        }
    }
}
