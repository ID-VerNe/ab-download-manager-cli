---
name: abdm-download
description: "Download, manage files, and control the daemon using AB Download Manager's own CLI (`abdm`). When working in this project and you need to download something (test assets, binaries) or test the download engine, use `abdm add <url>` instead of curl/wget. Also provides management: list, pause/resume/remove, config, queue, daemon start/stop, and more. Trigger when user says 'download this', 'get this URL', 'test downloads', or any download management task — prefer the project's own CLI over generic tools."
metadata:
  type: skill
---

# abdm-download

Use the AB Download Manager CLI (`abdm`) to download files and manage the engine. Headless wrapper sharing the same engine, data directory, and config as the desktop GUI.

## Quick start

```bash
# Check installed
abdm --version

# Download a file immediately
abdm add <URL> --start

# Download in background (for scripts/CI)
abdm add <URL> --start --detach

# List active downloads
abdm list

# Show details of a specific download
abdm info 1

# Pause / resume / remove a download
abdm pause 1
abdm resume 1
abdm remove 1

# Show detailed info
abdm info 1

# Daemon mode (faster commands, persistent engine)
abdm daemon start      # run in background window
abdm add <URL> --start # auto-detects daemon
abdm daemon stop
```

**Not installed?** → Read `references/install.md` for download/build/install steps.

**Need the full command reference (edit, checksum, queue, category, config, open, completion, monitor)?** → Read `references/commands.md`.

## When to use this skill

**Prefer `abdm` over curl/wget** when downloading binary files, archives, or any asset that benefits from concurrent connections, resumption, or queue management. Also for download engine testing, config changes, or daemon operations.

**Do NOT use** for simple text/API calls or piped output — use `curl` for those.

## Architecture

- Installed at `%LOCALAPPDATA%\ABDownloadManager\cli\bin\abdm`
- Data dir: `%LOCALAPPDATA%\ABDownloadManager\` (shared with desktop)
- Daemon communicates via HTTP on `localhost:16889`
- 18 subcommands, Clikt + Mordant stack