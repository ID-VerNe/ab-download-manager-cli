package com.abdownloadmanager.cli.di

import com.abdownloadmanager.cli.daemon.CliIntegrationHandler
import com.abdownloadmanager.cli.utils.CliAppSettings
import com.abdownloadmanager.cli.utils.CliPaths
import com.abdownloadmanager.integration.IntegrationHandler
import ir.amirab.downloader.DownloadManager
import ir.amirab.downloader.DownloadManagerMinimalControl
import ir.amirab.downloader.DownloadSettings
import ir.amirab.downloader.DownloaderRegistry
import ir.amirab.downloader.connection.HttpDownloaderClient
import ir.amirab.downloader.connection.OkHttpHttpDownloaderClient
import ir.amirab.downloader.connection.UserAgentProvider
import ir.amirab.downloader.connection.proxy.AutoConfigurableProxyProvider
import ir.amirab.downloader.connection.proxy.NoopSystemProxySelectorProvider
import ir.amirab.downloader.connection.proxy.ProxyStrategy
import ir.amirab.downloader.connection.proxy.ProxyStrategyProvider
import ir.amirab.downloader.connection.proxy.SystemProxySelectorProvider
import ir.amirab.downloader.db.DownloadListFileStorage
import ir.amirab.downloader.db.DownloadQueueFileStorageDatabase
import ir.amirab.downloader.db.IDownloadListDb
import ir.amirab.downloader.db.IDownloadPartListDb
import ir.amirab.downloader.db.IDownloadQueueDatabase
import ir.amirab.downloader.db.PartListFileStorage
import ir.amirab.downloader.db.TransactionalFileSaver
import ir.amirab.downloader.downloaditem.IDownloadCredentials
import ir.amirab.downloader.downloaditem.IDownloadItem
import ir.amirab.downloader.downloaditem.hls.HLSDownloader
import ir.amirab.downloader.downloaditem.http.HttpDownloadCredentials
import ir.amirab.downloader.downloaditem.http.HttpDownloadItem
import ir.amirab.downloader.downloaditem.http.HttpDownloader
import ir.amirab.downloader.queue.ManualDownloadQueue
import ir.amirab.downloader.queue.QueueManager
import ir.amirab.downloader.utils.EmptyFileCreator
import ir.amirab.downloader.utils.IDiskStat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.internal.tls.OkHostnameVerifier
import org.koin.core.module.dsl.singleOf
import org.koin.core.context.startKoin
import org.koin.dsl.bind
import org.koin.dsl.module
import java.io.File
import javax.net.ssl.X509TrustManager

/**
 * Headless DI container for the CLI module.
 * Boots only the download engine (DownloadManager + QueueManager + DB) without Compose/GUI deps.
 */
object CliDi {
    private var booted = false

    fun boot(configDir: File, downloadDir: File) {
        if (booted) {
            // If Koin context was stopped externally (e.g., by tests), re-boot
            val koinContext = try {
                org.koin.core.context.GlobalContext.getOrNull()
            } catch (_: Exception) {
                null
            }
            if (koinContext != null) return
            booted = false
        }

        val paths = CliPaths(configDir)

        // Ensure directories exist
        paths.dataDir.mkdirs()

        startKoin {
            modules(cliModule(paths, configDir))
        }
        booted = true
    }
}

