package com.abdownloadmanager.cli.daemon

import com.abdownloadmanager.cli.di.CliDownloadService
import com.abdownloadmanager.cli.utils.CliAppSettings
import com.abdownloadmanager.integration.HandlerMap
import com.abdownloadmanager.integration.MyResponse
import ir.amirab.downloader.DownloadSettings
import ir.amirab.downloader.NewDownloadItemProps
import ir.amirab.downloader.downloaditem.DownloadStatus
import ir.amirab.downloader.downloaditem.EmptyContext
import ir.amirab.downloader.downloaditem.IDownloadItem
import ir.amirab.downloader.downloaditem.DownloadJobExtraConfig
import ir.amirab.downloader.downloaditem.http.HttpDownloadItem
import ir.amirab.downloader.utils.OnDuplicateStrategy
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * HTTP API routes for the daemon process.
 *
 * All responses use a unified JSON envelope:
 *   {"success": true, "data": ..., "error": null}
 *
 * These routes are registered on the daemon's http4k/NanoHTTPD server
 * and are consumed by CLI commands (via DaemonClient), external scripts,
 * or the GUI.
 */
object DaemonApi {

    private val json = Json {
        encodeDefaults = true
        prettyPrint = false
        ignoreUnknownKeys = true
    }

    /**
     * Create the HandlerMap with all daemon API routes.
     *
     * @param downloadService The CliDownloadService for engine operations.
     * @param appSettings The CliAppSettings for reading/writing config (live reload).
     * @param downloadSettings The DownloadSettings to hot-reload on /api/config/set.
     * @param onShutdown Called when the shutdown endpoint receives a request.
     */
    fun createHandlerMap(
        downloadService: CliDownloadService? = null,
        appSettings: CliAppSettings? = null,
        downloadSettings: DownloadSettings? = null,
        onShutdown: () -> Unit = {},
    ): HandlerMap {
        val svc = downloadService

        return HandlerMap().apply {
            // --- Health check ---
            get("/ping") {
                MyResponse.Text("pong")
            }

            // --- Daemon status + stats ---
            get("/api/status") {
                if (svc == null) return@get jsonError("Daemon not fully initialized")
                val items = svc.getAllItems()
                val activeCount = items.count { it.status == DownloadStatus.Downloading }
                val pausedCount = items.count { it.status == DownloadStatus.Paused }
                val completedCount = items.count { it.status == DownloadStatus.Completed }
                val errorCount = items.count { it.status == DownloadStatus.Error }
                val total = items.size
                MyResponse.Json(jsonSuccess(mapOf(
                    "total" to total,
                    "active" to activeCount,
                    "paused" to pausedCount,
                    "completed" to completedCount,
                    "errored" to errorCount,
                )))
            }

            // --- List downloads ---
            get("/api/list") {
                if (svc == null) return@get jsonError("Daemon not fully initialized")
                // Support ?all=true query via the URI — note: HandlerMap uses exact URI match,
                // so we inspect the raw request body or use a separate route if needed.
                // For now, we always return all items. The CLI client sends the right query.
                val items = svc.getAllItems()
                val jsonList = items.map { it.toItemJson(svc) }
                val jsonStr = json.encodeToString(jsonList)
                MyResponse.Json(jsonStr)
            }

            // --- List single download (via ?id=X in URI) ---
            // HandlerMap does NOT support path params (/api/list/:id).
            // We register a separate /api/list-item route that accepts ?id= via body.
            // The DaemonClient sends POST /api/list-item with {"id": N}.
            post("/api/list-item") {
                if (svc == null) return@post jsonError("Daemon not fully initialized")
                val body = it.getBody().orEmpty()
                val id = runCatching {
                    json.decodeFromString<IdRequest>(body).id
                }.getOrElse { return@post jsonError("Invalid request body") }
                val item = runBlocking { svc.getItem(id) }
                if (item == null) return@post jsonError("Download #$id not found")
                val jsonStr = json.encodeToString(item.toItemJson())
                MyResponse.Json(jsonStr)
            }

            // --- Add download ---
            post("/api/add") {
                if (svc == null) return@post jsonError("Daemon not fully initialized")
                val body = it.getBody().orEmpty()
                val req = runCatching {
                    json.decodeFromString<AddRequest>(body)
                }.getOrElse { error ->
                    return@post jsonError("Invalid request body: ${error.message}")
                }
                val strategy = when (req.duplicate?.lowercase()) {
                    "override" -> OnDuplicateStrategy.OverrideDownload
                    "add-numbered" -> OnDuplicateStrategy.AddNumbered
                    else -> OnDuplicateStrategy.Abort
                }
                val downloadItem = HttpDownloadItem(
                    link = req.url,
                    id = -1,
                    folder = req.folder ?: System.getProperty("user.dir"),
                    name = req.name ?: req.url.substringAfterLast("/").substringBefore("?").ifEmpty {
                        "download_${System.currentTimeMillis()}"
                    },
                    username = req.username,
                    password = req.password,
                    preferredConnectionCount = req.connections,
                    speedLimit = req.speedLimit ?: 0,
                )
                val props = NewDownloadItemProps(
                    downloadItem = downloadItem,
                    extraConfig = null,
                    onDuplicateStrategy = strategy,
                    context = EmptyContext,
                )
                try {
                    val id = runBlocking { svc.addDownload(props) }

                    // If queue specified, add to queue
                    if (req.queueId != null) {
                        runBlocking { svc.addToQueue(req.queueId, id) }
                    }

                    // Auto-start if requested
                    if (req.start == true) {
                        runBlocking { svc.startDownload(id) }
                    }

                    MyResponse.Json(jsonSuccess(mapOf("id" to id)))
                } catch (e: Exception) {
                    jsonError("Failed to add download: ${e.message}")
                }
            }

            // --- Pause download(s) ---
            post("/api/pause") {
                if (svc == null) return@post jsonError("Daemon not fully initialized")
                val body = it.getBody().orEmpty()
                val ids = runCatching {
                    json.decodeFromString<IdsRequest>(body).ids
                }.getOrElse { return@post jsonError("Invalid request body") }
                runBlocking {
                    ids.forEach { id ->
                        val item = svc.getItem(id)
                        if (item != null) svc.pauseDownload(id)
                    }
                }
                MyResponse.Text(jsonSuccess("Paused ${ids.size} download(s)"))
            }

            // --- Resume download(s) ---
            post("/api/resume") {
                if (svc == null) return@post jsonError("Daemon not fully initialized")
                val body = it.getBody().orEmpty()
                val ids = runCatching {
                    json.decodeFromString<IdsRequest>(body).ids
                }.getOrElse { return@post jsonError("Invalid request body") }
                runBlocking {
                    ids.forEach { id ->
                        val item = svc.getItem(id)
                        if (item != null) svc.startDownload(id)
                    }
                }
                MyResponse.Text(jsonSuccess("Resumed ${ids.size} download(s)"))
            }

            // --- Remove download(s) ---
            post("/api/remove") {
                if (svc == null) return@post jsonError("Daemon not fully initialized")
                val body = it.getBody().orEmpty()
                val req = runCatching {
                    json.decodeFromString<RemoveRequest>(body)
                }.getOrElse { return@post jsonError("Invalid request body") }
                runBlocking {
                    req.ids.forEach { id ->
                        val item = svc.getItem(id)
                        if (item != null) svc.removeDownload(id, alsoRemoveFile = req.keepFile != true)
                    }
                }
                MyResponse.Text(jsonSuccess("Removed ${req.ids.size} download(s)"))
            }

            // --- Restart download(s) ---
            post("/api/restart") {
                if (svc == null) return@post jsonError("Daemon not fully initialized")
                val body = it.getBody().orEmpty()
                val ids = runCatching {
                    json.decodeFromString<IdsRequest>(body).ids
                }.getOrElse { return@post jsonError("Invalid request body") }
                runBlocking {
                    ids.forEach { id -> svc.restartDownload(id) }
                }
                MyResponse.Text(jsonSuccess("Restarted ${ids.size} download(s)"))
            }

            // --- Pause all ---
            post("/api/pause-all") {
                if (svc == null) return@post jsonError("Daemon not fully initialized")
                runBlocking { svc.pauseAll() }
                MyResponse.Text(jsonSuccess("Paused all downloads"))
            }

            // --- List all queues ---
            get("/api/queue") {
                if (svc == null) return@get jsonError("Daemon not fully initialized")
                val queues = svc.getQueues()
                val jsonList = queues.map { q ->
                    val model = q.getQueueModel()
                    mapOf(
                        "id" to model.id,
                        "name" to model.name,
                        "queueItems" to model.queueItems,
                        "isQueueActive" to q.isQueueActive,
                    )
                }
                val jsonStr = jsonList.joinToString(",", "[", "]") { entry ->
                    """{"id":${entry["id"]},"name":"${entry["name"]}","queueItems":${anyToJson(entry["queueItems"])},"isQueueActive":${entry["isQueueActive"]}}"""
                }
                MyResponse.Json(jsonStr)
            }

            // --- Start a queue ---
            post("/api/queue/start") {
                if (svc == null) return@post jsonError("Daemon not fully initialized")
                val body = it.getBody().orEmpty()
                val queueId = runCatching {
                    json.decodeFromString<QueueIdRequest>(body).queueId
                }.getOrElse { return@post jsonError("Invalid request body") }
                svc.startQueue(queueId)
                MyResponse.Text(jsonSuccess("Queue #$queueId started"))
            }

            // --- Shutdown ---
            post("/api/shutdown") {
                Thread {
                    try { Thread.sleep(200) } catch (_: InterruptedException) {}
                    onShutdown()
                }.start()
                MyResponse.Text(jsonSuccess("Daemon shutting down"))
            }

            // --- Open download file ---
            post("/api/open") {
                if (svc == null) return@post jsonError("Daemon not fully initialized")
                val body = it.getBody().orEmpty()
                val req = runCatching {
                    json.decodeFromString<IdRequest>(body)
                }.getOrElse { return@post jsonError("Invalid request body") }
                val item = runBlocking { svc.getItem(req.id) }
                if (item == null) return@post jsonError("Download #${req.id} not found")
                val file = java.io.File(item.folder, item.name)
                if (!file.exists()) return@post jsonError("File not found: ${file.absolutePath}")
                try {
                    java.awt.Desktop.getDesktop().open(file)
                    MyResponse.Text(jsonSuccess("Opened ${file.name}"))
                } catch (e: Exception) {
                    jsonError("Cannot open file: ${e.message}")
                }
            }

            // --- Open download folder ---
            post("/api/open-folder") {
                if (svc == null) return@post jsonError("Daemon not fully initialized")
                val body = it.getBody().orEmpty()
                val req = runCatching {
                    json.decodeFromString<IdRequest>(body)
                }.getOrElse { return@post jsonError("Invalid request body") }
                val item = runBlocking { svc.getItem(req.id) }
                if (item == null) return@post jsonError("Download #${req.id} not found")
                val folder = java.io.File(item.folder)
                if (!folder.exists()) return@post jsonError("Folder not found: ${folder.absolutePath}")
                try {
                    java.awt.Desktop.getDesktop().open(folder)
                    MyResponse.Text(jsonSuccess("Opened folder ${folder.absolutePath}"))
                } catch (e: Exception) {
                    jsonError("Cannot open folder: ${e.message}")
                }
            }

            // --- Set checksum ---
            post("/api/checksum/set") {
                if (svc == null) return@post jsonError("Daemon not fully initialized")
                val body = it.getBody().orEmpty()
                val req = runCatching {
                    json.decodeFromString<ChecksumRequest>(body)
                }.getOrElse { return@post jsonError("Invalid request body") }
                val item = runBlocking { svc.getItem(req.id) }
                if (item == null) return@post jsonError("Download #${req.id} not found")
                runBlocking {
                    svc.updateDownloadItem(req.id) { it.fileChecksum = req.checksum }
                }
                MyResponse.Text(jsonSuccess("Checksum set for #${req.id}"))
            }

            // --- Edit download properties ---
            post("/api/edit") {
                if (svc == null) return@post jsonError("Daemon not fully initialized")
                val body = it.getBody().orEmpty()
                val req = runCatching {
                    json.decodeFromString<EditRequest>(body)
                }.getOrElse { return@post jsonError("Invalid request body") }
                val item = runBlocking { svc.getItem(req.id) }
                if (item == null) return@post jsonError("Download #${req.id} not found")
                runBlocking {
                    svc.updateDownloadItem(req.id) { dl ->
                        req.name?.let { dl.name = it }
                        req.speedLimit?.let { dl.speedLimit = it }
                        req.connections?.let { dl.preferredConnectionCount = it }
                    }
                }
                MyResponse.Text(jsonSuccess("Updated download #${req.id}"))
            }

            // --- Get all config ---
            get("/api/config") {
                if (appSettings == null) return@get jsonError("Config not available")
                @Suppress("UNCHECKED_CAST")
                val all = appSettings.getAll() as Map<String, kotlinx.serialization.json.JsonElement>
                val jsonStr = json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), kotlinx.serialization.json.JsonObject(all))
                MyResponse.Json("""{"success":true,"data":$jsonStr,"error":null}""")
            }

