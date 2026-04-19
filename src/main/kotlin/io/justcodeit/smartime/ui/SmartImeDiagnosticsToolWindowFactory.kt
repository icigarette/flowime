package io.justcodeit.smartime.ui

import com.intellij.openapi.components.service
import com.intellij.openapi.Disposable
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.content.ContentFactory
import io.justcodeit.smartime.service.SmartImeProjectService
import java.awt.BorderLayout
import java.awt.Font
import java.awt.FlowLayout
import java.awt.datatransfer.StringSelection
import java.nio.file.Path
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.Timer

class SmartImeDiagnosticsToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val service = project.service<SmartImeProjectService>()
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
        val textArea = JBTextArea().apply {
            isEditable = false
            lineWrap = true
            wrapStyleWord = true
            font = Font(Font.MONOSPACED, Font.PLAIN, 12)
            text = service.renderDiagnostics()
        }
        val statusLabel = JBLabel("就绪")
        val autoRefreshCheckBox = JBCheckBox("自动刷新", true)
        val refreshButton = JButton("立即刷新")
        val copyButton = JButton("复制报告")
        val exportButton = JButton("导出报告")

        fun render() {
            textArea.text = service.renderDiagnostics()
            statusLabel.text = "已更新 ${LocalTime.now().format(timeFormatter)}"
        }

        refreshButton.addActionListener {
            val hasEditor = service.refreshDiagnosticsNow()
            render()
            if (!hasEditor) {
                statusLabel.text = "当前没有活动编辑器，已刷新适配器诊断 ${LocalTime.now().format(timeFormatter)}"
            }
        }

        copyButton.addActionListener {
            CopyPasteManager.getInstance().setContents(StringSelection(service.renderDiagnostics()))
            statusLabel.text = "已复制 ${LocalTime.now().format(timeFormatter)}"
        }

        exportButton.addActionListener {
            val reportPath: Path = service.exportStructuredReport()
            CopyPasteManager.getInstance().setContents(StringSelection(reportPath.toString()))
            statusLabel.text = "已导出 ${reportPath.fileName}，路径已复制 ${LocalTime.now().format(timeFormatter)}"
        }

        val timer = Timer(500) {
            if (autoRefreshCheckBox.isSelected) {
                render()
            }
        }.apply {
            start()
        }

        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT, 8, 8)).apply {
            add(refreshButton)
            add(copyButton)
            add(exportButton)
            add(autoRefreshCheckBox)
            add(statusLabel)
        }

        val rootPanel = JPanel(BorderLayout()).apply {
            add(toolbar, BorderLayout.NORTH)
            add(JBScrollPane(textArea), BorderLayout.CENTER)
        }

        render()

        val content = ContentFactory.getInstance().createContent(rootPanel, "", false)
        content.setDisposer(Disposable { timer.stop() })
        toolWindow.contentManager.addContent(content)
    }
}
