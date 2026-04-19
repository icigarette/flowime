package io.justcodeit.smartime.startup

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import io.justcodeit.smartime.service.SmartImeProjectService

class SmartImeProjectActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        project.service<SmartImeProjectService>()
    }
}