            // --- Set a config key ---
            post("/api/config/set") {
                if (appSettings == null || downloadSettings == null) {
                    return@post jsonError("Config not available")
                }
                val body = it.getBody().orEmpty()
                val req = runCatching {
                    json.decodeFromString<ConfigSetRequest>(body)
                }.getOrElse { return@post jsonError("Invalid request body") }

                val result = applyConfig(appSettings, downloadSettings, req.key, req.value)
                when (result) {
                    ConfigResult.Success -> {
                        appSettings.syncDownloadSettings(downloadSettings)
                        MyResponse.Text(jsonSuccess("${req.key} = ${req.value}"))
                    }
                    ConfigResult.UnknownKey -> jsonError("Unknown config key: ${req.key}")
                    ConfigResult.InvalidValue -> jsonError("Invalid value for ${req.key}: ${req.value}")
                }
            }
        }
    }

    /**
     * Apply a single config change. Supports all known keys.
     * @return ApplyResult indicating success, unknown key, or invalid value.
     */
    private fun applyConfig(
        appSettings: CliAppSettings,
        downloadSettings: DownloadSettings,
        key: String,
        value: String,
    ): ConfigResult {
        return when (key) {
            "threadCount" -> {
                val v = value.toIntOrNull()
                if (v == null) return ConfigResult.InvalidValue
                appSettings.setThreadCount(v)
                downloadSettings.defaultThreadCount = v
                ConfigResult.Success
            }
            "maxConcurrentDownloads" -> {
                val v = value.toIntOrNull()
                if (v == null) return ConfigResult.InvalidValue
                appSettings.setMaxConcurrentDownloads(v)
                ConfigResult.Success
            }
            "maxDownloadRetryCount" -> {
                val v = value.toIntOrNull()
                if (v == null) return ConfigResult.InvalidValue
                appSettings.setMaxDownloadRetryCount(v)
                downloadSettings.maxDownloadRetryCount = v
                ConfigResult.Success
            }
            "dynamicPartCreation" -> {
                val v = value.toBooleanStrictOrNull()
                if (v == null) return ConfigResult.InvalidValue
                appSettings.setDynamicPartCreation(v)
                downloadSettings.dynamicPartCreationMode = v
                ConfigResult.Success
            }
            "useServerLastModifiedTime" -> {
                val v = value.toBooleanStrictOrNull()
                if (v == null) return ConfigResult.InvalidValue
                appSettings.setUseServerLastModifiedTime(v)
                downloadSettings.useServerLastModifiedTime = v
                ConfigResult.Success
            }
            "appendExtensionToIncompleteDownloads" -> {
                val v = value.toBooleanStrictOrNull()
                if (v == null) return ConfigResult.InvalidValue
                appSettings.setAppendExtensionToIncompleteDownloads(v)
                downloadSettings.appendExtensionToIncompleteDownloads = v
                ConfigResult.Success
            }
            "useSparseFileAllocation" -> {
                val v = value.toBooleanStrictOrNull()
                if (v == null) return ConfigResult.InvalidValue
                appSettings.setUseSparseFileAllocation(v)
                downloadSettings.useSparseFileAllocation = v
                ConfigResult.Success
            }
            "speedLimit" -> {
                val v = value.toLongOrNull()
                if (v == null) return ConfigResult.InvalidValue
                appSettings.setSpeedLimit(v)
                downloadSettings.globalSpeedLimit = v
                ConfigResult.Success
            }
            "defaultDownloadFolder" -> {
                appSettings.setDefaultDownloadFolder(value)
                ConfigResult.Success
            }
            "userAgent" -> {
                appSettings.setUserAgent(value)
                ConfigResult.Success
            }
            else -> ConfigResult.UnknownKey
        }
    }

    private enum class ConfigResult { Success, UnknownKey, InvalidValue }

    // --- Request schemas ---

    @Serializable
    data class IdRequest(val id: Long)

    @Serializable
    data class QueueIdRequest(val queueId: Long)

    @Serializable
    data class ConfigSetRequest(val key: String, val value: String)

    @Serializable
    data class IdsRequest(val ids: List<Long>)

    @Serializable
    data class ChecksumRequest(val id: Long, val checksum: String)

    @Serializable
    data class EditRequest(
        val id: Long,
        val name: String? = null,
        val speedLimit: Long? = null,
        val connections: Int? = null,
    )

    @Serializable
    data class RemoveRequest(val ids: List<Long>, val keepFile: Boolean? = null)

    @Serializable
    data class AddRequest(
        val url: String,
        val name: String? = null,
        val folder: String? = null,
        val username: String? = null,
        val password: String? = null,
        val connections: Int? = null,
        val speedLimit: Long? = null,
        val duplicate: String? = null,
        val start: Boolean? = null,
        val queueId: Long? = null,
    )

    @Serializable
    data class ItemJson(
        val id: Long,
        val name: String,
        val url: String?,
        val folder: String?,
        val status: String,
        val size: Long,
        val sizeFormatted: String,
        val downloaded: Long,
        val dateAdded: Long,
        val startTime: Long?,
        val completeTime: Long?,
        val connections: Int?,
        val speedLimit: Long,
        val checksum: String?,
    )

    // --- JSON helpers ---

    private fun jsonSuccess(data: Map<String, Any?>): String {
        val entries = data.entries.joinToString(",", "{", "}") { (k, v) ->
            "\"$k\":${anyToJson(v)}"
        }
        return """{"success":true,"data":$entries,"error":null}"""
    }

    private fun jsonSuccess(msg: String): String {
        return """{"success":true,"data":"$msg","error":null}"""
    }

    private fun anyToJson(v: Any?): String = when (v) {
        null -> "null"
        is String -> "\"${v.replace("\"", "\\\"")}\""
        is Boolean -> v.toString()
        is Number -> v.toString()
        is List<*> -> v.joinToString(",", "[", "]") { anyToJson(it) }
        is Map<*, *> -> v.entries.joinToString(",", "{", "}") { (k, v) -> "\"$k\":${anyToJson(v)}" }
        else -> "\"$v\""
    }

    private fun jsonError(message: String): MyResponse {
        val escaped = message.replace("\"", "\\\"").replace("\n", "\\n")
        return MyResponse.Text(
            """{"success":false,"data":null,"error":"$escaped"}""",
            statusCode = 400,
        )
    }

    private fun IDownloadItem.toItemJson(svc: CliDownloadService? = null): ItemJson {
        val downloaded = if (svc != null) {
            runCatching { runBlocking { svc.getDownloadedSize(id) } }.getOrDefault(contentLength.coerceAtLeast(0))
        } else {
            contentLength.coerceAtLeast(0)
        }
        return ItemJson(
            id = id,
            name = name,
            url = link,
            folder = folder,
            status = status.name,
            size = contentLength,
            sizeFormatted = formatSize(contentLength),
            downloaded = downloaded,
            dateAdded = dateAdded,
            startTime = startTime,
            completeTime = completeTime,
            connections = preferredConnectionCount,
            speedLimit = speedLimit,
            checksum = fileChecksum,
        )
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1_073_741_824 -> "%.1f GB".format(bytes.toDouble() / 1_073_741_824)
            bytes >= 1_048_576 -> "%.1f MB".format(bytes.toDouble() / 1_048_576)
            bytes >= 1_024 -> "%.1f KB".format(bytes.toDouble() / 1_024)
            bytes < 0 -> "Unknown"
            else -> "$bytes B"
        }
    }

    /** Simple list serializer helper */
    private object ListSerializer {
        fun <T> serializer(elementSerializer: kotlinx.serialization.KSerializer<T>) =
            kotlinx.serialization.builtins.ListSerializer(elementSerializer)
    }
}