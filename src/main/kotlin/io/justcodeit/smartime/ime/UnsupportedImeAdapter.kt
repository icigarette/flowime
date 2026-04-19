package io.justcodeit.smartime.ime

import io.justcodeit.smartime.model.ImeSwitchResult
import io.justcodeit.smartime.model.InputMode
import io.justcodeit.smartime.model.SwitchStatus

class UnsupportedImeAdapter(
    private val reason: String,
) : ImeAdapter {
    override val adapterId: String = "unsupported"

    override fun isSupported(): Boolean = false

    override fun inspect(): ImeAdapterDiagnostics {
        return ImeAdapterDiagnostics(
            adapterId = adapterId,
            supported = false,
            supportDescription = reason,
            currentMode = null,
            currentInputSourceId = null,
            resolvedEnglishInputSourceId = null,
            resolvedChineseInputSourceId = null,
        )
    }

    override fun getCurrentMode(): InputMode? = null

    override fun switchTo(mode: InputMode): ImeSwitchResult {
        return ImeSwitchResult(
            status = SwitchStatus.UNSUPPORTED,
            adapterId = adapterId,
            requestedMode = mode,
            details = reason,
        )
    }

    override fun describeSupport(): String = reason
}
