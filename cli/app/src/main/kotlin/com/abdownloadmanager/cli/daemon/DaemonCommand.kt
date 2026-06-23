package com.abdownloadmanager.cli.daemon

import com.abdownloadmanager.cli.di.CliDi
import com.abdownloadmanager.cli.utils.CliPaths
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.PrintHelpMessage
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.terminal.Terminal

/**
 * Daemon command — manages the background download daemon lifecycle.
 *
 * Subcommands:
 *   start   - Start the daemon (foreground process)
 *   stop    - Send shutdown signal to running daemon
 *   status  - Check if daemon is running
 */
class DaemonCommand : CliktCommand(
    name = "daemon",
    help = "Manage the background download daemon"
) {
    override fun run() {
        // No subcommand — Clikt auto-shows help for commands with subcommands
    }
}

/** Start the daemon (blocks until shutdown) */
class DaemonStartCommand : CliktCommand(
    name = "start",
    help = "Start the download daemon (foreground process)"
) {
    override fun run() {
        val term = Terminal()

        // Check if already running
        val daemonClient = DaemonClient.withDefaultConfig()
        if (daemonClient.isRunning()) {
            term.println((TextColors.yellow)("Daemon is already running."))
            return
        }

        val configDir = CliPaths.detectDataDir()
        val downloadDir = configDir

        term.println((TextColors.blue)("Starting daemon (data dir: ${configDir.absolutePath})..."))

        // Boot DI and start the daemon (this call blocks)
        CliDi.boot(configDir, downloadDir)
        DaemonBootstrap.start()
    }
}

/** Stop a running daemon */
class DaemonStopCommand : CliktCommand(
    name = "stop",
    help = "Stop a running daemon"
) {
    override fun run() {
        val term = Terminal()
        val daemonClient = DaemonClient.withDefaultConfig()

        if (!daemonClient.isRunning()) {
            term.println((TextColors.yellow)("Daemon is not running."))
            return
        }

        term.println((TextColors.blue)("Stopping daemon..."))
        val success = daemonClient.shutdown()

        if (success) {
            term.println((TextColors.green)("Daemon stopped."))
        } else {
            term.println((TextColors.red)("Failed to send shutdown signal."))
        }
    }
}

/** Check daemon status */
class DaemonStatusCommand : CliktCommand(
    name = "status",
    help = "Check if the daemon is running"
) {
    override fun run() {
        val term = Terminal()
        val daemonClient = DaemonClient.withDefaultConfig()

        if (daemonClient.isRunning()) {
            term.println((TextColors.green)("Daemon is running."))
        } else {
            term.println((TextColors.yellow)("Daemon is not running."))
        }
    }
}