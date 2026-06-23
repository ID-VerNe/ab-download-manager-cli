package com.abdownloadmanager.cli.commands

import com.abdownloadmanager.cli.daemon.DaemonClient
import com.abdownloadmanager.cli.daemon.DaemonHelper
import com.abdownloadmanager.cli.di.CliDownloadService
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.types.long
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.terminal.Terminal
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * View or set the checksum of a download.
 *
 * Usage:
 *   abdm checksum <id>          — View current checksum
 *   abdm checksum <id> <hash>   — Set checksum to <hash>
 */
class ChecksumCommand : CliktCommand(
    name = "checksum",
    help = "View or set checksum for a download"
), KoinComponent {
    private val downloadService: CliDownloadService by inject()

    private val id: Long by argument(help = "Download ID").long()
    private val checksumValue: String? by argument(help = "Checksum hash to set (omit to view current)").optional()

    override fun run() = runBlocking {
        val term = Terminal()
        val daemonClient = DaemonHelper.daemonClient()
        if (daemonClient != null) {
            runWithDaemon(term, daemonClient)
        } else {
            downloadService.boot()
            runDirect(term)
        }
    }

    private fun runWithDaemon(term: Terminal, client: DaemonClient) {
        val cs = checksumValue
        if (cs != null) {
            val response = client.setChecksum(id, cs)
            if (DaemonHelper.isSuccess(response)) {
                term.println((TextColors.green)("Checksum set for #$id via daemon"))
            } else {
                term.println((TextColors.red)("Failed: ${DaemonHelper.getError(response)}"))
            }
        } else {
            val response = client.getDownload(id)
            if (DaemonHelper.isSuccess(response)) {
                val data = DaemonHelper.getData(response)
                term.println((TextColors.brightCyan)("Download #$id checksum (via daemon):"))
                term.println((TextColors.brightBlue)("  Checksum: ") + (data?.let { extractChecksumFromJson(it) } ?: "N/A"))
            } else {
                term.println((TextColors.red)("Download #$id not found via daemon"))
            }
        }
    }

    private suspend fun runDirect(term: Terminal) {
        val item = downloadService.getItem(id)
        if (item == null) {
            term.println((TextColors.red)("Download #$id not found"))
            return
        }

        if (checksumValue != null) {
            downloadService.updateDownloadItem(id) { it.fileChecksum = checksumValue }
            term.println((TextColors.green)("Checksum set for #$id: $checksumValue"))
        } else {
            term.println((TextColors.brightCyan)("Download #${item.id}"))
            term.println((TextColors.brightBlue)("  Name:     ") + item.name)
            term.println((TextColors.brightBlue)("  Checksum: ") + (item.fileChecksum ?: "Not set"))
        }
    }

    private fun extractChecksumFromJson(json: String): String? {
        return try {
            val jsonObj = Json { ignoreUnknownKeys = true }
                .decodeFromString<JsonObject>(json)
            jsonObj["checksum"]?.jsonPrimitive?.content
        } catch (_: Exception) { null }
    }
}