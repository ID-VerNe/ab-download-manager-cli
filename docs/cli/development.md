# AB Download Manager CLI — Developer Guide

## 1. Project Structure

```
cli/
└── app/
    ├── build.gradle.kts                    ← Dependencies, application config
    └── src/main/kotlin/com/abdownloadmanager/cli/
        │
        ├── CliMain.kt                      ← Entry point (fun main(), CliApp)
        │
        ├── commands/                       ← CliktCommand implementations
        │   ├── AddCommand.kt               ←  abdm add <url>
        │   ├── ListCommand.kt              ←  abdm list [--all] [--json]
        │   ├── ControlCommands.kt          ←  pause, resume, remove, restart, pause-all, info
        │   ├── ConfigCommand.kt            ←  config get/set/list
        │   ├── QueueCommand.kt             ←  queue list/start/stop
        │   ├── CategoryCommand.kt          ←  category list
        │   ├── OpenCommand.kt              ←  open, open-folder
        │   ├── EditCommand.kt              ←  edit <id> [--name] [--speed-limit] [--connections]
        │   ├── ChecksumCommand.kt          ←  checksum <id> [hash]
        │   └── CompletionCommand.kt        ←  completion --shell bash|zsh|fish
        │
        ├── daemon/                         ← Daemon mode infrastructure
        │   ├── DaemonBootstrap.kt          ←  Daemon lifecycle (start/shutdown)
        │   ├── DaemonCommand.kt            ←  "abdm daemon start/stop/status"
        │   ├── DaemonApi.kt                ←  HTTP API route definitions
        │   ├── DaemonClient.kt             ←  HTTP client for CLI commands
        │   ├── DaemonPaths.kt              ←  Port/lock file path management
        │   ├── DaemonHelper.kt             ←  Auto-detect daemon + table formatting
        │   └── CliIntegrationHandler.kt    ←  IntegrationHandler for daemon
        │
        ├── di/
        │   ├── CliDi.kt                    ←  Koin DI container (headless)
        │   └── CliDownloadService.kt       ←  Simplified engine wrapper
        │
        ├── tui/
        │   └── MonitorCommand.kt           ←  Real-time TUI monitor
        │
        └── utils/
            ├── CliPaths.kt                 ←  Data directory detection
            ├── CliAppSettings.kt           ←  Shared config (appSettings.json)
            └── CliAppInfo.kt               ←  Version, debug mode, constants
```

### Key File Roles

| File | Role | Complexity |
|------|------|------------|
| `CliMain.kt` | Entry point + top-level command tree | Low |
| `CliDi.kt` | Headless Koin DI container | Medium |
| `CliDownloadService.kt` | Simplified wrapper over DownloadManager | Low |
| `DaemonBootstrap.kt` | Daemon lifecycle management | Medium |
| `DaemonApi.kt` | HTTP API routes (19 endpoints) | High |
| `DaemonClient.kt` | HTTP client for CLI-daemon communication | Medium |
| `DaemonHelper.kt` | Auto-detect daemon + response parsing + table formatting | Low |

## 2. How to Build

### Build the CLI module

```bash
# From the project root:
./gradlew :cli:app:build
```

This compiles all sources and runs unit tests.

### Build a distributable archive

```bash
# Build a distribution zip/tar:
./gradlew :cli:app:distZip
# or
./gradlew :cli:app:distTar

# Output is in:
# cli/app/build/distributions/app-1.0.0.zip
```

### Build a native image (GraalVM, future)

```bash
# Requires GraalVM installed:
./gradlew :cli:app:nativeImage  # not yet configured
```

### Clean build

```bash
./gradlew :cli:app:clean
```

## 3. How to Run Locally

### Basic usage

