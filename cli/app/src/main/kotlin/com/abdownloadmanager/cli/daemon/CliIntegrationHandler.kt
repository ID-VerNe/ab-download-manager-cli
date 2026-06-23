package com.abdownloadmanager.cli.daemon

import com.abdownloadmanager.cli.di.CliDownloadService
import com.abdownloadmanager.integration.*
import ir.amirab.downloader.NewDownloadItemProps
import ir.amirab.downloader.downloaditem.EmptyContext
import ir.amirab.downloader.downloaditem.http.HttpDownloadCredentials
import ir.amirab.downloader.downloaditem.hls.HLSDownloadCredentials
import ir.amirab.downloader.downloaditem.http.HttpDownloadItem
import ir.amirab.downloader.queue.QueueManager
import ir.amirab.downloader.utils.OnDuplicateStrategy
import kotlinx.coroutines.runBlocking

/**
 * CLI-specific IntegrationHandler for the daemon.
 *
 * Provides the bridge between the generic integration API (used by
 * the browser extension and external tools) and the CLI's download engine.
 *
 * Used by the daemon to handle:
 *   - `/add` and `/start-headless-download` integration endpoints
 *   - Queue listing via `/queues`
 *
 * Unlike the desktop IntegrationHandlerImp, this:
 *   - Has no Compose/UI dependencies
 *   - Writes directly to CliDownloadService
 *   - Uses simple console logging instead of UI notifications
 */
class CliIntegrationHandler(
    private val downloadService: CliDownloadService,
    private val queueManager: QueueManager,
) : IntegrationHandler {

    override suspend fun addDownload(
        list: List<IDownloadCredentialsFromIntegration>,
        options: AddDownloadOptionsFromIntegration,
    ) {
        for (cred in list) {
            try {
                val downloadItem = HttpDownloadItem(
                    link = cred.link,
                    id = -1,
                    folder = System.getProperty("user.dir"),
                    name = cred.suggestedName
                        ?: cred.link.substringAfterLast("/").substringBefore("?").ifEmpty {
                            "download_${System.currentTimeMillis()}"
                        },
                )
                val props = NewDownloadItemProps(
                    downloadItem = downloadItem,
                    onDuplicateStrategy = OnDuplicateStrategy.default(),
                    extraConfig = null,
                    context = EmptyContext,
                )
                val id = downloadService.addDownload(props)

                if (options.silentStart) {
                    downloadService.startDownload(id)
                }

                println("[integration] Added download #$id: ${cred.link}")
            } catch (e: Exception) {
                System.err.println("[integration] Failed to add ${cred.link}: ${e.message}")
            }
        }
    }

    override fun listQueues(): List<ApiQueueModel> {
        return queueManager.getAll().map { queue ->
            val model = queue.getQueueModel()
            ApiQueueModel(id = model.id, name = model.name)
        }
    }

    override suspend fun addDownloadTask(task: NewDownloadTask) {
        val cred = task.downloadSource
        val downloadItem = HttpDownloadItem(
            link = cred.link,
            id = -1,
            folder = task.folder ?: System.getProperty("user.dir"),
            name = task.name
                ?: cred.suggestedName
                ?: cred.link.substringAfterLast("/").substringBefore("?").ifEmpty {
                    "download_${System.currentTimeMillis()}"
                },
        )
        val props = NewDownloadItemProps(
            downloadItem = downloadItem,
            onDuplicateStrategy = OnDuplicateStrategy.default(),
            extraConfig = null,
            context = EmptyContext,
        )
        val id = downloadService.addDownload(props)

        if (task.queueId != null) {
            val qId = task.queueId!!
            downloadService.addToQueue(qId, id)
            queueManager.getQueue(qId).start()
        } else {
            downloadService.startDownload(id)
        }

        println("[integration] Added download task #$id: ${cred.link}")
    }
}
