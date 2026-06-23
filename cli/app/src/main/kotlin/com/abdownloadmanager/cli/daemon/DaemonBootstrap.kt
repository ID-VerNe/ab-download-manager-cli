package com.abdownloadmanager.cli.daemon

import com.abdownloadmanager.cli.di.CliDownloadService
import com.abdownloadmanager.cli.utils.CliAppSettings
import com.abdownloadmanager.cli.utils.CliPaths
import com.abdownloadmanager.integration.http4k.MyHttp4KServer
import ir.amirab.downloader.DownloadSettings
import ir.amirab.downloader.downloaditem.contexts.StoppedBy
import ir.amirab.downloader.downloaditem.contexts.User
import kotlinx.coroutines.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.RandomAccessFile
import java.nio.channels.FileLock
import java.util.concurrent.CountDownLatch

/**
 * Bootstrap sequence for the download daemon.
 *
 * Lifecycle:
 *   start() → CliDi already booted → DownloadService.boot() → HTTP server start → block
 *   shutdown signal → HTTP server stop → lock cleanup → port file cleanup
 *
 * The daemon runs as a foreground process (qBittorrent-nox model).
 * No double-fork daemonization — let systemd, Docker, screen, etc. handle backgrounding.
 */
object DaemonBootstrap : KoinComponent {

    private val downloadService: CliDownloadService by inject()
    private val paths: CliPaths by inject()
    private val appSettings: CliAppSettings by inject()
    private val downloadSettings: DownloadSettings by inject()

    private var server: MyHttp4KServer? = null
    private var lock: FileLock? = null
    private var lockRaf: RandomAccessFile? = null
    private var shutdownLatch: CountDownLatch? = null

    /**
     * Start the daemon (blocking — does not return until shutdown is requested).
     *
     * @param port HTTP API port (default 16889)
     */
    fun start(port: Int = DEFAULT_PORT) {
        val daemonPaths = DaemonPaths(paths.dataDir)

        // 1. Acquire process lock (prevent multiple daemon instances)
        tryLock(daemonPaths)

        // 2. Boot the download engine (loads DB, restores pending downloads)
        println("[daemon] Booting download engine...")
        appSettings.syncDownloadSettings(downloadSettings)
        downloadService.boot()

        // 3. Create and start HTTP server
        shutdownLatch = CountDownLatch(1)
        val handlerMap = DaemonApi.createHandlerMap(
            downloadService = downloadService,
            appSettings = appSettings,
            downloadSettings = downloadSettings,
            onShutdown = {
                println("[daemon] Shutdown signal received")
                shutdownLatch?.countDown()
            }
        )
        val httpServer = MyHttp4KServer(port, handlerMap, isDebugMode = false)
        httpServer.startMyServer()
        server = httpServer

        // 4. Write port file for CLI client discovery
        daemonPaths.portFile.writeText(port.toString())

        // 5. Register JVM shutdown hook for clean exit
        Runtime.getRuntime().addShutdownHook(Thread {
            println("[daemon] JVM shutting down...")
            doShutdown(daemonPaths)
        })

        println("[daemon] Listening on http://localhost:$port (PID: ${ProcessHandle.current().pid()})")

        // 6. Block main thread until shutdown signal
        shutdownLatch?.await()

        // 7. Clean shutdown (reached when CountDownLatch is released)
        doShutdown(daemonPaths)
    }

    /**
     * Attempt to acquire an exclusive file lock.
     * Throws if another daemon instance is already running.
     */
    private fun tryLock(daemonPaths: DaemonPaths) {
        val raf = RandomAccessFile(daemonPaths.lockFile, "rw")
        val fileLock = raf.channel.tryLock()
        if (fileLock == null) {
            raf.close()
            throw IllegalStateException(
                "Another daemon instance is already running " +
                        "(lock file: ${daemonPaths.lockFile})"
            )
        }
        lock = fileLock
        lockRaf = raf
    }

    /**
     * Release the file lock.
     */
    private fun releaseLock() {
        try {
            lock?.release()
        } catch (_: Exception) {
        }
        try {
            lockRaf?.close()
        } catch (_: Exception) {
        }
        lock = null
        lockRaf = null
    }

    /**
     * Clean shutdown: stop HTTP server, delete port file, release lock.
     */
    private fun doShutdown(daemonPaths: DaemonPaths) {
        // Stop all active downloads gracefully
        runCatching {
            runBlocking { downloadService.pauseAll() }
        }

        // Stop HTTP server
        try {
            server?.stopMyServer()
        } catch (_: Exception) {
        }
        server = null

        // Delete port file
        try {
            daemonPaths.portFile.delete()
        } catch (_: Exception) {
        }

        // Release file lock (also deletes lock file on JVM exit)
        releaseLock()

        println("[daemon] Shutdown complete")
    }

    const val DEFAULT_PORT = 16889
}
