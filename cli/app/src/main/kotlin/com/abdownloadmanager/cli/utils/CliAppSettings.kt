package com.abdownloadmanager.cli.utils

import ir.amirab.downloader.DownloadSettings
import kotlinx.serialization.json.*

/**
 * CLI-side app settings that read/write the shared appSettings.json file
 * used by both the desktop GUI and the CLI.
 *
 * The file format is a flat JSON object with primitive values (same as
 * the desktop's MapConfig-based DataStore). This minimal reader/writer
 * avoids pulling in AndroidX DataStore + Arrow Optics dependencies.
 *
 * Thread safety: all operations are synchronized on a reentrant lock.
 */
class CliAppSettings(
    val settingsFile: java.io.File,
) {
    private val lock = Any()
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    // --- Defaults (mirror desktop AppSettingsModel defaults) ---
    val defaultThreadCount: Int get() = 8
    val defaultMaxConcurrentDownloads: Int get() = 3
    val defaultMaxDownloadRetryCount: Int get() = 3
    val defaultDynamicPartCreation: Boolean get() = true
    val defaultUseServerLastModifiedTime: Boolean get() = false
    val defaultAppendExtensionToIncomplete: Boolean get() = false
    val defaultUseSparseFileAllocation: Boolean get() = true
    val defaultSpeedLimit: Long get() = 0L
    val defaultDownloadFolder: String
        get() = System.getProperty("user.dir")

    // --- Getters ---
    fun readThreadCount(): Int = getInt("threadCount", defaultThreadCount)
    fun readMaxConcurrentDownloads(): Int = getInt("maxConcurrentDownloads", defaultMaxConcurrentDownloads)
    fun readMaxDownloadRetryCount(): Int = getInt("maxDownloadRetryCount", defaultMaxDownloadRetryCount)
    fun readDynamicPartCreation(): Boolean = getBoolean("dynamicPartCreation", defaultDynamicPartCreation)
    fun readUseServerLastModifiedTime(): Boolean = getBoolean("useServerLastModifiedTime", defaultUseServerLastModifiedTime)
    fun readAppendExtensionToIncompleteDownloads(): Boolean = getBoolean("appendExtensionToIncompleteDownloads", defaultAppendExtensionToIncomplete)
    fun readUseSparseFileAllocation(): Boolean = getBoolean("useSparseFileAllocation", defaultUseSparseFileAllocation)
    fun readSpeedLimit(): Long = getLong("speedLimit", defaultSpeedLimit)
    fun readDefaultDownloadFolder(): String = getString("defaultDownloadFolder", defaultDownloadFolder)
    fun readUserAgent(): String = getString("userAgent", "")

    // --- Setters ---
    fun setThreadCount(value: Int) = setInt("threadCount", value)
    fun setMaxConcurrentDownloads(value: Int) = setInt("maxConcurrentDownloads", value)
    fun setMaxDownloadRetryCount(value: Int) = setInt("maxDownloadRetryCount", value)
    fun setDynamicPartCreation(value: Boolean) = setBoolean("dynamicPartCreation", value)
    fun setUseServerLastModifiedTime(value: Boolean) = setBoolean("useServerLastModifiedTime", value)
    fun setAppendExtensionToIncompleteDownloads(value: Boolean) = setBoolean("appendExtensionToIncompleteDownloads", value)
    fun setUseSparseFileAllocation(value: Boolean) = setBoolean("useSparseFileAllocation", value)
    fun setSpeedLimit(value: Long) = setLong("speedLimit", value)
    fun setDefaultDownloadFolder(value: String) = setString("defaultDownloadFolder", value)
    fun setUserAgent(value: String) = setString("userAgent", value)

    /**
     * Copy relevant settings to the DownloadSettings object.
     * This is called during daemon boot and after `config set`.
     */
    fun syncDownloadSettings(settings: DownloadSettings) {
        settings.defaultThreadCount = readThreadCount()
        settings.dynamicPartCreationMode = readDynamicPartCreation()
        settings.useServerLastModifiedTime = readUseServerLastModifiedTime()
        settings.appendExtensionToIncompleteDownloads = readAppendExtensionToIncompleteDownloads()
        settings.useSparseFileAllocation = readUseSparseFileAllocation()
        settings.maxDownloadRetryCount = readMaxDownloadRetryCount()
        settings.globalSpeedLimit = readSpeedLimit()
    }

    /** Get a copy of all settings as a map (for listing). */
    fun getAll(): Map<String, Any?> {
        synchronized(lock) {
            return readJson()
        }
    }

    // --- Raw access ---
    private fun getInt(key: String, default: Int): Int {
        synchronized(lock) {
            val root = readJson()
            return root[key]?.jsonPrimitive?.content?.toIntOrNull() ?: default
        }
    }

    private fun getLong(key: String, default: Long): Long {
        synchronized(lock) {
            val root = readJson()
            return root[key]?.jsonPrimitive?.content?.toLongOrNull() ?: default
        }
    }

    private fun getBoolean(key: String, default: Boolean): Boolean {
        synchronized(lock) {
            val root = readJson()
            return root[key]?.jsonPrimitive?.let { p ->
                when {
                    p.isString -> p.content.toBooleanStrictOrNull()
                    else -> p.content.toBooleanStrictOrNull()
                }
            } ?: default
        }
    }

    private fun getString(key: String, default: String): String {
        synchronized(lock) {
            val root = readJson()
            return root[key]?.jsonPrimitive?.content ?: default
        }
    }

    private fun setInt(key: String, value: Int) = writePrimitive(key, JsonPrimitive(value))
    private fun setLong(key: String, value: Long) = writePrimitive(key, JsonPrimitive(value))
    private fun setBoolean(key: String, value: Boolean) = writePrimitive(key, JsonPrimitive(value))
    private fun setString(key: String, value: String) = writePrimitive(key, JsonPrimitive(value))

    private fun writePrimitive(key: String, value: JsonPrimitive) {
        synchronized(lock) {
            val root = readJson().toMutableMap()
            root[key] = value
            writeJson(root)
        }
    }

    private fun readJson(): Map<String, JsonElement> {
        return try {
            if (settingsFile.exists()) {
                val text = settingsFile.readText().trim()
                if (text.isEmpty()) return emptyMap()
                json.decodeFromString<Map<String, JsonElement>>(text)
            } else {
                emptyMap()
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun writeJson(data: Map<String, JsonElement>) {
        settingsFile.parentFile.mkdirs()
        val output = json.encodeToString(JsonObject.serializer(), JsonObject(data))
        settingsFile.writeText(output)
    }
}