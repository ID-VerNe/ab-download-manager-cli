package com.abdownloadmanager.cli.daemon

import com.abdownloadmanager.cli.utils.CliPaths
import java.io.File
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URI

/**
 * HTTP client for CLI commands to communicate with a running daemon.
 *
 * Discovers the daemon via its port file (written to dataDir/daemon/daemon.port),
 * then sends HTTP requests to localhost on that port.
 */
class DaemonClient(private val dataDir: File) {

    private val daemonDir: File get() = File(dataDir, "daemon")
    private val portFile: File get() = File(daemonDir, "daemon.port")

    companion object {
        /** Create a DaemonClient using the default data directory discovery. */
        fun withDefaultConfig(): DaemonClient {
            return DaemonClient(CliPaths.detectDataDir())
        }
    }

    /**
     * Read the daemon port from the port file.
     */
    private fun readPort(): Int? {
        if (!portFile.exists()) return null
        return try {
            portFile.readText().trim().toIntOrNull()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Send an HTTP POST with JSON body and return the response body.
     */
    private fun post(path: String, jsonBody: String, timeout: Int = 10000): String? {
        val port = readPort() ?: return null
        return try {
            val url = URI("http://localhost:$port$path").toURL()
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = timeout
            conn.readTimeout = timeout
            conn.setRequestProperty("Content-Type", "application/json")
            OutputStreamWriter(conn.outputStream).use { it.write(jsonBody) }
            if (conn.responseCode in 200..299) {
                conn.inputStream.bufferedReader().readText()
            } else {
                conn.errorStream?.bufferedReader()?.readText() ?: "null"
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Send an HTTP GET and return the response body.
     */
    private fun get(path: String, timeout: Int = 10000): String? {
        val port = readPort() ?: return null
        return try {
            val url = URI("http://localhost:$port$path").toURL()
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = timeout
            conn.readTimeout = timeout
            if (conn.responseCode in 200..299) {
                conn.inputStream.bufferedReader().readText()
            } else {
                conn.errorStream?.bufferedReader()?.readText() ?: "null"
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Check if the daemon is running by reading the port file
     * and making a /ping request.
     */
    fun isRunning(): Boolean {
        val port = readPort() ?: return false
        return try {
            val url = URI("http://localhost:$port/ping").toURL()
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 2000
            conn.readTimeout = 2000
            conn.responseCode == 200
        } catch (_: Exception) {
            false
        }
    }

    /** Send a shutdown signal to the daemon. */
    fun shutdown(): Boolean {
        return post("/api/shutdown", "{}") != null
    }

    /** Get daemon status (active/paused/completed counts). */
    fun getStatus(): String? = get("/api/status")

    /** List all downloads (returns JSON array). */
    fun listDownloads(): String? = get("/api/list")

    /** Get a single download by ID (returns JSON object). */
    fun getDownload(id: Long): String? = post("/api/list-item", """{"id":$id}""")

    /** Add a new download. Returns JSON response with the new ID. */
    fun addDownload(
        url: String,
        name: String? = null,
        folder: String? = null,
        username: String? = null,
        password: String? = null,
        connections: Int? = null,
        speedLimit: Long? = null,
        duplicate: String? = null,
        start: Boolean? = null,
        queueId: Long? = null,
    ): String? {
        val body = buildJsonObject {
            put("url", url)
            name?.let { put("name", it) }
            folder?.let { put("folder", it) }
            username?.let { put("username", it) }
            password?.let { put("password", it) }
            connections?.let { put("connections", it) }
            speedLimit?.let { put("speedLimit", it) }
            duplicate?.let { put("duplicate", it) }
            start?.let { put("start", it) }
            queueId?.let { put("queueId", it) }
        }
        return post("/api/add", body)
    }

    /** Pause one or more downloads by ID. */
    fun pauseDownload(ids: List<Long>): String? = post("/api/pause", """{"ids":${jsonArray(ids)}}""")

    /** Resume one or more downloads by ID. */
    fun resumeDownload(ids: List<Long>): String? = post("/api/resume", """{"ids":${jsonArray(ids)}}""")

    /** Remove one or more downloads by ID. */
    fun removeDownload(ids: List<Long>, keepFile: Boolean? = null): String? {
        val keep = keepFile?.let { ""","keepFile":$it""" } ?: ""
        return post("/api/remove", """{"ids":${jsonArray(ids)}$keep}""")
    }

    /** Restart one or more downloads by ID. */
    fun restartDownload(ids: List<Long>): String? = post("/api/restart", """{"ids":${jsonArray(ids)}}""")

    /** Pause all active downloads. */
    fun pauseAll(): String? = post("/api/pause-all", "{}")

    /** List all queues. */
    fun listQueues(): String? = get("/api/queue")

    /** Start a queue by ID. */
    fun startQueue(queueId: Long): String? = post("/api/queue/start", """{"queueId":$queueId}""")

    /** Get all config from daemon (live reload). */
    fun getConfig(): String? = get("/api/config")

    /** Set a config key on the daemon (live reload). */
    fun setConfig(key: String, value: String): String? =
        post("/api/config/set", """{"key":"$key","value":"$value"}""")

    /** Open a download file on the daemon machine. */
    fun openDownload(id: Long): String? = post("/api/open", """{"id":$id}""")

    /** Open the folder containing a download file on the daemon machine. */
    fun openDownloadFolder(id: Long): String? = post("/api/open-folder", """{"id":$id}""")

    /** Set checksum for a download. */
    fun setChecksum(id: Long, checksum: String): String? =
        post("/api/checksum/set", """{"id":$id,"checksum":"${checksum.replace("\"", "\\\"")}"}""")

    /** Edit download properties on the daemon. */
    fun editDownload(id: Long, name: String? = null, speedLimit: Long? = null, connections: Int? = null): String? {
        val parts = mutableListOf(""""id":$id""")
        name?.let { parts.add(""""name":"${it.replace("\"", "\\\"")}"""") }
        speedLimit?.let { parts.add(""""speedLimit":$it""") }
        connections?.let { parts.add(""""connections":$it""") }
        return post("/api/edit", parts.joinToString(",", "{", "}"))
    }

    // --- JSON helpers ---

    private fun jsonArray(ids: List<Long>): String = ids.joinToString(",", "[", "]")

    /** Minimal JSON object builder for client-side requests */
    private fun buildJsonObject(block: MutableMap<String, Any?>.() -> Unit): String {
        val map = mutableMapOf<String, Any?>()
        map.block()
        return map.entries.joinToString(",", "{", "}") { (k, v) ->
            "\"$k\":${valueToJson(v)}"
        }
    }

    private fun valueToJson(v: Any?): String = when (v) {
        null -> "null"
        is String -> "\"${v.replace("\"", "\\\"")}\""
        is Boolean -> v.toString()
        is Number -> v.toString()
        else -> "\"$v\""
    }
}