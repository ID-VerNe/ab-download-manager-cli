# AB Download Manager CLI — Architecture Design Document

## 1. High-Level Architecture

The CLI module (`abdm`) provides a terminal interface to the AB Download Manager engine. It operates in **two modes**:

```
┌───────────────────────────────────────────────────────────────────────────────┐
│                          CLI MODE DISPATCH                                    │
│                                                                               │
│   abdm add <url>                                                              │
│       │                                                                       │
│       ├── DaemonHelper.daemonClient() ≠ null ?                                │
│       │   └── YES ──► Forward via HTTP to running daemon                      │
│       │               (DaemonClient.post/get)                                 │
│       │                                                                       │
│       └── NO ───► Boot engine in-process (Direct Mode)                        │
│                    (CliDownloadService.boot() → DownloadManager.boot())       │
│                                                                               │
└───────────────────────────────────────────────────────────────────────────────┘

┌──────────────────────────┐     ┌─────────────────────────────────────────┐
│      DIRECT MODE          │     │         DAEMON MODE                     │
│                          │     │                                         │
│  ┌──────────────────┐   │     │  ┌──────────────┐  ┌─────────────────┐  │
│  │  abdm add <url>  │   │     │  │  abdm add     │  │  abdm daemon    │  │
│  │  abdm list       │   │     │  │  <url>        │  │  (long-lived)   │  │
│  │  abdm pause <id> │   │     │  │               │  │                 │  │
│  │  ...             │   │     │  │  DaemonClient ├──►│  HTTP Server    │  │
│  │                  │   │     │  │  (HTTP)       │  │  (http4k +      │  │
│  │  Koin DI ────────┤──│─────│──┤               │  │   NanoHTTPD)    │  │
│  │  │               │   │     │  └──────────────┘  │       │          │  │
│  │  ▼               │   │     │                    │       ▼          │  │
│  │  DownloadManager │   │     │                    │  DownloadManager │  │
│  │  (in-process)    │   │     │                    │  (daemon-owned)  │  │
│  │       │          │   │     │                    │       │          │  │
│  │       ▼          │   │     │                    │       ▼          │  │
│  │  JSON File DB    │   │     │                    │  JSON File DB    │  │
│  └──────────────────┘   │     │  └─────────────────┘                 │  │
│                          │     │   (shared appSettings.json)          │  │
└──────────────────────────┘     └─────────────────────────────────────────┘
```

### Mode Comparison

| Aspect | Direct Mode | Daemon Mode |
|--------|-------------|-------------|
| Process lifecycle | Per-command JVM launch | Long-lived daemon process |
| State | Single-shot, no persistence between commands | Continuous, maintains download state |
| Engine bootstrap | Every command (cold start) | Once at daemon start |
| CLI command latency | ~1-3s JVM + 200ms bootstrap | ~10ms HTTP round-trip |
| Background downloads | Not truly background (`--detach` returns immediately but process exits) | Fully supported via HTTP API |
| File sharing | Direct file I/O | Daemon owns all file I/O |
| JVM memory | Per-command allocation (then GC) | Continuous heap |

## 2. Daemon Lifecycle

### Bootstrap Sequence

```
DaemonBootstrap.start(port)
  │
  ├── 1. Acquire file lock
  │      RandomAccessFile(daemon.lock, "rw").channel.tryLock()
  │      If lock held → throw IllegalStateException ("already running")
  │
  ├── 2. Boot download engine
  │      CliAppSettings.syncDownloadSettings(DownloadSettings)
  │      CliDownloadService.boot()
  │        ├── queueManager.boot()
  │        └── downloadManager.boot()
  │             └── createJobForPendingDownloads()
  │                 └── Resume all non-completed downloads from DB
  │
  ├── 3. Create & start HTTP server
  │      HandlerMap ← DaemonApi.createHandlerMap(downloadService, ...)
  │      MyHttp4KServer(port, handlerMap).startMyServer()
  │        └── http4k + NanoHTTPD on localhost:16889
  │
  ├── 4. Write port file
  │      daemon/daemon.port ← port.toString()
  │      (CLI clients read this to discover the daemon)
  │
  ├── 5. Register JVM shutdown hook
  │      Runtime.addShutdownHook { doShutdown() }
  │
  ├── 6. Block main thread
  │      CountDownLatch.await()
  │
  └── 7. Clean shutdown (when latch released)
         ├── Pause all active downloads (graceful)
         ├── Stop HTTP server
         ├── Delete daemon.port
         └── Release file lock
```

