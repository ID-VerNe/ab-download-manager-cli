package com.abdownloadmanager.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.terminal.Terminal

/**
 * Shell completion command.
 *
 * Generates shell completion scripts for bash, zsh, and fish.
 * Usage:
 *   abdm completion --shell bash
 *   abdm completion --shell zsh
 *   abdm completion --shell fish
 */
class CompletionCommand : CliktCommand(
    name = "completion",
    help = "Generate shell completion scripts"
) {
    private val shell: String by option(
        "--shell", "-s",
        help = "Target shell"
    ).choice("bash", "zsh", "fish").required()

    override fun run() {
        val term = Terminal()

        val script = when (shell) {
            "bash" -> generateBashCompletion()
            "zsh" -> generateZshCompletion()
            "fish" -> generateFishCompletion()
            else -> error("Unsupported shell: $shell")
        }

        term.println((TextColors.green)("Add the following to your ~/.${shell}rc or equivalent:"))
        term.println()
        term.println((TextColors.blue)("  eval \"\$(abdm completion --shell $shell)\""))
        term.println()
        term.println((TextColors.green)("Or pipe to a file:"))
        term.println((TextColors.blue)("  abdm completion --shell $shell > ~/.${shell}_completion.sh"))
        term.println()

        echo(script)
    }

    private fun generateBashCompletion(): String = getBashScript()

    private fun generateZshCompletion(): String = getZshScript()

    private fun generateFishCompletion(): String = getFishScript()
}

private fun getBashScript(): String {
    val d = "${'$'}"
    return """
_abdm_complete() {
    local cur prev words cword
    _init_completion || return

    local commands="add list info pause resume remove restart pause-all monitor queue category config daemon open open-folder checksum edit completion --version --help"

    case ${d}prev in
        add) COMPREPLY=(); return ;;
        pause|resume|remove|restart|info|open|open-folder|checksum) COMPREPLY=( ); return ;;
        edit) COMPREPLY=( ); return ;;
        --shell|-s) COMPREPLY=( $(compgen -W "bash zsh fish" -- "${d}cur") ); return ;;
        --duplicate) COMPREPLY=( $(compgen -W "abort override add-numbered" -- "${d}cur") ); return ;;
        queue) COMPREPLY=( $(compgen -W "list start stop" -- "${d}cur") ); return ;;
        category) COMPREPLY=( $(compgen -W "list" -- "${d}cur") ); return ;;
        config) COMPREPLY=( $(compgen -W "get set list" -- "${d}cur") ); return ;;
        daemon) COMPREPLY=( $(compgen -W "start stop status" -- "${d}cur") ); return ;;
        completion) COMPREPLY=( $(compgen -W "--shell" -- "${d}cur") ); return ;;
    esac

    if [[ ${d}cword -ge 2 ]]; then
        local parent_cmd="${d}{words[${d}cword-2]}"
        if [[ -n "${d}parent_cmd" ]]; then
            case ${d}parent_cmd in
                queue) COMPREPLY=( $(compgen -W "list start stop" -- "${d}cur") ); return ;;
                category) COMPREPLY=( $(compgen -W "list" -- "${d}cur") ); return ;;
                config) COMPREPLY=( $(compgen -W "get set list" -- "${d}cur") ); return ;;
                daemon) COMPREPLY=( $(compgen -W "start stop status" -- "${d}cur") ); return ;;
                completion) COMPREPLY=( $(compgen -W "--shell" -- "${d}cur") ); return ;;
            esac
        fi
    fi

    COMPREPLY=( $(compgen -W "${d}commands" -- "${d}cur") )
} &&
complete -F _abdm_complete abdm
""".trimIndent()
}

