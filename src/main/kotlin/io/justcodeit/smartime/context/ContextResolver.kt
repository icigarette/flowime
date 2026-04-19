package io.justcodeit.smartime.context

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import io.justcodeit.smartime.model.ContextSnapshot

interface ContextResolver {
    fun resolve(project: Project, editor: Editor, psiFile: PsiFile, caretOffset: Int): ContextSnapshot
}

