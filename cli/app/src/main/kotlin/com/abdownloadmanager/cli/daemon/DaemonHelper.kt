package com.abdownloadmanager.cli.daemon

import com.abdownloadmanager.cli.di.CliDownloadService
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.terminal.Terminal
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * Helper for CLI commands to auto-detect daemon mode.
 *
 * When the daemon is running, commands forward requests via HTTP
 * instead of booting the engine locally. This avoids JVM startup cost
 * per command and provides a consistent daemon-managed state.
 */
object DaemonHelper {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Check if daemon is running and return a DaemonClient instance.
     */
    fun daemonClient(): DaemonClient? {
        val client = DaemonClient.withDefaultConfig()
        return if (client.isRunning()) client else null
    }

    /**
     * Execute a block with daemon forwarding or direct engine access.
     *
     * @param downloadService The local download service (for direct mode).
     * @param onDaemon Called with DaemonClient when daemon is running.
     * @param onDirect Called with CliDownloadService when daemon is not running.
     */
    suspend fun <T> withDaemonOrDirect(
        downloadService: CliDownloadService?,
        onDaemon: suspend (DaemonClient) -> T,
        onDirect: suspend (CliDownloadService) -> T,
    ): T {
        val client = daemonClient()
        if (client != null) {
            return onDaemon(client)
        }
        // Boot engine for direct mode
        downloadService?.boot()
        return onDirect(downloadService ?: error("No download service available"))
    }

    // --- JSON response parsers ---

    /**
     * Parse a daemon API JSON response into a usable object.
     * Expected format: {"success": bool, "data": ..., "error": string|null}
     */
    fun parseResponse(response: String?): JsonObject? {
        if (response == null) return null
        return try {
            json.decodeFromString<JsonObject>(response)
        } catch (_: Exception) {
            null
        }
    }

    fun isSuccess(response: String?): Boolean {
        val obj = parseResponse(response) ?: return false
        return obj["success"]?.jsonPrimitive?.content == "true"
    }

    fun getData(response: String?): String? {
        val obj = parseResponse(response) ?: return null
        return obj["data"]?.toString()
    }

    fun getError(response: String?): String? {
        val obj = parseResponse(response) ?: return null
        return obj["error"]?.toString()?.removeSurrounding("\"")
    }

    /**
     * Print a formatted table header for list output.
     */
    fun printTableHeader(term: Terminal) {
        term.println((TextColors.brightBlue)("┌──────┬──────────────────────────────────────┬──────────────┬──────────────┐"))
        term.println((TextColors.brightBlue)("│ ${"ID".padEnd(4)} │ ${"Name".padEnd(36)} │ ${"Status".padEnd(12)} │ ${"Size".padEnd(12)} │"))
        term.println((TextColors.brightBlue)("├──────┼──────────────────────────────────────┼──────────────┼──────────────┤"))
    }

    fun printTableRow(term: Terminal, id: Long, name: String, status: String, size: String) {
        val displayName = name.take(36)
        val displayStatus = when {
            status == "Completed" -> (TextColors.green)("Completed")
            status == "Paused" -> "Paused"
            status == "Downloading" -> "Downloading"
            status == "Error" -> (TextColors.red)("Error")
            status == "Added" -> "Queued"
            else -> status
        }
        term.print((TextColors.brightBlue)("│ "))
        term.print(id.toString().padEnd(4))
        term.print((TextColors.brightBlue)(" │ "))
        term.print(displayName.padEnd(36))
        term.print((TextColors.brightBlue)(" │ "))
        term.print(displayStatus.toString().padEnd(12))
        term.print((TextColors.brightBlue)(" │ "))
        term.print(size.padEnd(12))
        term.println((TextColors.brightBlue)(" │"))
    }

    fun printTableFooter(term: Terminal, count: Int) {
        term.println((TextColors.brightBlue)("└──────┴──────────────────────────────────────┴──────────────┴──────────────┘"))
        term.println("Total: $count download(s)")
    }
}
