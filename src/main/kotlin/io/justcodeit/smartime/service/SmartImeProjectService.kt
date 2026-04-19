package io.justcodeit.smartime.service

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.ex.EditorEventMulticasterEx
import com.intellij.openapi.editor.ex.FocusChangeListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.psi.PsiDocumentManager
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.util.Alarm
import io.justcodeit.smartime.context.ContextResolver
import io.justcodeit.smartime.context.PsiContextResolver
import io.justcodeit.smartime.ime.ImeAdapterDiagnostics
import io.justcodeit.smartime.ime.ImeAdapter
import io.justcodeit.smartime.ime.ImeAdapterProvider
import io.justcodeit.smartime.model.ContextSnapshot
import io.justcodeit.smartime.model.ContextType
import io.justcodeit.smartime.model.ImeSwitchResult
import io.justcodeit.smartime.model.InputMode
import io.justcodeit.smartime.model.SwitchDecision
import io.justcodeit.smartime.model.SwitchStatus
import io.justcodeit.smartime.model.SwitchTriggerReason
import io.justcodeit.smartime.model.TargetInputMode
import io.justcodeit.smartime.policy.DefaultSwitchPolicyEngine
import io.justcodeit.smartime.policy.SwitchPolicyEngine
import io.justcodeit.smartime.policy.SwitchStateMachine
import io.justcodeit.smartime.settings.SmartImeSettingsState
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.ArrayDeque