```bash
# Run from Gradle:
./gradlew :cli:app:run --args="--help"

# Add a download:
./gradlew :cli:app:run --args="add https://example.com/file.zip"

# Add and start with progress bar:
./gradlew :cli:app:run --args="add https://example.com/file.zip --start"

# List downloads:
./gradlew :cli:app:run --args="list"

# List as JSON:
./gradlew :cli:app:run --args="list --json"

# Pause/resume/remove:
./gradlew :cli:app:run --args="pause 1"
./gradlew :cli:app:run --args="resume 1"
./gradlew :cli:app:run --args="remove 1"

# Show download info:
./gradlew :cli:app:run --args="info 1"

# Start the daemon:
./gradlew :cli:app:run --args="daemon start"
```

### Run with custom data directory

```bash
./gradlew :cli:app:run --args="--config-dir /path/to/config add https://example.com/file.zip"
```

### Run with custom download directory

```bash
./gradlew :cli:app:run --args="--download-dir /path/to/downloads add https://example.com/file.zip"
```

### Run from a built distribution

```bash
# After building with distZip:
cd cli/app/build/distributions/
unzip app-1.0.0.zip
cd app-1.0.0/bin/
./app --help
```

## 4. How to Add a New Command

Adding a new command to `abdm` follows a step-by-step template. Here's the process:

### Step 1: Create the command class

Create a new file in `cli/app/src/main/kotlin/com/abdownloadmanager/cli/commands/`:

```kotlin
package com.abdownloadmanager.cli.commands

import com.abdownloadmanager.cli.daemon.DaemonClient
import com.abdownloadmanager.cli.daemon.DaemonHelper
import com.abdownloadmanager.cli.di.CliDownloadService
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.terminal.Terminal
import kotlinx.coroutines.runBlocking
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Description of what this command does.
 *
 * Usage:
 *   abdm mycommand <arg>
 */
class MyCommand : CliktCommand(
    name = "mycommand",
    help = "Description shown in --help"
), KoinComponent {

    // Inject the download service (DI-managed)
    private val downloadService: CliDownloadService by inject()

    // Define arguments and options using Clikt delegates
    private val myArg: Int by argument(help = "Description of argument").int()

    override fun run() = runBlocking {
        val term = Terminal()

        // 1. Auto-detect daemon mode
        val daemonClient = DaemonHelper.daemonClient()
        if (daemonClient != null) {
            runWithDaemon(term, daemonClient)
            return@runBlocking
        }

        // 2. Boot engine in direct mode
        downloadService.boot()
        runDirect(term)
    }

    private suspend fun runWithDaemon(term: Terminal, client: DaemonClient) {
        // Forward request via HTTP to daemon
        // val response = client.someMethod(myArg)
        term.println((TextColors.green)("Done via daemon"))
    }

    private suspend fun runDirect(term: Terminal) {
        // Operate on downloadService directly
        term.println((TextColors.green)("Done in direct mode"))
    }
}
```

### Step 2: Add the command to the command tree

In `CliMain.kt`, add your command to the `subcommands()` call:

```kotlin
val app = CliApp().subcommands(
    AddCommand(),
    ListCommand(),
    InfoCommand(),
    // ... existing commands ...
    MyCommand(),         // <-- Add here
)
```

### Step 3: Add HTTP API endpoint (if needed for daemon mode)

If your command needs a new daemon API endpoint, add it in `DaemonApi.kt`:

```kotlin
// In DaemonApi.createHandlerMap():
post("/api/myaction") {
    if (svc == null) return@post jsonError("Daemon not fully initialized")
    val body = it.getBody().orEmpty()
    val req = runCatching {
        json.decodeFromString<MyActionRequest>(body)
    }.getOrElse { return@post jsonError("Invalid request body") }
    // ... perform action ...
    MyResponse.Text(jsonSuccess("Action completed"))
}
```

Also add the DaemonClient method in `DaemonClient.kt`:

```kotlin
fun myAction(param: Int): String? =
    post("/api/myaction", """{"param":$param}""")
```

And add the request/response data class in `DaemonApi.kt`:

```kotlin
@Serializable
data class MyActionRequest(val param: Int)
```

### Step 4: Add shell completion

Add your command to `CompletionCommand.kt` in the bash, zsh, and fish completion scripts so it appears in tab-completion.

### Step 5: Test

