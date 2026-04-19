package io.justcodeit.smartime.policy

import io.justcodeit.smartime.model.ContextSnapshot
import io.justcodeit.smartime.model.InputMode
import io.justcodeit.smartime.model.SwitchDecision
import io.justcodeit.smartime.model.SwitchTriggerReason
import io.justcodeit.smartime.model.TargetInputMode

class SwitchStateMachine(
    private val clock: () -> Long = System::currentTimeMillis,
) {
    companion object {
        private const val DUPLICATE_CONTEXT_WINDOW_MS = 120L
    }

    data class EvaluationResult(
        val modeToApply: InputMode?,
        val suppressed: Boolean,
        val reason: String,
    )

    private var lastContextKey: String? = null
    private var lastEvaluationAt: Long = 0L
    private var lastAppliedMode: InputMode? = null

    fun evaluate(
        snapshot: ContextSnapshot,
        decision: SwitchDecision,
        triggerReason: SwitchTriggerReason,
    ): EvaluationResult {
        if (decision.targetMode == TargetInputMode.KEEP) {
            return EvaluationResult(modeToApply = null, suppressed = true, reason = "policy-keep")
        }

        val now = clock()
        val contextKey = "${snapshot.dedupeKey()}|${decision.targetMode}|$triggerReason"
        if (contextKey == lastContextKey && now - lastEvaluationAt < DUPLICATE_CONTEXT_WINDOW_MS) {
            return EvaluationResult(modeToApply = null, suppressed = true, reason = "duplicate-context")
        }

        val targetMode = InputMode.valueOf(decision.targetMode.name)
        if (targetMode == lastAppliedMode && now - lastEvaluationAt < 800) {
            lastContextKey = contextKey
            lastEvaluationAt = now
            return EvaluationResult(modeToApply = null, suppressed = true, reason = "already-in-target-mode")
        }

        lastContextKey = contextKey
        lastEvaluationAt = now
        return EvaluationResult(modeToApply = targetMode, suppressed = false, reason = "switch-required")
    }

    fun markApplied(mode: InputMode) {
        lastAppliedMode = mode
        lastEvaluationAt = clock()
    }
}
