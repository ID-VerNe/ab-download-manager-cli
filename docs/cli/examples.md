# AB Download Manager CLI — Usage Examples

This page shows practical, real-world examples of using `abdm`.

---

## Basic Single Download

```sh
abdm add "https://example.com/file.zip"
```

Downloads the file to the current directory. The download is added in a queued (paused) state.

---

## Download with Live Progress

```sh
abdm add "https://example.com/bigfile.iso" --start
```

Adds the download and immediately starts it, showing a live progress bar:

```
bigfile.iso                              ████████████████░░░░░░░░░░░░ 54.2% 432.1 MB/797.2 MB 3.1 MB/s ETA 1m57s
```

Press `Ctrl+C` to cancel the progress view (the download continues in the background).

---

## Batch Download (Multiple URLs)

```sh
abdm add \
  "https://example.com/file1.zip" \
  "https://example.com/file2.zip" \
  "https://example.com/file3.zip"
```

Adds all three downloads at once. Each gets its own ID.

---

## Download with Authentication

```sh
abdm add "https://private.example.com/protected.zip" --username alice --password s3cret
```

Uses HTTP Basic Authentication.

---

## Custom Output Location and Name

```sh
abdm add "https://example.com/video.mp4" \
  --output-dir ~/Videos \
  --name "tutorial-2025.mp4"
```

Downloads to `~/Videos/tutorial-2025.mp4`.

---

## High-Speed Download (Multi-Connection)

```sh
abdm add "https://example.com/large-file.iso" \
  --connections 32 \
  --start
```

Uses 32 concurrent connections to maximize throughput on servers that support range requests.

---

## Speed-Limited Download

```sh
abdm add "https://example.com/file.zip" --speed-limit 512000
```

Limits the download to 500 KB/s (512,000 bytes/sec). Set to `0` for unlimited.

---

## Duplicate Handling

```sh
# Default behavior: skip if the same URL already exists
abdm add "https://example.com/file.zip"

# Force re-download
abdm add "https://example.com/file.zip" --duplicate override

# Auto-rename to "file (1).zip", "file (2).zip", etc.
abdm add "https://example.com/file.zip" --duplicate add-numbered
```

---

## Detached Background Download

```sh
abdm add "https://example.com/bigfile.iso" --start --detach
```

Adds, starts, and returns to the prompt immediately. The download continues in the background (via the daemon or the direct engine).

Useful in scripts where you don't want to block waiting for a progress bar.

---

## Quiet / Silent Mode

```sh
abdm add "https://example.com/file.zip" --quiet
abdm add "https://example.com/file1.zip" "https://example.com/file2.zip" --quiet
```

Suppresses all output except errors. Exit code is still set: `0` for success, non-zero for errors.

---

## Daemon Workflow (Full Session)

### Start the daemon

```sh
# Terminal 1: Start the daemon (keeps running)
abdm daemon start
```

Output:
```
Starting daemon (data dir: C:\Users\You\.abdm-cli)...
[daemon] Booting download engine...
[daemon] Listening on http://localhost:16889 (PID: 12345)
```

### Add and manage downloads

```sh
# Terminal 2: Add downloads via the daemon
abdm add "https://example.com/video.mp4" --detach
abdm add "https://example.com/archive.zip" --detach
abdm add "https://example.com/image.iso" --detach --connections 16

# Check what's happening
abdm list
```

```
┌──────┬──────────────────────────────────────┬──────────────┬──────────────┐
│ ID   │ Name                                 │ Status       │ Size         │
├──────┼──────────────────────────────────────┼──────────────┼──────────────┤
│ 1    │ video.mp4                            │ Downloading  │ 69.8 MB      │
│ 2    │ archive.zip                          │ Downloading  │ 12.3 MB      │
│ 3    │ image.iso                            │ Added        │ 850.0 MB     │
└──────┴──────────────────────────────────────┴──────────────┴──────────────┘
Total: 3 download(s)
```

### Control downloads

