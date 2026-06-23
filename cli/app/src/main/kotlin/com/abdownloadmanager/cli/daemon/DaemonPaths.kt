package com.abdownloadmanager.cli.daemon

import java.io.File

/**
 * Manages daemon-related file paths (port file, lock file, etc.)
 * under the shared data directory.
 */
class DaemonPaths(dataDir: File) {
    val daemonDir: File = File(dataDir, "daemon").also { it.mkdirs() }
    val portFile: File = File(daemonDir, "daemon.port")
    val lockFile: File = File(daemonDir, "daemon.lock")
}
