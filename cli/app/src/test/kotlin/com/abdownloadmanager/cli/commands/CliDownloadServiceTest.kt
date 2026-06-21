package com.abdownloadmanager.cli.commands

import com.abdownloadmanager.cli.di.CliDi
import com.abdownloadmanager.cli.di.CliDownloadService
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.koin.core.context.stopKoin
import java.io.File
import kotlin.test.*

class CliDownloadServiceTest {

    private lateinit var downloadService: CliDownloadService
    private lateinit var tempDir: File

    @Before
    fun setup() {
        tempDir = java.nio.file.Files.createTempDirectory("abdm-cli-test").toFile()
        tempDir.deleteOnExit()
        try { stopKoin() } catch (_: Exception) {}
        CliDi.boot(tempDir, tempDir)
        // Reset CliDi booted flag so subsequent tests can re-boot
        downloadService = CliDownloadService(
            downloadManager = org.koin.core.context.GlobalContext.get().get(),
            queueManager = org.koin.core.context.GlobalContext.get().get(),
            paths = com.abdownloadmanager.cli.utils.CliPaths(tempDir),
        )
        downloadService.boot()
    }

    @After
    fun teardown() {
        try { stopKoin() } catch (_: Exception) {}
        tempDir.deleteRecursively()
    }

    @Test
    fun `test boot initializes without error`() {
        assertTrue(downloadService.getAllItems().isEmpty(), "Should have no downloads initially")
    }

    @Test
    fun `test add download creates new entry`() = runBlocking {
        val downloadItem = ir.amirab.downloader.downloaditem.http.HttpDownloadItem(
            link = "https://example.com/file.zip",
            id = -1,
            folder = tempDir.absolutePath,
            name = "test-file.zip",
        )
        val props = ir.amirab.downloader.NewDownloadItemProps(
            downloadItem = downloadItem,
            extraConfig = null,
            onDuplicateStrategy = ir.amirab.downloader.utils.OnDuplicateStrategy.AddNumbered,
            context = ir.amirab.downloader.downloaditem.EmptyContext,
        )
        val id = downloadService.addDownload(props)
        assertTrue(id >= 0, "Download ID should be non-negative")
        assertEquals("test-file.zip", downloadService.getItem(id)?.name)
    }

    @Test
    fun `test list items returns all downloads`() = runBlocking {
        assertTrue(downloadService.getAllItems().isEmpty(), "Should be empty initially")

        val item = ir.amirab.downloader.downloaditem.http.HttpDownloadItem(
            link = "https://example.com/file1.zip",
            id = -1,
            folder = tempDir.absolutePath,
            name = "file1.zip",
        )
        downloadService.addDownload(ir.amirab.downloader.NewDownloadItemProps(
            downloadItem = item,
            extraConfig = null,
            onDuplicateStrategy = ir.amirab.downloader.utils.OnDuplicateStrategy.AddNumbered,
            context = ir.amirab.downloader.downloaditem.EmptyContext,
        ))

        assertEquals(1, downloadService.getAllItems().size)
        assertEquals("file1.zip", downloadService.getAllItems().first().name)
    }
}