```sh
# Pause a specific download
abdm pause 1

# Pause multiple
abdm pause 2 3

# Resume
abdm resume 1

# View detailed info
abdm info 1
```

```
Download #1
  Name:    video.mp4
  URL:     https://example.com/video.mp4
  Folder:  C:\Users\You
  Status:  Paused
  Size:    69.8 MB
  Added:   2025-06-22 14:30:00
  Connections: 8
```

### Modify config live

```sh
# Increase concurrent downloads (takes effect immediately)
abdm config set maxConcurrentDownloads 10

# Set a global speed limit of 5 MB/s
abdm config set speedLimit 5242880

# Verify
abdm config get speedLimit
# speedLimit = 5242880
```

### Remove completed downloads

```sh
# Remove download entry but keep the file
abdm remove 1 --keep-file

# Remove download entry and delete the file
abdm remove 2
```

### Stop the daemon

```sh
abdm daemon stop
```

Active downloads are paused gracefully before the process exits.

---

## Real-Time Monitoring (TUI Mode)

```sh
abdm monitor
```

Launches a full-screen terminal UI that updates every second:

```
╔══════╤══════════════════════════════════╤════════════╤══════════════════╤══════════╤══════════╗
║ ID   │ Name                             │ Progress   │ Size             │ Speed    │ Status   ║
╠══════╪══════════════════════════════════╪════════════╪══════════════════╪══════════╪══════════╣
║ 3    │ image.iso                        │ ████████░░ │ 512.0M/850.0M    │ 4.2M/s   │ DL       ║
║ 4    │ data.tar.gz                      │ ██░░░░░░░░ │ 45.2M/210.0M     │ 890K/s   │ DL       ║
║ 5    │ doc.pdf                          │ Completed   │ 2.3M/2.3M        │ —        │ Done     ║
╚══════╧══════════════════════════════════╧════════════╧══════════════════╧══════════╧══════════╝
3 active, 2 completed | Press Ctrl+C to exit
```

Press `Ctrl+C` to exit and return to the shell prompt.

---

## Queue Management

```sh
# List queues
abdm queue list

# Add a download to a specific queue
abdm add "https://example.com/file.zip" --queue 1

# Start and stop queues
abdm queue start 1
abdm queue stop 1
```

---

## Restarting Downloads

```sh
# Restart download #1 (clears progress, re-downloads from scratch)
abdm restart 1

# Restart multiple at once
abdm restart 1 2 3
```

---

## Pausing All Downloads

```sh
# Pause everything at once
abdm pause-all
```

---

## Opening Files and Folders

```sh
# Open the downloaded file with the default app
abdm open 1

# Open the folder containing the download
abdm open-folder 1
```

---

## Checksum Management

```sh
# View current checksum on download #1
abdm checksum 1

# Set a SHA-256 checksum (verified after download completes)
abdm checksum 1 e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
```

---

## Editing Download Properties

```sh
# Rename a download
abdm edit 1 --name "final-version.zip"

# Set a speed limit on an active download
abdm edit 1 --speed-limit 102400

# Change the number of connections
abdm edit 1 --connections 12

# Multiple changes at once
abdm edit 1 --name "video.mp4" --connections 8 --speed-limit 0
```

---

## Shell Completions

### Bash

```sh
# Load for the current session
eval "$(abdm completion --shell bash)"

# Install permanently
abdm completion --shell bash > ~/.abdm_completion.sh
echo "source ~/.abdm_completion.sh" >> ~/.bashrc
```

### Zsh

```sh
# Load for the current session
eval "$(abdm completion --shell zsh)"

# Install permanently
abdm completion --shell zsh > ~/.abdm_completion.zsh
echo "source ~/.abdm_completion.zsh" >> ~/.zshrc
```

### Fish

```sh
# Load for the current session
abdm completion --shell fish | source

# Install permanently
abdm completion --shell fish > ~/.config/fish/completions/abdm.fish
```

After installing tab-completions, type `abdm ` and press `Tab` to see available commands. Try `abdm config ` (with a trailing space followed by Tab) to see subcommands, or `abdm add --` (double dash + Tab) to see options.

