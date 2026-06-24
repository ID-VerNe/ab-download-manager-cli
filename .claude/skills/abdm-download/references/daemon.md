# abdm daemon mode

The daemon is a background JVM process that runs the download engine persistently. When the daemon is running, CLI commands communicate via HTTP on `localhost:16889` instead of booting the engine per command.

## Starting the daemon

```bash
# Start (blocks the terminal — use a separate window or background it)
abdm daemon start

# Start on a specific port (default: 16889)
abdm daemon start --port 16890
```

Once started, all `abdm` subcommands auto-detect the daemon and forward requests — no special flag needed.

## Checking status

```bash
abdm daemon status
# → Daemon is running.
# or
# → Daemon is not running.
```

## Stopping the daemon

```bash
abdm daemon stop
```

Stops all active downloads gracefully, stops the HTTP server, deletes the port file, and releases the process lock.

## Architecture

- **Lock file**: `%LOCALAPPDATA%\ABDownloadManager\daemon\daemon.lock` — prevents multiple instances
- **Port file**: `%LOCALAPPDATA%\ABDownloadManager\daemon\daemon.port` — stores port number for client discovery
- **Data directory**: `%LOCALAPPDATA%\ABDownloadManager\` (shared with desktop GUI)
- Config changes via `abdm config set` take effect immediately in daemon mode (live reload)

## Lifecycle

```
start() → acquire lock → boot engine → start HTTP server → write port file → block
shutdown signal → pause all downloads → stop server → delete port file → release lock
```

## Resource management

- The daemon holds one file lock — kill the process (SIGKILL/Taskkill /F) and the OS releases it automatically
- A stale port file from a crash is overwritten on next clean start
- Active downloads persist in the download DB — on restart, they resume from their last-saved state
