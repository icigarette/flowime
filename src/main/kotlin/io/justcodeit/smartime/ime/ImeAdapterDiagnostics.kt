package io.justcodeit.smartime.ime

import io.justcodeit.smartime.model.InputMode

data class ImeAdapterDiagnostics(
    val adapterId: String,
    val supported: Boolean,
    val supportDescription: String,
    val currentMode: InputMode?,
    val currentInputSourceId: String?,
    val resolvedEnglishInputSourceId: String?,
    val resolvedChineseInputSourceId: String?,
    val enabledInputSourceIds: List<String> = emptyList(),
)

