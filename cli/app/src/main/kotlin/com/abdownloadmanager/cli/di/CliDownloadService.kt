package com.abdownloadmanager.cli.di

import com.abdownloadmanager.cli.utils.CliPaths
import ir.amirab.downloader.DownloadManager
import ir.amirab.downloader.DownloadManagerMinimalControl
import ir.amirab.downloader.NewDownloadItemProps
import ir.amirab.downloader.downloaditem.EmptyContext
import ir.amirab.downloader.downloaditem.IDownloadItem
import ir.amirab.downloader.downloaditem.contexts.ResumedBy
import ir.amirab.downloader.downloaditem.contexts.StoppedBy
import ir.amirab.downloader.downloaditem.contexts.User
import ir.amirab.downloader.queue.QueueManager
import kotlinx.coroutines.runBlocking

/**
 * Simplified download service for CLI usage.
 * Wraps the DownloadManager with CLI-friendly operations.
 */
class CliDownloadService(
    private val downloadManager: DownloadManager,
    private val queueManager: QueueManager,
    private val paths: CliPaths,
) {
    private var booted = false

    /** Initialize the download engine: boot the DB, resume pending downloads, etc. */
    fun boot() {
        if (booted) return
        runBlocking {
            queueManager.boot()
            downloadManager.boot()
        }
        booted = true
    }

    /** Add a new download item */
    suspend fun addDownload(props: NewDownloadItemProps): Long {
        return downloadManager.addDownload(props)
    }

    /** Start (resume) a download */
    suspend fun startDownload(id: Long) {
        downloadManager.startJob(id, ResumedBy(User))
    }

    /** Pause a download */
    suspend fun pauseDownload(id: Long) {
        downloadManager.stopJob(id, StoppedBy(User))
    }

    /** Remove a download */
    suspend fun removeDownload(id: Long, alsoRemoveFile: Boolean = false) {
        downloadManager.deleteDownload(
            id = id,
            alsoRemoveFile = { alsoRemoveFile },
            context = EmptyContext,
        )
    }

    /** Get all download items */
    fun getAllItems(): List<IDownloadItem> {
        return runBlocking { downloadManager.dlListDb.getAll() }
    }

    /** Get item by ID */
    suspend fun getItem(id: Long): IDownloadItem? {
        return downloadManager.dlListDb.getById(id)
    }

    /** Get the count of active (unfinished) downloads */
    fun getActiveCount(): Int {
        return runBlocking { downloadManager.getActiveCount() }
    }

    /** Get the downloaded byte count for a given download job */
    suspend fun getDownloadedSize(id: Long): Long {
        return downloadManager.downloadJobs
            .find { it.id == id }
            ?.getDownloadedSize() ?: 0L
    }
}