```bash
./gradlew :cli:app:run --args="mycommand 42 --help"
./gradlew :cli:app:run --args="mycommand 42"
```

### Template Checklist

- [ ] Command class extends `CliktCommand` and implements `KoinComponent`
- [ ] Arguments/options use Clikt delegates (`argument()`, `option()`, `flag()`)
- [ ] `run()` method detects daemon mode via `DaemonHelper.daemonClient()`
- [ ] Direct mode calls `downloadService.boot()` before engine operations
- [ ] Daemon mode delegates to `DaemonClient` HTTP methods
- [ ] Command registered in `CliMain.subcommands()`
- [ ] (If needed) API endpoint added to `DaemonApi.kt`
- [ ] (If needed) Client method added to `DaemonClient.kt`
- [ ] Shell completion updated in `CompletionCommand.kt`

## 5. Testing Strategy

### Current Test Coverage

The CLI module currently has minimal test coverage. Tests can be found at:

```
cli/app/src/test/kotlin/com/abdownloadmanager/cli/
```

### Recommended Testing Approach

#### Unit Tests

Test individual command logic by extracting the core business logic (the `runDirect()` and `runWithDaemon()` methods) from CliktCommand subclasses:

```kotlin
class AddCommandTest {
    @Test
    fun `test add command parses options correctly`() {
        // Test argument parsing
    }
}
```

#### Integration Tests (Daemon Mode)

Use the test fixture pattern:

```kotlin
class DaemonIntegrationTest {
    @Test
    fun `test daemon API round-trip`() {
        // Start daemon on random port
        // Send HTTP request
        // Assert response
        // Stop daemon
    }
}
```

#### End-to-End Tests

Test the full CLI pipeline by running `./gradlew :cli:app:run` with controlled arguments:

```kotlin
class CliE2ETest {
    @Test
    fun `test add command via gradle`() {
        val output = runGradle(":cli:app:run", "--args=\"add https://example.com/file.zip\"")
        assert(output.contains("Successfully added"))
    }
}
```

### Test Dependencies

The project is configured with:

```kotlin
testImplementation(kotlin("test"))
testImplementation("junit:junit:4.13.2")
```

### What to Test

| Component | Test Priority | What to Test |
|-----------|---------------|-------------|
| Command classes | High | Argument parsing, error handling, mode dispatch |
| CliDownloadService | High | Wrapper methods, error propagation |
| DaemonClient | High | HTTP communication, JSON parsing, port file discovery |
| DaemonApi | High | All 19 endpoints, envelope format, error responses |
| DaemonBootstrap | Medium | Lock acquisition, lifecycle hooks, clean shutdown |
| DaemonHelper | Medium | `withDaemonOrDirect()`, response parsing |
| CliAppSettings | Medium | Read/write round-trip, defaults, concurrent access |
| CliPaths | Low | Directory detection priority |
| CliIntegrationHandler | Low | addDownload, listQueues, addDownloadTask |

### Running Tests

```bash
# Run all CLI tests:
./gradlew :cli:app:test

# Run a specific test:
./gradlew :cli:app:test --tests "com.abdownloadmanager.cli.commands.AddCommandTest"
```

## 6. Debug Mode

### Enabling Debug Mode

Set the `CLI_DEBUG` environment variable to `true`:

```bash
# Linux/Mac:
CLI_DEBUG=true ./gradlew :cli:app:run --args="add https://example.com/file.zip"

# Windows PowerShell:
$env:CLI_DEBUG="true"
./gradlew :cli:app:run --args="add https://example.com/file.zip"

# Windows CMD:
set CLI_DEBUG=true
./gradlew :cli:app:run --args="add https://example.com/file.zip"
```

### What Debug Mode Does

Debug mode is checked in `CliAppInfo.isDebugMode`:

```kotlin
object CliAppInfo {
    var isDebugMode: Boolean = false  // Set by main() based on CLI_DEBUG env var

    const val APP_NAME = "AB Download Manager CLI"
    const val GITHUB_REPO = "https://github.com/amir1376/ab-download-manager"
}
```

