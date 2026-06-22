package com.abdownloadmanager.cli.tui

import com.abdownloadmanager.cli.di.CliDownloadService
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.terminal.Terminal
import ir.amirab.downloader.downloaditem.DownloadStatus
import ir.amirab.downloader.downloaditem.IDownloadItem
import kotlinx.coroutines.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.math.roundToLong

/**
 * Real-time TUI Monitor - displays live download progress panel that refreshes periodically.
 * Press Ctrl+C to exit.
 */
class MonitorCommand : CliktCommand(
    name = "monitor",
    help = "Monitor downloads in real-time (TUI mode)"
), KoinComponent {
    private val downloadService: CliDownloadService by inject()

    private val speedCache = mutableMapOf<Long, SpeedSample>()
    private data class SpeedSample(val bytes: Long, val time: Long)

    override fun run() = runBlocking {
        val term = Terminal()
        downloadService.boot()

        // Hide cursor for TUI mode
        term.print("[?25l")
        try {
            while (isActive) {
                // Clear screen and move cursor to top
                term.print("[2J[H")

                val items = downloadService.getAllItems()
                render(term, items)

                delay(1000) // refresh every second
            }
        } catch (_: CancellationException) {
            // expected on Ctrl+C
        } finally {
            term.print("[?25h") // restore cursor
        }
    }

    private suspend fun render(term: Terminal, items: List<IDownloadItem>): Int {
        val activeItems = items.filter {
            it.status != DownloadStatus.Completed
        }
        val completedItems = items.filter {
            it.status == DownloadStatus.Completed
        }

        if (activeItems.isEmpty()) {
            term.println((TextColors.green)("╔══════════════════════════════════════╗"))
            term.println((TextColors.green)("║      No active downloads            ║"))
            term.println((TextColors.green)("╚══════════════════════════════════════╝"))
            return 3
        }

        // Header
        term.println((TextColors.brightCyan)("╔══════╤══════════════════════════════════╤════════════╤══════════════╤══════════╤══════════╗"))
        term.println((TextColors.brightCyan)("║ ${"ID".padEnd(4)} │ ${"Name".padEnd(36)} │ ${"Progress".padEnd(10)} │ ${"Size".padEnd(12)} │ ${"Speed".padEnd(8)} │ ${"Status".padEnd(8)} ║"))
        term.println((TextColors.brightCyan)("╠══════╪══════════════════════════════════╪════════════╪══════════════╪══════════╪══════════╣"))

        for (item in activeItems) {
            val downloaded = downloadService.getDownloadedSize(item.id).coerceAtLeast(0)
            val total = item.contentLength
            val displayTotal = total.coerceAtLeast(downloaded)

            val pct = if (total > 0) {
                (downloaded * 100.0 / total).let { if (it > 100.0) 100.0 else it }
            } else 0.0

            // Speed calculation (rolling over ~2 seconds)
            val now = System.currentTimeMillis()
            val prev = speedCache[item.id]
            val speed = if (prev != null && now - prev.time in 1..5000) {
                ((downloaded - prev.bytes) * 1000.0 / (now - prev.time)).roundToLong().coerceAtLeast(0)
            } else 0L
            speedCache[item.id] = SpeedSample(downloaded, now)

            val eta = if (speed > 0 && total > downloaded) {
                formatDuration(((total - downloaded) * 1000 / speed).toLong())
            } else "—"

            // Render progress bar
            val barWidth = 10
            val filled = (pct / 100.0 * barWidth).roundToLong().toInt()
            val bar = "█".repeat(filled) + "░".repeat((barWidth - filled).coerceAtLeast(0))

            val pctDisplay = "$bar ${pctStr(pct)}"
            val statusStr = when (item.status) {
                DownloadStatus.Downloading -> TextColors.green("DL")
                DownloadStatus.Paused -> TextColors.yellow("Paused")
                DownloadStatus.Error -> TextColors.red("Error")
                DownloadStatus.Added -> TextColors.gray("Queued")
                DownloadStatus.Completed -> TextColors.green("Done")
            }

            val name = item.name.take(36).padEnd(36)
            val idStr = item.id.toString().padEnd(4)
            val sizeStr = formatSize(downloaded).padStart(8) + "/" + formatSize(displayTotal).padStart(8)

            term.print((TextColors.brightCyan)("║ "))
            term.print(TextColors.white(idStr))
            term.print((TextColors.brightCyan)(" │ "))
            term.print(TextColors.white(name))
            term.print((TextColors.brightCyan)(" │ "))
            term.print(TextColors.cyan(pctDisplay))
            term.print((TextColors.brightCyan)(" │ "))
            term.print(TextColors.white(sizeStr))
            term.print((TextColors.brightCyan)(" │ "))
            term.print(TextColors.white(speedStr(speed).padEnd(8)))
            term.print((TextColors.brightCyan)(" │ "))
            term.print(statusStr.toString().padEnd(8))
            term.println((TextColors.brightCyan)(" ║"))
        }

        term.println((TextColors.brightCyan)("╚══════╧══════════════════════════════════╧════════════╧══════════════╧══════════╧══════════╝"))
        term.println("${activeItems.size} active, ${completedItems.size} completed | Press Ctrl+C to exit")

        return 3 + activeItems.size + 2
    }

    private fun pctStr(pct: Double): String = "%.0f%%".format(pct).padStart(4)

    private fun speedStr(bytes: Long): String {
        return when {
            bytes >= 1_073_741_824 -> "%.1fG/s".format(bytes.toDouble() / 1_073_741_824)
            bytes >= 1_048_576 -> "%.1fM/s".format(bytes.toDouble() / 1_048_576)
            bytes >= 1_024 -> "%.0fK/s".format(bytes.toDouble() / 1_024)
            bytes <= 0 -> "—"
            else -> "$bytes B/s"
        }
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1_073_741_824 -> "%.1fG".format(bytes.toDouble() / 1_073_741_824)
            bytes >= 1_048_576 -> "%.1fM".format(bytes.toDouble() / 1_048_576)
            bytes >= 1_024 -> "%.0fK".format(bytes.toDouble() / 1_024)
            else -> "$bytes B"
        }
    }

    private fun formatDuration(ms: Long): String {
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return when {
            h > 0 -> "${h}h${m}m"
            m > 0 -> "${m}m${s}s"
            else -> "${s}s"
        }
    }
}