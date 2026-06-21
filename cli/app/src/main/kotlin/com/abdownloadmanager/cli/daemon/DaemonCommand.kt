package com.abdownloadmanager.cli.daemon

import com.abdownloadmanager.cli.di.CliDownloadService
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.terminal.Terminal
import kotlinx.coroutines.*

/**
 * Daemon command - runs the download engine in the background.
 * Other CLI commands can communicate with it via the shared database.
 */
class DaemonCommand : CliktCommand(
    name = "daemon",
    help = "Start the download daemon in the background"
) {
    override fun run() {
        val term = Terminal()
        term.println((TextColors.yellow)("Daemon mode is not yet available in the CLI module."))
        term.println("Use the desktop app for daemon functionality.")
        term.println("CLI commands work directly with the download engine.")
    }
}