private fun cliModule(paths: CliPaths, dataDir: File) = module {
    // === Paths ===
    single { paths }

    // === Coroutine scope ===
    single {
        CoroutineScope(SupervisorJob())
    }

    // === JSON ===
    // Dynamically register serializers for all downloader types
    single {
        val downloaderRegistry: DownloaderRegistry = get()
        Json {
            encodeDefaults = true
            prettyPrint = false
            ignoreUnknownKeys = true
            serializersModule = SerializersModule {
                polymorphic(IDownloadItem::class) {
                    defaultDeserializer { HttpDownloadItem.serializer() }
                    downloaderRegistry.getAll().forEach {
                        subclass(it.downloadItemClass, it.downloadItemSerializer)
                    }
                }
                polymorphic(IDownloadCredentials::class) {
                    defaultDeserializer { HttpDownloadCredentials.serializer() }
                    downloaderRegistry.getAll().forEach {
                        subclass(it.downloadCredentialsClass, it.downloadCredentialsSerializer)
                    }
                }
            }
        }
    }

    // === Download Settings ===
    single {
        DownloadSettings(
            defaultThreadCount = 8,
            maxDownloadRetryCount = 3,
        )
    }

    // === TrustManager for SSL (system default — no trust-all) ===
    single<X509TrustManager> {
        val tmFactory = javax.net.ssl.TrustManagerFactory.getInstance(
            javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm()
        )
        tmFactory.init(null as java.security.KeyStore?)
        tmFactory.trustManagers[0] as X509TrustManager
    }

    // === OkHttpClient ===
    single<OkHttpClient> {
        val trustManager: X509TrustManager = get()
        val sslContext = javax.net.ssl.SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf(trustManager), null)

        OkHttpClient.Builder()
            .protocols(listOf(Protocol.HTTP_1_1))
            .dispatcher(Dispatcher().apply {
                maxRequests = Int.MAX_VALUE
                maxRequestsPerHost = Int.MAX_VALUE
            })
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .hostnameVerifier(OkHostnameVerifier)
            .build()
    }

    // === Proxy (no-op for CLI) ===
    single<ProxyStrategyProvider> {
        object : ProxyStrategyProvider {
            override fun getProxyStrategyFor(url: String): ProxyStrategy = ProxyStrategy.Direct
        }
    }

    single<AutoConfigurableProxyProvider> {
        AutoConfigurableProxyProvider.NoOp()
    }

    single<SystemProxySelectorProvider> {
        NoopSystemProxySelectorProvider()
    }

    // === User Agent ===
    single<UserAgentProvider> {
        object : UserAgentProvider {
            override fun getUserAgent(): String {
                val configured = get<CliAppSettings>().readUserAgent()
                return configured.ifEmpty { "ABDownloadManager-CLI/1.0" }
            }
        }
    }

    // === HTTP Client ===
    single<HttpDownloaderClient> {
        OkHttpHttpDownloaderClient(
            okHttpClient = get(),
            customUserAgentProvider = get(),
            proxyStrategyProvider = get(),
            systemProxySelectorProvider = get(),
            autoConfigurableProxyProvider = get(),
        )
    }

    // === Disk Stat ===
    single<IDiskStat> {
        object : IDiskStat {
            override fun getRemainingSpace(path: File): Long = path.freeSpace
        }
    }

    // === File Creator ===
    single {
        EmptyFileCreator(
            diskStat = get(),
            useSparseFile = { get<DownloadSettings>().useSparseFileAllocation }
        )
    }

    // === Transactional File Saver ===
    single { TransactionalFileSaver(get()) }

    // === DB Layers ===
    single<IDownloadQueueDatabase> {
        DownloadQueueFileStorageDatabase(
            queueFolder = paths.queuesDir,
            fileSaver = get(),
        )
    }

    single<IDownloadListDb> {
        DownloadListFileStorage(
            downloadListFolder = paths.downloadListDir,
            fileSaver = get(),
        )
    }

    single<IDownloadPartListDb> {
        PartListFileStorage(
            folder = paths.partsDir,
            fileSaver = get(),
        )
    }

    // === Downloaders ===
    single<HttpDownloader> { HttpDownloader(lazy { get<HttpDownloaderClient>() }) }
    single<HLSDownloader> { HLSDownloader(lazy { get<HttpDownloaderClient>() }) }

    // === Downloader Registry ===
    single {
        DownloaderRegistry().apply {
            add(get<HttpDownloader>())
            add(get<HLSDownloader>())
        }
    }

    // === Download Manager ===
    single {
        DownloadManager(
            dlListDb = get(),
            partListDb = get(),
            settings = get(),
            emptyFileCreator = get(),
            downloaderRegistry = get(),
            downloadDataFolder = paths.downloadDataDir,
        )
    }.bind(DownloadManagerMinimalControl::class)

    // === Queue Manager ===
    single {
        QueueManager(
            queueDb = get(),
            listOfJobs = get(),
        )
    }

    // === Manual Download Queue ===
    single {
        ManualDownloadQueue(
            downloadEvents = get(),
            scope = get(),
        )
    }

    // === CLI-specific Download Service ===
    single {
        CliDownloadService(get(), get(), get())
    }

    // === CLI App Settings (shared with desktop) ===
    single {
        CliAppSettings(paths.appSettingsFile)
    }

    // === Integration Handler (daemon endpoint bridge) ===
    single<IntegrationHandler> {
        CliIntegrationHandler(get(), get())
    }
}