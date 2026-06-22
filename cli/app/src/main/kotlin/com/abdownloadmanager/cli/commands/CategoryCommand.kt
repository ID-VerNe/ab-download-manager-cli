package com.abdownloadmanager.cli.commands

import com.abdownloadmanager.cli.utils.CliPaths
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.terminal.Terminal
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@Serializable
data class CliCategory(
    @SerialName("id") val id: Long,
    @SerialName("name") val name: String,
    @SerialName("icon") val icon: String = "",
    @SerialName("path") val path: String = "",
    @SerialName("usePath") val usePath: Boolean = true,
    @SerialName("acceptedFileTypes") val acceptedFileTypes: List<String> = emptyList(),
    @SerialName("acceptedUrlPatterns") val acceptedUrlPatterns: List<String> = emptyList(),
    @SerialName("items") val items: List<Long> = emptyList(),
)

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
    private val cliPaths: CliPaths by inject()

    override fun run() = runBlocking {
        val term = Terminal()
        val json = Json { ignoreUnknownKeys = true }

        val categoriesFile = cliPaths.categoriesFile
        if (!categoriesFile.exists()) {
            term.println((TextColors.yellow)("No categories found. File does not exist: ${categoriesFile.absolutePath}"))
            return@runBlocking
        }

        val categories: List<CliCategory> = try {
            val text = categoriesFile.readText()
            json.decodeFromString(text)
        } catch (e: Exception) {
            term.println((TextColors.red)("Error reading categories: ${e.message}"))
            return@runBlocking
        }

        if (categories.isEmpty()) {
            term.println((TextColors.yellow)("No categories found."))
            return@runBlocking
        }

        term.println((TextColors.brightBlue)("┌────┬──────────────────────────────────────┬──────────────────────────────────────┬────────┬────────┐"))
        term.println((TextColors.brightBlue)("│ ${"ID".padEnd(2)} │ ${"Name".padEnd(36)} │ ${"Path".padEnd(36)} │ ${"Types".padEnd(6)} │ ${"Items".padEnd(6)} │"))
        term.println((TextColors.brightBlue)("├────┼──────────────────────────────────────┼──────────────────────────────────────┼────────┼────────┤"))

        for (cat in categories) {
            val name = cat.name.take(36)
            val path = cat.path.take(36)
            val typeCount = cat.acceptedFileTypes.size.toString().padEnd(6)
            val itemCount = cat.items.size.toString().padEnd(6)

            term.print((TextColors.brightBlue)("│ "))
            term.print(cat.id.toString().padEnd(2))
            term.print((TextColors.brightBlue)(" │ "))
            term.print(name.padEnd(36))
            term.print((TextColors.brightBlue)(" │ "))
            term.print(path.padEnd(36))
            term.print((TextColors.brightBlue)(" │ "))
            term.print(typeCount)
            term.print((TextColors.brightBlue)(" │ "))
            term.print(itemCount)
            term.println((TextColors.brightBlue)(" │"))
        }

        term.println((TextColors.brightBlue)("└────┴──────────────────────────────────────┴──────────────────────────────────────┴────────┴────────┘"))
        term.println("Total: ${categories.size} categories")
    }
}