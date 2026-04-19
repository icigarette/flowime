package io.justcodeit.smartime.ime

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.platform.mac.CoreFoundation
import io.justcodeit.smartime.model.ImeSwitchResult
import io.justcodeit.smartime.model.InputMode
import io.justcodeit.smartime.model.SwitchStatus
import io.justcodeit.smartime.settings.SmartImeSettingsState

class MacOsNativeImeAdapter(
    private val settingsProvider: () -> SmartImeSettingsState.State,
) : ImeAdapter {
    private val logger = Logger.getInstance(MacOsNativeImeAdapter::class.java)
    private val coreFoundation = CoreFoundation.INSTANCE
    private val carbonLibrary = loadCarbonLibrary()
    private val api = loadApi()

    override val adapterId: String = "macos-native"

    override fun isSupported(): Boolean = SystemInfo.isMac && carbonLibrary != null && api != null

    override fun inspect(): ImeAdapterDiagnostics {
        val settings = settingsProvider()
        val currentInputSourceId = getCurrentInputSourceId()
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
            enabledInputSourceIds = listEnabledInputSourceIds(),
        )
    }

    override fun getCurrentMode(): InputMode? {
        val currentId = getCurrentInputSourceId() ?: return null
        val settings = settingsProvider()
        return when (currentId) {
            resolveEnglishInputSource(settings) -> InputMode.ENGLISH
            resolveChineseInputSource(settings) -> InputMode.CHINESE
            else -> null
        }
    }

    override fun switchTo(mode: InputMode): ImeSwitchResult {
        if (!isSupported()) {
            return ImeSwitchResult(
                status = SwitchStatus.UNSUPPORTED,
                adapterId = adapterId,
                requestedMode = mode,
                details = "macOS native text input source API is unavailable",
            )
        }

        if (getCurrentMode() == mode) {
            return ImeSwitchResult(
                status = SwitchStatus.SKIPPED,
                adapterId = adapterId,
                requestedMode = mode,
                details = "Already in $mode",
            )
        }

        val settings = settingsProvider()
        val sourceId = when (mode) {
            InputMode.ENGLISH -> resolveEnglishInputSource(settings)
            InputMode.CHINESE -> resolveChineseInputSource(settings)
        }

        if (sourceId.isBlank()) {
            return ImeSwitchResult(
                status = SwitchStatus.UNSUPPORTED,
                adapterId = adapterId,
                requestedMode = mode,
                details = "No macOS input source id could be resolved for $mode",
            )
        }

        val inputSource = findEnabledInputSourceById(sourceId)
            ?: return ImeSwitchResult(
                status = SwitchStatus.FAILED,
                adapterId = adapterId,
                requestedMode = mode,
                details = "Enabled input source not found: $sourceId",
            )

        return try {
            val status = api!!.TISSelectInputSource(inputSource)
            if (status == 0) {
                ImeSwitchResult(
                    status = SwitchStatus.SWITCHED,
                    adapterId = adapterId,
                    requestedMode = mode,
                    details = "Switched via macOS native API to $sourceId",
                )
            } else {
                ImeSwitchResult(
                    status = SwitchStatus.FAILED,
                    adapterId = adapterId,
                    requestedMode = mode,
                    details = "TISSelectInputSource failed with status=$status for $sourceId",
                )
            }
        } catch (t: Throwable) {
            logger.warn("Failed to switch macOS input source to $sourceId", t)
            ImeSwitchResult(
                status = SwitchStatus.FAILED,
                adapterId = adapterId,
                requestedMode = mode,
                details = "Native switch failed for $sourceId: ${t.message}",
            )
        } finally {
            inputSource.release()
        }
    }

    override fun describeSupport(): String {
        if (!isSupported()) {
            return "macOS native Text Input Source Services adapter unavailable"
        }

        val settings = settingsProvider()
        return buildString {
            append("Using macOS native Text Input Source Services")
            append(" | current=")
            append(getCurrentInputSourceId().orEmpty().ifBlank { "unknown" })
            append(" | english=")
            append(resolveEnglishInputSource(settings).ifBlank { "unresolved" })
            append(" | chinese=")
            append(resolveChineseInputSource(settings).ifBlank { "unresolved" })
        }
    }

    private fun getCurrentInputSourceId(): String? {
        val current = api?.TISCopyCurrentKeyboardInputSource() ?: return null
        return try {
            getInputSourceId(current)
        } finally {
            current.release()
        }
    }

    private fun findEnabledInputSourceById(sourceId: String): CoreFoundation.CFTypeRef? {
        val inputSources = api?.TISCreateInputSourceList(null, 0) ?: return null
        return try {
            for (index in 0 until inputSources.getCount()) {
                val pointer = inputSources.getValueAtIndex(index)
                val candidate = CoreFoundation.CFTypeRef(pointer)
                if (getInputSourceId(candidate) == sourceId) {
                    candidate.retain()
                    return candidate
                }
            }
            null
        } finally {
            inputSources.release()
        }
    }

    private fun listEnabledInputSourceIds(): List<String> {
        val inputSources = api?.TISCreateInputSourceList(null, 0) ?: return emptyList()
        return try {
            buildList {
                for (index in 0 until inputSources.getCount()) {
                    val pointer = inputSources.getValueAtIndex(index)
                    val candidate = CoreFoundation.CFTypeRef(pointer)
                    getInputSourceId(candidate)?.let(::add)
                }
            }.distinct()
        } finally {
            inputSources.release()
        }
    }

    private fun getInputSourceId(inputSource: CoreFoundation.CFTypeRef): String? {
        val property = getStringProperty(inputSource, "kTISPropertyInputSourceID")
        if (!property.isNullOrBlank()) {
            return property
        }

        return getStringProperty(inputSource, "kTISPropertyInputModeID")
    }

    private fun getStringProperty(inputSource: CoreFoundation.CFTypeRef, keyName: String): String? {
        val propertyKey = getCarbonStringConstant(keyName) ?: return null
        val propertyPointer = api?.TISGetInputSourceProperty(inputSource, propertyKey) ?: return null
        return CoreFoundation.CFStringRef(propertyPointer).stringValue()
    }

    private fun getCarbonStringConstant(name: String): CoreFoundation.CFStringRef? {
        val library = carbonLibrary ?: return null
        return try {
            val pointer = library.getGlobalVariableAddress(name).getPointer(0)
            CoreFoundation.CFStringRef(pointer)
        } catch (t: Throwable) {
            logger.warn("Failed to resolve Carbon global constant $name", t)
            null
        }
    }

    private fun resolveEnglishInputSource(settings: SmartImeSettingsState.State): String {
        if (settings.englishInputSourceId.isNotBlank()) {
            return settings.englishInputSourceId
        }

        val asciiCapable = api?.TISCopyCurrentASCIICapableKeyboardInputSource() ?: return "com.apple.keylayout.ABC"
        return try {
            getInputSourceId(asciiCapable).orEmpty().ifBlank { "com.apple.keylayout.ABC" }
        } finally {
            asciiCapable.release()
        }
    }

    private fun resolveChineseInputSource(settings: SmartImeSettingsState.State): String {
        if (settings.chineseInputSourceId.isNotBlank()) {
            return settings.chineseInputSourceId
        }

        return resolvePreferredLanguageInputSource("zh-Hans")
            ?: resolvePreferredLanguageInputSource("zh-Hant")
            ?: ""
    }

    private fun resolvePreferredLanguageInputSource(languageTag: String): String? {
        val language = CoreFoundation.CFStringRef.createCFString(languageTag)
        val inputSource = try {
            api?.TISCopyInputSourceForLanguage(language)
        } finally {
            language.release()
        } ?: return null

        return try {
            getInputSourceId(inputSource)
        } finally {
            inputSource.release()
        }
    }

    private fun loadCarbonLibrary(): NativeLibrary? {
        if (!SystemInfo.isMac) {
            return null
        }

        return try {
            NativeLibrary.getInstance("Carbon")
        } catch (t: Throwable) {
            logger.warn("Failed to load Carbon framework for macOS IME adapter", t)
            null
        }
    }

    private fun loadApi(): MacOsTextInputSourceApi? {
        if (!SystemInfo.isMac) {
            return null
        }

        return try {
            Native.load("Carbon", MacOsTextInputSourceApi::class.java) as MacOsTextInputSourceApi
        } catch (t: Throwable) {
            logger.warn("Failed to load macOS text input source API", t)
            null
        }
    }
}
