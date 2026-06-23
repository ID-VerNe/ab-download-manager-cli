package com.abdownloadmanager.cli.commands

import com.abdownloadmanager.cli.daemon.DaemonClient
import com.abdownloadmanager.cli.daemon.DaemonHelper
import com.abdownloadmanager.cli.di.CliDownloadService
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.long
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.terminal.Terminal
import kotlinx.coroutines.runBlocking
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File

/**
 * Open the downloaded file with the system default application.
 *
 * In daemon mode, the daemon opens the file on behalf of the client.
 * In direct mode, opens locally via java.awt.Desktop.
 */
class OpenCommand : CliktCommand(
    name = "open",
    help = "Open the downloaded file with the default application"
), KoinComponent {
    private val downloadService: CliDownloadService by inject()

    private val id: Long by argument(help = "Download ID").long()

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
        val response = client.openDownload(id)
        if (DaemonHelper.isSuccess(response)) {
            term.println((TextColors.green)("Opened download #$id via daemon"))
        } else {
            term.println((TextColors.red)("Failed: ${DaemonHelper.getError(response)}"))
        }
    }

    private fun runDirect(term: Terminal) {
        val item = runBlocking { downloadService.getItem(id) }
        if (item == null) {
            term.println((TextColors.red)("Download #$id not found"))
            return
        }
        val file = File(item.folder, item.name)
        if (!file.exists()) {
            term.println((TextColors.yellow)("File not found: ${file.absolutePath}"))
            return
        }
        try {
            java.awt.Desktop.getDesktop().open(file)
            term.println((TextColors.green)("Opened: ${file.name}"))
        } catch (e: Exception) {
            // Fallback: platform-specific commands
            try {
                val os = System.getProperty("os.name").lowercase()
                val process = when {
                    os.contains("win") -> ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", file.absolutePath).start()
                    os.contains("mac") -> ProcessBuilder("open", file.absolutePath).start()
                    else -> ProcessBuilder("xdg-open", file.absolutePath).start()
                }
                process.waitFor()
                term.println((TextColors.green)("Opened: ${file.name}"))
            } catch (e2: Exception) {
                term.println((TextColors.red)("Cannot open file: ${e2.message}"))
                term.println((TextColors.blue)("File path: ${file.absolutePath}"))
            }
        }
    }
}

/**
 * Open the folder containing the downloaded file.
 */
class OpenFolderCommand : CliktCommand(
    name = "open-folder",
    help = "Open the folder containing the downloaded file"
), KoinComponent {
    private val downloadService: CliDownloadService by inject()

    private val id: Long by argument(help = "Download ID").long()

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
        val response = client.openDownloadFolder(id)
        if (DaemonHelper.isSuccess(response)) {
            term.println((TextColors.green)("Opened folder for #$id via daemon"))
        } else {
            term.println((TextColors.red)("Failed: ${DaemonHelper.getError(response)}"))
        }
    }

    private fun runDirect(term: Terminal) {
        val item = runBlocking { downloadService.getItem(id) }
        if (item == null) {
            term.println((TextColors.red)("Download #$id not found"))
            return
        }
        val folder = File(item.folder)
        if (!folder.exists()) {
            term.println((TextColors.yellow)("Folder not found: ${folder.absolutePath}"))
            return
        }
        try {
            java.awt.Desktop.getDesktop().open(folder)
            term.println((TextColors.green)("Opened folder: ${folder.absolutePath}"))
        } catch (e: Exception) {
            try {
                val os = System.getProperty("os.name").lowercase()
                val process = when {
                    os.contains("win") -> ProcessBuilder("explorer.exe", folder.absolutePath).start()
                    os.contains("mac") -> ProcessBuilder("open", folder.absolutePath).start()
                    else -> ProcessBuilder("xdg-open", folder.absolutePath).start()
                }
                process.waitFor()
                term.println((TextColors.green)("Opened folder: ${folder.absolutePath}"))
            } catch (e2: Exception) {
                term.println((TextColors.red)("Cannot open folder: ${e2.message}"))
                term.println((TextColors.blue)("Folder path: ${folder.absolutePath}"))
            }
        }
    }
}