package com.abdownloadmanager.cli.commands

import com.abdownloadmanager.cli.daemon.DaemonClient
import com.abdownloadmanager.cli.daemon.DaemonHelper
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
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

        // Auto-detect daemon mode
        val daemonClient = DaemonHelper.daemonClient()
        if (daemonClient != null) {
            runWithDaemon(term, daemonClient)
            return@runBlocking
        }

        // Direct mode
        downloadService.boot()
        runDirect(term)
    }

    private fun runWithDaemon(term: Terminal, daemonClient: DaemonClient) {
        val response = daemonClient.listDownloads()
        if (response == null) {
            term.println((TextColors.red)("Failed to communicate with daemon"))
            return
        }

        val items = try {
            Json { ignoreUnknownKeys = true }.decodeFromString<JsonArray>(response)
        } catch (_: Exception) {
            term.println((TextColors.red)("Failed to parse daemon response"))
            return
        }

        val filteredItems = if (all) items else items.filter { itemObj ->
            val status = (itemObj as JsonObject)["status"]?.jsonPrimitive?.content ?: ""
            status != "Completed"
        }

        if (filteredItems.isEmpty()) {
            if (json) {
                term.println("[]")
            } else {
                term.println((TextColors.yellow)("No downloads found."))
            }
            return
        }

        if (json) {
            term.println(Json { prettyPrint = true }.encodeToString(filteredItems))
        } else {
            DaemonHelper.printTableHeader(term)
            for (itemObj in filteredItems) {
                val obj = itemObj as JsonObject
                val id = obj["id"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
                val name = obj["name"]?.jsonPrimitive?.content ?: "?"
                val status = obj["status"]?.jsonPrimitive?.content ?: "Unknown"
                val size = obj["sizeFormatted"]?.jsonPrimitive?.content ?: "?"
                DaemonHelper.printTableRow(term, id, name, status, size)
            }
            DaemonHelper.printTableFooter(term, filteredItems.size)
        }
    }

    private fun runDirect(term: Terminal) {
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
            return
        }

        if (json) {
            val jsonItems = items.map { it.toDownloadJsonItem() }
            term.println(Json { prettyPrint = true }.encodeToString(jsonItems))
        } else {
            DaemonHelper.printTableHeader(term)
            for (item in items) {
                val status = when (item.status) {
                    DownloadStatus.Downloading -> "Downloading"
                    DownloadStatus.Paused -> "Paused"
                    DownloadStatus.Completed -> "Completed"
                    DownloadStatus.Error -> "Error"
                    DownloadStatus.Added -> "Added"
                }
                val size = if (item.contentLength > 0) {
                    formatSize(item.contentLength)
                } else {
                    "?"
                }
                DaemonHelper.printTableRow(term, item.id, item.name, status, size)
            }
            DaemonHelper.printTableFooter(term, items.size)
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