### Port File Protocol

CLI commands discover the daemon via a **port file**:

| File | Path | Purpose |
|------|------|---------|
| `daemon.port` | `{dataDir}/daemon/daemon.port` | Contains the HTTP port as text (default `16889`) |
| `daemon.lock` | `{dataDir}/daemon/daemon.lock` | File lock preventing duplicate daemon instances |

The `DaemonClient` class implements discovery:
1. Read `{dataDir}/daemon/daemon.port`
2. Parse port as integer
3. Send `GET /ping` to `http://localhost:{port}`
4. If 200 OK → daemon is running

### Lock File Mechanism

- Uses Java NIO `FileChannel.tryLock()` for OS-level advisory locking
- Lock is held for the entire daemon lifetime
- Automatically released when daemon JVM exits
- On `daemon stop`: lock is released, then port file is deleted
- On crash: lock released by OS, stale port file is cleaned on next start

### Shutdown Sequence

```
Trigger: POST /api/shutdown → onShutdown callback → CountDownLatch.countDown()
  │
  ├── DaemonBootstrap.doShutdown()
  │     ├── downloadService.pauseAll()
  │     │     └── downloadManager.stopAll(StoppedBy.User)
  │     │           └── Mark all active downloads as Paused in DB
  │     │
  │     ├── server.stopMyServer()
  │     │     └── http4k server.stop()
  │     │
  │     ├── daemonPaths.portFile.delete()
  │     │     └── Remove discovery file so clients know daemon is gone
  │     │
  │     └── releaseLock()
  │           └── lock.release() + lockRaf.close()
  │
  └── JVM exits
```

## 3. Module Dependency Graph

```
cli:app (abdm)
  │
  ├── downloader:core          ← DownloadManager, DownloadSettings, DB, QueueManager
  ├── downloader:monitor       ← Download monitoring events (for @Immutable annotations)
  ├── shared:utils             ← Utility classes (PathValidator, FileNameUtil, etc.)
  ├── shared:config            ← Shared configuration types
  ├── shared:nanohttp4k        ← NanoHTTPD adapter for http4k (daemon HTTP server)
  ├── integration:server       ← MyHttp4KServer, HandlerMap, MyRequest, MyResponse
  │
  ├── libs:koin.core           ← Dependency injection (no Compose/Koin Compose)
  ├── libs:kotlin.coroutines   ← Coroutines for async engine operations
  ├── libs:kotlin.serialization ← JSON serialization for DB + API
  ├── libs:okhttp              ← HTTP client (daemon communication)
  ├── libs:arrow.core          ← Functional types (Option/Some for nullable fields)
  ├── clikt                    ← CLI argument parsing (CliktCommand subclassing)
  ├── mordant                  ← Terminal output (colors, tables, formatting)
  └── compose.runtime          ← Only for @Immutable annotations in monitor module
```

### Dependency Rationale

