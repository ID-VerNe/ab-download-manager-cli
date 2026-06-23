# AB Download Manager CLI — Daemon HTTP API Reference

## 1. Base URL

All daemon API endpoints are served on `http://localhost:16889` (default port).

## 2. JSON Envelope Format

Every response follows this unified envelope:

```json
{
  "success": true | false,
  "data": <any> | null,
  "error": "<string>" | null
}
```

| Field | Type | Description |
|-------|------|-------------|
| `success` | Boolean | Whether the operation succeeded |
| `data` | Any | Response payload (object, array, or string) |
| `error` | String or null | Human-readable error message on failure |

**Error responses** use HTTP status code 400. **Success responses** use HTTP 200.

### Helpers (DaemonApi.kt)

```kotlin
fun jsonSuccess(data: Map<String, Any?>): String   // {"success":true, "data":{...}, "error":null}
fun jsonSuccess(msg: String): String                 // {"success":true, "data":"msg", "error":null}
fun jsonError(message: String): MyResponse           // {"success":false, "data":null, "error":"msg"}
// jsonError returns statusCode=400
```

## 3. Complete Endpoint Reference

### 3.1 Health Check

```
GET /ping
```

**Description:** Liveness probe. Returns a simple text response.

**Response:** `200 OK`
```
pong
```

**Envelope note:** This is a plain text response, not JSON envelope.

---

### 3.2 Daemon Status

```
GET /api/status
```

**Description:** Returns aggregate statistics about active, paused, completed, and errored downloads.

**Request:** None

**Response (200):**
```json
{
  "success": true,
  "data": {
    "total": 12,
    "active": 2,
    "paused": 5,
    "completed": 4,
    "errored": 1
  },
  "error": null
}
```

| Field | Type | Description |
|-------|------|-------------|
| `total` | Int | Total downloads in DB |
| `active` | Int | Currently downloading |
| `paused` | Int | Paused by user |
| `completed` | Int | Successfully finished |
| `errored` | Int | Failed with error |

---

### 3.3 List Downloads

```
GET /api/list
```

**Description:** Returns all downloads as a JSON array.

**Request:** None (query params not currently supported by HandlerMap exact-matching; for filtering use `?all=true` sent via the raw URI — currently always returns all items).

**Response (200):**
```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "name": "file.zip",
      "url": "https://example.com/file.zip",
      "folder": "C:\\Downloads",
      "status": "Downloading",
      "size": 104857600,
      "sizeFormatted": "100.0 MB",
      "downloaded": 52428800,
      "dateAdded": 1719000000000,
      "startTime": 1719000100000,
      "completeTime": null,
      "connections": 8,
      "speedLimit": 0,
      "checksum": null
    }
  ],
  "error": null
}
```

**ItemJson fields:**

| Field | Type | Description |
|-------|------|-------------|
| `id` | Long | Unique download ID |
| `name` | String | File name |
| `url` | String or null | Download URL |
| `folder` | String or null | Output directory path |
| `status` | String | One of: `Added`, `Downloading`, `Paused`, `Completed`, `Error`, `Merging`, `Checking` |
| `size` | Long | Total content length (bytes; -1 if unknown) |
| `sizeFormatted` | String | Human-readable size (e.g. "100.0 MB") |
| `downloaded` | Long | Bytes downloaded so far |
| `dateAdded` | Long | Unix timestamp (ms) when download was added |
| `startTime` | Long or null | Unix timestamp when download started |
| `completeTime` | Long or null | Unix timestamp when download completed |
| `connections` | Int or null | Preferred connection count |
| `speedLimit` | Long | Per-download speed limit in bytes/sec (0 = unlimited) |
| `checksum` | String or null | File checksum hash (if set) |

---

### 3.4 Get Single Download

```
POST /api/list-item
```

**Description:** Get a single download's details by ID.

**Request body:**
```json
{
  "id": 1
}
```