---

## Scripting with JSON

The `--json` flag makes command output machine-readable for shell scripts.

### List download IDs

```bash
# Get all active download IDs
abdm list --json | jq -r '.[].id'

# Output:
# 1
# 2
# 3
```

### Pause all active downloads programmatically

```bash
for id in $(abdm list --json | jq -r '.[].id'); do
  abdm pause "$id"
done
```

### Get download details

```bash
# Get download info as JSON
abdm info 1 --json | jq '.[0]'

# Extract specific fields
abdm info 1 --json | jq -r '.[0].status'
```

### Monitor via polling (simple script)

```bash
#!/bin/bash
# monitor.sh - Poll download status every 5 seconds
while true; do
  clear
  abdm list --json | jq -r '
    .[] | "\(.id): \(.name) [\(.status)] \(.sizeFormatted)"
  '
  sleep 5
done
```

### Daemon health check

```bash
#!/bin/bash
# Check if daemon is running before sending commands
if abdm daemon status | grep -q "running"; then
  abdm add "https://example.com/file.zip" --detach
else
  echo "Daemon is not running. Starting..."
  abdm daemon start &
  sleep 2
  abdm add "https://example.com/file.zip" --detach
fi
```

---

## Docker / Headless Server

### Dockerfile

```dockerfile
FROM eclipse-temurin:17-jre

COPY abdm /usr/local/bin/abdm

EXPOSE 16889

CMD ["abdm", "daemon", "start"]
```

### docker-compose.yml

```yaml
services:
  abdm:
    image: abdm-headless
    ports:
      - "16889:16889"
    volumes:
      - ./downloads:/downloads
      - ./data:/root/.abdm-cli
    restart: unless-stopped
```

### Control from host

```sh
# Add a download (the CLI detects the daemon automatically)
abdm add "https://example.com/file.zip" --detach

# Or via the HTTP API directly
curl -X POST http://localhost:16889/api/add \
  -H "Content-Type: application/json" \
  -d '{"url": "https://example.com/file.zip", "start": true}'

# List downloads via API
curl http://localhost:16889/api/list
```

---

## Config Management Patterns

### Save and restore configuration

```bash
# Save current config
abdm config list > abdm-config-backup.txt

# Batch set config values
abdm config set threadCount 16
abdm config set maxConcurrentDownloads 8
abdm config set maxDownloadRetryCount 5
abdm config set speedLimit 0
abdm config set dynamicPartCreation true
abdm config set useSparseFileAllocation true
```

### Set a custom User-Agent

```sh
abdm config set userAgent "MyDownloader/1.0"
```

---

## Using a Custom Config Directory

```sh
# Run everything in an isolated directory (useful for testing)
abdm --config-dir /tmp/abdm-test add "https://example.com/file.zip"
abdm --config-dir /tmp/abdm-test list
abdm --config-dir /tmp/abdm-test daemon start
```

---

## Using a Custom Download Directory

```sh
# Set download directory for a single command
abdm add "https://example.com/file.zip" --download-dir /mnt/storage/downloads

# Or use the short form
abdm add "https://example.com/file.zip" -d /mnt/storage/downloads
```

---

## Common Workflows

### Download a file and wait for completion

```sh
abdm add "https://example.com/file.zip" --start
```

The `--start` flag automatically shows live progress and blocks until the download finishes or fails.

### Download multiple files, wait for all

```sh
# Add all downloads (paused)
abdm add "https://example.com/a.zip" "https://example.com/b.zip" "https://example.com/c.zip"

# Start them all
abdm resume 1 2 3

# Monitor progress
abdm monitor
```

### Speed up a slow download

```sh
# Increase connections
abdm edit 1 --connections 16

# Increase global concurrency
abdm config set maxConcurrentDownloads 10
```

### Clean up failed downloads

```sh
# Remove all errored downloads
abdm list --json | jq -r '.[] | select(.status == "Error") | .id' | xargs abdm remove --keep-file
```