| Dependency | Why It's Used | Where |
|-----------|---------------|-------|
| **clikt** | Declarative CLI argument/option parsing via subclassing `CliktCommand` | All command classes |
| **mordant** | Cross-platform ANSI terminal output (colors, tables, progress bars) | All commands with terminal output |
| **koin** | Service locator / DI without framework overhead | `CliDi.kt`, all `KoinComponent` commands |
| **okhttp** | HTTP client for daemon communication; also used as the download engine's HTTP client | `DaemonClient.kt`, `CliDi.kt` |
| **http4k** | Server framework wrapping NanoHTTPD for the daemon HTTP API | `MyHttp4KServer.kt` |
| **arrow** | `Option` / `Some` used in `IDownloadItem.copy()` for partial updates | `DownloadManager.kt` |
| **compose.runtime** | Only needed for `@Immutable` annotations in `downloader:monitor` | Pulled transitively |

## 4. Headless DI (No Compose)

The CLI module runs a **headless** Koin container with **zero Compose/UI dependencies**:

```
CliDi.boot(configDir, downloadDir)
  │
  startKoin {
    modules(cliModule(paths, configDir))
  }
```

### DI Module Registration (cliModule)

```
┌─────────────────────────────────────────────────────────────┐
│ cliModule(paths, configDir)                                  │
│                                                             │
│  single → CliPaths                                           │
│  single → CoroutineScope(SupervisorJob())                    │
│  single → Json (with polymorphic serializer registration)    │
│  single → DownloadSettings (defaults: threadCount=8, ...)    │
│  single → X509TrustManager (permissive)                      │
│  single → OkHttpClient (HTTP/1.1, unlimited connections)     │
│  single → ProxyStrategyProvider (NoOp - direct only)         │
│  single → AutoConfigurableProxyProvider (NoOp)               │
│  single → SystemProxySelectorProvider (NoOp)                 │
│  single → UserAgentProvider ("ABDownloadManager-CLI/1.0")    │
│  single → HttpDownloaderClient (OkHttpHttpDownloaderClient)  │
│  single → IDiskStat (File.freeSpace)                         │
│  single → EmptyFileCreator                                   │
│  single → TransactionalFileSaver                             │
│  single → IDownloadQueueDatabase (FileStorage)               │
│  single → IDownloadListDb (FileStorage)                      │
│  single → IDownloadPartListDb (FileStorage)                  │
│  single → HttpDownloader                                     │
│  single → HLSDownloader                                     │
│  single → DownloaderRegistry (HttpDownloader + HLSDownloader)│
│  single → DownloadManager (core engine)                      │
│  single → QueueManager                                       │
│  single → ManualDownloadQueue                                │
│  single → CliDownloadService (CLI wrapper)                   │
│  single → CliAppSettings (shared config)                     │
│  single → IntegrationHandler = CliIntegrationHandler         │
└─────────────────────────────────────────────────────────────┘
```

### CliDownloadService Wrapper

`CliDownloadService` is a simplified facade over `DownloadManager` that provides CLI-friendly methods:

| Method | Wraps | Purpose |
|--------|-------|---------|
| `boot()` | `queueManager.boot()` + `downloadManager.boot()` | Initialize engine |
| `addDownload(props)` | `downloadManager.addDownload(props)` | Queue new download |
| `startDownload(id)` | `downloadManager.startJob(id, ResumedBy(User))` | Resume download |
| `pauseDownload(id)` | `downloadManager.stopJob(id, StoppedBy(User))` | Pause download |
| `removeDownload(id, alsoRemoveFile)` | `downloadManager.deleteDownload(id, ...)` | Delete download |
| `restartDownload(id)` | `downloadManager.reset()` + `startJob()` | Reset and restart |
| `pauseAll()` | `downloadManager.stopAll(StoppedBy(User))` | Pause all |
| `getAllItems()` | `downloadManager.dlListDb.getAll()` | List all downloads |
| `getItem(id)` | `downloadManager.dlListDb.getById(id)` | Get single download |
| `updateDownloadItem(id, updater)` | `downloadManager.updateDownloadItem(id, null, updater)` | Edit properties |
| `addToQueue(queueId, downloadId)` | `queueManager.addToQueue()` | Queue management |
| `getQueues()` | `queueManager.getAll()` | List queues |
| `getActiveCount()` | `downloadManager.getActiveCount()` | Active download count |

