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

    /** Restart (reset) a download — clears downloaded data and restarts */
    suspend fun restartDownload(id: Long) {
        downloadManager.reset(id, context = ResumedBy(User))
        downloadManager.startJob(id, ResumedBy(User))
    }

    /** Pause all active downloads */
    suspend fun pauseAll() {
        downloadManager.stopAll(StoppedBy(User))
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

    /** Add a download to a queue */
    suspend fun addToQueue(queueId: Long, downloadId: Long) {
        queueManager.addToQueue(queueId, downloadId)
    }

    /** Get all queues */
    fun getQueues(): List<ir.amirab.downloader.queue.DownloadQueue> {
        return queueManager.getAll()
    }

    /** Start a queue by ID */
    fun startQueue(queueId: Long) {
        queueManager.getQueue(queueId).start()
    }

    /** Get the main queue ID */
    fun getMainQueueId(): Long {
        return queueManager.getMainQueue().id
    }

    /** Update a download item's properties in-place. */
    suspend fun updateDownloadItem(id: Long, updater: (IDownloadItem) -> Unit) {
        downloadManager.updateDownloadItem(id, null, updater)
    }
}