@Service(Service.Level.PROJECT)
class SmartImeProjectService(
    private val project: Project,
) : Disposable {
    companion object {
        private const val FAST_MODE_CACHE_TTL_MS = 120L
        private const val MODE_CACHE_TTL_MS = 350L
    }

    private val logger = Logger.getInstance(SmartImeProjectService::class.java)
    private val contextResolver: ContextResolver = PsiContextResolver()
    private val policyEngine: SwitchPolicyEngine = DefaultSwitchPolicyEngine()
    private val stateMachine = SwitchStateMachine()
    private val adapterProvider = ImeAdapterProvider { SmartImeSettingsState.getInstance().snapshot() }
    private val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)
    private val diagnosticsLog = ArrayDeque<String>()
    private val sessionStats = SessionStats()

    @Volatile
    private var pendingRepeatedLog: RepeatedLogState? = null

    @Volatile
    private var lastKnownModeState: KnownModeState? = null

    @Volatile
    private var manualOverrideState: ManualOverrideState? = null

    @Volatile
    private var latestDiagnostics = DiagnosticsState()

    @Volatile
    private var pendingEvaluation: PendingEvaluation? = null

    init {
        registerListeners()
    }

    fun renderDiagnostics(): String {
        val state = latestDiagnostics
        val logTail = diagnosticsLog.joinToString(separator = "\n")
        return buildString {
            appendLine("项目：${project.name}")
            appendLine("适配器优先级：${adapterProvider.describePriority()}")
            appendLine("当前适配器：${state.adapterDescription}")
            appendLine("适配器可用：${state.adapterDiagnostics?.supported ?: "n/a"}")
            appendLine("当前输入模式：${state.adapterDiagnostics?.currentMode ?: "n/a"}")
            appendLine("当前输入源：${state.adapterDiagnostics?.currentInputSourceId ?: "n/a"}")
            appendLine("解析出的英文输入源：${state.adapterDiagnostics?.resolvedEnglishInputSourceId ?: "n/a"}")
            appendLine("解析出的中文输入源：${state.adapterDiagnostics?.resolvedChineseInputSourceId ?: "n/a"}")
            appendLine("当前上下文：${displayContextType(state.snapshot?.contextType)}")
            appendLine("语言：${state.snapshot?.language ?: "n/a"}")
            appendLine("PSI 元素：${state.snapshot?.psiElementType ?: "n/a"}")
            appendLine("决策：${displayTargetMode(state.decision?.targetMode)}")
            appendLine("决策原因：${state.decision?.explanation ?: "n/a"}")
            appendLine("最近结果：${displaySwitchStatus(state.result?.status)}")
            appendLine("结果详情：${state.result?.details ?: "n/a"}")
            appendLine("最近触发器：${displayTriggerReason(state.trigger)}")
            appendLine("最近耗时：${state.lastDurationMs?.let { "${it}ms" } ?: "n/a"}")
            appendLine("手动切换尊重期：${renderManualOverrideState()}")
            appendLine()
            appendLine("已启用输入源：")
            val enabledSources = state.adapterDiagnostics?.enabledInputSourceIds.orEmpty()
            appendLine(enabledSources.joinToString(separator = "\n").ifBlank { "n/a" })
            appendLine()
            appendLine("候选适配器：")
            appendLine(
                state.candidateDiagnostics.joinToString(separator = "\n") {
                    "${it.adapterId} | supported=${it.supported} | current=${it.currentInputSourceId ?: "n/a"} | ${it.supportDescription}"
                }.ifBlank { "n/a" },
            )
            appendLine()
            appendLine("会话统计：")
            appendLine("总评估次数：${sessionStats.totalEvaluations}")
            appendLine("已切换：${sessionStats.statusCounts[SwitchStatus.SWITCHED] ?: 0}")
            appendLine("已跳过：${sessionStats.statusCounts[SwitchStatus.SKIPPED] ?: 0}")
            appendLine("失败：${sessionStats.statusCounts[SwitchStatus.FAILED] ?: 0}")
            appendLine("不支持：${sessionStats.statusCounts[SwitchStatus.UNSUPPORTED] ?: 0}")
            appendLine("触发器分布：${sessionStats.topTriggerSummary().ifBlank { "n/a" }}")
            appendLine("上下文分布：${sessionStats.topContextSummary().ifBlank { "n/a" }}")
            appendLine("适配器分布：${sessionStats.topAdapterSummary().ifBlank { "n/a" }}")
            appendLine("高频结果原因：${sessionStats.topReasonSummary().ifBlank { "n/a" }}")
            appendLine("未知上下文 PSI：${sessionStats.topUnknownPsiSummary().ifBlank { "n/a" }}")
            appendLine("缓存短路次数：${sessionStats.cachedShortCircuits}")
            appendLine("手动切换尊重次数：${sessionStats.manualOverrideSkips}")
            appendLine()
            appendLine("最近事件：")
            appendLine(logTail.ifBlank { "暂无事件。" })
        }
    }

    fun refreshDiagnosticsNow(): Boolean {
        val selectedEditor = FileEditorManager.getInstance(project).selectedTextEditor
        return if (selectedEditor != null) {
            alarm.cancelAllRequests()
            processEvaluation(selectedEditor, SwitchTriggerReason.MANUAL_REFRESH)
            true
        } else {
            val adapter = adapterProvider.get()
            latestDiagnostics = latestDiagnostics.copy(
                adapterDescription = adapter.describeSupport(),
                adapterDiagnostics = adapter.inspect(),
                candidateDiagnostics = adapterProvider.inspectCandidates(),
                trigger = SwitchTriggerReason.MANUAL_REFRESH,
            )
            false
        }
    }

    override fun dispose() {
        flushRepeatedLogSummary()
    }

    fun exportStructuredReport(): Path {
        val timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneId.systemDefault())
            .format(Instant.now())
        val reportDir = Paths.get(System.getProperty("java.io.tmpdir"), "smart-ime-reports")
        Files.createDirectories(reportDir)
        val reportPath = reportDir.resolve("smart-ime-report-$timestamp.txt")
        Files.writeString(reportPath, renderStructuredReport(), StandardCharsets.UTF_8)
        return reportPath
    }

    private fun registerListeners() {
        val multicaster = EditorFactory.getInstance().eventMulticaster
        multicaster.addCaretListener(
            object : CaretListener {
                override fun caretPositionChanged(event: CaretEvent) {
                    schedule(event.editor, SwitchTriggerReason.CARET_MOVED)
                }
            },
            this,
        )

        (multicaster as? EditorEventMulticasterEx)?.addFocusChangeListener(
            object : FocusChangeListener {
                override fun focusGained(editor: Editor) {
                    schedule(editor, SwitchTriggerReason.EDITOR_FOCUSED)
                }
            },
            this,
        )

        project.messageBus.connect(this).subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun selectionChanged(event: FileEditorManagerEvent) {
                    val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return
                    schedule(editor, SwitchTriggerReason.FILE_SWITCHED)
                }
            },
        )
    }

    private fun schedule(editor: Editor, reason: SwitchTriggerReason) {
        if (editor.project != project) {
            return
        }

        pendingEvaluation = PendingEvaluation(editor, reason)
        val debounceMs = SmartImeSettingsState.getInstance().snapshot().debounceDelayMs
        alarm.cancelAllRequests()
        alarm.addRequest({ flushPending() }, debounceMs)
    }

    private fun flushPending() {
        val pending = pendingEvaluation ?: return
        pendingEvaluation = null
        processEvaluation(pending.editor, pending.reason)
    }

    private fun processEvaluation(editor: Editor, reason: SwitchTriggerReason) {
        if (project.isDisposed || editor.isDisposed) {
            return
        }

        val startedAtNs = System.nanoTime()
        val settings = SmartImeSettingsState.getInstance().snapshot()
        val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return

        val snapshot = contextResolver.resolve(project, editor, psiFile, editor.caretModel.offset)
        val decision = policyEngine.decide(snapshot, settings)
        val stateResult = stateMachine.evaluate(snapshot, decision, reason)
        val adapter = adapterProvider.get()
        val deepDiagnostics = reason == SwitchTriggerReason.MANUAL_REFRESH
        val adapterDiagnostics = if (deepDiagnostics) {
            adapter.inspect()
        } else {
            latestDiagnostics.adapterDiagnostics
        }
        val candidateDiagnostics = if (deepDiagnostics) {
            adapterProvider.inspectCandidates()
        } else {
            latestDiagnostics.candidateDiagnostics
        }
        val manualOverrideSkip = maybeRespectManualOverride(adapter, stateResult.modeToApply, reason)
        val switchResult = when {
            stateResult.suppressed -> {
                ImeSwitchResult(
                    status = SwitchStatus.SKIPPED,
                    adapterId = adapter.adapterId,
                    requestedMode = null,
                    details = stateResult.reason,
                )
            }

            stateResult.modeToApply == null -> {
                ImeSwitchResult(
                    status = SwitchStatus.SKIPPED,
                    adapterId = adapter.adapterId,
                    requestedMode = null,
                    details = "No mode requested",
                )
            }

            manualOverrideSkip != null -> manualOverrideSkip

            else -> {
                maybeShortCircuitWithKnownMode(adapter, stateResult.modeToApply, reason) ?: adapter.switchTo(stateResult.modeToApply)
            }
        }

        if (shouldRegisterManualOverride(stateResult.modeToApply, switchResult)) {
            registerManualOverride(switchResult.adapterId, stateResult.modeToApply!!)
        }

        if (switchResult.status == SwitchStatus.SWITCHED) {
            manualOverrideState = null
            stabilizeEditorInputContext(editor, switchResult)
        }

        if (shouldRememberKnownMode(switchResult) && stateResult.modeToApply != null) {
            stateMachine.markApplied(stateResult.modeToApply)
            rememberKnownMode(adapter.adapterId, stateResult.modeToApply)
        }

        val durationMs = (System.nanoTime() - startedAtNs) / 1_000_000

        updateDiagnostics(
            snapshot = snapshot,
            decision = decision,
            result = switchResult,
            trigger = reason,
            durationMs = durationMs,
            adapterDescription = if (deepDiagnostics) adapter.describeSupport() else latestDiagnostics.adapterDescription,
            adapterDiagnostics = adapterDiagnostics,
            candidateDiagnostics = candidateDiagnostics,
        )
    }

    private fun updateDiagnostics(
        snapshot: ContextSnapshot,
        decision: SwitchDecision,
        result: ImeSwitchResult,
        trigger: SwitchTriggerReason,
        durationMs: Long,
        adapterDescription: String,
        adapterDiagnostics: ImeAdapterDiagnostics?,
        candidateDiagnostics: List<ImeAdapterDiagnostics>,
    ) {
        latestDiagnostics = DiagnosticsState(
            snapshot = snapshot,
            decision = decision,
            result = result,
            trigger = trigger,
            adapterDescription = adapterDescription,
            adapterDiagnostics = adapterDiagnostics,
            candidateDiagnostics = candidateDiagnostics,
            lastDurationMs = durationMs,
        )

        sessionStats.record(snapshot, result, trigger)
        val record = EvaluationRecord(
            timestamp = Instant.now(),
            snapshot = snapshot,
            decision = decision,
            result = result,
            trigger = trigger,
            durationMs = durationMs,
        )
        val logLine = record.toUiSummary()
        if (diagnosticsLog.size >= 20) {
            diagnosticsLog.removeFirst()
        }
        diagnosticsLog.addLast(logLine)
        if (SmartImeSettingsState.getInstance().snapshot().debugLogging) {
            emitStructuredLog(record)
        }
    }

    private fun emitStructuredLog(record: EvaluationRecord) {
        val signature = record.signature()
        val currentRepeated = pendingRepeatedLog
        if (currentRepeated != null && currentRepeated.signature == signature) {
            pendingRepeatedLog = currentRepeated.copy(count = currentRepeated.count + 1)
            if (currentRepeated.count + 1 >= 10) {
                flushRepeatedLogSummary()
                pendingRepeatedLog = RepeatedLogState(signature = signature, sample = record, count = 0)
            }
            return
        }

        flushRepeatedLogSummary()
        pendingRepeatedLog = RepeatedLogState(signature = signature, sample = record, count = 0)
        logRecord(record)
    }

    private fun flushRepeatedLogSummary() {
        val repeated = pendingRepeatedLog ?: return
        if (repeated.count <= 0) {
            pendingRepeatedLog = null
            return
        }

        logger.info("${repeated.sample.toStructuredSummary()} | repeated=${repeated.count}")
        pendingRepeatedLog = null
    }

    private fun logRecord(record: EvaluationRecord) {
        when (record.result.status) {
            SwitchStatus.FAILED,
            SwitchStatus.UNSUPPORTED,
            -> logger.warn(record.toStructuredSummary())

            SwitchStatus.SWITCHED,
            SwitchStatus.SKIPPED,
            -> logger.info(record.toStructuredSummary())
        }
    }

    private fun renderStructuredReport(): String {
        val state = latestDiagnostics
        val settings = SmartImeSettingsState.getInstance().snapshot()
        return buildString {
            appendLine("# Smart IME Structured Report")
            appendLine()
            appendLine("generatedAt=${Instant.now()}")
            appendLine("projectName=${project.name}")
            appendLine("projectBasePath=${project.basePath ?: "n/a"}")
            appendLine("adapterPriority=${adapterProvider.describePriority()}")
            appendLine()
            appendLine("[settings]")
            appendLine("enabled=${settings.enabled}")
            appendLine("javaCommentsChinese=${settings.javaCommentsChinese}")
            appendLine("kotlinCommentsChinese=${settings.kotlinCommentsChinese}")
            appendLine("stringsChinese=${settings.stringsChinese}")
            appendLine("xmlCommentsChinese=${settings.xmlCommentsChinese}")
            appendLine("debugLogging=${settings.debugLogging}")
            appendLine("enableExperimentalNativeAdapters=${settings.enableExperimentalNativeAdapters}")
            appendLine("englishSwitchCommandConfigured=${settings.englishSwitchCommand.isNotBlank()}")
            appendLine("chineseSwitchCommandConfigured=${settings.chineseSwitchCommand.isNotBlank()}")
            appendLine("currentModeCommandConfigured=${settings.currentModeCommand.isNotBlank()}")
            appendLine("englishInputSourceId=${settings.englishInputSourceId.ifBlank { "n/a" }}")
            appendLine("chineseInputSourceId=${settings.chineseInputSourceId.ifBlank { "n/a" }}")
            appendLine("debounceDelayMs=${settings.debounceDelayMs}")
            appendLine("manualOverrideRespectMs=${settings.manualOverrideRespectMs}")
            appendLine()
            appendLine("[latest]")
            appendLine("adapterDescription=${state.adapterDescription}")
            appendLine("adapterSupported=${state.adapterDiagnostics?.supported ?: "n/a"}")
            appendLine("currentMode=${state.adapterDiagnostics?.currentMode ?: "n/a"}")
            appendLine("currentInputSourceId=${state.adapterDiagnostics?.currentInputSourceId ?: "n/a"}")
            appendLine("resolvedEnglishInputSourceId=${state.adapterDiagnostics?.resolvedEnglishInputSourceId ?: "n/a"}")
            appendLine("resolvedChineseInputSourceId=${state.adapterDiagnostics?.resolvedChineseInputSourceId ?: "n/a"}")
            appendLine("contextType=${state.snapshot?.contextType ?: "n/a"}")
            appendLine("language=${state.snapshot?.language ?: "n/a"}")
            appendLine("psiElementType=${state.snapshot?.psiElementType ?: "n/a"}")
            appendLine("decision=${state.decision?.targetMode ?: "n/a"}")
            appendLine("decisionExplanation=${state.decision?.explanation ?: "n/a"}")
            appendLine("resultStatus=${state.result?.status ?: "n/a"}")
            appendLine("resultAdapter=${state.result?.adapterId ?: "n/a"}")
            appendLine("resultDetails=${state.result?.details ?: "n/a"}")
            appendLine("trigger=${state.trigger ?: "n/a"}")
            appendLine("durationMs=${state.lastDurationMs ?: "n/a"}")
            appendLine()
            appendLine("[session]")
            appendLine("totalEvaluations=${sessionStats.totalEvaluations}")
            appendLine("switched=${sessionStats.statusCounts[SwitchStatus.SWITCHED] ?: 0}")
            appendLine("skipped=${sessionStats.statusCounts[SwitchStatus.SKIPPED] ?: 0}")
            appendLine("failed=${sessionStats.statusCounts[SwitchStatus.FAILED] ?: 0}")
            appendLine("unsupported=${sessionStats.statusCounts[SwitchStatus.UNSUPPORTED] ?: 0}")
            appendLine("triggerSummary=${sessionStats.topTriggerSummary().ifBlank { "n/a" }}")
            appendLine("contextSummary=${sessionStats.topContextSummary().ifBlank { "n/a" }}")
            appendLine("adapterSummary=${sessionStats.topAdapterSummary().ifBlank { "n/a" }}")
            appendLine("reasonSummary=${sessionStats.topReasonSummary().ifBlank { "n/a" }}")
            appendLine("unknownPsiSummary=${sessionStats.topUnknownPsiSummary().ifBlank { "n/a" }}")
            appendLine("cachedShortCircuits=${sessionStats.cachedShortCircuits}")
            appendLine("manualOverrideSkips=${sessionStats.manualOverrideSkips}")
            appendLine("manualOverrideState=${renderManualOverrideState()}")
            appendLine()
            appendLine("[candidates]")
            if (state.candidateDiagnostics.isEmpty()) {
                appendLine("n/a")
            } else {
                state.candidateDiagnostics.forEach { candidate ->
                    appendLine(
                        listOf(
                            "adapterId=${candidate.adapterId}",
                            "supported=${candidate.supported}",
                            "currentMode=${candidate.currentMode ?: "n/a"}",
                            "currentInputSourceId=${candidate.currentInputSourceId ?: "n/a"}",
                            "resolvedEnglish=${candidate.resolvedEnglishInputSourceId ?: "n/a"}",
                            "resolvedChinese=${candidate.resolvedChineseInputSourceId ?: "n/a"}",
                            "supportDescription=${candidate.supportDescription}",
                        ).joinToString(" | "),
                    )
                }
            }
            appendLine()
            appendLine("[recentEvents]")
            if (diagnosticsLog.isEmpty()) {
                appendLine("n/a")
            } else {
                diagnosticsLog.forEach { appendLine(it) }
            }
        }
    }

    private fun stabilizeEditorInputContext(editor: Editor, result: ImeSwitchResult) {
        if (!SystemInfo.isMac || result.requestedMode != InputMode.CHINESE) {
            return
        }

        val component = editor.contentComponent
        IdeFocusManager.getInstance(project).doWhenFocusSettlesDown {
            IdeFocusManager.getInstance(project).requestFocus(component, true)
        }
    }

    private data class PendingEvaluation(
        val editor: Editor,
        val reason: SwitchTriggerReason,
    )

    private data class DiagnosticsState(
        val snapshot: ContextSnapshot? = null,
        val decision: SwitchDecision? = null,
        val result: ImeSwitchResult? = null,
        val trigger: SwitchTriggerReason? = null,
        val adapterDescription: String = "n/a",
        val adapterDiagnostics: ImeAdapterDiagnostics? = null,
        val candidateDiagnostics: List<ImeAdapterDiagnostics> = emptyList(),
        val lastDurationMs: Long? = null,
    )

    private data class EvaluationRecord(
        val timestamp: Instant,
        val snapshot: ContextSnapshot,
        val decision: SwitchDecision,
        val result: ImeSwitchResult,
        val trigger: SwitchTriggerReason,
        val durationMs: Long,
    ) {
        fun signature(): String {
            return listOf(
                trigger.name,
                snapshot.contextType.name,
                snapshot.language,
                snapshot.psiElementType,
                decision.targetMode.name,
                result.status.name,
                result.adapterId,
                result.details,
            ).joinToString("|")
        }

        fun toStructuredSummary(): String {
            return buildString {
                append(timestamp)
                append(" trigger=").append(trigger)
                append(" context=").append(snapshot.contextType)
                append(" lang=").append(snapshot.language)
                append(" psi=").append(snapshot.psiElementType)
                append(" offset=").append(snapshot.offset)
                append(" target=").append(decision.targetMode)
                append(" status=").append(result.status)
                append(" adapter=").append(result.adapterId)
                append(" durationMs=").append(durationMs)
                append(" reason=").append(result.details)
            }
        }

        fun toUiSummary(): String {
            return "${timestamp} [$trigger] ${snapshot.contextType} -> ${decision.targetMode} (${result.status}: ${result.details}, ${durationMs}ms)"
        }
    }

    private data class RepeatedLogState(
        val signature: String,
        val sample: EvaluationRecord,
        val count: Int,
    )

    private class SessionStats {
        var totalEvaluations: Int = 0
            private set

        val statusCounts: MutableMap<SwitchStatus, Int> = linkedMapOf()
        private val triggerCounts: MutableMap<SwitchTriggerReason, Int> = linkedMapOf()
        private val contextCounts: MutableMap<String, Int> = linkedMapOf()
        private val adapterCounts: MutableMap<String, Int> = linkedMapOf()
        private val reasonCounts: MutableMap<String, Int> = linkedMapOf()
        private val unknownPsiCounts: MutableMap<String, Int> = linkedMapOf()

        fun record(
            snapshot: ContextSnapshot,
            result: ImeSwitchResult,
            trigger: SwitchTriggerReason,
        ) {
            totalEvaluations += 1
            statusCounts.increment(result.status)
            triggerCounts.increment(trigger)
            contextCounts.increment(snapshot.contextType.name)
            adapterCounts.increment(result.adapterId)
            reasonCounts.increment("${result.status}:${result.details}")
            if (result.details == "cached-current-mode") {
                cachedShortCircuits += 1
            }
            if (result.details == "manual-override-respected") {
                manualOverrideSkips += 1
            }
            if (snapshot.contextType.name == "UNKNOWN") {
                unknownPsiCounts.increment(snapshot.psiElementType)
            }
        }

        fun topTriggerSummary(): String = triggerCounts.topSummary()

        fun topContextSummary(): String = contextCounts.topSummary()

        fun topAdapterSummary(): String = adapterCounts.topSummary()

        fun topReasonSummary(): String = reasonCounts.topSummary(limit = 5)

        fun topUnknownPsiSummary(): String = unknownPsiCounts.topSummary(limit = 5)

        var cachedShortCircuits: Int = 0
            private set

        var manualOverrideSkips: Int = 0
            private set

        private fun <K> MutableMap<K, Int>.increment(key: K) {
            this[key] = (this[key] ?: 0) + 1
        }

        private fun <K> Map<K, Int>.topSummary(limit: Int = 4): String {
            return entries
                .sortedByDescending { it.value }
                .take(limit)
                .joinToString(separator = ", ") { "${it.key}=${it.value}" }
        }
    }

    private fun canShortCircuitWithCachedMode(
        adapterId: String,
        targetMode: InputMode,
        reason: SwitchTriggerReason,
    ): Boolean {
        if (reason != SwitchTriggerReason.CARET_MOVED) {
            return false
        }

        val cached = lastKnownModeState ?: return false
        if (cached.adapterId != adapterId || cached.mode != targetMode) {
            return false
        }

        return System.currentTimeMillis() - cached.observedAtMs <= MODE_CACHE_TTL_MS
    }

    private fun maybeShortCircuitWithKnownMode(
        adapter: ImeAdapter,
        targetMode: InputMode,
        reason: SwitchTriggerReason,
    ): ImeSwitchResult? {
        if (!canShortCircuitWithCachedMode(adapter.adapterId, targetMode, reason)) {
            return null
        }

        val cached = lastKnownModeState ?: return null
        val ageMs = System.currentTimeMillis() - cached.observedAtMs
        if (ageMs <= FAST_MODE_CACHE_TTL_MS) {
            return ImeSwitchResult(
                status = SwitchStatus.SKIPPED,
                adapterId = adapter.adapterId,
                requestedMode = targetMode,
                details = "cached-current-mode",
            )
        }

        val actualMode = adapter.getCurrentMode()
        if (actualMode == targetMode) {
            return ImeSwitchResult(
                status = SwitchStatus.SKIPPED,
                adapterId = adapter.adapterId,
                requestedMode = targetMode,
                details = "verified-current-mode",
            )
        }

        lastKnownModeState = null
        return null
    }

    private fun maybeRespectManualOverride(
        adapter: ImeAdapter,
        targetMode: InputMode?,
        reason: SwitchTriggerReason,
    ): ImeSwitchResult? {
        if (targetMode == null || reason == SwitchTriggerReason.MANUAL_REFRESH) {
            return null
        }

        val activeOverride = activeManualOverrideState() ?: return null
        if (activeOverride.adapterId != adapter.adapterId || activeOverride.mode == targetMode) {
            return null
        }

        return ImeSwitchResult(
            status = SwitchStatus.SKIPPED,
            adapterId = adapter.adapterId,
            requestedMode = targetMode,
            details = "manual-override-respected",
        )
    }

    private fun shouldRegisterManualOverride(
        targetMode: InputMode?,
        switchResult: ImeSwitchResult,
    ): Boolean {
        if (targetMode == null || switchResult.status != SwitchStatus.SKIPPED) {
            return false
        }

        if (!switchResult.details.startsWith("Already in ")) {
            return false
        }

        val knownMode = lastKnownModeState ?: return false
        return knownMode.adapterId == switchResult.adapterId && knownMode.mode != targetMode
    }

    private fun registerManualOverride(adapterId: String, actualMode: InputMode) {
        val now = System.currentTimeMillis()
        val respectMs = SmartImeSettingsState.getInstance().snapshot().manualOverrideRespectMs.toLong().coerceAtLeast(0)
        manualOverrideState = ManualOverrideState(
            adapterId = adapterId,
            mode = actualMode,
            detectedAtMs = now,
            expiresAtMs = now + respectMs,
        )
        rememberKnownMode(adapterId, actualMode)
    }

    private fun activeManualOverrideState(): ManualOverrideState? {
        val state = manualOverrideState ?: return null
        if (System.currentTimeMillis() <= state.expiresAtMs) {
            return state
        }
        manualOverrideState = null
        return null
    }

    private fun shouldRememberKnownMode(result: ImeSwitchResult): Boolean {
        return when (result.status) {
            SwitchStatus.SWITCHED -> result.requestedMode != null
            SwitchStatus.SKIPPED -> {
                result.requestedMode != null && (
                    result.details.startsWith("Already in ")
                    )
            }

            SwitchStatus.FAILED,
            SwitchStatus.UNSUPPORTED,
            -> false
        }
    }

    private fun rememberKnownMode(adapterId: String, mode: io.justcodeit.smartime.model.InputMode) {
        lastKnownModeState = KnownModeState(
            adapterId = adapterId,
            mode = mode,
            observedAtMs = System.currentTimeMillis(),
        )
    }

    private fun renderManualOverrideState(): String {
        val state = activeManualOverrideState() ?: return "未激活"
        val remainingMs = (state.expiresAtMs - System.currentTimeMillis()).coerceAtLeast(0)
        return "${displayInputMode(state.mode)}，剩余 ${remainingMs}ms"
    }

    private fun displayContextType(contextType: ContextType?): String {
        return when (contextType) {
            ContextType.CODE -> "代码 (CODE)"
            ContextType.LINE_COMMENT -> "行注释 (LINE_COMMENT)"
            ContextType.BLOCK_COMMENT -> "块注释 (BLOCK_COMMENT)"
            ContextType.DOC_COMMENT -> "文档注释 (DOC_COMMENT)"
            ContextType.STRING_LITERAL -> "字符串 (STRING_LITERAL)"
            ContextType.XML_COMMENT -> "XML 注释 (XML_COMMENT)"
            ContextType.UNKNOWN -> "未知 (UNKNOWN)"
            null -> "n/a"
        }
    }

    private fun displayInputMode(mode: InputMode?): String {
        return when (mode) {
            InputMode.ENGLISH -> "英文 (ENGLISH)"
            InputMode.CHINESE -> "中文 (CHINESE)"
            null -> "n/a"
        }
    }

    private fun displayTargetMode(mode: TargetInputMode?): String {
        return when (mode) {
            TargetInputMode.ENGLISH -> "英文 (ENGLISH)"
            TargetInputMode.CHINESE -> "中文 (CHINESE)"
            TargetInputMode.KEEP -> "保持 (KEEP)"
            null -> "n/a"
        }
    }

    private fun displaySwitchStatus(status: SwitchStatus?): String {
        return when (status) {
            SwitchStatus.SWITCHED -> "已切换 (SWITCHED)"
            SwitchStatus.SKIPPED -> "已跳过 (SKIPPED)"
            SwitchStatus.FAILED -> "失败 (FAILED)"
            SwitchStatus.UNSUPPORTED -> "不支持 (UNSUPPORTED)"
            null -> "n/a"
        }
    }

    private fun displayTriggerReason(trigger: SwitchTriggerReason?): String {
        return when (trigger) {
            SwitchTriggerReason.CARET_MOVED -> "光标移动 (CARET_MOVED)"
            SwitchTriggerReason.FILE_SWITCHED -> "文件切换 (FILE_SWITCHED)"
            SwitchTriggerReason.EDITOR_FOCUSED -> "编辑器聚焦 (EDITOR_FOCUSED)"
            SwitchTriggerReason.MANUAL_REFRESH -> "手动刷新 (MANUAL_REFRESH)"
            null -> "n/a"
        }
    }

    private data class KnownModeState(
        val adapterId: String,
        val mode: InputMode,
        val observedAtMs: Long,
    )

    private data class ManualOverrideState(
        val adapterId: String,
        val mode: InputMode,
        val detectedAtMs: Long,
        val expiresAtMs: Long,
    )
}
