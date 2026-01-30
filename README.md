# OneQuery - Hytale Server Query Plugin

A lightweight UDP query protocol plugin for Hytale servers. Query your server status without the overhead of HTTP.

## Features

- **UDP Protocol** - Same port as game server, no extra ports to manage
- **Zero Dependencies** - Works standalone with no external plugins
- **Secure** - Challenge-response authentication prevents amplification attacks``
- **Network Mode** - Aggregate player counts across multiple servers using Redis
- **Access Control** - Token-based authentication for protected endpoints
- **Server List Integration** - Automatic registration with [hytale.one](https://hytale.one/)

## Installation

1. Download the latest release from [Releases](../../releases)
2. Place `onequery-x.x.x.jar` in your server's `plugins` directory
3. Restart the server

## Quick Start

The plugin works out of the box with sensible defaults. For most single-server setups, no configuration is needed.

## Configuration

Configuration file: `plugins/OneQuery/config.json`

```json
{
  "Enabled": true,
  "LegacyProtocolEnabled": true
}
```

### Basic Options

| Option | Default | Description |
|--------|---------|-------------|
| `Enabled` | `true` | Enable or disable the query protocol |
| `LegacyProtocolEnabled` | `true` | Support V1 protocol for older clients |

## Network Mode

Aggregate data across multiple servers using Redis. Perfect for server networks that want to show combined player counts and player lists.

### Why Network Mode?

- Show total players across all your servers
- Display combined player list from lobby server
- Track players across your network in real-time

### Configuration

```json
{
  "Network": {
    "Enabled": true,
    "ServerId": "survival-1",
    "NetworkId": "my-network",
    "Mode": "AGGREGATE",
    "Store": {
      "Type": "redis",
      "Redis": {
        "Host": "redis.example.com",
        "Port": 6379,
        "Username": "optional",
        "Password": "optional",
        "Database": 0,
        "UseTLS": false
      }
    }
  }
}
```

| Option | Default | Description |
|--------|---------|-------------|
| `Enabled` | `false` | Enable network mode |
| `ServerId` | `"server-1"` | Unique identifier for this server. Must be unique across all servers in the network. |
| `NetworkId` | `"default"` | Groups servers together. Only servers with the same NetworkId share data. |
| `Mode` | `"AGGREGATE"` | How this server participates in the network (see below) |
| `Store.Type` | `"redis"` | Storage backend type (only `redis` supported) |
| `Store.Redis.Host` | `"localhost"` | Redis server hostname |
| `Store.Redis.Port` | `6379` | Redis server port |
| `Store.Redis.Username` | `null` | Redis username for ACL auth (Redis 6+) |
| `Store.Redis.Password` | `null` | Redis password |
| `Store.Redis.Database` | `0` | Redis database number |
| `Store.Redis.UseTLS` | `false` | Enable TLS/SSL connection |

### Network Modes

| Mode | Description |
|------|-------------|
| `PUBLISH` | Report server state to Redis only |
| `SYNC` | Publish + receive updates from other servers |
| `AGGREGATE` | Sync + return combined data in query responses |

### Setup Examples

**Game Server (publish only)**

Game servers only need to publish their state. They don't need to know about other servers.

```json
{
  "Network": {
    "Enabled": true,
    "ServerId": "survival-1",
    "NetworkId": "my-network",
    "Mode": "PUBLISH",
    "Store": {
      "Type": "redis",
      "Redis": { "Host": "redis.local" }
    }
  }
}
```

**Lobby Server (aggregate)**

Lobby servers aggregate data from all servers and return combined stats in query responses.

```json
{
  "Network": {
    "Enabled": true,
    "ServerId": "lobby-1",
    "NetworkId": "my-network",
    "Mode": "AGGREGATE",
    "Store": {
      "Type": "redis",
      "Redis": { "Host": "redis.local" }
    }
  }
}
```

### Network Architecture

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│  Survival-1 │     │  Survival-2 │     │   Minigame  │
│   PUBLISH   │     │   PUBLISH   │     │   PUBLISH   │
└──────┬──────┘     └──────┬──────┘     └──────┬──────┘
       │                   │                   │
       └───────────────────┼───────────────────┘
                           │
                     ┌─────▼─────┐
                     │   Redis   │
                     └─────┬─────┘
                           │
                     ┌─────▼─────┐
                     │   Lobby   │
                     │ AGGREGATE │◄──── Query clients connect here
                     └───────────┘
```

### Plugin API

Other plugins can access network data using the OneQuery API. Requires `SYNC` or `AGGREGATE` mode.

```java
import dev.hytaleone.query.api.OneQueryAPI;

// Check if API is available
if (!OneQueryAPI.isAvailable()) {
    return;
}

OneQueryAPI api = OneQueryAPI.get();

// Get total player count across all servers
int total = api.getPlayerCount();

// Get all players in the network
List<PlayerInfo> allPlayers = api.getPlayers();

// Get players on specific servers using wildcards
List<PlayerInfo> survivalPlayers = api.getPlayers("survival-*");
List<PlayerInfo> euPlayers = api.getPlayers("*-eu-*");
int lobbyCount = api.getPlayerCount("lobby-?");

// Check if a player is online anywhere
boolean isOnline = api.isPlayerOnline(playerUuid);
Optional<PlayerInfo> player = api.getPlayer("Username");
```

**Wildcard patterns:**
- `*` matches any characters (e.g., `survival-*` matches `survival-1`, `survival-eu`)
- `?` matches a single character (e.g., `lobby-?` matches `lobby-1`, `lobby-2`)

## Access Control

Control who can query your server. By default, all endpoints are public.

```json
{
  "Authentication": {
    "Public": {
      "Basic": true,
      "Players": false
    },
    "Tokens": {
      "my-secret-token": {
        "Basic": true,
        "Players": true
      }
    }
  }
}
```

### Endpoints

| Endpoint | Description |
|----------|-------------|
| `Basic` | Server name, MOTD, player count, version info |
| `Players` | Player list with names and UUIDs |

### Public Access

The `Public` section controls what unauthenticated clients can access:

- `"Basic": true` - Anyone can query server info
- `"Players": false` - Player list requires authentication

### Token Authentication

Tokens allow specific clients to access protected endpoints. Each token has its own permissions.

```json
{
  "Tokens": {
    "website-readonly": {
      "Basic": true,
      "Players": false
    },
    "admin-full-access": {
      "Basic": true,
      "Players": true
    },
    "discord-bot-token": {
      "Basic": true,
      "Players": true
    }
  }
}
```

**How clients use tokens:**
- Clients include the token in the query request
- Server validates the token and checks permissions
- If valid, the request is processed with the token's permissions
- If invalid or missing, public permissions apply

**Token best practices:**
- Use unique tokens for each application (server lists, etc.)
- Use long, random strings (32+ characters recommended)
- Revoke tokens by removing them from config and restarting

## Server Info Overrides

Override server information returned in query responses. Useful for networks or when you want to display a custom hostname.

```json
{
  "ServerInfo": {
    "ServerName": "My Awesome Server",
    "Motd": "Welcome to our server!",
    "Host": "play.example.com",
    "Port": 5520,
    "MaxPlayers": 100
  }
}
```

All fields are optional. When not set, actual server values are used.

## Full Configuration Example

```json
{
  "Enabled": true,
  "LegacyProtocolEnabled": true,
  "ServerInfo": {
    "ServerName": "My Network",
    "Motd": "Welcome to our server!",
    "Host": "play.mynetwork.com",
    "Port": 5520,
    "MaxPlayers": 1000
  },
  "Authentication": {
    "Public": {
      "Basic": true,
      "Players": false
    },
    "Tokens": {
      "admin-token-123": {
        "Basic": true,
        "Players": true
      }
    }
  },
  "Network": {
    "Enabled": true,
    "ServerId": "lobby-1",
    "NetworkId": "mynetwork",
    "Mode": "AGGREGATE",
    "Store": {
      "Type": "redis",
      "Redis": {
        "Host": "redis.mynetwork.com",
        "Port": 6379,
        "Username": "default",
        "Password": "secret",
        "Database": 0,
        "UseTLS": false
      }
    },
    "Timing": {
      "HeartbeatIntervalSeconds": 15,
      "CacheRefreshSeconds": 60
    }
  },
  "ServerList": {
    "Enabled": true,
    "ServerId": "hytaleone_abc123"
  }
}
```

## Client Libraries

Query servers from your application:

| Language | Package | Status |
|----------|---------|--------|
| Node.js / TypeScript | [@hytaleone/query](https://github.com/HytaleOne/query-js) | Available |
| Python | - | Coming Soon |
| Go | - | Coming Soon |
| Rust | - | Coming Soon |

Want to build a client library? See our [Protocol Documentation](docs/PROTOCOL.md).

## Documentation

- [Protocol Specification](docs/PROTOCOL.md) - V2 protocol details for client developers
- [API Reference](docs/API.md) - Query types and response formats

## Building from Source

```bash
mvn clean package
```

Output: `target/onequery-x.x.x.jar`

## Server List Registration

Register your server on [hytale.one](https://hytale.one/) to make it discoverable to players.

```json
{
  "ServerList": {
    "Enabled": true,
    "ServerId": "your-server-id"
  }
}
```

| Option | Default | Description |
|--------|---------|-------------|
| `ServerList.Enabled` | `true` | Enable server list registration |
| `ServerList.ServerId` | `null` | Your server ID (assigned by hytale.one) |

## License

MIT License - see [LICENSE](LICENSE) for details.

---

**[hytale.one](https://hytale.one/)** - Discover Hytale Servers
