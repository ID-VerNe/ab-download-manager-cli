# Eval 3: Check downloads in progress and show details of download #2

## Task

1. List all downloads currently in progress
2. Show detailed information about download #2

## Commands to Run

### Step 1: List all non-finished downloads

```bash
./gradlew :cli:app:run --args='list'
```

This uses the `list` subcommand (defined in `ListCommand.kt`). By default it filters out completed downloads, showing only those with status: `Downloading`, `Paused`, `Error`, or `Added` (Queued).

To see **all** downloads including finished ones (to get an overview):

```bash
./gradlew :cli:app:run --args='list -a'
```

### Step 2: Get details for download #2

```bash
./gradlew :cli:app:run --args='info 2'
```

This uses the `info` subcommand (defined in `InfoCommand` inside `ControlCommands.kt`). It accepts a single download ID as an argument and prints:

| Field       | Description                        |
|-------------|------------------------------------|
| Name        | The name of the download           |
| URL         | The source URL                     |
| Folder      | Destination folder on disk         |
| Status      | Current status (Downloading/Paused/Error/Completed/Added) |
| Size        | Total content length (formatted)   |
| Added       | Timestamp when the download was added |
| Connections | (optional) Preferred connection count |
| Speed limit | (optional) Speed limit in B/s      |
| Checksum    | (optional) File checksum           |

## Source Code

### List command (`ListCommand.kt`)

Located at:
`cli/app/src/main/kotlin/com/abdownloadmanager/cli/commands/ListCommand.kt`

Key behavior:
- Default: shows all downloads **except** completed ones
- `--all` / `-a` flag: shows all downloads including completed
- Outputs an ASCII table with columns: ID, Name (truncated to 36 chars), Status, Size

### Info command (`InfoCommand` in `ControlCommands.kt`)

Located at:
`cli/app/src/main/kotlin/com/abdownloadmanager/cli/commands/ControlCommands.kt`

Key behavior:
- Takes a single download ID as a positional argument
- Returns null-handling: prints "Download #N not found" in red if ID doesn't exist
- Fields shown are sourced from the `CliDownloadService.getItem(id)` return type

## Expected Output

**Step 1 (list)** would look like:

```
┌──────┬──────────────────────────────────────┬──────────────┬──────────────┐
│ ID   │ Name                                 │ Status       │ Size         │
├──────┼──────────────────────────────────────┼──────────────┼──────────────┤
│ 1    │ Some download                         │ Downloading  │ 154.2 MB     │
│ 2    │ Another download                      │ Paused       │ 42.7 MB      │
└──────┴──────────────────────────────────────┴──────────────┴──────────────┘
Total: 2 download(s)
```

**Step 2 (info 2)** would look like:

```
Download #2
  Name:    Another download
  URL:     https://example.com/file.zip
  Folder:  C:\Downloads
  Status:  Paused
  Size:    42.70 MB
  Added:   2026-06-20 14:30:00
```

## Note

Java is not installed on this machine, so the commands could not be executed directly. The above commands should be run on a system that has Java 11+ and the project dependencies cached.