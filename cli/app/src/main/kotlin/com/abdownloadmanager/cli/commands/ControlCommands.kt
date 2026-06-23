package com.abdownloadmanager.cli.commands

import com.abdownloadmanager.cli.daemon.DaemonClient
import com.abdownloadmanager.cli.daemon.DaemonHelper
import com.abdownloadmanager.cli.di.CliDownloadService
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.long
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.terminal.Terminal
import ir.amirab.downloader.downloaditem.EmptyContext
import ir.amirab.downloader.downloaditem.IDownloadItem
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@Serializable
data class DownloadInfoJsonItem(
    val id: Long,
    val name: String,
    val url: String?,
    val folder: String?,
    val status: String,
    val size: Long,
    val sizeFormatted: String,
    val dateAdded: Long,
    val startTime: Long?,
    val completeTime: Long?,
    val connections: Int?,
    val speedLimit: Long,
    val checksum: String?,
)

/**
 * Base class for control commands (pause/resume/remove) that share the
 * daemon detection pattern.
 */
abstract class BaseControlCommand(
    name: String,
    help: String,
) : CliktCommand(name = name, help = help), KoinComponent {
    protected val downloadService: CliDownloadService by inject()

    protected val ids: List<Long> by argument(
        help = "Download ID(s)"
    ).long().multiple()

    final override fun run() = runBlocking {
        val term = Terminal()
        val daemonClient = DaemonHelper.daemonClient()
        if (daemonClient != null) {
            runWithDaemon(term, daemonClient)
        } else {
            downloadService.boot()
            runDirect(term)
        }
    }

    protected abstract suspend fun runWithDaemon(term: Terminal, client: DaemonClient)
    protected abstract suspend fun runDirect(term: Terminal)
}

class PauseCommand : BaseControlCommand(
    name = "pause",
    help = "Pause one or more downloads"
) {
    override suspend fun runWithDaemon(term: Terminal, client: DaemonClient) {
        val response = client.pauseDownload(ids)
        if (DaemonHelper.isSuccess(response)) {
            term.println((TextColors.green)("Paused ${ids.size} download(s) via daemon"))
        } else {
            term.println((TextColors.red)("Failed: ${DaemonHelper.getError(response)}"))
        }
    }

    override suspend fun runDirect(term: Terminal) {
        var count = 0
        for (id in ids) {
            val item = downloadService.getItem(id)
            if (item == null) {
                term.println((TextColors.yellow)("! Download #$id not found"))
                continue
            }
            downloadService.pauseDownload(id)
            term.println("(${(TextColors.yellow)("i")}) Paused #$id: ${item.name}")
            count++
        }
        if (count > 0) {
            term.println((TextColors.green)("Paused $count download(s)"))
        }
    }
}

class ResumeCommand : BaseControlCommand(
    name = "resume",
    help = "Resume one or more downloads"
) {
    override suspend fun runWithDaemon(term: Terminal, client: DaemonClient) {
        val response = client.resumeDownload(ids)
        if (DaemonHelper.isSuccess(response)) {
            term.println((TextColors.green)("Resumed ${ids.size} download(s) via daemon"))
        } else {
            term.println((TextColors.red)("Failed: ${DaemonHelper.getError(response)}"))
        }
    }

    override suspend fun runDirect(term: Terminal) {
        var count = 0
        for (id in ids) {
            val item = downloadService.getItem(id)
            if (item == null) {
                term.println((TextColors.yellow)("! Download #$id not found"))
                continue
            }
            downloadService.startDownload(id)
            term.println("(${(TextColors.green)("+")}) Resumed #$id: ${item.name}")
            count++
        }
        if (count > 0) {
            term.println((TextColors.green)("Resumed $count download(s)"))
        }
    }
}

class RemoveCommand : BaseControlCommand(
    name = "remove",
    help = "Remove one or more downloads"
) {
    private val keepFile: Boolean by option("--keep-file", "-k", help = "Keep the downloaded file").flag()

    override suspend fun runWithDaemon(term: Terminal, client: DaemonClient) {
        val response = client.removeDownload(ids, keepFile = keepFile)
        if (DaemonHelper.isSuccess(response)) {
            term.println((TextColors.green)("Removed ${ids.size} download(s) via daemon"))
        } else {
            term.println((TextColors.red)("Failed: ${DaemonHelper.getError(response)}"))
        }
    }

    override suspend fun runDirect(term: Terminal) {
        var count = 0
        for (id in ids) {
            val item = downloadService.getItem(id)
            if (item == null) {
                term.println((TextColors.yellow)("! Download #$id not found"))
                continue
            }
            downloadService.removeDownload(id, alsoRemoveFile = !keepFile)
            term.println("(${(TextColors.red)("-")}) Removed #$id: ${item.name}")
            count++
        }
        if (count > 0) {
            term.println((TextColors.green)("Removed $count download(s)"))
        }
    }
}

class RestartCommand : BaseControlCommand(
    name = "restart",
    help = "Restart (re-download) one or more downloads"
) {
    override suspend fun runWithDaemon(term: Terminal, client: DaemonClient) {
        val response = client.restartDownload(ids)
        if (DaemonHelper.isSuccess(response)) {
            term.println((TextColors.green)("Restarted ${ids.size} download(s) via daemon"))
        } else {
            term.println((TextColors.red)("Failed: ${DaemonHelper.getError(response)}"))
        }
    }

    override suspend fun runDirect(term: Terminal) {
        var count = 0
        for (id in ids) {
            val item = downloadService.getItem(id)
            if (item == null) {
                term.println((TextColors.yellow)("! Download #$id not found"))
                continue
            }
            downloadService.restartDownload(id)
            term.println("(${(TextColors.green)("↻")}) Restarted #$id: ${item.name}")
            count++
        }
        if (count > 0) {
            term.println((TextColors.green)("Restarted $count download(s)"))
        }
    }
}

