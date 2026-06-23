package com.abdownloadmanager.cli.commands

import com.abdownloadmanager.cli.daemon.DaemonHelper
import com.abdownloadmanager.cli.di.CliDownloadService
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.long
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.terminal.Terminal
import ir.amirab.downloader.queue.QueueManager
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class QueueCommand : CliktCommand(
    name = "queue",
    help = "Manage download queues"
) {
    override fun run() {
        // Parent command - subcommands handle actions
    }
}

class QueueListCommand : CliktCommand(
    name = "list",
    help = "List all queues"
), KoinComponent {
    private val downloadService: CliDownloadService by inject()
    private val queueManager: QueueManager by inject()

    override fun run() = runBlocking {
        val term = Terminal()
        val daemonClient = DaemonHelper.daemonClient()
        if (daemonClient != null) {
            runWithDaemon(term, daemonClient)
            return@runBlocking
        }
        runDirect(term)
    }

    private fun runWithDaemon(term: Terminal, client: com.abdownloadmanager.cli.daemon.DaemonClient) {
        val response = client.listQueues()
        if (response == null) {
            term.println((TextColors.red)("Failed to communicate with daemon"))
            return
        }
        try {
            val envelope = Json { ignoreUnknownKeys = true }.decodeFromString<JsonObject>(response)
            val data = envelope["data"]?.toString() ?: response
            val queues = Json { ignoreUnknownKeys = true }.decodeFromString<kotlinx.serialization.json.JsonArray>(data)
            if (queues.isEmpty()) {
                term.println((TextColors.yellow)("No queues found."))
                return
            }
            for (queueEntry in queues) {
                val obj = queueEntry as JsonObject
                val id = obj["id"]?.jsonPrimitive?.content ?: "?"
                val name = obj["name"]?.jsonPrimitive?.content ?: "?"
                val isActive = obj["isQueueActive"]?.jsonPrimitive?.boolean ?: false
                val status = if (isActive) (TextColors.green)("Active") else (TextColors.yellow)("Stopped")
                term.println("Queue #$id: \"$name\" - $status")
            }
        } catch (e: Exception) {
            term.println((TextColors.red)("Error parsing daemon response: ${e.message}"))
        }
    }

    private fun runDirect(term: Terminal) {
        val queues = queueManager.getAll()
        if (queues.isEmpty()) {
            term.println((TextColors.yellow)("No queues found."))
            return
        }
        for (queue in queues) {
            val qm = queue.getQueueModel()
            val status = if (queue.isQueueActive) {
                (TextColors.green)("Active")
            } else {
                (TextColors.yellow)("Stopped")
            }
            term.println("Queue #${qm.id}: \"${qm.name}\" - $status")
        }
    }
}

class QueueStartCommand : CliktCommand(
    name = "start",
    help = "Start a queue"
), KoinComponent {
    private val downloadService: CliDownloadService by inject()
    private val queueManager: QueueManager by inject()

    private val queueId: Long by argument(help = "Queue ID to start").long()

    override fun run() = runBlocking {
        val term = Terminal()
        val daemonClient = DaemonHelper.daemonClient()
        if (daemonClient != null) {
            val response = daemonClient.startQueue(queueId)
            if (DaemonHelper.isSuccess(response)) {
                term.println((TextColors.green)("Queue #$queueId started via daemon"))
            } else {
                term.println((TextColors.red)("Failed: ${DaemonHelper.getError(response)}"))
            }
            return@runBlocking
        }
        runDirect(term)
    }

    private fun runDirect(term: Terminal) {
        val queue = queueManager.getQueue(queueId)
        queue.start()
        term.println((TextColors.green)("Queue #$queueId started"))
    }
}

class QueueStopCommand : CliktCommand(
    name = "stop",
    help = "Stop a queue"
), KoinComponent {
    private val downloadService: CliDownloadService by inject()
    private val queueManager: QueueManager by inject()

    private val queueId: Long by argument(help = "Queue ID to stop").long()

    override fun run() = runBlocking {
        val term = Terminal()
        val daemonClient = DaemonHelper.daemonClient()
        if (daemonClient != null) {
            val response = daemonClient.stopQueue(queueId)
            if (DaemonHelper.isSuccess(response)) {
                term.println((TextColors.green)("Queue #$queueId stopped via daemon"))
            } else {
                term.println((TextColors.red)("Failed: ${DaemonHelper.getError(response)}"))
            }
            return@runBlocking
        }
        runDirect(term)
    }

    private fun runDirect(term: Terminal) {
        val queue = queueManager.getQueue(queueId)
        runBlocking { queue.stopAsync() }
        term.println((TextColors.green)("Queue #$queueId stopped"))
    }
}