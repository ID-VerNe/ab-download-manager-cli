# Install abdm

The AB Download Manager CLI (`abdm`) is distributed as part of the NSIS installer. Choose your path:

## Option 1: Download pre-built installer (fastest)

Download the latest `.exe` from the GitHub Releases page:

```
https://github.com/ID-VerNe/ab-download-manager-cli/releases/latest
```

### Windows — silent install

```powershell
# Download the latest release (replace URL with actual release)
curl -L -o ABDownloadManager-Setup.exe "https://github.com/ID-VerNe/ab-download-manager-cli/releases/latest/download/ABDownloadManager-Setup-*.exe"

# Silent install (adds abdm to PATH automatically)
./ABDownloadManager-Setup.exe /S

# Verify
abdm --version
```

### Windows — interactive install

Just double-click the `.exe` — the installer is a standard NSIS wizard.

## Option 2: Build from source

Requires: **JDK 21** and Git.

```bash
# Clone the repo
git clone https://github.com/ID-VerNe/ab-download-manager-cli.git
cd ab-download-manager-cli

# Build the CLI distribution (no desktop GUI needed)
./gradlew :cli:app:installDist

# The CLI is now at cli/app/build/install/abdm/bin/abdm
# Run it directly:
cli/app/build/install/abdm/bin/abdm --version

# (Optional) Build the full NSIS installer:
./gradlew :desktop:app:createInstallerNsis
# Installer at: desktop/app/build/custom-installer/ABDownloadManager.exe
```

### Dev mode — quick run without install

```bash
./gradlew :cli:app:run --args="<command>"
```

## Verify installation

```bash
abdm --version
# → abdm version 1.0.0

abdm daemon status
# → Daemon is not running.

abdm list
# → No downloads found.
```

## Troubleshooting

| Symptom | Fix |
|---------|-----|
| `'abdm' is not recognized` | Install the NSIS package; or build from source and use the path directly |
| `Java not found` | Install JDK 21+ from [adoptium.net](https://adoptium.net) |
| `Daemon fails to start` | Check `%LOCALAPPDATA%\ABDownloadManager\daemon\daemon.port` for stale lock |
| `Connection refused` | Daemon not running — run `abdm daemon start` first |