## 5. HTTP API Layer

### HandlerMap Routing

The HTTP API uses a simple `HandlerMap` with exact URI matching (no path parameters):

```
HandlerMap
  ├── .get("/ping", handler)
  ├── .get("/api/status", handler)
  ├── .get("/api/list", handler)
  ├── .post("/api/add", handler)
  ├── .post("/api/pause", handler)
  ├── .post("/api/resume", handler)
  ├── .post("/api/remove", handler)
  ├── .post("/api/restart", handler)
  ├── .post("/api/pause-all", handler)
  ├── .get("/api/queue", handler)
  ├── .post("/api/queue/start", handler)
  ├── .post("/api/list-item", handler)
  ├── .post("/api/shutdown", handler)
  ├── .post("/api/open", handler)
  ├── .post("/api/open-folder", handler)
  ├── .post("/api/checksum/set", handler)
  ├── .post("/api/edit", handler)
  ├── .get("/api/config", handler)
  └── .post("/api/config/set", handler)
```

### JSON Envelope Format

All API responses use a unified JSON envelope:

**Success:**
```json
{
  "success": true,
  "data": { ... },
  "error": null
}
```

**Error:**
```json
{
  "success": false,
  "data": null,
  "error": "Human-readable error message"
}
```

**Simple message:**
```json
{
  "success": true,
  "data": "Operation completed",
  "error": null
}
```

The envelope is constructed by `DaemonApi.jsonSuccess()` and `DaemonApi.jsonError()` helper methods. Error responses use HTTP status code 400.

### CliIntegrationHandler Bridge

`CliIntegrationHandler` implements `IntegrationHandler` for the CLI/daemon context. It provides the bridge between the generic integration API (used by browser extensions and external tools) and the CLI's download engine. Unlike the desktop `IntegrationHandlerImp`, it:

- Has no Compose/UI dependencies
- Writes directly to `CliDownloadService`
- Uses console logging instead of UI notifications
- Supports `addDownload()`, `listQueues()`, and `addDownloadTask()` integration endpoints

## 6. Configuration Flow

### Shared Config Format

The CLI and desktop GUI share the same `appSettings.json` file:

```
{dataDir}/
  config/
    appSettings.json      ← Flat JSON object, shared with desktop
  download_db/
    downloadlist/         ← Download items (one JSON file per item)
    parts/                ← Download part metadata
    queues/               ← Queue definitions
    categories/           ← Category definitions
  download_data/          ← Partial download data (".abdm-part" files)
  daemon/
    daemon.port           ← Daemon HTTP port (daemon mode only)
    daemon.lock           ← Daemon file lock (daemon mode only)
```

### Supported Config Keys

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `threadCount` | Int | 8 | Default connection threads per download |
| `maxConcurrentDownloads` | Int | 3 | Maximum simultaneous downloads |
| `maxDownloadRetryCount` | Int | 3 | Retry count on failure |
| `dynamicPartCreation` | Boolean | true | Create parts adaptively |
| `useServerLastModifiedTime` | Boolean | false | Preserve server file timestamp |
| `appendExtensionToIncompleteDownloads` | Boolean | false | Append `.abdm-part` to partial files |
| `useSparseFileAllocation` | Boolean | true | Pre-allocate file space sparsely |
| `speedLimit` | Long | 0 | Global speed limit in bytes/sec (0 = unlimited) |
| `defaultDownloadFolder` | String | user.dir | Default download output folder |
| `userAgent` | String | "" | Custom User-Agent header |

### Config Read/Write Flow

```
Config Set (daemon mode):
  abdm config set threadCount 16
    │
    ├── DaemonHelper.daemonClient() ≠ null?
    │   └── YES
    │       └── POST /api/config/set { key: "threadCount", value: "16" }
    │           └── DaemonApi.applyConfig(appSettings, downloadSettings, key, value)
    │               ├── appSettings.setThreadCount(16) → writes appSettings.json
    │               ├── downloadSettings.defaultThreadCount = 16 → live reload
    │               └── appSettings.syncDownloadSettings(downloadSettings)
    │
    └── NO (direct mode)
        └── appSettings.setThreadCount(16) → writes appSettings.json
            Uses daemon for changes to take effect
```

