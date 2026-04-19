package io.justcodeit.smartime.settings

import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

class SmartImeSettingsConfigurable : Configurable {
    private val enabledCheckBox = JBCheckBox("启用自动输入法切换")
    private val javaCommentsCheckBox = JBCheckBox("Java 注释切换为中文")
    private val kotlinCommentsCheckBox = JBCheckBox("Kotlin 注释切换为中文")
    private val stringsCheckBox = JBCheckBox("Java / Kotlin 字符串切换为中文")
    private val xmlCommentsCheckBox = JBCheckBox("XML 注释切换为中文")
    private val debugLoggingCheckBox = JBCheckBox("启用调试日志（写入 idea.log）")
    private val nativeAdaptersCheckBox = JBCheckBox("启用实验性原生 IME 适配器（部分系统可能导致 IDEA 崩溃）")
    private val englishCommandField = JBTextField().apply {
        emptyText.text = "例如：im-select com.apple.keylayout.ABC"
        toolTipText = "切换到英文输入法时执行的命令"
    }
    private val chineseCommandField = JBTextField().apply {
        emptyText.text = "例如：im-select com.apple.inputmethod.SCIM.ITABC"
        toolTipText = "切换到中文输入法时执行的命令"
    }
    private val currentModeCommandField = JBTextField().apply {
        emptyText.text = "可选：输出 ENGLISH 或 CHINESE"
        toolTipText = "用于探测当前输入模式的命令，输出必须是 ENGLISH 或 CHINESE"
    }
    private val englishInputField = JBTextField().apply {
        emptyText.text = "例如：com.apple.keylayout.ABC"
        toolTipText = "im-select 或部分适配器使用的英文输入源 ID"
    }
    private val chineseInputField = JBTextField().apply {
        emptyText.text = "例如：com.apple.inputmethod.SCIM.ITABC"
        toolTipText = "im-select 或部分适配器使用的中文输入源 ID"
    }
    private val debounceDelayField = JBTextField().apply {
        emptyText.text = "默认 90"
        toolTipText = "事件防抖时间，单位毫秒"
    }
    private val manualOverrideRespectField = JBTextField().apply {
        emptyText.text = "默认 1500"
        toolTipText = "检测到你手动切换输入法后，插件暂时不改回去的时间，单位毫秒"
    }

    override fun getDisplayName(): String = "FlowIME 输入切换"

    override fun createComponent(): JComponent {
        return panel {
            row {
                cell(enabledCheckBox)
            }
            group("切换规则") {
                row { cell(javaCommentsCheckBox) }
                row { cell(kotlinCommentsCheckBox) }
                row { cell(stringsCheckBox) }
                row { cell(xmlCommentsCheckBox) }
            }
            group("用户意图保护") {
                row("手动切换尊重期（毫秒）") { cell(manualOverrideRespectField) }
            }
            group("安全与诊断") {
                row { cell(debugLoggingCheckBox) }
                row { cell(nativeAdaptersCheckBox) }
            }
            group("命令适配器（推荐）") {
                row("英文切换命令") { cell(englishCommandField) }
                row("中文切换命令") { cell(chineseCommandField) }
                row("当前模式探测命令") { cell(currentModeCommandField) }
            }
            group("输入源与性能") {
                row("英文输入源 ID") { cell(englishInputField) }
                row("中文输入源 ID") { cell(chineseInputField) }
                row("防抖延迟（毫秒）") { cell(debounceDelayField) }
            }
        }
    }

    override fun isModified(): Boolean {
        val state = SmartImeSettingsState.getInstance().snapshot()
        return enabledCheckBox.isSelected != state.enabled ||
            javaCommentsCheckBox.isSelected != state.javaCommentsChinese ||
            kotlinCommentsCheckBox.isSelected != state.kotlinCommentsChinese ||
            stringsCheckBox.isSelected != state.stringsChinese ||
            xmlCommentsCheckBox.isSelected != state.xmlCommentsChinese ||
            debugLoggingCheckBox.isSelected != state.debugLogging ||
            nativeAdaptersCheckBox.isSelected != state.enableExperimentalNativeAdapters ||
            englishCommandField.text != state.englishSwitchCommand ||
            chineseCommandField.text != state.chineseSwitchCommand ||
            currentModeCommandField.text != state.currentModeCommand ||
            englishInputField.text != state.englishInputSourceId ||
            chineseInputField.text != state.chineseInputSourceId ||
            debounceDelayField.text != state.debounceDelayMs.toString() ||
            manualOverrideRespectField.text != state.manualOverrideRespectMs.toString()
    }

    override fun apply() {
        SmartImeSettingsState.getInstance().update(
            SmartImeSettingsState.State(
                enabled = enabledCheckBox.isSelected,
                javaCommentsChinese = javaCommentsCheckBox.isSelected,
                kotlinCommentsChinese = kotlinCommentsCheckBox.isSelected,
                stringsChinese = stringsCheckBox.isSelected,
                xmlCommentsChinese = xmlCommentsCheckBox.isSelected,
                debugLogging = debugLoggingCheckBox.isSelected,
                enableExperimentalNativeAdapters = nativeAdaptersCheckBox.isSelected,
                englishSwitchCommand = englishCommandField.text.trim(),
                chineseSwitchCommand = chineseCommandField.text.trim(),
                currentModeCommand = currentModeCommandField.text.trim(),
                englishInputSourceId = englishInputField.text.trim(),
                chineseInputSourceId = chineseInputField.text.trim(),
                debounceDelayMs = debounceDelayField.text.toIntOrNull()?.coerceAtLeast(10) ?: 90,
                manualOverrideRespectMs = manualOverrideRespectField.text.toIntOrNull()?.coerceAtLeast(0) ?: 1_500,
            ),
        )
    }

    override fun reset() {
        val state = SmartImeSettingsState.getInstance().snapshot()
        enabledCheckBox.isSelected = state.enabled
        javaCommentsCheckBox.isSelected = state.javaCommentsChinese
        kotlinCommentsCheckBox.isSelected = state.kotlinCommentsChinese
        stringsCheckBox.isSelected = state.stringsChinese
        xmlCommentsCheckBox.isSelected = state.xmlCommentsChinese
        debugLoggingCheckBox.isSelected = state.debugLogging
        nativeAdaptersCheckBox.isSelected = state.enableExperimentalNativeAdapters
        englishCommandField.text = state.englishSwitchCommand
        chineseCommandField.text = state.chineseSwitchCommand
        currentModeCommandField.text = state.currentModeCommand
        englishInputField.text = state.englishInputSourceId
        chineseInputField.text = state.chineseInputSourceId
        debounceDelayField.text = state.debounceDelayMs.toString()
        manualOverrideRespectField.text = state.manualOverrideRespectMs.toString()
    }
}
