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
        // Open via platform-specific command (headless-safe)
        openWithSystem(file, isFolder = false, term = term)
    }
}

/**
 * Open a file or folder using platform-specific commands.
 * Headless-safe — does not use java.awt.Desktop.
 */
private fun openWithSystem(target: File, isFolder: Boolean, term: Terminal) {
    val name = if (isFolder) "folder" else "file"
    try {
        val os = System.getProperty("os.name").lowercase()
        val process = when {
            os.contains("win") -> {
                if (isFolder) ProcessBuilder("explorer.exe", target.absolutePath).start()
                else ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", target.absolutePath).start()
            }
            os.contains("mac") -> ProcessBuilder("open", target.absolutePath).start()
            else -> ProcessBuilder("xdg-open", target.absolutePath).start()
        }
        process.waitFor()
        val label = if (isFolder) target.absolutePath else target.name
        term.println((TextColors.green)("Opened $name: $label"))
    } catch (e: Exception) {
        term.println((TextColors.red)("Cannot open $name: ${e.message}"))
        term.println((TextColors.blue)("Path: ${target.absolutePath}"))
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
        openWithSystem(folder, isFolder = true, term = term)
    }
}