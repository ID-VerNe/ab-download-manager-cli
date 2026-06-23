package com.abdownloadmanager.cli.commands

import com.abdownloadmanager.cli.daemon.DaemonHelper
import com.abdownloadmanager.cli.utils.CliAppSettings
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.default
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.terminal.Terminal
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Configuration management command.
 *
 * Reads/writes the shared appSettings.json file used by both
 * the desktop GUI and the CLI daemon.
 *
 * Subcommands:
 *   config get <key>    - Get a setting value
 *   config set <key> <value> - Set a setting value
 *   config list         - List all settings
 */
class ConfigCommand : CliktCommand(
    name = "config",
    help = "Manage application settings (read/write shared config)"
) {
    override fun run() {
        // Parent command — subcommands handle actions
    }
}

/** Get a single setting by key */
class ConfigGetCommand : CliktCommand(
    name = "get",
    help = "Get a setting value"
), KoinComponent {
    private val appSettings: CliAppSettings by inject()

    private val key: String by argument(help = "Setting key (e.g. threadCount, maxConcurrentDownloads)")

    override fun run() {
        val term = Terminal()
        val all = appSettings.getAll()
        val value = all[key]
        if (value == null) {
            term.println((TextColors.yellow)("Unknown key: $key"))
            return
        }
        term.println("$key = $value")
    }
}

/** Set a single setting by key and value */
class ConfigSetCommand : CliktCommand(
    name = "set",
    help = "Set a setting value"
), KoinComponent {
    private val appSettings: CliAppSettings by inject()

    private val key: String by argument(help = "Setting key")
    private val value: String by argument(help = "Setting value")

    override fun run() {
        val term = Terminal()

        // Try daemon mode first — live reload via HTTP
        val daemonClient = DaemonHelper.daemonClient()
        if (daemonClient != null) {
            runWithDaemon(term, daemonClient)
            return
        }

        // Direct mode: write to file
        runDirect(term)
    }

    private fun runWithDaemon(term: Terminal, daemonClient: com.abdownloadmanager.cli.daemon.DaemonClient) {
        val response = daemonClient.setConfig(key, value)
        if (DaemonHelper.isSuccess(response)) {
            term.println((TextColors.green)("$key = $value (via daemon)"))
        } else {
            term.println((TextColors.red)("Failed: ${DaemonHelper.getError(response) ?: "Unknown error"}"))
        }
    }

    private fun runDirect(term: Terminal) {
        val result = try {
            when (key) {
                "threadCount" -> appSettings.setThreadCount(value.toInt())
                "maxConcurrentDownloads" -> appSettings.setMaxConcurrentDownloads(value.toInt())
                "maxDownloadRetryCount" -> appSettings.setMaxDownloadRetryCount(value.toInt())
                "dynamicPartCreation" -> appSettings.setDynamicPartCreation(value.toBoolean())
                "useServerLastModifiedTime" -> appSettings.setUseServerLastModifiedTime(value.toBoolean())
                "appendExtensionToIncompleteDownloads" -> appSettings.setAppendExtensionToIncompleteDownloads(value.toBoolean())
                "useSparseFileAllocation" -> appSettings.setUseSparseFileAllocation(value.toBoolean())
                "speedLimit" -> appSettings.setSpeedLimit(value.toLong())
                "defaultDownloadFolder" -> appSettings.setDefaultDownloadFolder(value)
                "userAgent" -> appSettings.setUserAgent(value)
                else -> {
                    term.println((TextColors.yellow)("Unknown or unsupported key: $key"))
                    term.println("Supported keys: threadCount, maxConcurrentDownloads, maxDownloadRetryCount, dynamicPartCreation, useServerLastModifiedTime, appendExtensionToIncompleteDownloads, useSparseFileAllocation, speedLimit, defaultDownloadFolder, userAgent")
                    return
                }
            }
            true
        } catch (e: Exception) {
            term.println((TextColors.red)("Error: ${e.message}"))
            false
        }

        if (result) {
            term.println((TextColors.green)("$key = $value"))
            term.println((TextColors.blue)("Note: start the daemon (abdm daemon start) for changes to take effect"))
        }
    }
}

/** List all settings */
class ConfigListCommand : CliktCommand(
    name = "list",
    help = "List all settings"
), KoinComponent {
    val appSettings: CliAppSettings by inject()

    override fun run() {
        val term = Terminal()
        val all = appSettings.getAll()

        if (all.isEmpty()) {
            term.println((TextColors.yellow)("No settings found. Using defaults."))
            term.println("  threadCount = ${appSettings.defaultThreadCount}")
            term.println("  maxConcurrentDownloads = ${appSettings.defaultMaxConcurrentDownloads}")
            term.println("  maxDownloadRetryCount = ${appSettings.defaultMaxDownloadRetryCount}")
            term.println("  dynamicPartCreation = ${appSettings.defaultDynamicPartCreation}")
            term.println("  useServerLastModifiedTime = ${appSettings.defaultUseServerLastModifiedTime}")
            term.println("  appendExtensionToIncompleteDownloads = ${appSettings.defaultAppendExtensionToIncomplete}")
            term.println("  useSparseFileAllocation = ${appSettings.defaultUseSparseFileAllocation}")
            term.println("  speedLimit = ${appSettings.defaultSpeedLimit}")
            term.println("  defaultDownloadFolder = ${appSettings.defaultDownloadFolder}")
            return
        }

        for ((k, v) in all.entries.sortedBy { it.key }) {
            term.println("  $k = $v")
        }
    }
}