When enabled (as seen in `CliMain.kt`'s error handler):

```kotlin
} catch (e: Exception) {
    term.println((TextColors.red)("Error: ${e.message}"))
    if (CliAppInfo.isDebugMode) {
        e.printStackTrace()  // ← Full stack trace in debug mode
    }
    System.exit(1)
}
```

**Effects of debug mode:**
- Full Java stack traces printed on errors instead of just the error message
- Additional diagnostic information printed

### Debugging the Daemon

```bash
# Start daemon with debug output:
CLI_DEBUG=true ./gradlew :cli:app:run --args="daemon start"

# In another terminal, send commands:
./gradlew :cli:app:run --args="add https://example.com/file.zip --start"
```

### Advanced Debugging

For deeper debugging, add temporary `println()` statements or use a Java debugger:

```bash
# Run with JVM debug port:
./gradlew :cli:app:run --debug-jvm --args="add https://example.com/file.zip"

# Or manually:
./gradlew -Dorg.gradle.jvmargs="-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005" :cli:app:run --args="list"
```

Then attach a remote debugger (e.g., IntelliJ IDEA) on port 5005.

## 7. Dependency Injection Guidelines

### When to inject via Koin

Commands that need access to the download engine should implement `KoinComponent`:

```kotlin
class MyCommand : CliktCommand(...), KoinComponent {
    private val downloadService: CliDownloadService by inject()
    private val paths: CliPaths by inject()
    private val appSettings: CliAppSettings by inject()
}
```

### Adding new DI bindings

Add new singletons or factories in `CliDi.kt`:

```kotlin
private fun cliModule(paths: CliPaths, dataDir: File) = module {
    // ... existing bindings ...

    single {
        MyNewService(get<SomeDependency>())
    }
}
```

## 8. Configuration Keys Reference

All config keys available via `abdm config get/set`:

```
threadCount                        Int      Default: 8
maxConcurrentDownloads             Int      Default: 3
maxDownloadRetryCount              Int      Default: 3
dynamicPartCreation                Boolean  Default: true
useServerLastModifiedTime          Boolean  Default: false
appendExtensionToIncompleteDownloads Boolean  Default: false
useSparseFileAllocation            Boolean  Default: true
speedLimit                         Long     Default: 0 (unlimited)
defaultDownloadFolder              String   Default: current directory
userAgent                          String   Default: "" (empty)
```

## 9. Common Tasks

### Adding a new library dependency

Edit `cli/app/build.gradle.kts`:

```kotlin
dependencies {
    // ... existing dependencies ...
    implementation("com.example:my-lib:1.0.0")
}
```

### Changing the default daemon port

Edit the `DEFAULT_PORT` constant in `DaemonBootstrap.kt`:

```kotlin
const val DEFAULT_PORT = 16889
```

### Changing the data directory

The data directory is auto-detected by `CliPaths.detectDataDir()` in `CliPaths.kt`. Override via `--config-dir` CLI flag.

### Changing JVM arguments

Edit `cli/app/build.gradle.kts`:

```kotlin
application {
    applicationDefaultJvmArgs = listOf("-Djava.awt.headless=true")
}
```

## 10. Known Limitations

1. **HandlerMap exact URI matching**: The HTTP server uses exact URI matching, not path patterns. `GET /api/list/1` will NOT match `GET /api/list`. Use query parameters or POST with body for parameterized requests.

2. **No Jetty/Netty**: The embedded HTTP server uses NanoHTTPD via http4k. This is lightweight but not suitable for high-throughput production use.

3. **No authentication**: The daemon API is unauthenticated and listens only on localhost. Do not expose the daemon port to external networks.

4. **JVM startup cost**: Each direct-mode CLI command incurs ~1-3s of JVM startup time. Daemon mode eliminates this by keeping the JVM running.

5. **`System.exit(0)`**: The CLI calls `System.exit(0)` after command completion to force JVM termination. This prevents background OkHttp threads from keeping the process alive. This is intentional but means cleanup hooks run on every command.