**Response (200):**
```json
{
  "success": true,
  "data": {
    "id": 1,
    "name": "file.zip",
    "url": "https://example.com/file.zip",
    "folder": "C:\\Downloads",
    "status": "Downloading",
    "size": 104857600,
    "sizeFormatted": "100.0 MB",
    "downloaded": 52428800,
    "dateAdded": 1719000000000,
    "startTime": 1719000100000,
    "completeTime": null,
    "connections": 8,
    "speedLimit": 0,
    "checksum": null
  },
  "error": null
}
```

**Error:** `400 Bad Request` — Download ID not found
```json
{"success":false,"data":null,"error":"Download #999 not found"}
```

---

### 3.5 Add Download

```
POST /api/add
```

**Description:** Add one or more downloads. Supports HTTP Basic Auth, connection count, speed limiting, duplicate handling, queue assignment, and auto-start.

**Request body:**
```json
{
  "url": "https://example.com/file.zip",
  "name": "myfile.zip",
  "folder": "C:\\Downloads",
  "username": "user",
  "password": "pass",
  "connections": 8,
  "speedLimit": 0,
  "duplicate": "abort",
  "start": true,
  "queueId": 1
}
```

| Field | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| `url` | String | **Yes** | — | Download URL |
| `name` | String | No | Auto-detected from URL | Output file name |
| `folder` | String | No | `user.dir` | Output directory |
| `username` | String | No | null | HTTP Basic Auth username |
| `password` | String | No | null | HTTP Basic Auth password |
| `connections` | Int | No | null | Concurrent connections |
| `speedLimit` | Long | No | 0 | Per-download speed limit (bytes/sec) |
| `duplicate` | String | No | `"abort"` | Duplicate strategy: `"abort"`, `"override"`, or `"add-numbered"` |
| `start` | Boolean | No | false | Auto-start download immediately |
| `queueId` | Long | No | null | Queue ID to assign download to |

**Response (200):**
```json
{
  "success": true,
  "data": {
    "id": 42
  },
  "error": null
}
```

**Errors:**
- `400` — Invalid request body (malformed JSON, missing URL)
- `400` — Duplicate download (when `duplicate` is `"abort"` and a download with the same path exists)
- `400` — Download failed to be added (engine error)

---

### 3.6 Pause Download(s)

```
POST /api/pause
```

**Description:** Pause one or more active downloads.

**Request body:**
```json
{
  "ids": [1, 2, 3]
}
```

**Response (200):**
```json
{
  "success": true,
  "data": "Paused 3 download(s)",
  "error": null
}
```

---

### 3.7 Resume Download(s)

```
POST /api/resume
```

**Description:** Resume one or more paused downloads.

**Request body:**
```json
{
  "ids": [1, 2, 3]
}
```

**Response (200):**
```json
{
  "success": true,
  "data": "Resumed 3 download(s)",
  "error": null
}
```

---

### 3.8 Remove Download(s)

```
POST /api/remove
```

**Description:** Remove one or more downloads. Optionally keep the downloaded file on disk.

**Request body:**
```json
{
  "ids": [1, 2, 3],
  "keepFile": true
}
```

| Field | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| `ids` | Long[] | **Yes** | — | Download IDs to remove |
| `keepFile` | Boolean | No | false | If true, keep the downloaded file on disk |

**Response (200):**
```json
{
  "success": true,
  "data": "Removed 3 download(s)",
  "error": null
}
```

---

### 3.9 Restart Download(s)

```
POST /api/restart
```

**Description:** Reset and restart one or more downloads. Clears all downloaded data and re-downloads from scratch.

**Request body:**
```json
{
  "ids": [1, 2]
}
```

**Response (200):**
```json
{
  "success": true,
  "data": "Restarted 2 download(s)",
  "error": null
}
```

---

### 3.10 Pause All Downloads

```
POST /api/pause-all
```

**Description:** Pause every currently active download.

**Request body:** `{}`

**Response (200):**
```json
{
  "success": true,
  "data": "Paused all downloads",
  "error": null
}
```

---

### 3.11 List Queues

```
GET /api/queue
```

