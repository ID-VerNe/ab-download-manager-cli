# abdm commands reference

All commands run as `abdm <command> [options]`. After starting the daemon (`abdm daemon start`), commands auto-forward via HTTP.

## Download management

```bash
# Add and start a download (foreground with progress bar)
abdm add <URL> --start

# Add with custom file name and output directory
abdm add <URL> -o <DIR> -n <FILENAME> --start

# Add with 8 concurrent connections and speed limit (500 KB/s)
abdm add <URL> -c 8 -l 500000 --start

# Multiple URLs at once
abdm add <URL1> <URL2> --start

# Quiet mode (no progress output — useful in scripts)
abdm add <URL> --start --quiet

# Handle duplicates: override existing / auto-rename
abdm add <URL> --duplicate override
abdm add <URL> --duplicate add-numbered

# HTTP Basic Auth
abdm add <URL> -u <username> -p <password>

# Add to a specific queue
abdm add <URL> -q <queue-id> --start
```

## List & Info

```bash
# Show active (non-completed) downloads
abdm list

# Show ALL downloads (including completed)
abdm list -a

# Output as JSON
abdm list --json
abdm list -a --json

# Show detailed info for a specific download
abdm info 1

# Show multiple downloads
abdm info 1 2 3

# JSON output for scripting
abdm info 1 --json
```

## Control (pause / resume / remove / restart)

```bash
# Pause one or more downloads
abdm pause 1
abdm pause 1 2 3

# Resume
abdm resume 1

# Remove
abdm remove 1
abdm remove 1 --keep-file   # keep downloaded file on disk

# Restart (re-download from scratch)
abdm restart 1

# Pause ALL active downloads
abdm pause-all
```

## Edit download properties

```bash
# Rename a download
abdm edit 1 --name "new-filename"

# Set speed limit (bytes/sec)
abdm edit 1 --speed-limit 500000

# Change connection count
abdm edit 1 --connections 8

# Combine
abdm edit 1 --name "myfile" --speed-limit 0 --connections 4
```

## Checksum management

```bash
# View current checksum
abdm checksum 1

# Set checksum (for verification after download)
abdm checksum 1 e3b0c44298fc1c149afbf4c8996fb924
```

## Queue management

```bash
# List all queues
abdm queue list

# Start/stop a queue
abdm queue start 0
abdm queue stop 0
```

## Category

```bash
# List all categories
abdm category list
```

## Configuration

```bash
# List all config values
abdm config list

# Get a specific setting
abdm config get threadCount

# Set a setting (with value range validation)
abdm config set threadCount 8           # 1-64
abdm config set maxConcurrentDownloads 3  # 1-50
abdm config set speedLimit 0            # 0 = unlimited
abdm config set userAgent "MyAgent/1.0"
abdm config set defaultDownloadFolder "C:\Downloads"

# In daemon mode, config changes take effect immediately (live reload)
```

## Open files

```bash
# Open a downloaded file with the system default app
abdm open 1

# Open the folder containing a downloaded file
abdm open-folder 1
```

## Shell completion

```bash
# Generate completion script for your shell
abdm completion --shell bash    # → eval "$(abdm completion --shell bash)"
abdm completion --shell zsh
abdm completion --shell fish
```

## TUI Monitor

```bash
# Real-time terminal dashboard showing active downloads
abdm monitor
# Press Ctrl+C to exit
```

## Daemon management

See `references/daemon.md` for full daemon lifecycle details.

```bash
# Start the daemon (blocks the terminal — run in background)
abdm daemon start

# Check if daemon is running
abdm daemon status

# Stop the daemon
abdm daemon stop
```