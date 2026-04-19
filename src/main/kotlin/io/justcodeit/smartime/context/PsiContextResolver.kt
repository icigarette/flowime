package io.justcodeit.smartime.context

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import io.justcodeit.smartime.model.ContextSnapshot
import io.justcodeit.smartime.model.ContextType
import org.jetbrains.kotlin.psi.KtBlockStringTemplateEntry
import org.jetbrains.kotlin.psi.KtEscapeStringTemplateEntry
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtSimpleNameStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

class PsiContextResolver : ContextResolver {
    private val logger = Logger.getInstance(PsiContextResolver::class.java)

    override fun resolve(project: Project, editor: Editor, psiFile: PsiFile, caretOffset: Int): ContextSnapshot {
        val normalizedOffset = normalizeOffset(psiFile, caretOffset)
        val element = findElement(psiFile, normalizedOffset)
        val contextType = classifyContext(psiFile, element)

        return ContextSnapshot(
            fileType = psiFile.fileType.name,
            language = psiFile.language.id,
            offset = normalizedOffset,
            contextType = contextType,
            psiElementType = element?.node?.elementType?.toString() ?: element?.javaClass?.simpleName ?: "UNKNOWN",
            timestamp = System.currentTimeMillis(),
        )
    }

    private fun normalizeOffset(psiFile: PsiFile, caretOffset: Int): Int {
        val textLength = psiFile.textLength
        if (textLength <= 0) {
            return 0
        }
        return caretOffset.coerceIn(0, textLength - 1)
    }

    private fun findElement(psiFile: PsiFile, offset: Int): PsiElement? {
        val current = psiFile.findElementAt(offset)
        val previous = if (offset > 0) psiFile.findElementAt(offset - 1) else null

        if (current == null) {
            return previous
        }

        if (current is PsiWhiteSpace && previous != null) {
            if (current.text.contains('\n') || current.text.contains('\r')) {
                return previous
            }

            val nextOffset = current.textRange.endOffset.takeIf { it < psiFile.textLength }
            val next = nextOffset?.let { psiFile.findElementAt(it) }
            return next ?: previous
        }

        return current
    }

    private fun classifyContext(psiFile: PsiFile, element: PsiElement?): ContextType {
        if (element == null) {
            return ContextType.UNKNOWN
        }

        if (!isSupportedLanguage(psiFile.language.id)) {
            return ContextType.UNKNOWN
        }

        return try {
            val comment = PsiTreeUtil.getParentOfType(element, PsiComment::class.java, false)
            if (comment != null) {
                classifyComment(comment.text)
            } else if (isKotlinInterpolationExpression(psiFile, element)) {
                ContextType.CODE
            } else if (isKotlinStringLiteralContent(psiFile, element)) {
                ContextType.STRING_LITERAL
            } else if (isStringLiteral(psiFile, element)) {
                ContextType.STRING_LITERAL
            } else if (psiFile.language.id.equals("XML", ignoreCase = true)) {
                ContextType.UNKNOWN
            } else {
                ContextType.CODE
            }
        } catch (t: Throwable) {
            logger.warn("Failed to resolve PSI context", t)
            ContextType.UNKNOWN
        }
    }

    private fun classifyComment(text: String): ContextType {
        val normalized = text.trimStart()
        return when {
            normalized.startsWith("<!--") -> ContextType.XML_COMMENT
            normalized.startsWith("/**") -> ContextType.DOC_COMMENT
            normalized.startsWith("//") -> ContextType.LINE_COMMENT
            normalized.startsWith("/*") -> ContextType.BLOCK_COMMENT
            else -> ContextType.UNKNOWN
        }
    }

    private fun isStringLiteral(psiFile: PsiFile, element: PsiElement): Boolean {
        if (!isJavaOrKotlin(psiFile.language.id)) {
            return false
        }

        if (psiFile.language.id.equals("kotlin", ignoreCase = true)) {
            return false
        }

        val injectionHost = PsiTreeUtil.getParentOfType(element, PsiLanguageInjectionHost::class.java, false) ?: return false
        if (injectionHost is PsiComment) {
            return false
        }

        val text = injectionHost.text.trim()
        return text.startsWith("\"") || text.startsWith("'") || text.startsWith("\"\"\"")
    }

    private fun isJavaOrKotlin(languageId: String): Boolean {
        return languageId.equals("JAVA", ignoreCase = true) || languageId.equals("kotlin", ignoreCase = true)
    }

    private fun isSupportedLanguage(languageId: String): Boolean {
        return isJavaOrKotlin(languageId) || languageId.equals("XML", ignoreCase = true)
    }

    private fun isKotlinInterpolationExpression(psiFile: PsiFile, element: PsiElement): Boolean {
        if (!psiFile.language.id.equals("kotlin", ignoreCase = true)) {
            return false
        }

        val entry = PsiTreeUtil.getParentOfType(element, KtStringTemplateEntry::class.java, false) ?: return false
        return entry is KtBlockStringTemplateEntry || entry is KtSimpleNameStringTemplateEntry
    }

    private fun isKotlinStringLiteralContent(psiFile: PsiFile, element: PsiElement): Boolean {
        if (!psiFile.language.id.equals("kotlin", ignoreCase = true)) {
            return false
        }

        val entry = PsiTreeUtil.getParentOfType(element, KtStringTemplateEntry::class.java, false)
        if (entry is KtLiteralStringTemplateEntry || entry is KtEscapeStringTemplateEntry) {
            return true
        }

        val templateExpression = PsiTreeUtil.getParentOfType(element, KtStringTemplateExpression::class.java, false)
        return templateExpression != null && entry == null
    }
}
