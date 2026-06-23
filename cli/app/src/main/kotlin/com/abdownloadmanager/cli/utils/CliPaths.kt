package com.abdownloadmanager.cli.utils

import java.io.File

/**
 * CLI-specific paths for the download manager data directory.
 *
 * When installed alongside the desktop app, uses the desktop's
 * data directory so both versions share the same download list.
 * When run standalone, uses ~/.abdm-cli as before.
 */
class CliPaths(
    configDir: File
) {
    val dataDir: File = configDir

    /** Config subdirectory */
    val configDirFile: File = ensureDir(File(dataDir, "config"))

    /** System data directory */
    val systemDir: File = ensureDir(File(dataDir, "system"))

    /** Download database */
    val downloadDbDir: File = ensureDir(File(dataDir, "download_db"))
    val downloadListDir: File = ensureDir(File(downloadDbDir, "downloadlist"))
    val partsDir: File = ensureDir(File(downloadDbDir, "parts"))
    val queuesDir: File = ensureDir(File(downloadDbDir, "queues"))

    /** Download data (partial files, etc.) */
    val downloadDataDir: File = ensureDir(File(dataDir, "download_data"))

    /** Categories */
    val categoriesFile: File = File(downloadDbDir, "categories/categories.json")

    /** App settings (shared with desktop) */
    val appSettingsFile: File = File(configDirFile, "appSettings.json")

    companion object {
        /**
         * Detect the desktop app's data directory.
         * Priority:
         * 1. Desktop app's known path (%LOCALAPPDATA%/AB Download Manager)
         * 2. Default CLI path (~/.abdm-cli)
         */
        fun detectDataDir(): File {
            // When running from the desktop install dir, share its config
            val desktopDataDir = findDesktopDataDir()
            if (desktopDataDir != null) {
                return desktopDataDir
            }
            // Fall back to CLI default
            return File(System.getProperty("user.home"), ".abdm-cli")
        }

        /**
         * Try to find an existing desktop ABDM installation.
         * Looks for the common install locations.
         */
        private fun findDesktopDataDir(): File? {
            val localAppData = System.getenv("LOCALAPPDATA") ?: ""
            val candidates = listOf(
                // Desktop app default install path (NSIS installer: $LOCALAPPDATA\ABDownloadManager)
                File(localAppData, "ABDownloadManager"),
                // Desktop app data dir (Compose native distribution uses prettified name)
                File(localAppData, "AB Download Manager"),
                // Also check user home
                File(System.getProperty("user.home"), "AB Download Manager"),
                // Development build location
                File("desktop/app/build/compose/binaries/main-release/app/AB Download Manager").absoluteFile,
            )
            return candidates.firstOrNull { it.isDirectory }
        }

        fun ensureDir(dir: File): File {
            dir.mkdirs()
            return dir
        }
    }
}
