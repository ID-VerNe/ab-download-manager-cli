package com.abdownloadmanager.cli.utils

/**
 * Shared formatting utilities for CLI output.
 *
 * Consolidates duplicate formatSize/formatSpeed/formatDuration functions
 * that previously appeared across 6+ command files.
 */
object CliFormatting {

    /**
     * Format a byte count into a human-readable string (B, KB, MB, GB).
     */
    fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1_073_741_824 -> "%.1f GB".format(bytes.toDouble() / 1_073_741_824)
            bytes >= 1_048_576 -> "%.1f MB".format(bytes.toDouble() / 1_048_576)
            bytes >= 1_024 -> "%.1f KB".format(bytes.toDouble() / 1_024)
            bytes < 0 -> "Unknown"
            else -> "$bytes B"
        }
    }

    /**
     * Format a byte count into a shorter human-readable string (B, K, M, G).
     * Used in monitor TUI where space is limited.
     */
    fun formatSizeCompact(bytes: Long): String {
        return when {
            bytes >= 1_073_741_824 -> "%.1fG".format(bytes.toDouble() / 1_073_741_824)
            bytes >= 1_048_576 -> "%.1fM".format(bytes.toDouble() / 1_048_576)
            bytes >= 1_024 -> "%.0fK".format(bytes.toDouble() / 1_024)
            else -> "$bytes B"
        }
    }

    /**
     * Format a speed in bytes/sec.
     */
    fun formatSpeed(bytes: Long): String {
        return when {
            bytes >= 1_073_741_824 -> "%.1f GB/s".format(bytes.toDouble() / 1_073_741_824)
            bytes >= 1_048_576 -> "%.1f MB/s".format(bytes.toDouble() / 1_048_576)
            bytes >= 1_024 -> "%.1f KB/s".format(bytes.toDouble() / 1_024)
            else -> "$bytes B/s"
        }
    }

    /**
     * Format a speed for the monitor TUI (compact layout).
     */
    fun formatSpeedCompact(bytes: Long): String {
        return when {
            bytes >= 1_073_741_824 -> "%.1fG/s".format(bytes.toDouble() / 1_073_741_824)
            bytes >= 1_048_576 -> "%.1fM/s".format(bytes.toDouble() / 1_048_576)
            bytes >= 1_024 -> "%.0fK/s".format(bytes.toDouble() / 1_024)
            bytes <= 0 -> "—"
            else -> "$bytes B/s"
        }
    }

    /**
     * Format a duration in ms to a human-readable string.
     */
    fun formatDuration(ms: Long): String {
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return when {
            h > 0 -> "${h}h${m}m${s}s"
            m > 0 -> "${m}m${s}s"
            else -> "${s}s"
        }
    }

    /**
     * Format a timestamp.
     */
    fun formatTimestamp(ts: Long): String {
        if (ts <= 0) return "N/A"
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = ts }
        return "%tF %tT".format(cal, cal)
    }
}
