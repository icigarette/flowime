package io.justcodeit.smartime.policy

import io.justcodeit.smartime.model.ContextSnapshot
import io.justcodeit.smartime.model.SwitchDecision
import io.justcodeit.smartime.settings.SmartImeSettingsState

interface SwitchPolicyEngine {
    fun decide(snapshot: ContextSnapshot, settings: SmartImeSettingsState.State): SwitchDecision
}

