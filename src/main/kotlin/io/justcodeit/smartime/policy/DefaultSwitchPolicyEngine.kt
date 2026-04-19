package io.justcodeit.smartime.policy

import io.justcodeit.smartime.model.ContextSnapshot
import io.justcodeit.smartime.model.ContextType
import io.justcodeit.smartime.model.SwitchDecision
import io.justcodeit.smartime.model.TargetInputMode
import io.justcodeit.smartime.settings.SmartImeSettingsState

class DefaultSwitchPolicyEngine : SwitchPolicyEngine {
    override fun decide(snapshot: ContextSnapshot, settings: SmartImeSettingsState.State): SwitchDecision {
        if (!settings.enabled) {
            return SwitchDecision(TargetInputMode.KEEP, "plugin-disabled")
        }

        return when (snapshot.contextType) {
            ContextType.CODE -> SwitchDecision(TargetInputMode.ENGLISH, "code-defaults-to-english")
            ContextType.LINE_COMMENT,
            ContextType.BLOCK_COMMENT,
            ContextType.DOC_COMMENT,
            -> {
                val enabled = when {
                    snapshot.language.equals("JAVA", ignoreCase = true) -> settings.javaCommentsChinese
                    snapshot.language.equals("kotlin", ignoreCase = true) -> settings.kotlinCommentsChinese
                    else -> false
                }
                if (enabled) {
                    SwitchDecision(TargetInputMode.CHINESE, "comment-defaults-to-chinese")
                } else {
                    SwitchDecision(TargetInputMode.KEEP, "comment-rule-disabled")
                }
            }

            ContextType.STRING_LITERAL -> {
                if (settings.stringsChinese) {
                    SwitchDecision(TargetInputMode.CHINESE, "string-literal-defaults-to-chinese")
                } else {
                    SwitchDecision(TargetInputMode.KEEP, "string-rule-disabled")
                }
            }

            ContextType.XML_COMMENT -> {
                if (settings.xmlCommentsChinese) {
                    SwitchDecision(TargetInputMode.CHINESE, "xml-comment-defaults-to-chinese")
                } else {
                    SwitchDecision(TargetInputMode.KEEP, "xml-comment-rule-disabled")
                }
            }

            ContextType.UNKNOWN -> SwitchDecision(TargetInputMode.KEEP, "unknown-context-kept")
        }
    }
}

