package com.abdownloadmanager.cli.commands

import com.abdownloadmanager.cli.daemon.DaemonClient
import com.abdownloadmanager.cli.daemon.DaemonHelper
import com.abdownloadmanager.cli.di.CliDownloadService
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.long
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.terminal.Terminal
import kotlinx.coroutines.runBlocking
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Edit download properties (name, speed limit, connections).
 *
 * Usage:
 *   abdm edit <id> --name <new-name>
 *   abdm edit <id> --speed-limit <bytes-per-sec>
 *   abdm edit <id> --connections <N>
 */
class EditCommand : CliktCommand(
    name = "edit",
    help = "Edit download properties"
), KoinComponent {
    private val downloadService: CliDownloadService by inject()

    private val id: Long by argument(help = "Download ID").long()

    private val newName: String? by option("--name", "-n", help = "New name for the download")
    private val speedLimit: Long? by option("--speed-limit", "-l", help = "Speed limit in bytes/sec (0 = unlimited)").long()
    private val connections: Int? by option("--connections", "-c", help = "Number of concurrent connections").int()

    override fun run() = runBlocking {
        val term = Terminal()

        if (newName == null && speedLimit == null && connections == null) {
            term.println((TextColors.yellow)("No changes specified. Use --name, --speed-limit, or --connections."))
            return@runBlocking
        }

        val daemonClient = DaemonHelper.daemonClient()
        if (daemonClient != null) {
            runWithDaemon(term, daemonClient)
        } else {
            downloadService.boot()
            runDirect(term)
        }
    }

    private fun runWithDaemon(term: Terminal, client: DaemonClient) {
        val response = client.editDownload(id, name = newName, speedLimit = speedLimit, connections = connections)
        if (DaemonHelper.isSuccess(response)) {
            term.println((TextColors.green)("Updated download #$id via daemon"))
        } else {
            term.println((TextColors.red)("Failed: ${DaemonHelper.getError(response)}"))
        }
    }

    private suspend fun runDirect(term: Terminal) {
        val item = downloadService.getItem(id)
        if (item == null) {
            term.println((TextColors.red)("Download #$id not found"))
            return
        }

        downloadService.updateDownloadItem(id) { dl ->
            newName?.let { dl.name = it }
            speedLimit?.let { dl.speedLimit = it }
            connections?.let { dl.preferredConnectionCount = it }
        }

        term.println((TextColors.green)("Updated download #$id"))
        if (newName != null) term.println((TextColors.brightBlue)("  Name:     ") + newName)
        if (speedLimit != null) term.println((TextColors.brightBlue)("  Speed limit: ") + speedLimit)
        if (connections != null) term.println((TextColors.brightBlue)("  Connections: ") + connections)
    }
}