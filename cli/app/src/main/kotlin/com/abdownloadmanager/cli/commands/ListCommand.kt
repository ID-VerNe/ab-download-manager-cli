package com.abdownloadmanager.cli.commands

import com.abdownloadmanager.cli.di.CliDownloadService
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.terminal.Terminal
import ir.amirab.downloader.downloaditem.DownloadStatus
import ir.amirab.downloader.downloaditem.IDownloadItem
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@Serializable
data class DownloadJsonItem(
    val id: Long,
    val name: String,
    val url: String?,
    val folder: String?,
    val status: String,
    val size: Long,
    val sizeFormatted: String,
    val dateAdded: Long,
    val connections: Int?,
    val speedLimit: Long,
    val checksum: String?,
)

class ListCommand : CliktCommand(
    name = "list",
    help = "List all downloads"
), KoinComponent {
    private val downloadService: CliDownloadService by inject()

    private val all: Boolean by option("--all", "-a", help = "Show all downloads including finished").flag()
    private val json: Boolean by option("--json", help = "Output as JSON").flag()

    override fun run() = runBlocking {
        val term = Terminal()
        downloadService.boot()

        val items = if (all) {
            downloadService.getAllItems()
        } else {
            downloadService.getAllItems().filter { it.status != DownloadStatus.Completed }
        }

        if (items.isEmpty()) {
            if (json) {
                term.println("[]")
            } else {
                term.println((TextColors.yellow)("No downloads found."))
            }
            return@runBlocking
        }

        if (json) {
            val jsonItems = items.map { it.toDownloadJsonItem() }
            term.println(Json { prettyPrint = true }.encodeToString(jsonItems))
        } else {
            term.println((TextColors.brightBlue)("┌──────┬──────────────────────────────────────┬──────────────┬──────────────┐"))
            term.println((TextColors.brightBlue)("│ ${"ID".padEnd(4)} │ ${"Name".padEnd(36)} │ ${"Status".padEnd(12)} │ ${"Size".padEnd(12)} │"))
            term.println((TextColors.brightBlue)("├──────┼──────────────────────────────────────┼──────────────┼──────────────┤"))

            for (item in items) {
                val name = item.name.take(36)
                val status = when (item.status) {
                    DownloadStatus.Downloading -> "Downloading"
                    DownloadStatus.Paused -> "Paused"
                    DownloadStatus.Completed -> (TextColors.green)("Completed")
                    DownloadStatus.Error -> (TextColors.red)("Error")
                    DownloadStatus.Added -> "Queued"
                }
                val size = if (item.contentLength > 0) {
                    formatSize(item.contentLength)
                } else {
                    "?"
                }

                term.print((TextColors.brightBlue)("│ "))
                term.print(item.id.toString().padEnd(4))
                term.print((TextColors.brightBlue)(" │ "))
                term.print(name.padEnd(36))
                term.print((TextColors.brightBlue)(" │ "))
                term.print(status.toString().padEnd(12))
                term.print((TextColors.brightBlue)(" │ "))
                term.print(size.padEnd(12))
                term.println((TextColors.brightBlue)(" │"))
            }

            term.println((TextColors.brightBlue)("└──────┴──────────────────────────────────────┴──────────────┴──────────────┘"))
            term.println("Total: ${items.size} download(s)")
        }
    }

    private fun IDownloadItem.toDownloadJsonItem() = DownloadJsonItem(
        id = id,
        name = name,
        url = link,
        folder = folder,
        status = status.name,
        size = contentLength,
        sizeFormatted = formatSize(contentLength),
        dateAdded = dateAdded,
        connections = preferredConnectionCount,
        speedLimit = speedLimit,
        checksum = fileChecksum,
    )

    private fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1_073_741_824 -> "%.1f GB".format(bytes.toDouble() / 1_073_741_824)
            bytes >= 1_048_576 -> "%.1f MB".format(bytes.toDouble() / 1_048_576)
            bytes >= 1_024 -> "%.1f KB".format(bytes.toDouble() / 1_024)
            else -> "$bytes B"
        }
    }
}