class PauseAllCommand : CliktCommand(
    name = "pause-all",
    help = "Pause all active downloads"
), KoinComponent {
    private val downloadService: CliDownloadService by inject()

    override fun run() = runBlocking {
        val term = Terminal()
        val daemonClient = DaemonHelper.daemonClient()
        if (daemonClient != null) {
            val response = daemonClient.pauseAll()
            if (DaemonHelper.isSuccess(response)) {
                term.println((TextColors.green)("Paused all downloads via daemon"))
            } else {
                term.println((TextColors.red)("Failed: ${DaemonHelper.getError(response)}"))
            }
        } else {
            downloadService.boot()
            downloadService.pauseAll()
            term.println((TextColors.green)("Paused all downloads"))
        }
    }
}

class InfoCommand : CliktCommand(
    name = "info",
    help = "Show detailed information about a download"
), KoinComponent {
    private val downloadService: CliDownloadService by inject()

    private val ids: List<Long> by argument(
        help = "Download ID(s)"
    ).long().multiple()

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
        if (json) {
            // JSON output for multiple IDs
            val jsonItems = ids.mapNotNull { id ->
                val response = daemonClient.getDownload(id)
                if (response == null || !DaemonHelper.isSuccess(response)) {
                    term.println((TextColors.red)("Download #$id not found via daemon"))
                    null
                } else {
                    response
                }
            }
            if (jsonItems.isNotEmpty()) {
                term.println(Json { prettyPrint = true }.encodeToString(jsonItems))
            }
        } else {
            for (id in ids) {
                val response = daemonClient.getDownload(id)
                if (response == null) {
                    term.println((TextColors.red)("Download #$id not found via daemon"))
                    continue
                }
                // Parse the JSON response data field
                val jsonStr = try {
                    val obj = Json { ignoreUnknownKeys = true }.decodeFromString<kotlinx.serialization.json.JsonObject>(response)
                    obj["data"]?.toString() ?: response
                } catch (_: Exception) { response }

                term.println((TextColors.brightCyan)("Download #$id (via daemon)"))
                // Try to extract fields from the JSON response
                try {
                    val item = Json { ignoreUnknownKeys = true }.decodeFromString<kotlinx.serialization.json.JsonObject>(
                        if (jsonStr.startsWith("{")) jsonStr else "{}"
                    )
                    term.println((TextColors.brightBlue)("  Data:    ") + item.toString().take(200))
                } catch (_: Exception) {
                    term.println((TextColors.brightBlue)("  Data:    ") + jsonStr.take(200))
                }
                term.println()
            }
        }
    }

    private suspend fun runDirect(term: Terminal) {
        val foundItems = ids.mapNotNull { id ->
            val item = downloadService.getItem(id)
            if (item == null) {
                term.println((TextColors.red)("Download #$id not found"))
                null
            } else {
                item
            }
        }

        if (json) {
            val jsonItems = foundItems.map { it.toDownloadInfoJson() }
            term.println(Json { prettyPrint = true }.encodeToString(jsonItems))
        } else {
            for (item in foundItems) {
                term.println((TextColors.brightCyan)("Download #${item.id}"))
                term.println((TextColors.brightBlue)("  Name:    ") + item.name)
                term.println((TextColors.brightBlue)("  URL:     ") + item.link)
                term.println((TextColors.brightBlue)("  Folder:  ") + item.folder)
                term.println((TextColors.brightBlue)("  Status:  ") + item.status.name)
                term.println((TextColors.brightBlue)("  Size:    ") + formatSize(item.contentLength))
                term.println((TextColors.brightBlue)("  Added:   ") + formatTimestamp(item.dateAdded))

                if (item.preferredConnectionCount != null) {
                    term.println((TextColors.brightBlue)("  Connections: ") + item.preferredConnectionCount)
                }
                if (item.speedLimit > 0) {
                    term.println((TextColors.brightBlue)("  Speed limit: ") + formatSpeed(item.speedLimit))
                }
                if (item.fileChecksum != null) {
                    term.println((TextColors.brightBlue)("  Checksum: ") + item.fileChecksum)
                }
                term.println()
            }
        }
    }

    private fun IDownloadItem.toDownloadInfoJson(): DownloadInfoJsonItem {
        return DownloadInfoJsonItem(
            id = id,
            name = name,
            url = link,
            folder = folder,
            status = status.name,
            size = contentLength,
            sizeFormatted = formatSize(contentLength),
            dateAdded = dateAdded,
            startTime = startTime,
            completeTime = completeTime,
            connections = preferredConnectionCount,
            speedLimit = speedLimit,
            checksum = fileChecksum,
        )
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1_073_741_824 -> "%.2f GB".format(bytes.toDouble() / 1_073_741_824)
            bytes >= 1_048_576 -> "%.2f MB".format(bytes.toDouble() / 1_048_576)
            bytes >= 1_024 -> "%.2f KB".format(bytes.toDouble() / 1_024)
            bytes < 0 -> "Unknown"
            else -> "$bytes B"
        }
    }

    private fun formatSpeed(bytes: Long): String {
        return when {
            bytes >= 1_073_741_824 -> "%.1f GB/s".format(bytes.toDouble() / 1_073_741_824)
            bytes >= 1_048_576 -> "%.1f MB/s".format(bytes.toDouble() / 1_048_576)
            bytes >= 1_024 -> "%.1f KB/s".format(bytes.toDouble() / 1_024)
            else -> "$bytes B/s"
        }
    }

    private fun formatTimestamp(ts: Long): String {
        if (ts <= 0) return "N/A"
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = ts }
        return "%tF %tT".format(cal, cal)
    }
}
