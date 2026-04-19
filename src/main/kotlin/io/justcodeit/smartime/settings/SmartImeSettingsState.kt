package io.justcodeit.smartime.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service

@Service(Service.Level.APP)
@State(name = "SmartImeSettings", storages = [Storage("smart-ime.xml")])
class SmartImeSettingsState : PersistentStateComponent<SmartImeSettingsState.State> {

    data class State(
        var enabled: Boolean = true,
        var javaCommentsChinese: Boolean = true,
        var kotlinCommentsChinese: Boolean = true,
        var stringsChinese: Boolean = true,
        var xmlCommentsChinese: Boolean = true,
        var debugLogging: Boolean = true,
        var enableExperimentalNativeAdapters: Boolean = false,
        var englishSwitchCommand: String = "",
        var chineseSwitchCommand: String = "",
        var currentModeCommand: String = "",
        var englishInputSourceId: String = "com.apple.keylayout.ABC",
        var chineseInputSourceId: String = "",
        var debounceDelayMs: Int = 90,
        var manualOverrideRespectMs: Int = 1_500,
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    fun snapshot(): State = state.copy()

    fun update(newState: State) {
        state = newState
    }

    companion object {
        fun getInstance(): SmartImeSettingsState = service()
    }
}
