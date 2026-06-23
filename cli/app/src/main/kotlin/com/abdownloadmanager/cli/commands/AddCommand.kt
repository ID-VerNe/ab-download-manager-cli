package com.abdownloadmanager.cli.commands

import com.abdownloadmanager.cli.daemon.DaemonHelper
import com.abdownloadmanager.cli.daemon.DaemonClient
import com.abdownloadmanager.cli.di.CliDownloadService
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.long
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyle
import com.github.ajalt.mordant.terminal.Terminal
import ir.amirab.downloader.NewDownloadItemProps
import ir.amirab.downloader.downloaditem.DownloadStatus
import ir.amirab.downloader.downloaditem.EmptyContext
import ir.amirab.downloader.downloaditem.http.HttpDownloadItem
import ir.amirab.downloader.utils.OnDuplicateStrategy
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.text.DecimalFormat
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.milliseconds

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
        help = "Start download immediately and show progress (blocking)"
    ).flag()

    private val detach: Boolean by option(
        "--detach", "-d",
        help = "Add and start in background, exit immediately (use with --start)"
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

    private val duplicate: String? by option(
        "--duplicate",
        help = "Duplicate handling: abort (default, skip & warn), override (re-download), add-numbered (auto-rename)"
    )

    private val username: String? by option(
        "--username", "-u",
        help = "HTTP Basic Auth username"
    )

    private val password: String? by option(
        "--password", "-p",
        help = "HTTP Basic Auth password"
    )

    private val speedLimit: Long? by option(
        "--speed-limit", "-l",
        help = "Download speed limit in bytes per second (0 = unlimited)"
    ).long()

    override fun run() = runBlocking {
        val term = Terminal()

        // Auto-detect daemon mode
        val daemonClient = DaemonHelper.daemonClient()
        if (daemonClient != null) {
            runWithDaemon(term, daemonClient)
            return@runBlocking
        }

        // Direct mode (daemon not running)
        downloadService.boot()
        runDirect(term)
    }

    /**
     * Daemon mode: forward add requests via HTTP.
     */
    private suspend fun runWithDaemon(term: Terminal, daemonClient: DaemonClient) {
        var successCount = 0

        for (url in urls) {
            val folder = outputDir ?: System.getProperty("user.dir")
            val itemName = fileName ?: url.substringAfterLast("/").substringBefore("?").ifEmpty {
                "download_${System.currentTimeMillis()}"
            }
            try {
                val response = daemonClient.addDownload(
                    url = url,
                    name = fileName,
                    folder = outputDir,
                    username = username,
                    password = password,
                    connections = connections,
                    speedLimit = speedLimit,
                    duplicate = duplicate,
                    start = start || detach,
                    queueId = queue,
                )
                if (DaemonHelper.isSuccess(response)) {
                    successCount++
                    if (!quiet) {
                        if (start && detach) {
                            term.println((TextColors.green)("✓") + " Started download: $itemName (background, via daemon)")
                        } else if (start) {
                            term.println((TextColors.green)("✓") + " Added download: $itemName (via daemon)")
                        } else {
                            term.println((TextColors.green)("✓") + " Added download: $itemName (via daemon)")
                        }
                    }
                } else {
                    val err = DaemonHelper.getError(response) ?: "Unknown error"
                    term.println((TextColors.red)("✗") + " $url: $err")
                }
            } catch (e: Exception) {
                if (!quiet) {
                    term.println((TextColors.red)("✗") + " Failed to add $url: ${e.message}")
                }
            }
        }

        if (!quiet && successCount > 0) {
            term.println((TextColors.green)("Successfully added $successCount download(s) via daemon"))
        }
    }

    /**
     * Direct mode: existing behavior with local engine.
     */
    private suspend fun runDirect(term: Terminal) {
        var successCount = 0

        for (url in urls) {
            val folder = outputDir ?: System.getProperty("user.dir")
            val itemName = fileName ?: url.substringAfterLast("/").substringBefore("?").ifEmpty {
                "download_${System.currentTimeMillis()}"
            }
            try {
                val strategy = when (duplicate?.lowercase()) {
                    "override" -> OnDuplicateStrategy.OverrideDownload
                    "add-numbered" -> OnDuplicateStrategy.AddNumbered
                    else -> OnDuplicateStrategy.Abort // default: abort on duplicate
                }

                val downloadItem = HttpDownloadItem(
                    link = url,
                    id = -1,
                    folder = folder,
                    name = itemName,
                    username = username,
                    password = password,
                    preferredConnectionCount = connections,
                    speedLimit = speedLimit ?: 0,
                )

                val props = NewDownloadItemProps(
                    downloadItem = downloadItem,
                    extraConfig = null,
                    onDuplicateStrategy = strategy,
                    context = EmptyContext,
                )

                val id = downloadService.addDownload(props)
                successCount++

                val queueId = queue
                if (queueId != null) {
                    downloadService.addToQueue(queueId, id)
                }

                if (start && detach) {
                    // Detach mode: start and exit immediately
                    downloadService.startDownload(id)
                    if (!quiet) {
                        term.println((TextColors.green)("✓") + " Started download #$id: $itemName (background)")
                    }
                } else if (start) {
                    // Blocking mode: start download and show progress bar
                    downloadService.startDownload(id)
                    if (!quiet) {
                        showProgress(term, id, url)
                    }
                } else {
                    if (!quiet) {
                        term.println((TextColors.green)("✓") + " Added download #$id: $itemName")
                    }
                }
            } catch (e: IllegalStateException) {
                if (!quiet) {
                    term.println()
                    term.println((TextColors.red)("✗ DUPLICATE: ") + (TextColors.yellow)("$itemName already exists at $folder"))
                    term.println("  " + TextColors.gray("Use --duplicate override to re-download"))
                    term.println("  " + TextColors.gray("Use --duplicate add-numbered to auto-rename"))
                    term.println("  " + TextColors.gray("Use 'abdm list' to see existing downloads"))
                    term.println()
                }
            } catch (e: Exception) {
                if (!quiet) {
                    term.println((TextColors.red)("✗") + " Failed to add $url: ${e.message}")
                }
            }
        }

        if (!quiet && successCount > 0 && !start) {
            term.println((TextColors.green)("Successfully added $successCount download(s)"))
        }
    }

    private suspend fun showProgress(term: Terminal, id: Long, url: String) {
        // Periodically poll for download status and size
        var lastBytes = 0L
        var lastTime = System.currentTimeMillis()

        while (true) {
            val item = downloadService.getItem(id) ?: break
            when (item.status) {
                DownloadStatus.Completed -> {
                    term.println()
                    term.println((TextColors.green)("✓") + " Completed #$id: ${item.name} (${formatSize(item.contentLength)})")
                    break
                }
                DownloadStatus.Error -> {
                    term.println()
                    term.println((TextColors.red)("✗") + " Failed #$id: ${item.name}")
                    break
                }
                DownloadStatus.Paused -> {
                    term.println()
                    term.println((TextColors.yellow)("⏸") + " Paused #$id: ${item.name}")
                    break
                }
                else -> {
                    val now = System.currentTimeMillis()
                    val elapsed = now - lastTime
                    val downloaded = downloadService.getDownloadedSize(id).coerceAtLeast(0)
                    val total = item.contentLength
                    val displayTotal = total.coerceAtLeast(downloaded)

                    // Speed calculation
                    val speed = if (elapsed > 0 && downloaded >= lastBytes) {
                        ((downloaded - lastBytes) * 1000.0 / elapsed).roundToLong()
                    } else 0L

                    val progress = if (total > 0) {
                        (downloaded * 100.0 / total).let { if (it > 100.0) 100.0 else it }
                    } else 0.0

                    val eta = if (speed > 0 && total > downloaded) {
                        formatDuration(((total - downloaded) * 1000 / speed).toLong())
                    } else "∞"

                    // Render progress bar
                    val barWidth = 30
                    val filled = (progress / 100.0 * barWidth).roundToLong().toInt()
                    val bar = "█".repeat(filled) + "░".repeat((barWidth - filled).coerceAtLeast(0))

                    term.print("\r" + " ".repeat(120))
                    val nameStr = item.name.take(40).padEnd(40)
                    val pctStr = "%.1f%%".format(progress).padStart(7)
                    val statsStr = " $bar $pctStr ${formatSize(downloaded)}/${formatSize(displayTotal)} ${formatSize(speed)}/s ETA $eta"
                    term.print("\r" + " ".repeat(120))
                    term.print("\r" + TextColors.cyan(nameStr) + TextColors.brightWhite(statsStr))

                    lastBytes = downloaded
                    lastTime = now
                    delay(500)
                }
            }
        }
    }

    private fun formatSize(bytes: Long): String {
        val df = DecimalFormat("#,##0.#")
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${df.format(bytes / 1024.0)} KB"
            bytes < 1024 * 1024 * 1024 -> "${df.format(bytes / (1024.0 * 1024))} MB"
            else -> "${df.format(bytes / (1024.0 * 1024 * 1024))} GB"
        }
    }

    private fun formatDuration(ms: Long): String {
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return when {
            h > 0 -> "${h}h${m}m${s}s"
            m > 0 -> "${m}m${s}s"
            else -> "${s}s"
        }
    }
}