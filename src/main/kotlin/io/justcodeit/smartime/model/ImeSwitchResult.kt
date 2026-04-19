package io.justcodeit.smartime.model

data class ImeSwitchResult(
    val status: SwitchStatus,
    val adapterId: String,
    val requestedMode: InputMode?,
    val details: String,
)

