# Eval 2: Download sample video with custom options
## Skill: abdm-download

### Task Description
Download https://test-videos.example.com/sample.mp4 to ~/Downloads/demo/ with filename demo-video.mp4 using 4 connections.

### Skill Guidance
The `abdm-download` skill instructed us to use the AB Download Manager CLI instead of curl/wget.

### Commands Produced

**Step 1: Ensure output directory exists**
```bash
mkdir -p ~/Downloads/demo
```
or on Windows:
```powershell
mkdir "C:\Users\VerNe\Downloads\demo"
```

**Step 2: Download using ABDM CLI (the skill's recommended approach)**
```bash
./gradlew :cli:app:run --args="add https://test-videos.example.com/sample.mp4 -o ~/Downloads/demo -n demo-video.mp4 -c 4 --start"
```

**Explanation of flags:**
- `add` — Add a new download
- `https://test-videos.example.com/sample.mp4` — The source URL
- `-o ~/Downloads/demo` — Output directory (saves to ~/Downloads/demo/)
- `-n demo-video.mp4` — Rename the saved file to demo-video.mp4
- `-c 4` — Use 4 concurrent connections for faster download
- `--start` — Begin downloading immediately

### Why ABDM CLI over alternatives
| Tool | Issue |
|------|-------|
| `curl` | No resumption, single connection, no progress management |
| `wget` | Single connection, no built-in queue management |
| `Invoke-WebRequest` | Windows-only, single-stream, no resumption |
| **ABDM CLI** | Multi-connection, resumable, queue-aware, project-native |

### Outcome
The skill correctly produced the ABDM CLI command. The command could not be executed because `JAVA_HOME` is not set in this environment (Gradle requires Java). If Java were available, the download would start immediately with 4 parallel connections.

### Comparison to eval spec
Expected (from evals.json):
```
./gradlew :cli:app:run --args="add https://test-videos.example.com/sample.mp4 -o ~/Downloads/demo -n demo-video.mp4 -c 4 --start"
```

Actual produced:
```
./gradlew :cli:app:run --args="add https://test-videos.example.com/sample.mp4 -o ~/Downloads/demo -n demo-video.mp4 -c 4 --start"
```

**Result: MATCH** — The skill correctly composed the command with all required flags (URL, output dir, filename, connections, start).