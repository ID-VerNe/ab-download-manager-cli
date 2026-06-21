package com.abdownloadmanager.cli

import com.abdownloadmanager.cli.commands.*
import com.abdownloadmanager.cli.daemon.DaemonCommand
import com.abdownloadmanager.cli.di.CliDi
import com.abdownloadmanager.cli.tui.MonitorCommand
import com.abdownloadmanager.cli.utils.CliAppInfo
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.terminal.Terminal
import java.io.File

class CliApp : CliktCommand(
    name = "abdm",
    help = "AB Download Manager CLI"
) {
    override fun run() {
        echo("AB Download Manager CLI", err = true)
        echo("Use --help to see available commands.", err = true)
    }
}

fun main(args: Array<String>) {
    val term = Terminal()

    // Handle --version before booting DI
    if (args.contains("--version")) {
        println("abdm version ${CliAppInfo.appVersion}")
        return
    }

    try {
        val configDirFile = parseArg(args, "--config-dir")?.let(::File)
            ?: File(System.getProperty("user.home"), ".abdm-cli")
        val downloadDirFile = parseArg(args, "--download-dir")?.let(::File)
            ?: parseArg(args, "-d")?.let(::File)
            ?: File(System.getProperty("user.dir"))

        CliDi.boot(configDirFile, downloadDirFile)

        val app = CliApp().subcommands(
            AddCommand(),
            ListCommand(),
            InfoCommand(),
            PauseCommand(),
            ResumeCommand(),
            RemoveCommand(),
            QueueCommand().subcommands(
                QueueListCommand(),
                QueueStartCommand(),
                QueueStopCommand(),
            ),
            CategoryCommand().subcommands(
                CategoryListCommand(),
            ),
            MonitorCommand(),
            DaemonCommand(),
        )

        app.main(args)
    } catch (e: com.github.ajalt.clikt.core.CliktError) {
        System.err.println(e.message)
        System.exit(64)
    } catch (e: Exception) {
        term.println((TextColors.red)("Error: ${e.message}"))
        if (CliAppInfo.isDebugMode) {
            e.printStackTrace()
        }
        System.exit(1)
    }
}

private fun parseArg(args: Array<String>, flag: String): String? {
    val idx = args.indexOf(flag)
    return if (idx >= 0 && idx + 1 < args.size && !args[idx + 1].startsWith("-")) {
        args[idx + 1]
    } else null
}