private fun getZshScript(): String {
    val d = "${'$'}"
    return """
#compdef abdm

_abdm_commands() {
    local -a commands
    commands=(
        'add:Add one or more downloads'
        'list:List downloads'
        'info:Show detailed download info'
        'pause:Pause one or more downloads'
        'resume:Resume one or more downloads'
        'remove:Remove one or more downloads'
        'restart:Restart (re-download) one or more downloads'
        'pause-all:Pause all active downloads'
        'monitor:Monitor downloads in real-time'
        'queue:Manage download queues'
        'category:Manage categories'
        'config:Manage application settings'
        'daemon:Control the download daemon'
        'open:Open downloaded file with default app'
        'open-folder:Open download folder'
        'checksum:View or set checksum'
        'edit:Edit download properties'
        'completion:Generate shell completion scripts'
    )
    _describe 'command' commands
}

_abdm() {
    local context state state_descr line
    typeset -A opt_args

    _arguments -C \
        '--version[Show version]' \
        '--help[Show help]' \
        '1: :->command' \
        '*:: :->args'

    case ${d}state in
        command)
            _abdm_commands
            ;;
        args)
            case ${d}words[1] in
                add)
                    _arguments \
                        '--output-dir=-o[Output directory]:directory:_directories' \
                        '--name=-n[Output file name]:name:' \
                        '--start=-s[Start immediately and show progress]' \
                        '--detach=-d[Start in background]' \
                        '--queue=-q[Queue ID]:queue id:' \
                        '--connections=-c[Number of connections]:count:' \
                        '--speed-limit=-l[Speed limit]:bytes:' \
                        '--duplicate[Duplicate handling]:(abort override add-numbered)' \
                        '--username=-u[Auth username]:user:' \
                        '--password=-p[Auth password]:password:' \
                        '--quiet[Suppress progress output]' \
                        '--help[Show help]' \
                        ':url:_urls'
                    ;;
                pause|resume|remove|restart|info|open|open-folder|checksum)
                    _arguments \
                        '--help[Show help]' \
                        ':id:'
                    ;;
                edit)
                    _arguments \
                        '--name=-n[New name]:name:' \
                        '--speed-limit=-l[Speed limit]:bytes:' \
                        '--connections=-c[Connections]:count:' \
                        '--help[Show help]' \
                        ':id:'
                    ;;
                queue)
                    _arguments \
                        '--help[Show help]' \
                        '1: :(list start stop)'
                    ;;
                category)
                    _arguments \
                        '--help[Show help]' \
                        '1: :(list)'
                    ;;
                config)
                    _arguments \
                        '--help[Show help]' \
                        '1: :(get set list)' \
                        '*:: :->config_args'
                    case ${d}words[1] in
                        get|set)
                            _arguments \
                                ':key:(threadCount maxConcurrentDownloads maxDownloadRetryCount dynamicPartCreation useServerLastModifiedTime appendExtensionToIncompleteDownloads useSparseFileAllocation speedLimit defaultDownloadFolder userAgent)'
                            ;;
                    esac
                    ;;
                daemon)
                    _arguments \
                        '--help[Show help]' \
                        '1: :(start stop status)'
                    ;;
                completion)
                    _arguments \
                        '--shell=-s[Target shell]:(bash zsh fish)' \
                        '--help[Show help]'
                    ;;
                monitor)
                    _arguments \
                        '--help[Show help]'
                    ;;
            esac
            ;;
    esac
}

_abdm
""".trimIndent()
}

