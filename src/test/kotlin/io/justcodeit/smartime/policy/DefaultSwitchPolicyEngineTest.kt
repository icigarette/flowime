package io.justcodeit.smartime.policy

import io.justcodeit.smartime.model.ContextSnapshot
import io.justcodeit.smartime.model.ContextType
import io.justcodeit.smartime.model.TargetInputMode
import io.justcodeit.smartime.settings.SmartImeSettingsState
import kotlin.test.Test
import kotlin.test.assertEquals

class DefaultSwitchPolicyEngineTest {
    private val engine = DefaultSwitchPolicyEngine()

    @Test
    fun `maps code to english`() {
        val decision = engine.decide(snapshot(ContextType.CODE, "JAVA"), defaultSettings())
        assertEquals(TargetInputMode.ENGLISH, decision.targetMode)
    }

    @Test
    fun `maps comments to chinese when enabled`() {
        val decision = engine.decide(snapshot(ContextType.LINE_COMMENT, "kotlin"), defaultSettings())
        assertEquals(TargetInputMode.CHINESE, decision.targetMode)
    }

    @Test
    fun `keeps unknown context`() {
        val decision = engine.decide(snapshot(ContextType.UNKNOWN, "XML"), defaultSettings())
        assertEquals(TargetInputMode.KEEP, decision.targetMode)
    }

    private fun snapshot(contextType: ContextType, language: String): ContextSnapshot {
        return ContextSnapshot(
            fileType = language,
            language = language,
            offset = 0,
            contextType = contextType,
            psiElementType = "test",
            timestamp = 0,
        )
    }

    private fun defaultSettings(): SmartImeSettingsState.State = SmartImeSettingsState.State()
}
