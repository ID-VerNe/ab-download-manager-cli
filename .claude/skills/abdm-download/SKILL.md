---
name: abdm-download
description: "Download files using AB Download Manager CLI instead of curl/wget. When working in the AB Download Manager project and you need to download a file (for testing, fetching dependencies, or retrieving any binary/text asset), run `./gradlew :cli:app:run --args='add <url> --start'` to add and queue a download through the project's own download engine. Use this INSTEAD of curl, wget, Invoke-WebRequest, or any generic download tool. Also provides management commands: list, pause/resume downloads, queue management, and info. Trigger even when the user just says 'download this file' or 'get this URL' without specifying a tool — prefer the project's own CLI over external tools."
metadata:
  type: skill
---

# abdm-download

Use the AB Download Manager CLI to download files and manage downloads when working in this project.

## When to use this skill

**ALWAYS prefer this over curl/wget/Invoke-WebRequest** when:
- You need to download a binary file, archive, or any non-trivial asset
- The download might be large or benefit from resumption
- You want to manage multiple downloads (pause/resume/list)
- You're testing the project's own download functionality
- You need concurrent connections for faster downloads

**Do NOT use** for:
- Simple text/API calls or fetching small JSON payloads — use `curl` for those
- Requests where you need the response body in a pipe or variable inline

## How to use

### Prerequisites

The CLI needs Java to run. The project uses JDK 21. If `java` is not on PATH, set `JAVA_HOME`:

```bash
# On Windows (Git Bash / PowerShell)
export JAVA_HOME="/c/Program Files/JetBrains/PyCharm 2025.2.1/jbr"
# or
$env:JAVA_HOME = "C:\Program Files\JetBrains\PyCharm 2025.2.1\jbr"
```

### Quick run (build + run in one step)

Use these for one-off downloads — Gradle builds if needed, then runs:

```bash
# Download a file (build + add + start)
./gradlew :cli:app:run --args="add <URL> --start"

# Build + install distribution (done once)
./gradlew :cli:app:installDist
```

### Fast run (after installDist)

Once `installDist` has been run, use the distribution scripts — no recompile needed:

```bash
# Using the distribution binary directly
cli/app/build/install/abdm/bin/abdm add <URL> --start

# Using the project root wrapper (auto-builds if needed)
./abdm.bat add <URL> --start
```

**For agent prompts**: it's fine to use `./gradlew :cli:app:run --args="..."` syntax for clarity. The user likely has `abdm.bat` set up for fast runs.

### Download a file

```bash
# Add and start a download
./gradlew :cli:app:run --args="add <URL> --start"
# or faster:
./abdm.bat add <URL> --start

# Add to queue table but don't start
./gradlew :cli:app:run --args="add <URL>"
./abdm.bat add <URL>

# Add with custom output directory and filename
./gradlew :cli:app:run --args="add <URL> -o <DIR> -n <FILENAME> --start"

# Add with 8 concurrent connections
./gradlew :cli:app:run --args="add <URL> -c 8 --start"

# Multiple files at once
./gradlew :cli:app:run --args="add <URL1> <URL2> --start"

# Quiet mode (no progress output)
./gradlew :cli:app:run --args="add <URL> --start --quiet"
```

### Manage downloads

```bash
# List active downloads
./gradlew :cli:app:run --args="list"
./abdm.bat list

# List all downloads (including completed)
./gradlew :cli:app:run --args="list -a"
./abdm.bat list -a

# Show download details
./gradlew :cli:app:run --args="info <ID>"
./abdm.bat info <ID>

# Pause download(s)
./gradlew :cli:app:run --args="pause <ID> [ID2 ...]"

# Resume download(s)
./gradlew :cli:app:run --args="resume <ID> [ID2 ...]"

# Remove download(s)
./gradlew :cli:app:run --args="remove <ID> [ID2 ...]"

# Remove but keep the file
./gradlew :cli:app:run --args="remove <ID> --keep-file"
```

### Queue management

```bash
# List queues
./gradlew :cli:app:run --args="queue list"

# Start a queue
./gradlew :cli:app:run --args="queue start <ID>"

# Stop a queue
./gradlew :cli:app:run --args="queue stop <ID>"
```

### Monitor

```bash
# Show current active downloads
./gradlew :cli:app:run --args="monitor"
```

### Category

```bash
# List categories
./gradlew :cli:app:run --args="category list"
```

## Architecture notes

- The CLI module lives in `cli/app/` and shares the download engine from `downloader/core/`
- Data is stored in `~/.abdm-cli/` — separate from the desktop app
- Downloads persist across CLI invocations
- The DI container (`CliDi.kt`) boots a headless download manager without any GUI components
- All commands use Clikt for argument parsing and Mordant for terminal output

## Important flags

| Flag | Short | Description |
|------|-------|-------------|
| `--start` | `-s` | Begin downloading immediately |
| `--output-dir` | `-o` | Custom download directory |
| `--name` | `-n` | Custom file name |
| `--connections` | `-c` | Concurrent connections |
| `--queue` | `-q` | Queue ID to add to |
| `--quiet` | | Suppress output |
| `--all` | `-a` | Show all downloads (for list) |
| `--keep-file` | `-k` | Keep file on remove |
| `--config-dir` | | Custom config directory |
| `--download-dir` | `-d` | Default download directory |