private fun getFishScript(): String = """
# abdm completions for fish shell

function __fish_abdm_using_command
    set cmd (commandline -opc)
    if [ (count ${d}cmd) -gt 1 ]
        set target ${d}cmd[2..-1]
        for i in ${d}argv
            if [ (count ${d}target) -ge (count ${d}i) ]
                set match 1
                for j in (seq (count ${d}i))
                    if not test ${d}target[${d}j] = ${d}i[${d}j]
                        set match 0
                        break
                    end
                end
                if test ${d}match -eq 1
                    return 0
                end
            end
        end
    end
    return 1
end

# Top-level commands
complete -c abdm -f -n "not __fish_abdm_using_command" -a add           -d "Add one or more downloads"
complete -c abdm -f -n "not __fish_abdm_using_command" -a list          -d "List downloads"
complete -c abdm -f -n "not __fish_abdm_using_command" -a info          -d "Show detailed download info"
complete -c abdm -f -n "not __fish_abdm_using_command" -a pause         -d "Pause downloads"
complete -c abdm -f -n "not __fish_abdm_using_command" -a resume        -d "Resume downloads"
complete -c abdm -f -n "not __fish_abdm_using_command" -a remove        -d "Remove downloads"
complete -c abdm -f -n "not __fish_abdm_using_command" -a restart       -d "Restart downloads"
complete -c abdm -f -n "not __fish_abdm_using_command" -a pause-all     -d "Pause all active downloads"
complete -c abdm -f -n "not __fish_abdm_using_command" -a monitor       -d "Monitor downloads in real-time"
complete -c abdm -f -n "not __fish_abdm_using_command" -a queue         -d "Manage download queues"
complete -c abdm -f -n "not __fish_abdm_using_command" -a category      -d "Manage categories"
complete -c abdm -f -n "not __fish_abdm_using_command" -a config        -d "Manage settings"
complete -c abdm -f -n "not __fish_abdm_using_command" -a daemon        -d "Control the daemon"
complete -c abdm -f -n "not __fish_abdm_using_command" -a open          -d "Open file with default app"
complete -c abdm -f -n "not __fish_abdm_using_command" -a open-folder   -d "Open download folder"
complete -c abdm -f -n "not __fish_abdm_using_command" -a checksum      -d "View or set checksum"
complete -c abdm -f -n "not __fish_abdm_using_command" -a edit          -d "Edit download properties"
complete -c abdm -f -n "not __fish_abdm_using_command" -a completion    -d "Generate completions"

# add options
complete -c abdm -f -n "__fish_abdm_using_command add" -l output-dir -s o -d "Output directory" -r
complete -c abdm -f -n "__fish_abdm_using_command add" -l name -s n -d "File name" -r
complete -c abdm -f -n "__fish_abdm_using_command add" -l start -s s -d "Start and show progress"
complete -c abdm -f -n "__fish_abdm_using_command add" -l detach -s d -d "Start in background"
complete -c abdm -f -n "__fish_abdm_using_command add" -l queue -s q -d "Queue ID" -r
complete -c abdm -f -n "__fish_abdm_using_command add" -l connections -s c -d "Connections" -r
complete -c abdm -f -n "__fish_abdm_using_command add" -l speed-limit -s l -d "Speed limit" -r
complete -c abdm -f -n "__fish_abdm_using_command add" -l duplicate -d "Duplicate" -x -a "abort override add-numbered"
complete -c abdm -f -n "__fish_abdm_using_command add" -l username -s u -d "Username" -r
complete -c abdm -f -n "__fish_abdm_using_command add" -l password -s p -d "Password" -r
complete -c abdm -f -n "__fish_abdm_using_command add" -l quiet -d "Suppress output"

# edit options
complete -c abdm -f -n "__fish_abdm_using_command edit" -l name -s n -d "New name" -r
complete -c abdm -f -n "__fish_abdm_using_command edit" -l speed-limit -s l -d "Speed limit" -r
complete -c abdm -f -n "__fish_abdm_using_command edit" -l connections -s c -d "Connections" -r

# completion options
complete -c abdm -f -n "__fish_abdm_using_command completion" -l shell -s s -d "Shell" -x -a "bash zsh fish"

# queue subcommands
complete -c abdm -f -n "__fish_abdm_using_command queue" -a list -d "List queues"
complete -c abdm -f -n "__fish_abdm_using_command queue" -a start -d "Start a queue"
complete -c abdm -f -n "__fish_abdm_using_command queue" -a stop -d "Stop a queue"

# category subcommands
complete -c abdm -f -n "__fish_abdm_using_command category" -a list -d "List categories"

# config subcommands + keys
complete -c abdm -f -n "__fish_abdm_using_command config" -a get -d "Get a setting"
complete -c abdm -f -n "__fish_abdm_using_command config" -a set -d "Set a setting"
complete -c abdm -f -n "__fish_abdm_using_command config" -a list -d "List all settings"
complete -c abdm -f -n "__fish_abdm_using_command config get" -k -x -a "
threadCount
maxConcurrentDownloads
maxDownloadRetryCount
dynamicPartCreation
useServerLastModifiedTime
appendExtensionToIncompleteDownloads
useSparseFileAllocation
speedLimit
defaultDownloadFolder
userAgent
"
complete -c abdm -f -n "__fish_abdm_using_command config set" -k -x -a "
threadCount
maxConcurrentDownloads
maxDownloadRetryCount
dynamicPartCreation
useServerLastModifiedTime
appendExtensionToIncompleteDownloads
useSparseFileAllocation
speedLimit
defaultDownloadFolder
userAgent
"

# daemon subcommands
complete -c abdm -f -n "__fish_abdm_using_command daemon" -a start -d "Start daemon"
complete -c abdm -f -n "__fish_abdm_using_command daemon" -a stop -d "Stop daemon"
complete -c abdm -f -n "__fish_abdm_using_command daemon" -a status -d "Daemon status"
""".trimIndent()

// Import the d variable used in fish script
private val d: String get() = "${'$'}"