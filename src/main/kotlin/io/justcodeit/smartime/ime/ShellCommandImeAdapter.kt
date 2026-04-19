package io.justcodeit.smartime.ime

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo
import io.justcodeit.smartime.model.ImeSwitchResult
import io.justcodeit.smartime.model.InputMode
import io.justcodeit.smartime.model.SwitchStatus
import io.justcodeit.smartime.settings.SmartImeSettingsState

class ShellCommandImeAdapter(
    private val settingsProvider: () -> SmartImeSettingsState.State,
) : ImeAdapter {
    private val logger = Logger.getInstance(ShellCommandImeAdapter::class.java)

    override val adapterId: String = "shell-command"

    override fun isSupported(): Boolean {
        val settings = settingsProvider()
        return settings.englishSwitchCommand.isNotBlank() && settings.chineseSwitchCommand.isNotBlank()
    }

    override fun inspect(): ImeAdapterDiagnostics {
        val settings = settingsProvider()
        return ImeAdapterDiagnostics(
            adapterId = adapterId,
            supported = isSupported(),
            supportDescription = describeSupport(),
            currentMode = getCurrentMode(),
            currentInputSourceId = null,
            resolvedEnglishInputSourceId = settings.englishSwitchCommand.ifBlank { null },
            resolvedChineseInputSourceId = settings.chineseSwitchCommand.ifBlank { null },
        )
    }

    override fun getCurrentMode(): InputMode? {
        val command = settingsProvider().currentModeCommand.trim()
        if (command.isBlank()) {
            return null
        }

        val output = runCommand(command) ?: return null
        return parseMode(output)
    }

    override fun switchTo(mode: InputMode): ImeSwitchResult {
        if (!isSupported()) {
            return ImeSwitchResult(
                status = SwitchStatus.UNSUPPORTED,
                adapterId = adapterId,
                requestedMode = mode,
                details = "Shell command adapter is not configured",
            )
        }

        if (getCurrentMode() == mode) {
            return ImeSwitchResult(SwitchStatus.SKIPPED, adapterId, mode, "Already in $mode")
        }

        val settings = settingsProvider()
        val command = when (mode) {
            InputMode.ENGLISH -> settings.englishSwitchCommand
            InputMode.CHINESE -> settings.chineseSwitchCommand
        }.trim()

        if (command.isBlank()) {
            return ImeSwitchResult(
                status = SwitchStatus.UNSUPPORTED,
                adapterId = adapterId,
                requestedMode = mode,
                details = "No shell command configured for $mode",
            )
        }

        return if (runCommand(command) != null) {
            ImeSwitchResult(SwitchStatus.SWITCHED, adapterId, mode, "Switched via shell command")
        } else {
            ImeSwitchResult(SwitchStatus.FAILED, adapterId, mode, "Shell command failed for $mode")
        }
    }

    override fun describeSupport(): String {
        val settings = settingsProvider()
        val hasEnglish = settings.englishSwitchCommand.isNotBlank()
        val hasChinese = settings.chineseSwitchCommand.isNotBlank()
        val hasProbe = settings.currentModeCommand.isNotBlank()
        return if (hasEnglish && hasChinese) {
            if (hasProbe) {
                "Using configured shell commands with current mode probe"
            } else {
                "Using configured shell commands without current mode probe"
            }
        } else {
            "Shell command adapter requires both English and Chinese switch commands"
        }
    }

    private fun parseMode(output: String): InputMode? {
        return when (output.trim().uppercase()) {
            "ENGLISH" -> InputMode.ENGLISH
            "CHINESE" -> InputMode.CHINESE
            else -> null
        }
    }

    private fun runCommand(command: String): String? {
        return try {
            val commandLine = if (SystemInfo.isWindows) {
                GeneralCommandLine("cmd.exe", "/c", command)
            } else {
                GeneralCommandLine("/bin/zsh", "-lc", command)
            }
            val output = CapturingProcessHandler(commandLine).runProcess(3_000)
            if (output.exitCode == 0) {
                output.stdout.trim()
            } else {
                logger.warn("Shell command failed: $command -> ${output.stderr}")
                null
            }
        } catch (t: Throwable) {
            logger.warn("Shell command execution failed: $command", t)
            null
        }
    }
}