### Live Reload Mechanism

When the daemon receives a `config set` request via HTTP API, it:
1. Writes the new value to `appSettings.json`
2. Updates the in-memory `DownloadSettings` object immediately
3. Running download jobs pick up the new settings on their next iteration

This allows:
- `abdm config set threadCount 16` → daemon live-reloads without restart
- Desktop GUI settings changes are automatically picked up by the daemon on next boot
- `abdm daemon status` checks HTTP health + aggregate statistics

## 7. Data Directory Detection

```
CliPaths.detectDataDir()
  │
  ├── 1. Try existing desktop install
  │      %LOCALAPPDATA%/ABDownloadManager
  │      %LOCALAPPDATA%/AB Download Manager
  │      ~/AB Download Manager
  │      ./desktop/app/build/compose/binaries/...
  │
  └── 2. Fall back to CLI default
       ~/.abdm-cli
```

This ensures the CLI shares the same download database as the desktop GUI when both are installed. The `--config-dir` CLI flag overrides all detection.

## 8. Data Flow Diagrams

### Add Download Flow

```
abdm add https://example.com/file.zip --start
  │
  ├── Daemon running? ─ YES ─► DaemonClient.addDownload(url, start=true)
  │                                   │
  │                                   ▼
  │                             POST /api/add
  │                                   │
  │                                   ▼
  │                             DaemonApi creates HttpDownloadItem
  │                                   │
  │                                   ▼
  │                             downloadService.addDownload(props)
  │                                   │
  │                                   ├── DownloadManager.addDownload()
  │                                   │     ├── Duplicate detection
  │                                   │     ├── DB insert (dlListDb.add)
  │                                   │     ├── Create DownloadJob
  │                                   │     └── Boot job (prepare parts)
  │                                   │
  │                                   └── downloadService.startDownload(id)
  │                                         └── DownloadManager.startJob()
  │                                               └── DownloadJob.resume()
  │                                                     └── HTTP download loop
  │
  └── Daemon not running ─► DownloadService.boot()
                              └── runDirect() (in-process)
                                    └── Same engine path, but CLI blocks
                                        and shows progress bar
```

### List Downloads Flow

```
abdm list
  │
  ├── Daemon running? ─ YES ─► GET /api/list
  │                                   │
  │                                   ▼
  │                             DaemonApi: dlListDb.getAll()
  │                                   │
  │                                   ▼
  │                             Return JSON array of ItemJson objects
  │                                   │
  │                                   ▼
  │                             CLI parses JSON, renders table
  │
  └── Daemon not running ─► downloadService.getAllItems()
                              └── Render table directly from engine
```

## 9. Key Design Decisions

1. **Direct + Daemon dual mode**: All commands auto-detect daemon presence. No config flags needed. This provides backward compatibility (direct mode) while allowing progressive enhancement (daemon mode).

2. **Qbittorrent-nox model for daemon**: The daemon runs as a foreground process that blocks on `CountDownLatch.await()`. No double-fork daemonization. Users background it via shell (`&`), systemd, screen, tmux, Docker, or Windows service wrappers.

3. **File lock for singleton enforcement**: Uses Java NIO `FileChannel.tryLock()` which is atomic on all platforms. Prevents accidental duplicate daemon instances without needing PID files.

4. **No API authentication**: Daemon listens on `localhost:16889` only. No auth token for MVP. Can be added later via HTTP header if remote access is needed.

5. **Shared config with desktop**: The CLI reads/writes the same `appSettings.json` used by the Compose Desktop GUI. This means configuration changes from either tool are immediately visible to both.
