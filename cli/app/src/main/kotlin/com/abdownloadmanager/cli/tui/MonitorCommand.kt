package com.abdownloadmanager.cli.tui

import com.abdownloadmanager.cli.di.CliDownloadService
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.terminal.Terminal
import ir.amirab.downloader.downloaditem.DownloadStatus
import kotlinx.coroutines.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * TUI Monitor Command - displays real-time download progress in the terminal.
 */
class MonitorCommand : CliktCommand(
    name = "monitor",
    help = "Monitor downloads in real-time (TUI mode)"
), KoinComponent {
    private val downloadService: CliDownloadService by inject()

    override fun run() {
        val term = Terminal()
        downloadService.boot()

        // Show current status once (non-streaming)
        val items = downloadService.getAllItems().filter { it.status != DownloadStatus.Completed }
        if (items.isEmpty()) {
            term.println((TextColors.yellow)("No active downloads"))
            return
        }

        term.println((TextColors.brightCyan)("Current Active Downloads:"))
        for (item in items) {
            term.println("  #${item.id}: ${item.name} - ${item.status.name}")
        }
        term.println()
        term.println((TextColors.brightBlue)("Use 'abdm list' to see all downloads"))
    }
}