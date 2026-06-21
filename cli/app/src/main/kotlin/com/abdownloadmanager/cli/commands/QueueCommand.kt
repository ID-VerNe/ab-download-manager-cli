package com.abdownloadmanager.cli.commands

import com.abdownloadmanager.cli.di.CliDownloadService
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.long
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.terminal.Terminal
import ir.amirab.downloader.queue.QueueManager
import kotlinx.coroutines.runBlocking
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
    private val queueManager: QueueManager by inject()

    override fun run() = runBlocking {
        val term = Terminal()
        val queues = queueManager.getAll()

        if (queues.isEmpty()) {
            term.println((TextColors.yellow)("No queues found."))
            return@runBlocking
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
        downloadService.boot()
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
        downloadService.boot()
        val queue = queueManager.getQueue(queueId)
        queue.stopAsync()
        term.println((TextColors.green)("Queue #$queueId stopped"))
    }
}