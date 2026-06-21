package com.abdownloadmanager.cli.commands

import com.abdownloadmanager.cli.di.CliDownloadService
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.terminal.Terminal
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class CategoryCommand : CliktCommand(
    name = "category",
    help = "Manage download categories"
) {
    override fun run() {
        // Parent command - subcommands handle actions
    }
}

class CategoryListCommand : CliktCommand(
    name = "list",
    help = "List all categories"
), KoinComponent {
    private val downloadService: CliDownloadService by inject()

    override fun run() {
        val term = Terminal()
        downloadService.boot()
        val items = downloadService.getAllItems()

        if (items.isEmpty()) {
            term.println((TextColors.yellow)("No downloads found."))
            return
        }

        term.println("Categories are managed through the desktop app.")
        term.println("Total downloads: ${items.size}")
    }
}