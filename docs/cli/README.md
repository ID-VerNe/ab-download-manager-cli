# AB Download Manager CLI (`abdm`)

Command-line interface for AB Download Manager — add, monitor, pause, resume, and organize downloads entirely from the terminal. Operates standalone (direct mode) or as a client to a background daemon process.

## Install

Download the CLI distribution or use the NSIS installer from the [releases page](https://github.com/amir1376/ab-download-manager/releases/latest). Requires Java 17+.

## Quick Start

```sh
# Add a download
abdm add "https://example.com/file.zip"

# Add and start with live progress
abdm add "https://example.com/file.zip" --start

# List active downloads
abdm list

# Pause, resume, or remove
abdm pause 1
abdm resume 1
abdm remove 1 --keep-file

# Start the background daemon (for persistent sessions)
abdm daemon start
```

## Documentation

See the full documentation for all commands, options, examples, and troubleshooting:

- **[User Guide](user-guide.md)** -- Installation, command reference, configuration, daemon vs direct mode, FAQ
- **[Usage Examples](examples.md)** -- Practical real-world examples and scripting patterns