**Description:** List all download queues and their items.

**Response (200):**
```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "name": "Default Queue",
      "queueItems": [1, 2, 5],
      "isQueueActive": true
    },
    {
      "id": 2,
      "name": "Batch Queue",
      "queueItems": [3, 4],
      "isQueueActive": false
    }
  ],
  "error": null
}
```

| Field | Type | Description |
|-------|------|-------------|
| `id` | Long | Queue ID |
| `name` | String | Queue display name |
| `queueItems` | Long[] | Download IDs in the queue |
| `isQueueActive` | Boolean | Whether the queue is actively processing |

---

### 3.12 Start Queue

```
POST /api/queue/start
```

**Description:** Start processing a queue.

**Request body:**
```json
{
  "queueId": 1
}
```

**Response (200):**
```json
{
  "success": true,
  "data": "Queue #1 started",
  "error": null
}
```

---

### 3.13 Shutdown Daemon

```
POST /api/shutdown
```

**Description:** Gracefully shut down the daemon process. The daemon will:
1. Pause all active downloads
2. Stop the HTTP server
3. Delete the port file
4. Release the file lock
5. Exit the JVM

**Request body:** `{}`

**Response (200):**
```json
{
  "success": true,
  "data": "Daemon shutting down",
  "error": null
}
```

**Note:** The response is sent immediately. The daemon process shuts down ~200ms after sending the response (delay allows the HTTP response to be delivered).

---

### 3.14 Open Download File

```
POST /api/open
```

**Description:** Open a completed download's file with the system default application (on the daemon machine). Uses `java.awt.Desktop.getDesktop().open()`.

**Request body:**
```json
{
  "id": 1
}
```

**Response (200):**
```json
{
  "success": true,
  "data": "Opened file.zip",
  "error": null
}
```

**Errors:**
- `400` — Download not found
- `400` — File not found on disk
- `400` — Cannot open file (no desktop environment, headless JVM)

---

### 3.15 Open Download Folder

```
POST /api/open-folder
```

**Description:** Open the folder containing a download's file in the system file manager (on the daemon machine).

**Request body:**
```json
{
  "id": 1
}
```

**Response (200):**
```json
{
  "success": true,
  "data": "Opened folder C:\\Downloads",
  "error": null
}
```

**Errors:** Same as `/api/open`.

---

### 3.16 Set Checksum

```
POST /api/checksum/set
```

**Description:** Set or update the checksum hash for a download. Used for post-download integrity verification.

**Request body:**
```json
{
  "id": 1,
  "checksum": "d41d8cd98f00b204e9800998ecf8427e"
}
```

**Response (200):**
```json
{
  "success": true,
  "data": "Checksum set for #1",
  "error": null
}
```

**Errors:**
- `400` — Invalid request body
- `400` — Download #ID not found

---

### 3.17 Edit Download Properties

```
POST /api/edit
```

**Description:** Edit a download's properties (name, speed limit, connection count). All fields are optional — only provided fields are updated.

