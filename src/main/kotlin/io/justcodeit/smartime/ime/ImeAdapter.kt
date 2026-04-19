package io.justcodeit.smartime.ime

import io.justcodeit.smartime.model.ImeSwitchResult
import io.justcodeit.smartime.model.InputMode

interface ImeAdapter {
    val adapterId: String

    fun isSupported(): Boolean

    fun inspect(): ImeAdapterDiagnostics

    fun getCurrentMode(): InputMode?

    fun switchTo(mode: InputMode): ImeSwitchResult

    fun describeSupport(): String
}
