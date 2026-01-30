# OneQuery Protocol Specification (V2)

This document describes the OneQuery V2 binary protocol for querying Hytale server status over UDP.

## Overview

- **Transport**: UDP
- **Port**: Same as game server (default 5520)
- **Byte Order**: Little-endian
- **String Encoding**: UTF-8, length-prefixed (2-byte length + bytes)
- **Max Packet Size**: 1400 bytes (MTU-safe)

## Connection Flow

```
Client                          Server
  |                               |
  |-- Challenge Request --------->|
  |<-- Challenge Response --------|
  |                               |
  |-- Query Request ------------->|
  |<-- Query Response ------------|
```

All queries (except challenge) require a valid challenge token to prevent amplification attacks.

## Request Format

### Magic Bytes

All requests start with the magic string `ONEQUERY` (8 bytes, ASCII).

### Challenge Request

Request a challenge token before making queries.

```
Offset  Size  Field
0       8     Magic: "ONEQUERY"
8       1     Type: 0x00 (CHALLENGE)
```

**Total size**: 9 bytes

### Query Request

```
Offset  Size  Field
0       8     Magic: "ONEQUERY"
8       1     Type: 0x01 (BASIC) or 0x02 (PLAYERS)
9       32    Challenge Token (from challenge response)
41      4     Request ID (echoed in response)
45      2     Flags (see Request Flags)
47      4     Offset (for pagination, used with PLAYERS)
51      ...   Optional: Auth Token (if FLAG_HAS_AUTH_TOKEN set)
```

**Request Flags**:

| Flag | Value | Description |
|------|-------|-------------|
| `FLAG_HAS_AUTH_TOKEN` | `0x0001` | Request includes auth token |

**Auth Token Format** (when flag is set):

```
Offset  Size  Field
51      2     Token Length
53      N     Token Bytes (UTF-8)
```

## Response Format

### Header

All responses start with this header:

```
Offset  Size  Field
0       8     Magic: "ONEREPLY"
8       1     Protocol Version (0x01)
9       2     Flags (see Response Flags)
11      4     Request ID (echoed from request)
15      2     Payload Length
17      ...   Payload (TLV-encoded)
```

**Response Flags**:

| Flag | Value | Description |
|------|-------|-------------|
| `FLAG_HAS_MORE_PLAYERS` | `0x0001` | More players available (pagination) |
| `FLAG_AUTH_REQUIRED` | `0x0002` | Authentication required for this endpoint |
| `FLAG_IS_NETWORK` | `0x0010` | Response contains aggregated network data |
| `FLAG_HAS_ADDRESS` | `0x0020` | Response includes host/port |

### Challenge Response

```
Offset  Size  Field
0       8     Magic: "ONEREPLY"
8       1     Type: 0x00 (CHALLENGE)
9       32    Challenge Token
41      7     Reserved (zeros)
```

**Total size**: 48 bytes

The challenge token is valid for the client's IP address only and expires after a short period.

## TLV Payload Format

Response payloads use Type-Length-Value (TLV) encoding:

```
Offset  Size  Field
0       2     Type
2       2     Length (N)
4       N     Value
```

### TLV Types

| Type | Value | Description |
|------|-------|-------------|
| `SERVER_INFO` | `0x0001` | Server information |
| `PLAYER_LIST` | `0x0002` | Player list |

### Server Info (Type 0x0001)

Returned for BASIC queries.

```
Offset  Size     Field
0       2+N      Server Name (string)
...     2+N      MOTD (string)
...     4        Player Count (int32)
...     4        Max Players (int32)
...     2+N      Version (string)
...     4        Protocol Version (int32)
...     2+N      Protocol Hash (string)
...     2+N      Host (string) - only if FLAG_HAS_ADDRESS
...     2        Port (uint16) - only if FLAG_HAS_ADDRESS
```

### Player List (Type 0x0002)

Returned for PLAYERS queries.

```
Offset  Size     Field
0       4        Total Player Count (across all pages)
4       4        Players in this Response
8       4        Offset (starting index)
12      ...      Player Entries
```

**Player Entry**:

```
Offset  Size     Field
0       2+N      Username (string)
...     8        UUID Most Significant Bits
...     8        UUID Least Significant Bits
```

## Data Types

### String

Length-prefixed UTF-8 string:

```
Offset  Size  Field
0       2     Length (N)
2       N     UTF-8 Bytes
```

### Integer (int32)

4 bytes, little-endian, signed.

### Short (uint16)

2 bytes, little-endian, unsigned.

### UUID

16 bytes total:
- 8 bytes: Most Significant Bits
- 8 bytes: Least Significant Bits

## Pagination

The PLAYERS endpoint supports pagination for servers with many players.

1. Send a PLAYERS request with `offset = 0`
2. Check `FLAG_HAS_MORE_PLAYERS` in response flags
3. If set, send another request with `offset = previous_offset + players_received`
4. Repeat until `FLAG_HAS_MORE_PLAYERS` is not set

## Error Handling

### AUTH_REQUIRED Response

When `FLAG_AUTH_REQUIRED` is set, the client must retry with an auth token.

```
Response Flags: 0x0002
Payload: Empty or minimal SERVER_INFO
```

### Invalid Challenge Token

Requests with invalid/expired challenge tokens receive no response (dropped silently).

### Network Mode

When `FLAG_IS_NETWORK` is set:
- Player counts are aggregated across all servers in the network
- Player list includes players from all servers
- Individual server info is not available

## Example Implementation

### Pseudocode: Basic Query

```
// 1. Request challenge
send(server, "ONEQUERY" + byte(0x00))
response = receive()
challengeToken = response[9:41]

// 2. Send basic query
request = "ONEQUERY"
request += byte(0x01)          // BASIC type
request += challengeToken      // 32 bytes
request += int32LE(requestId)  // 4 bytes
request += int16LE(0)          // flags
request += int32LE(0)          // offset

send(server, request)
response = receive()

// 3. Parse response
magic = response[0:8]          // "ONEREPLY"
version = response[8]          // 0x01
flags = int16LE(response[9:11])
echoedId = int32LE(response[11:15])
payloadLen = int16LE(response[15:17])
payload = response[17:17+payloadLen]

// 4. Parse TLV payload
tlvType = int16LE(payload[0:2])
tlvLen = int16LE(payload[2:4])
tlvValue = payload[4:4+tlvLen]

// 5. Parse server info from tlvValue
serverName = readString(tlvValue)
motd = readString(tlvValue)
playerCount = readInt32(tlvValue)
// ... etc
```

## Version History

| Version | Changes |
|---------|---------|
| 0x01 | Initial V2 release |
