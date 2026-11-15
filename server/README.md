# PeekParty server (PeekMusic)

This is upstream [metroserver](https://github.com/MetrolistGroup/metroserver) with PeekMusic's friends system on
top. It runs at `wss://listen.peekmusic.org/ws` (see `website/README.md` for the Caddy block).

## What PeekMusic adds

- `peekproto/peek_friends.proto`: friend payloads, separate from upstream's `metroproto/listentogether.proto`
  (vendored from metroproto at the commit the app's submodule uses). The 1.3.0 field numbers must not change.
- `internal/server/peek_friends*.go`: accounts (a random app token, stored only as its SHA-256), friend codes,
  friend requests, presence, party invites and account deletion. Upstream code only calls into it from five
  marked places (`// PeekMusic:`): the message switch, client removal, the feature list, the codec fallback for
  generated payloads, and startup.
- `authenticate` with `presence_only`: the party connection signs in so friends see which party you are in, but
  gets no friend messages; the app keeps a separate friends connection for those.
- Feature `peek_friends_v2`: clients that announce it get friend requests, invites and deletion. Apps without it
  (PeekMusic 1.3.0) keep working: adding them befriends at once, as before, because they cannot accept requests.

Message types: `authenticate`, `add_friend`, `remove_friend` (also withdraws or declines a request), `decline_invite`,
`accept_friend_request`, `decline_friend_request`, `invite_friend`, `delete_friend_account` from the app;
`auth_success`, `friends_status`, `friend_presence_update`, `friend_added`, `friend_removed`, `friend_request`,
`friend_invite`, `friend_invite_declined`, `friend_account_deleted` from the server.

## Updating from upstream

Copy the new upstream files over, keep the five `// PeekMusic:` lines, run `go test -race ./...`.
The import commit `chore(server): import upstream metroserver` shows the untouched upstream state.

## Deploy

```sh
rsync -a --delete --exclude data --exclude ua_policy.json --exclude docker-compose.yml \
  server/ peekmusic:/opt/metroserver/
ssh peekmusic 'cd /opt/metroserver && docker compose up -d --build'
```

`docker-compose.yml` on the server keeps the port on 127.0.0.1, `data/` holds the bbolt database, and logs go to
journald (14 days, as the privacy policy says).

---

# Upstream: Metroserver

[![codecov](https://codecov.io/gh/MetrolistGroup/metroserver/graph/badge.svg)](https://codecov.io/gh/MetrolistGroup/metroserver)

A high performance Go WebSocket server for Metrolist's "Listen Together" feature.
Utilizes protobuf and gzip compression for fast and efficient communication between clients.

# Quickstart

## Locally
You need to install go, protobuf, and protoc-gen-go

```bash
git clone https://github.com/MetrolistGroup/metroserver
cd metroserver

# Generate protobuf files (required first time)
./scripts/generate_proto.sh

# Download dependencies
go mod download

# Build the server
go build -o metroserver ./cmd/metroserver

# Run on default port 8080
./metroserver

# Run on custom port
PORT=9000 ./metroserver

# Run with a User-Agent policy and the protected User-Agent report
cp ua_policy.example.json ua_policy.json
UA_POLICY_FILE=./ua_policy.json UA_ADMIN_TOKEN='replace-me' ./metroserver

# Read persistent connection counts
curl -H 'Authorization: Bearer replace-me' http://localhost:8080/uas
```

## Configuration

`PORT` sets the HTTP/WebSocket port. It defaults to `8080`.

`UA_POLICY_FILE` points at a JSON User-Agent policy. Every client gets normal
service unless a rule names it: `block` refuses the connection, `advert` replaces
the queue title with an ad, and `rickroll` does that and slips Never Gonna Give
You Up into the queue. Only normal-service clients can host. The `allow` list is
accepted for older policy files and has no effect. Send `SIGHUP` to reload the
policy for new connections. See `ua_policy.example.json`.

`DATABASE_FILE` sets the shared bbolt database path and defaults to
`metroserver.db` (`/app/data/metroserver.db` in the Docker image). Rooms and
reconnect tokens are written to it every two seconds, so a crash loses at most
that much; it also stores connection counts for up to 10,000 distinct, sanitized
User-Agent values. `UA_ADMIN_TOKEN` enables
`/uas`; send it as a bearer token to read the counts. Without the token, `/uas`
is not exposed. The database is created with `0600` permissions.

## Protocol features

Clients list optional features in `client_capabilities.features`; the server
answers with the subset it enabled. Older clients that send none keep the
original behaviour.

| Feature | Effect |
|---|---|
| `request_id` | Envelope `request_id` is echoed on replies and errors; silent commands get an `ack`; throttled `request_sync` returns `sync_rate_limited`. |
| `origin_tag` | `sync_playback.origin_user_id` names the user whose action caused it. |
| `drift_report` | Positions reported in `ping` come back as `pong.drift_ms`. |
| `desync_canary` | A `ping.state_hash` mismatch sets `pong.state_mismatch` and triggers a `sync_state`. |
| `server_restarting` | `server_restarting` arrives before a graceful shutdown (close code 1012). |
| `suggestion_ack` | Guests receive `suggestion_submitted` with their suggestion ID. |
| `replace_queue` | `change_track` with `replace_queue` replaces the queue even when empty. |
| `host_sequence` | Play/pause/seek with an older `host_sequence` are rejected as `stale_action`. |
| `track_revision` | `buffer_ready.track_revision` must match the latest `change_track`. |
| `update_track` | `update_track` corrects current-track metadata without restarting it. |

A host that stays disconnected for 30 seconds is replaced by the connected
listener with the lowest round trip; the original host takes the room back if
they reconnect within the 15-minute grace period.

## Project Structure

- `cmd/metroserver` contains the executable entrypoint.
- `internal/server` contains server behavior and tests.
- `metroproto` contains the protobuf schema submodule.
- `proto` contains generated Go protobuf code.
- `scripts` contains development and generation scripts.

## Docker

```bash
# Clone the repository
git clone https://github.com/MetrolistGroup/metroserver
cd metroserver

# Build locally
docker build -t MetrolistGroup:latest .

# Run on port 8080
docker run -d \
  -p 8080:8080 \
  -e PORT=8080 \
  --name metroserver \
  metroserver:latest

# Run on custom port with a User-Agent policy
docker run -d \
  -p 9000:9000 \
  -e PORT=9000 \
  -e UA_POLICY_FILE=/config/ua_policy.json \
  -e DATABASE_FILE=/app/data/metroserver.db \
  -e UA_ADMIN_TOKEN="$UA_ADMIN_TOKEN" \
  -v "$PWD/ua_policy.example.json:/config/ua_policy.json:ro" \
  -v metroserver-data:/app/data \
  --name metroserver \
  metroserver:latest
```

## Docker Compose

```yaml
---
services:
  metroserver:
    image: ghcr.io/MetrolistGroup/metroserver:latest
    ports:
      - "8080:8080"
    environment:
      - PORT=8080
      - UA_POLICY_FILE=/config/ua_policy.json
      - DATABASE_FILE=/app/data/metroserver.db
      - UA_ADMIN_TOKEN=${UA_ADMIN_TOKEN}
    volumes:
      - ./ua_policy.example.json:/config/ua_policy.json:ro
      - user-agent-data:/app/data
    healthcheck:
      test:
        [
          "CMD",
          "wget",
          "--no-verbose",
          "--tries=1",
          "--spider",
          "http://localhost:8080/health",
        ]
      interval: 30s
      timeout: 3s
      retries: 3
      start_period: 5s
    restart: unless-stopped
    # Graceful shutdown drains clients and saves state within 10 seconds.
    stop_grace_period: 15s

volumes:
  user-agent-data:
```
