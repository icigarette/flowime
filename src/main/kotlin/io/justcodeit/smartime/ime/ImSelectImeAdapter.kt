package io.justcodeit.smartime.ime

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo
import io.justcodeit.smartime.model.ImeSwitchResult
import io.justcodeit.smartime.model.InputMode
import io.justcodeit.smartime.model.SwitchStatus
import io.justcodeit.smartime.settings.SmartImeSettingsState

class ImSelectImeAdapter(
    private val settingsProvider: () -> SmartImeSettingsState.State,
) : ImeAdapter {
    private val logger = Logger.getInstance(ImSelectImeAdapter::class.java)
    private val executablePath = PathEnvironmentVariableUtil.findInPath("im-select")?.absolutePath

    override val adapterId: String = "im-select"

    override fun isSupported(): Boolean = executablePath != null

    override fun inspect(): ImeAdapterDiagnostics {
        val settings = settingsProvider()
        val currentInputSourceId = if (isSupported()) runCommand() else null
        return ImeAdapterDiagnostics(
            adapterId = adapterId,
            supported = isSupported(),
            supportDescription = describeSupport(),
            currentMode = currentInputSourceId?.let { current ->
                when (current) {
                    resolveEnglishInputSource(settings) -> InputMode.ENGLISH
                    resolveChineseInputSource(settings) -> InputMode.CHINESE
                    else -> null
                }
            },
            currentInputSourceId = currentInputSourceId,
            resolvedEnglishInputSourceId = resolveEnglishInputSource(settings),
            resolvedChineseInputSourceId = resolveChineseInputSource(settings),
        )
    }

    override fun getCurrentMode(): InputMode? {
        if (!isSupported()) {
            return null
        }

        val current = runCommand() ?: return null
        val settings = settingsProvider()
        val englishId = resolveEnglishInputSource(settings)
        val chineseId = resolveChineseInputSource(settings)

        return when (current) {
            englishId -> InputMode.ENGLISH
            chineseId -> InputMode.CHINESE
            else -> null
        }
    }

    override fun switchTo(mode: InputMode): ImeSwitchResult {
        if (!isSupported()) {
            return ImeSwitchResult(SwitchStatus.UNSUPPORTED, adapterId, mode, "im-select is not available in PATH")
        }

        if (getCurrentMode() == mode) {
            return ImeSwitchResult(SwitchStatus.SKIPPED, adapterId, mode, "Already in $mode")
        }

        val settings = settingsProvider()
        val sourceId = when (mode) {
            InputMode.ENGLISH -> resolveEnglishInputSource(settings)
            InputMode.CHINESE -> resolveChineseInputSource(settings)
        }

        if (sourceId.isBlank()) {
            return ImeSwitchResult(SwitchStatus.UNSUPPORTED, adapterId, mode, "No input source id configured for $mode")
        }

        val output = runCommand(sourceId)
        return if (output != null) {
            val stabilized = stabilizeMacChineseSwitch(mode, sourceId)
            val details = if (stabilized) {
                "Switched via im-select to $sourceId (macOS chinese re-applied)"
            } else {
                "Switched via im-select to $sourceId"
            }
            ImeSwitchResult(SwitchStatus.SWITCHED, adapterId, mode, details)
        } else {
            ImeSwitchResult(SwitchStatus.FAILED, adapterId, mode, "im-select failed for $sourceId")
        }
    }

    override fun describeSupport(): String {
        return if (executablePath != null) {
            "Using im-select at $executablePath"
        } else {
            "im-select not found in PATH"
        }
    }

    private fun resolveEnglishInputSource(settings: SmartImeSettingsState.State): String {
        return settings.englishInputSourceId.ifBlank { "com.apple.keylayout.ABC" }
    }

    private fun resolveChineseInputSource(settings: SmartImeSettingsState.State): String {
        if (settings.chineseInputSourceId.isNotBlank()) {
            return settings.chineseInputSourceId
        }

        if (!SystemInfo.isMac) {
            return ""
        }

        return detectMacChineseInputSource()
    }

    private fun detectMacChineseInputSource(): String {
        val output = runPlainCommand("/usr/bin/defaults", "read", "com.apple.HIToolbox", "AppleEnabledInputSources") ?: return ""
        val regex = Regex("\"Input Mode\"\\s*=\\s*\"([^\"]+)\";")
        return regex.find(output)?.groupValues?.getOrNull(1).orEmpty()
    }

    private fun runCommand(vararg args: String): String? {
        val executable = executablePath ?: return null
        return runPlainCommand(executable, *args)
    }

    private fun stabilizeMacChineseSwitch(mode: InputMode, sourceId: String): Boolean {
        if (!SystemInfo.isMac || mode != InputMode.CHINESE) {
            return false
        }

        val replay = runCommand(sourceId)
        if (replay == null) {
            logger.warn("macOS chinese switch re-apply failed for $sourceId")
            return false
        }
        return true
    }

    private fun runPlainCommand(command: String, vararg args: String): String? {
        return try {
            val output = CapturingProcessHandler(GeneralCommandLine(command, *args))
                .runProcess(3_000)

            if (output.exitCode == 0) {
                output.stdout.trim()
            } else {
                logger.warn("Command failed: $command ${args.joinToString(" ")} -> ${output.stderr}")
                null
            }
        } catch (t: Throwable) {
            logger.warn("Command execution failed: $command ${args.joinToString(" ")}", t)
            null
        }
    }
}