**Request body:**
```json
{
  "id": 1,
  "name": "new-filename.zip",
  "speedLimit": 1048576,
  "connections": 4
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `id` | Long | **Yes** | Download ID |
| `name` | String | No | New file name |
| `speedLimit` | Long | No | Speed limit in bytes/sec (0 = unlimited) |
| `connections` | Int | No | Number of concurrent connections |

**Response (200):**
```json
{
  "success": true,
  "data": "Updated download #1",
  "error": null
}
```

**Errors:**
- `400` — Download #ID not found

---

### 3.18 Get All Configuration

```
GET /api/config
```

**Description:** Return all application settings as a JSON object.

**Response (200):**
```json
{
  "success": true,
  "data": {
    "threadCount": 8,
    "maxConcurrentDownloads": 3,
    "maxDownloadRetryCount": 3,
    "dynamicPartCreation": true,
    "useServerLastModifiedTime": false,
    "appendExtensionToIncompleteDownloads": false,
    "useSparseFileAllocation": true,
    "speedLimit": 0,
    "defaultDownloadFolder": "C:\\Users\\user\\Downloads",
    "userAgent": "ABDownloadManager-CLI/1.0"
  },
  "error": null
}
```

---

### 3.19 Set Configuration Key

```
POST /api/config/set
```

**Description:** Set a single configuration key. The daemon live-reloads the value immediately — no restart required.

**Request body:**
```json
{
  "key": "threadCount",
  "value": "16"
}
```

**Supported keys and value types:**

| Key | Type | Example `value` |
|-----|------|-----------------|
| `threadCount` | Int | `"8"` |
| `maxConcurrentDownloads` | Int | `"3"` |
| `maxDownloadRetryCount` | Int | `"5"` |
| `dynamicPartCreation` | Boolean | `"true"` |
| `useServerLastModifiedTime` | Boolean | `"false"` |
| `appendExtensionToIncompleteDownloads` | Boolean | `"true"` |
| `useSparseFileAllocation` | Boolean | `"true"` |
| `speedLimit` | Long | `"1048576"` (1 MB/s) |
| `defaultDownloadFolder` | String | `"C:\\Downloads"` |
| `userAgent` | String | `"MyAgent/1.0"` |

**Response (200):**
```json
{
  "success": true,
  "data": "threadCount = 16",
  "error": null
}
```

**Errors:**
- `400` — Unknown config key
```json
{"success":false,"data":null,"error":"Unknown config key: invalidKey"}
```
- `400` — Invalid value for key
```json
{"success":false,"data":null,"error":"Invalid value for threadCount: not-a-number"}
```

---

## 4. Port File Protocol

CLI commands discover the daemon via a port file rather than hardcoded ports or broadcast discovery.

### Port File Location

```
{dataDir}/daemon/daemon.port
```

Where `{dataDir}` is discovered via `CliPaths.detectDataDir()`:
1. Desktop install: `%LOCALAPPDATA%/ABDownloadManager`
2. Fallback: `~/.abdm-cli`

### Format

The port file contains a single line with the HTTP port number as text:

```
16889
```

### Discovery Algorithm (DaemonClient)

```
DaemonClient.isRunning():
  1. portFile ← {dataDir}/daemon/daemon.port
  2. if !portFile.exists() → return false
  3. port ← portFile.readText().trim().toIntOrNull()
  4. if port == null → return false
  5. try:
       GET http://localhost:{port}/ping
       if response == 200 → return true
     catch: → return false
```

### Port File Lifecycle

| Event | Action |
|-------|--------|
| Daemon start | Write port number to `daemon.port` |
| Normal shutdown | Delete `daemon.port` |
| Crash | File remains stale — next daemon start writes fresh value |
| CLI command | Reads file, pings `/ping`, uses daemon if 200 OK |

### Stale Port File Handling

If the daemon crashes without cleanup, the stale `daemon.port` file remains. The CLI's `DaemonClient.isRunning()` handles this gracefully because:
1. It reads the port
2. Attempts `GET /ping` with a 2-second timeout
3. If connection fails or times out → returns `false` (daemon not running)
4. A new `daemon start` will overwrite the file with a fresh value

## 5. Error Response Codes

| Scenario | HTTP Status | Envelope `success` | `error` field |
|----------|-------------|-------------------|---------------|
| Success | 200 | `true` | `null` |
| Daemon not fully initialized | 400 | `false` | `"Daemon not fully initialized"` |
| Invalid JSON body | 400 | `false` | `"Invalid request body: ..."` |
| Download not found | 400 | `false` | `"Download #{id} not found"` |
| Unknown config key | 400 | `false` | `"Unknown config key: {key}"` |
| Invalid config value | 400 | `false` | `"Invalid value for {key}: {value}"` |
| File not found (open) | 400 | `false` | `"File not found: {path}"` |
| Cannot open file/folder | 400 | `false` | `"Cannot open file: {message}"` |
| Route not found | 404 | N/A | N/A (plain text "Not Found") |
| Server error | 500 | N/A | N/A |
