package com.abdownloadmanager.cli.commands

import com.abdownloadmanager.cli.di.CliDownloadService
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.long
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.terminal.Terminal
import ir.amirab.downloader.NewDownloadItemProps
import ir.amirab.downloader.downloaditem.EmptyContext
import ir.amirab.downloader.downloaditem.http.HttpDownloadItem
import ir.amirab.downloader.utils.OnDuplicateStrategy
import kotlinx.coroutines.runBlocking
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class AddCommand : CliktCommand(
    name = "add",
    help = "Add a new download"
), KoinComponent {
    private val downloadService: CliDownloadService by inject()

    private val urls: List<String> by argument(
        help = "URL(s) to download"
    ).multiple()

    private val outputDir: String? by option(
        "--output-dir", "-o",
        help = "Output directory"
    )

    private val fileName: String? by option(
        "--name", "-n",
        help = "Output file name"
    )

    private val start: Boolean by option(
        "--start", "-s",
        help = "Start download immediately"
    ).flag()

    private val queue: Long? by option(
        "--queue", "-q",
        help = "Queue ID to add to"
    ).long()

    private val connections: Int? by option(
        "--connections", "-c",
        help = "Number of concurrent connections"
    ).int()

    private val quiet: Boolean by option(
        "--quiet",
        help = "Suppress progress output"
    ).flag()

    override fun run() = runBlocking {
        val term = Terminal()
        downloadService.boot()
        var successCount = 0

        for (url in urls) {
            try {
                val folder = outputDir ?: System.getProperty("user.dir")
                val name = fileName ?: url.substringAfterLast("/").substringBefore("?").ifEmpty {
                    "download_${System.currentTimeMillis()}"
                }

                val downloadItem = HttpDownloadItem(
                    link = url,
                    id = -1,
                    folder = folder,
                    name = name,
                    preferredConnectionCount = connections,
                )

                val props = NewDownloadItemProps(
                    downloadItem = downloadItem,
                    extraConfig = null,
                    onDuplicateStrategy = OnDuplicateStrategy.AddNumbered,
                    context = EmptyContext,
                )

                val id = downloadService.addDownload(props)
                if (start || queue == null) {
                    downloadService.startDownload(id)
                }

                if (!quiet) {
                    term.println((TextColors.green)("✓") + " Added download #$id: $name")
                }
                successCount++
            } catch (e: Exception) {
                term.println((TextColors.red)("✗") + " Failed to add $url: ${e.message}")
            }
        }

        if (!quiet && successCount > 0) {
            term.println((TextColors.green)("Successfully added $successCount download(s)"))
        }
    }
}