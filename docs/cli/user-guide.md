# AB Download Manager — CLI User Guide

## Overview

**AB Download Manager CLI (`abdm`)** is a command-line interface for [AB Download Manager](https://abdownloadmanager.com). It provides the full download engine in a headless, scriptable form — no GUI required. You can add, monitor, pause, resume, and organize downloads entirely from the terminal.

The CLI operates in two modes:

- **Direct mode** — Each command boots the engine, runs the operation, and exits. Best for one-off downloads and scripting.
- **Daemon mode** — A persistent background process manages all downloads. CLI commands communicate with it over HTTP (localhost:16889). Config changes apply live without restarts.

---

## Installation

### Option 1: NSIS Windows Installer (recommended)

Download the latest installer from the [releases page](https://github.com/amir1376/ab-download-manager/releases/latest). The installer places `abdm.exe` in your `PATH` and sets up the data directory alongside the desktop app.

After installation, open a new terminal and verify:

```sh
abdm --version
```

### Option 2: Manual ZIP

1. Download the CLI distribution ZIP from the releases page.
2. Extract it to a directory of your choice (e.g. `C:\tools\abdm`).
3. Add that directory to your `PATH` environment variable.

### Option 3: Docker / Headless server

The daemon runs as a foreground process (like qBittorrent-nox). In Docker, run:

```dockerfile
FROM ... # Java 17+ runtime
COPY abdm /usr/local/bin/abdm
EXPOSE 16889
CMD ["abdm", "daemon", "start"]
```

Then send commands from another container or over the network (the HTTP API listens on `0.0.0.0:16889`).

---

## Quick Start

```sh
# Add a download
abdm add "https://example.com/file.zip"

# Add and start immediately with live progress
abdm add "https://example.com/file.zip" --start

# List active downloads
abdm list

# Pause a download
abdm pause 1

# Resume it
abdm resume 1

# Remove a download (keeps the file on disk)
abdm remove 1 --keep-file
```

---

## Global Options

These flags can be placed anywhere on the command line:

| Flag | Description |
|------|-------------|
| `--version` | Print the version and exit |
| `--help` | Show help for any command |
| `--config-dir <path>` | Override the config/data directory |
| `--download-dir <path>` / `-d <path>` | Override the default download directory |

---

## Command Reference

### `abdm add <url> [url...]`

Add one or more downloads. Accepts multiple URLs on the same command.

**Options:**

| Flag | Short | Description |
|------|-------|-------------|
| `--output-dir <dir>` | `-o` | Output directory (default: current directory) |
| `--name <name>` | `-n` | Output file name (default: derived from URL) |
| `--start` | `-s` | Start immediately and show a live progress bar (blocking) |
| `--detach` | `-d` | Start in background, exit immediately (implies `--start`) |
| `--queue <id>` | `-q` | Queue ID to add the download to |
| `--connections <n>` | `-c` | Number of concurrent connections per download |
| `--speed-limit <bytes>` | `-l` | Speed limit in bytes/second (0 = unlimited) |
| `--duplicate <strategy>` | | How to handle duplicate URLs: `abort` (default, skip & warn), `override` (re-download), `add-numbered` (auto-rename) |
| `--username <user>` | `-u` | HTTP Basic Auth username |
| `--password <pass>` | `-p` | HTTP Basic Auth password |
| `--quiet` | | Suppress all non-error output |

**Examples:**

```sh
abdm add "https://example.com/video.mp4"
abdm add "https://example.com/a.zip" "https://example.com/b.zip"
abdm add "https://example.com/file.zip" --output-dir ./downloads --name myfile.zip
abdm add "https://example.com/bigfile.iso" --start
abdm add "https://example.com/bigfile.iso" --connections 16
abdm add "https://example.com/protected.zip" --username user --password pass
abdm add "https://example.com/file.zip" --duplicate override
```

**Progress bar with `--start`:**

When you use `--start`, the CLI shows a live progress bar with speed and ETA:

```
video.mp4                                ████████████████████░░░░░░░░░░ 64.7% 45.2 MB/69.8 MB 2.3 MB/s ETA 11s
```

---

### `abdm list`

List all active (non-completed) downloads.

**Options:**

| Flag | Short | Description |
|------|-------|-------------|
| `--all` | `-a` | Show all downloads including completed |
| `--json` | | Output as JSON (for scripting) |

**Output format (table):**

```
┌──────┬──────────────────────────────────────┬──────────────┬──────────────┐
│ ID   │ Name                                 │ Status       │ Size         │
├──────┼──────────────────────────────────────┼──────────────┼──────────────┤
│ 1    │ video.mp4                            │ Downloading  │ 69.8 MB      │
│ 2    │ archive.zip                          │ Paused       │ 12.3 MB      │
└──────┴──────────────────────────────────────┴──────────────┴──────────────┘
Total: 2 download(s)
```

**JSON output:**

```json
[
  {
    "id": 1,
    "name": "video.mp4",
    "url": "https://example.com/video.mp4",
    "status": "Downloading",
    "size": 73282886,
    "sizeFormatted": "69.9 MB",
    "dateAdded": 1719000000000,
    "connections": 8,
    "speedLimit": 0
  }
]
```

---

### `abdm info <id> [id...]`

Show detailed information about one or more downloads.

**Options:**

| Flag | Description |
|------|-------------|
| `--json` | Output as JSON |

**Output:**

```
Download #1
  Name:    video.mp4
  URL:     https://example.com/video.mp4
  Folder:  C:\Users\You\Downloads
  Status:  Downloading
  Size:    69.8 MB
  Added:   2025-06-22 14:30:00
  Connections: 8
```

---

### `abdm pause <id> [id...]`

Pause one or more active downloads. Accepts multiple IDs separated by spaces.

```sh
abdm pause 1
abdm pause 1 2 3
```

### `abdm resume <id> [id...]`

Resume one or more paused downloads. Accepts multiple IDs.

```sh
abdm resume 1
abdm resume 1 2 3
```

### `abdm remove <id> [id...]`

Remove one or more downloads from the list.

**Options:**

| Flag | Short | Description |
|------|-------|-------------|
| `--keep-file` | `-k` | Delete the download entry but keep the file on disk (default: delete both) |

```sh
abdm remove 1
abdm remove 1 2 3 --keep-file
```

### `abdm restart <id> [id...]`

Clear downloaded data and restart one or more downloads from scratch. Accepts multiple IDs.

```sh
abdm restart 1
abdm restart 1 2 3
```

### `abdm pause-all`

Pause all currently active downloads.

```sh
abdm pause-all
```

---

### `abdm monitor`

Launch a real-time TUI (terminal UI) that shows live progress for all active downloads. Refreshes every second. Press `Ctrl+C` to exit.

```
╔══════╤══════════════════════════════════╤════════════╤══════════════════╤══════════╤══════════╗
║ ID   │ Name                             │ Progress   │ Size             │ Speed    │ Status   ║
╠══════╪══════════════════════════════════╪════════════╪══════════════════╪══════════╪══════════╣
║ 1    │ video.mp4                        │ ████████░░ │ 45.2M/69.8M      │ 2.3M/s   │ DL       ║
║ 2    │ image.iso                        │ ██░░░░░░░░ │ 156.0M/850.0M    │ 890K/s   │ DL       ║
╚══════╧══════════════════════════════════╧════════════╧══════════════════╧══════════╧══════════╝
2 active, 5 completed | Press Ctrl+C to exit
```

---

### `abdm queue list`

List all download queues and their status (Active / Stopped).

```sh
abdm queue list
```

```
Queue #1: "Main" - Active
Queue #2: "Priority" - Stopped
```

### `abdm queue start <id>`

Start a queue by ID; queued downloads begin processing.

```sh
abdm queue start 1
```

### `abdm queue stop <id>`

Stop a queue by ID; queued downloads are paused.

```sh
abdm queue stop 1
```

---

### `abdm category list`

List all download categories with their paths, accepted file types, and item counts.

```
┌────┬──────────────────────────────────────┬──────────────────────────────────────┬────────┬────────┐
│ ID │ Name                                 │ Path                                 │ Types  │ Items  │
├────┼──────────────────────────────────────┼──────────────────────────────────────┼────────┼────────┤
│ 1  │ Documents                            │ C:\Users\You\Downloads\Documents     │ 3      │ 12     │
│ 2  │ Videos                               │ C:\Users\You\Downloads\Videos        │ 2      │ 5      │
└────┴──────────────────────────────────────┴──────────────────────────────────────┴────────┴────────┘
Total: 2 categories
```

---

### `abdm config list`

List all current configuration settings with their values.

```sh
abdm config list
```

```
  threadCount = 8
  maxConcurrentDownloads = 3
  speedLimit = 0
  defaultDownloadFolder = C:\Users\You
  ...
```

If no settings have been customized, defaults are shown.

### `abdm config get <key>`

Read a single setting.

```sh
abdm config get threadCount
# threadCount = 8
```

### `abdm config set <key> <value>`

Write a single setting. When the daemon is running, changes apply live via HTTP. When running standalone, changes are written to the shared `appSettings.json` file and will take effect on the next daemon start.

**Supported keys:**

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `threadCount` | int | 8 | Default number of connections per download |
| `maxConcurrentDownloads` | int | 3 | Maximum simultaneous downloads |
| `maxDownloadRetryCount` | int | 3 | Retries on failure |
| `dynamicPartCreation` | bool | true | Dynamically create/merge download parts |
| `useServerLastModifiedTime` | bool | false | Use the server-provided `Last-Modified` timestamp |
| `appendExtensionToIncompleteDownloads` | bool | false | Append `.abdm!` to in-progress files |
| `useSparseFileAllocation` | bool | true | Use sparse files for pre-allocation |
| `speedLimit` | long | 0 | Global speed limit in bytes/sec (0 = unlimited) |
| `defaultDownloadFolder` | string | CWD | Default output directory |
| `userAgent` | string | `ABDownloadManager-CLI/1.0` | Custom User-Agent header |

```sh
abdm config set threadCount 16
abdm config set maxConcurrentDownloads 5
abdm config set speedLimit 1048576   # 1 MB/s
abdm config set defaultDownloadFolder "C:\Users\You\Downloads"
```

---

### `abdm daemon start`

Start the background download daemon. This runs as a **foreground process** (blocks the terminal). Use systemd, screen, tmux, or Docker to background it.

The daemon:
- Boots the download engine and restores pending downloads
- Starts an HTTP API on `http://localhost:16889`
- Writes a port file to `~/.abdm-cli/daemon/daemon.port`
- Acquires an exclusive file lock to prevent duplicate instances

```sh
abdm daemon start
```

Output:
```
Starting daemon (data dir: C:\Users\You\.abdm-cli)...
[daemon] Booting download engine...
[daemon] Listening on http://localhost:16889 (PID: 12345)
```

### `abdm daemon stop`

Send a shutdown signal to the running daemon. Active downloads are paused gracefully before the process exits.

```sh
abdm daemon stop
```

### `abdm daemon status`

Check whether the daemon is currently running.

```sh
abdm daemon status
# Daemon is running.
# (or) Daemon is not running.
```

---

### `abdm open <id>`

Open the downloaded file with the system default application.

```sh
abdm open 1
```

In daemon mode, the file is opened on the daemon host machine (useful for headless setups).

### `abdm open-folder <id>`

Open the folder containing the downloaded file in the system file manager.

```sh
abdm open-folder 1
```

---

### `abdm checksum <id>`

View the current checksum (hash) for a download. If set, the checksum is verified after download completes.

```sh
abdm checksum 1
```

### `abdm checksum <id> <hash>`

Set the checksum (hash) for a download. After download completes, the engine will verify the file against this hash.

```sh
abdm checksum 1 e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
```

---

### `abdm edit <id>`

Edit properties of an existing download. At least one option is required.

**Options:**

| Flag | Short | Description |
|------|-------|-------------|
| `--name <name>` | `-n` | New name for the download |
| `--speed-limit <bytes>` | `-l` | Speed limit in bytes/sec (0 = unlimited) |
| `--connections <n>` | `-c` | Number of concurrent connections |

```sh
abdm edit 1 --name "renamed-file.zip"
abdm edit 1 --speed-limit 512000    # 500 KB/s
abdm edit 1 --connections 12
abdm edit 1 --name "video.mp4" --speed-limit 0
```

---

### `abdm completion --shell <bash|zsh|fish>`

Generate shell completion scripts. The script is printed to stdout.

**Instructions:**

Pipe the output for immediate use in your current shell session:

```sh
# For bash
eval "$(abdm completion --shell bash)"

# For zsh
eval "$(abdm completion --shell zsh)"

# For fish
abdm completion --shell fish | source
```

For permanent installation, save to a file and source it from your shell's rc file:

```sh
abdm completion --shell bash > ~/.abdm_completion.sh
echo "source ~/.abdm_completion.sh" >> ~/.bashrc
```

```sh
abdm completion --shell zsh > ~/.abdm_completion.zsh
echo "source ~/.abdm_completion.zsh" >> ~/.zshrc
```

```sh
abdm completion --shell fish > ~/.config/fish/completions/abdm.fish
```

Alternatively, add a one-liner to your rc file:

```sh
# ~/.bashrc or ~/.zshrc
eval "$(abdm completion --shell bash)"   # or zsh
```

Completions include:
- All top-level commands
- Subcommands for `queue`, `category`, `config`, `daemon`, `completion`
- All flags and their expected arguments
- Config key names for `config get` / `config set`
- Shell type names for `completion --shell`
- Duplicate strategy names for `add --duplicate`

---

## Daemon Mode vs Direct Mode

### Understanding the two modes

Every CLI command automatically detects whether the daemon is running:

| Mode | How it works | When to use |
|------|-------------|-------------|
| **Direct** | Boots the engine fresh for each command. The download database is read/written directly. | Quick one-off downloads, scripting batch jobs, when you don't need persistence between commands. |
| **Daemon** | Sends HTTP requests to the background daemon process (port 16889). The daemon manages state. | Long-running sessions, browser integration, live config changes, running on a headless server. |

### How auto-detection works

1. The CLI looks for `~/.abdm-cli/daemon/daemon.port` (or `%LOCALAPPDATA%/ABDownloadManager/daemon/daemon.port` if installed alongside the desktop app).
2. It reads the port number and sends a `GET /ping` request.
3. If the daemon responds with HTTP 200, all commands forward to the daemon via HTTP.
4. If there is no response, the CLI falls back to direct mode.

### Using the daemon for a typical workflow

```sh
# Terminal 1: Start the daemon
abdm daemon start

# Terminal 2: Add downloads (they go through the daemon)
abdm add "https://example.com/bigfile.iso" --start --detach
abdm add "https://example.com/another.zip"

# Check status
abdm list
abdm info 1

# Modify config live
abdm config set speedLimit 2097152

# Stop when done
abdm daemon stop
```

### Running the daemon on a headless server

Since `abdm daemon start` is a foreground process (like `qBittorrent-nox`), use your preferred process supervisor:

```sh
# With screen
screen -S abdm-daemon abdm daemon start

# With systemd (create a service unit)
# With Docker (CMD abdm daemon start)
```

---

## Scripting with JSON Output

Many commands support `--json` for machine-readable output:

```bash
# List downloads as JSON
abdm list --json

# Get download info as JSON
abdm info 1 --json

# Scripted download with ID capture
# The daemon responds with the new download ID
```

Combine with tools like `jq` for powerful scripting:

```bash
# Get all active download IDs
abdm list --json | jq '.[].id'

# Pause all downloads (using the IDs from list)
abdm list --json | jq -r '.[].id' | xargs abdm pause
```

---

## Configuration File

Settings are stored in a shared `appSettings.json` file:

**Windows (alongside desktop app):**
`%LOCALAPPDATA%\ABDownloadManager\config\appSettings.json`

**Standalone CLI:**
`~/.abdm-cli/config/appSettings.json`

This file is a flat JSON object. You can edit it manually with any text editor while the daemon is stopped, or use `abdm config set` while the daemon is running for live changes.

```json
{
  "threadCount": 16,
  "maxConcurrentDownloads": 5,
  "speedLimit": 1048576
}
```

---

## Data Directory

The CLI stores its data (download database, parts, queues, categories, config) in a shared data directory.

**Location discovery order:**
1. `--config-dir <path>` flag (explicit override)
2. Desktop app's data directory at `%LOCALAPPDATA%\ABDownloadManager` (when installed alongside the GUI)
3. `~/.abdm-cli` (standalone fallback)

Directory structure:
```
~/.abdm-cli/
├── config/
│   └── appSettings.json          # Shared settings
├── system/                        # System-level data
├── download_db/
│   ├── downloadlist/              # Individual download item files
│   ├── parts/                     # Download part tracking
│   ├── queues/                    # Queue definitions
│   └── categories/
│       └── categories.json        # Category definitions
├── download_data/                 # Partial download data
└── daemon/
    ├── daemon.port                # Port file (daemon running)
    └── daemon.lock                # Lock file (daemon running)
```

---

## Environment

- **Java**: Requires Java 17+ (bundled with the NSIS installer)
- **Terminal**: ANSI color support recommended (most modern terminals)
- **Platforms**: Windows, Linux, macOS

---

## Troubleshooting FAQ

### "Daemon is already running" when starting

The daemon uses an exclusive file lock to prevent multiple instances. If you see this message:

1. Stop the existing daemon: `abdm daemon stop`
2. If that fails, delete the lock file: `rm -f ~/.abdm-cli/daemon/daemon.lock`
3. Restart: `abdm daemon start`

### "Failed to communicate with daemon" from commands

The CLI detected the port file but could not reach the daemon process:

1. Check if the daemon is running: `abdm daemon status`
2. If it says it is running but commands fail, the daemon may have crashed but left the port file. Clean up: `abdm daemon stop` or manually delete `~/.abdm-cli/daemon/daemon.port`
3. Restart the daemon.

### "No downloads found" when I know there are downloads

By default, `abdm list` only shows **active** (non-completed) downloads. Use `--all` / `-a` to see completed ones:

```sh
abdm list --all
```

### Download shows "Added" but never starts

Downloads added without `--start` go into a queued state. Either:

- Use `abdm start <id>` (see note below), or
- Add with `--start` / `--detach` to auto-start, or
- Start a queue with `abdm queue start <id>` if it was added to a stopped queue.

Note: The current CLI does not have a standalone `start` command — use `resume` to begin a queued download.

### Port conflicts

If port 16889 is already in use by another application:

The default port is currently fixed at 16889. To change it, modify the `DEFAULT_PORT` constant in `DaemonBootstrap.kt` and rebuild.

### Progress bar always shows 100%

If the progress display reports 100% throughout the download, this is a known limitation — the downloaded byte count may not be accurately reported for all download methods.

### "Unknown or unsupported key" for config

Run `abdm config list` first to see the full list of available keys and their current values. Only the keys listed in the supported-keys table above are valid.

### How do I see a command's help?

```sh
abdm --help                  # All commands
abdm add --help              # Specific command
abdm config set --help       # Nested subcommand
```

### How do I check my version?

```sh
abdm --version
```

### How do I run the CLI from the source code?

```sh
./gradlew :cli:app:run --args="add URL"
./gradlew :cli:app:run --args="list"
```

---

## Building from Source

```sh
# Build the CLI distribution
./gradlew :cli:app:distZip
./gradlew :cli:app:distTar

# Run directly (development)
./gradlew :cli:app:run --args="--help"
```

The distribution ZIP/TAR will be in `cli/app/build/distributions/`.

---

## HTTP API (for advanced scripting)

When the daemon is running, it exposes an HTTP API on `http://localhost:16889`. All responses use a unified JSON envelope:

```json
{"success": true, "data": ..., "error": null}
```

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/ping` | GET | Health check — returns `"pong"` |
| `/api/status` | GET | Daemon status with download counts |
| `/api/list` | GET | List all downloads (JSON array) |
| `/api/list-item` | POST | Get single download (`{"id": N}`) |
| `/api/add` | POST | Add download (see DaemonClient for body schema) |
| `/api/pause` | POST | Pause downloads (`{"ids": [1,2]}`) |
| `/api/resume` | POST | Resume downloads (`{"ids": [1,2]}`) |
| `/api/remove` | POST | Remove downloads (`{"ids": [1,2], "keepFile": true}`) |
| `/api/restart` | POST | Restart downloads (`{"ids": [1,2]}`) |
| `/api/pause-all` | POST | Pause all downloads |
| `/api/queue` | GET | List all queues |
| `/api/queue/start` | POST | Start a queue (`{"queueId": 1}`) |
| `/api/config` | GET | Get all config |
| `/api/config/set` | POST | Set config (`{"key": "threadCount", "value": "16"}`) |
| `/api/open` | POST | Open download file (`{"id": 1}`) |
| `/api/open-folder` | POST | Open download folder (`{"id": 1}`) |
| `/api/checksum/set` | POST | Set checksum (`{"id": 1, "checksum": "hash"}`) |
| `/api/edit` | POST | Edit download (`{"id": 1, "name": "...", "speedLimit": 0}`) |
| `/api/shutdown` | POST | Gracefully shut down the daemon |

---

## Need Help?

- [GitHub Issues](https://github.com/amir1376/ab-download-manager/issues)
- [Telegram Discussion Group](https://t.me/abdownloadmanager_discussion)
- [Telegram Channel](https://t.me/abdownloadmanager)
- [Project Website](https://abdownloadmanager.com)
