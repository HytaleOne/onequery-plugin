# OneQuery Plugin API

This guide covers how to use the OneQuery API from other Hytale plugins.

## Overview

The OneQuery API allows plugins to access network-wide player data when running in `SYNC` or `AGGREGATE` mode.

## Getting Started

```java
import dev.hytaleone.query.api.OneQueryAPI;

// Check if API is available
if (!OneQueryAPI.isAvailable()) {
    // Network mode is disabled or not initialized
    return;
}

// Get the API instance
OneQueryAPI api = OneQueryAPI.get();
```

## Player Queries

### Get All Players

```java
List<PlayerInfo> players = api.getPlayers();
```

### Get Players by Server Pattern

Use wildcard patterns to filter players by server:

```java
// All players on survival servers
List<PlayerInfo> survivalPlayers = api.getPlayers("survival-*");

// All players on EU servers
List<PlayerInfo> euPlayers = api.getPlayers("*-eu-*");

// Players on a specific server
List<PlayerInfo> lobby1Players = api.getPlayers("lobby-1");
```

### Get Player Count

```java
// Total players across all servers
int total = api.getPlayerCount();

// Players on matching servers
int survivalCount = api.getPlayerCount("survival-*");
```

### Find a Player

```java
// By UUID
Optional<PlayerInfo> player = api.getPlayer(uuid);

// By username (case-insensitive)
Optional<PlayerInfo> player = api.getPlayer("Username");
```

### Check if Player is Online

```java
boolean online = api.isPlayerOnline(uuid);
boolean online = api.isPlayerOnline("Username");
```

## Server Queries

### Get All Servers

```java
List<ServerState> servers = api.getServers();
```

### Get Servers by Pattern

```java
List<ServerState> survivalServers = api.getServers("survival-*");
```

### Get a Specific Server

```java
Optional<ServerState> server = api.getServer("survival-1");
```

### Get Server Count

```java
int count = api.getServerCount();
```

## Network Info

### Get Network ID

```java
String networkId = api.getNetworkId();
```

### Get Local Server Info

```java
String localServerId = api.getLocalServerId();
ServerState localState = api.getLocalServerState();
```

### Check Mode

```java
// Is this server receiving updates from other servers?
boolean syncing = api.isSyncing();

// Is this server aggregating data in query responses?
boolean aggregating = api.isAggregating();
```

## Async Operations

### Fetch Fresh Snapshot

Force a fresh fetch from Redis (bypasses cache):

```java
api.fetchSnapshot().thenAccept(snapshot -> {
    int totalPlayers = snapshot.getTotalPlayerCount();
    List<ServerState> servers = snapshot.servers();
    List<PlayerInfo> players = snapshot.players();
});
```

## Data Models

### PlayerInfo

```java
public record PlayerInfo(
    UUID uuid,
    String username,
    String serverId,
    String serverName
) {}
```

### ServerState

```java
public record ServerState(
    String serverId,
    String serverName,
    int playerCount,
    int maxPlayers,
    String host,
    int port,
    long lastHeartbeat,
    boolean online
) {}
```

## Wildcard Patterns

| Pattern | Matches |
|---------|---------|
| `*` | Everything |
| `survival-*` | `survival-1`, `survival-eu`, `survival-hardcore` |
| `*-eu-*` | `survival-eu-1`, `lobby-eu-2` |
| `lobby-?` | `lobby-1`, `lobby-2` (single character) |

## Example: Player Lookup

```java
public String findPlayer(String username) {
    if (!OneQueryAPI.isAvailable()) {
        return "Network mode not available";
    }

    OneQueryAPI api = OneQueryAPI.get();

    return api.getPlayer(username)
        .map(p -> p.username() + " is on " + p.serverName())
        .orElse("Player not found");
}
```

## Requirements

- Network mode must be enabled in config
- Server must be in `SYNC` or `AGGREGATE` mode
- API becomes available after plugin initialization
