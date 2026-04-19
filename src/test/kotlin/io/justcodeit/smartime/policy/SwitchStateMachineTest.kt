package io.justcodeit.smartime.policy

import io.justcodeit.smartime.model.ContextSnapshot
import io.justcodeit.smartime.model.ContextType
import io.justcodeit.smartime.model.InputMode
import io.justcodeit.smartime.model.SwitchDecision
import io.justcodeit.smartime.model.SwitchTriggerReason
import io.justcodeit.smartime.model.TargetInputMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SwitchStateMachineTest {
    @Test
    fun `suppresses keep decisions`() {
        val stateMachine = SwitchStateMachine(clock = { 1_000L })
        val result = stateMachine.evaluate(
            snapshot(),
            SwitchDecision(TargetInputMode.KEEP, "keep"),
            SwitchTriggerReason.CARET_MOVED,
        )

        assertTrue(result.suppressed)
        assertEquals(null, result.modeToApply)
    }

    @Test
    fun `deduplicates repeated context quickly`() {
        var now = 1_000L
        val stateMachine = SwitchStateMachine(clock = { now })
        val decision = SwitchDecision(TargetInputMode.CHINESE, "comment")

        val first = stateMachine.evaluate(snapshot(), decision, SwitchTriggerReason.CARET_MOVED)
        assertFalse(first.suppressed)
        assertEquals(InputMode.CHINESE, first.modeToApply)

        now = 1_100L
        val second = stateMachine.evaluate(snapshot(), decision, SwitchTriggerReason.CARET_MOVED)
        assertTrue(second.suppressed)
    }

    @Test
    fun `allows repeated context after duplicate window expires`() {
        var now = 1_000L
        val stateMachine = SwitchStateMachine(clock = { now })
        val decision = SwitchDecision(TargetInputMode.CHINESE, "comment")

        val first = stateMachine.evaluate(snapshot(), decision, SwitchTriggerReason.CARET_MOVED)
        assertFalse(first.suppressed)

        now = 1_130L
        val second = stateMachine.evaluate(snapshot(), decision, SwitchTriggerReason.CARET_MOVED)
        assertFalse(second.suppressed)
        assertEquals(InputMode.CHINESE, second.modeToApply)
    }

    private fun snapshot(): ContextSnapshot {
        return ContextSnapshot(
            fileType = "JAVA",
            language = "JAVA",
            offset = 10,
            contextType = ContextType.LINE_COMMENT,
            psiElementType = "PsiComment",
            timestamp = 0,
        )
    }
}
