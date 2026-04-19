package io.justcodeit.smartime.ime

import com.intellij.openapi.util.SystemInfo
import io.justcodeit.smartime.settings.SmartImeSettingsState

class ImeAdapterProvider(
    private val settingsProvider: () -> SmartImeSettingsState.State,
) {
    private val shellCommandAdapter by lazy { ShellCommandImeAdapter(settingsProvider) }
    private val macOsNativeAdapter by lazy { MacOsNativeImeAdapter(settingsProvider) }
    private val windowsNativeAdapter by lazy { WindowsNativeImeAdapter(settingsProvider) }
    private val imSelectAdapter by lazy { ImSelectImeAdapter(settingsProvider) }
    private val unsupportedAdapter by lazy {
        val osName = when {
            SystemInfo.isMac -> "macOS"
            SystemInfo.isWindows -> "Windows"
            else -> "Unsupported OS"
        }
        UnsupportedImeAdapter("$osName adapter is not available. Configure shell commands, install im-select, or explicitly enable the experimental native adapter.")
    }

    private fun nativeAdaptersEnabled(): Boolean = settingsProvider().enableExperimentalNativeAdapters

    fun get(): ImeAdapter {
        if (shellCommandAdapter.isSupported()) {
            return shellCommandAdapter
        }

        if (nativeAdaptersEnabled() && SystemInfo.isMac && macOsNativeAdapter.isSupported()) {
            return macOsNativeAdapter
        }

        if (nativeAdaptersEnabled() && SystemInfo.isWindows && windowsNativeAdapter.isSupported()) {
            return windowsNativeAdapter
        }

        if (imSelectAdapter.isSupported()) {
            return imSelectAdapter
        }

        return unsupportedAdapter
    }

    fun describePriority(): String {
        return when {
            shellCommandAdapter.isSupported() && !nativeAdaptersEnabled() ->
                "shell-command -> im-select -> unsupported (native adapters disabled)"
            shellCommandAdapter.isSupported() && SystemInfo.isMac && macOsNativeAdapter.isSupported() ->
                "shell-command -> macOS native -> im-select -> unsupported"
            shellCommandAdapter.isSupported() && SystemInfo.isWindows && windowsNativeAdapter.isSupported() ->
                "shell-command -> windows native -> im-select -> unsupported"
            shellCommandAdapter.isSupported() -> "shell-command -> im-select -> unsupported"
            !nativeAdaptersEnabled() -> "im-select -> unsupported (native adapters disabled)"
            SystemInfo.isMac && macOsNativeAdapter.isSupported() -> "macOS native -> im-select -> unsupported"
            SystemInfo.isWindows && windowsNativeAdapter.isSupported() -> "windows native -> im-select -> unsupported"
            imSelectAdapter.isSupported() -> "im-select -> unsupported"
            else -> "unsupported"
        }
    }

    fun inspectCandidates(): List<ImeAdapterDiagnostics> {
        val diagnostics = mutableListOf<ImeAdapterDiagnostics>()
        diagnostics += shellCommandAdapter.inspect()
        if (SystemInfo.isMac) {
            diagnostics += if (nativeAdaptersEnabled()) {
                macOsNativeAdapter.inspect()
            } else {
                ImeAdapterDiagnostics(
                    adapterId = "macos-native",
                    supported = false,
                    supportDescription = "Disabled by settings: experimental native adapters",
                    currentMode = null,
                    currentInputSourceId = null,
                    resolvedEnglishInputSourceId = null,
                    resolvedChineseInputSourceId = null,
                )
            }
        }
        if (SystemInfo.isWindows) {
            diagnostics += if (nativeAdaptersEnabled()) {
                windowsNativeAdapter.inspect()
            } else {
                ImeAdapterDiagnostics(
                    adapterId = "windows-native",
                    supported = false,
                    supportDescription = "Disabled by settings: experimental native adapters",
                    currentMode = null,
                    currentInputSourceId = null,
                    resolvedEnglishInputSourceId = null,
                    resolvedChineseInputSourceId = null,
                )
            }
        }
        diagnostics += imSelectAdapter.inspect()
        diagnostics += unsupportedAdapter.inspect()
        return diagnostics
    }
}
