package io.justcodeit.smartime.ime

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo
import com.sun.jna.Native
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import io.justcodeit.smartime.model.ImeSwitchResult
import io.justcodeit.smartime.model.InputMode
import io.justcodeit.smartime.model.SwitchStatus
import io.justcodeit.smartime.settings.SmartImeSettingsState

class WindowsNativeImeAdapter(
    private val settingsProvider: () -> SmartImeSettingsState.State,
) : ImeAdapter {
    private val logger = Logger.getInstance(WindowsNativeImeAdapter::class.java)
    private val user32 = User32.INSTANCE
    private val imm32 = loadImmApi()

    override val adapterId: String = "windows-native"

    override fun isSupported(): Boolean = SystemInfo.isWindows && imm32 != null && isImmEnabled()

    override fun inspect(): ImeAdapterDiagnostics {
        val foregroundWindow = getForegroundWindow()
        val currentOpenStatus = foregroundWindow?.let { getOpenStatus(it) }
        return ImeAdapterDiagnostics(
            adapterId = adapterId,
            supported = isSupported(),
            supportDescription = describeSupport(),
            currentMode = when (currentOpenStatus) {
                true -> InputMode.CHINESE
                false -> InputMode.ENGLISH
                null -> null
            },
            currentInputSourceId = getKeyboardLayoutName(),
            resolvedEnglishInputSourceId = "ime-open=false",
            resolvedChineseInputSourceId = "ime-open=true",
        )
    }

    override fun getCurrentMode(): InputMode? {
        val foregroundWindow = getForegroundWindow() ?: return null
        return when (getOpenStatus(foregroundWindow)) {
            true -> InputMode.CHINESE
            false -> InputMode.ENGLISH
            null -> null
        }
    }

    override fun switchTo(mode: InputMode): ImeSwitchResult {
        if (!isSupported()) {
            return ImeSwitchResult(
                status = SwitchStatus.UNSUPPORTED,
                adapterId = adapterId,
                requestedMode = mode,
                details = "Windows native IME adapter is unavailable",
            )
        }

        if (getCurrentMode() == mode) {
            return ImeSwitchResult(
                status = SwitchStatus.SKIPPED,
                adapterId = adapterId,
                requestedMode = mode,
                details = "Already in $mode",
            )
        }

        val foregroundWindow = getForegroundWindow()
            ?: return ImeSwitchResult(
                status = SwitchStatus.FAILED,
                adapterId = adapterId,
                requestedMode = mode,
                details = "No foreground window available for IME switching",
            )

        val targetOpen = mode == InputMode.CHINESE
        if (setOpenStatus(foregroundWindow, targetOpen)) {
            return ImeSwitchResult(
                status = SwitchStatus.SWITCHED,
                adapterId = adapterId,
                requestedMode = mode,
                details = "Switched via Windows IMM open status to $targetOpen",
            )
        }

        if (setOpenStatusViaDefaultImeWindow(foregroundWindow, targetOpen)) {
            return ImeSwitchResult(
                status = SwitchStatus.SWITCHED,
                adapterId = adapterId,
                requestedMode = mode,
                details = "Switched via Windows default IME window message to $targetOpen",
            )
        }

        return ImeSwitchResult(
            status = SwitchStatus.FAILED,
            adapterId = adapterId,
            requestedMode = mode,
            details = "Failed to change Windows IME open status",
        )
    }

    override fun describeSupport(): String {
        if (!SystemInfo.isWindows) {
            return "Windows native IME adapter unavailable on non-Windows system"
        }

        if (imm32 == null) {
            return "Imm32 DLL could not be loaded"
        }

        return buildString {
            append("Using Windows IMM32 open-status adapter")
            append(" | immEnabled=")
            append(isImmEnabled())
            append(" | keyboardLayout=")
            append(getKeyboardLayoutName().ifBlank { "unknown" })
        }
    }

    private fun getForegroundWindow(): WinDef.HWND? {
        return try {
            user32.GetForegroundWindow()
        } catch (t: Throwable) {
            logger.warn("Failed to query Windows foreground window", t)
            null
        }
    }

    private fun getOpenStatus(hwnd: WinDef.HWND): Boolean? {
        val api = imm32 ?: return null
        val himc = try {
            api.ImmGetContext(hwnd)
        } catch (t: Throwable) {
            logger.warn("ImmGetContext failed", t)
            null
        } ?: return getOpenStatusViaDefaultImeWindow(hwnd)

        return try {
            api.ImmGetOpenStatus(himc)
        } catch (t: Throwable) {
            logger.warn("ImmGetOpenStatus failed", t)
            null
        } finally {
            runCatching { api.ImmReleaseContext(hwnd, himc) }
        }
    }

    private fun setOpenStatus(hwnd: WinDef.HWND, targetOpen: Boolean): Boolean {
        val api = imm32 ?: return false
        val himc = try {
            api.ImmGetContext(hwnd)
        } catch (t: Throwable) {
            logger.warn("ImmGetContext failed before setting open status", t)
            null
        } ?: return false

        return try {
            api.ImmSetOpenStatus(himc, targetOpen)
        } catch (t: Throwable) {
            logger.warn("ImmSetOpenStatus failed", t)
            false
        } finally {
            runCatching { api.ImmReleaseContext(hwnd, himc) }
        }
    }

    private fun getOpenStatusViaDefaultImeWindow(hwnd: WinDef.HWND): Boolean? {
        val imeWindow = try {
            imm32?.ImmGetDefaultIMEWnd(hwnd)
        } catch (t: Throwable) {
            logger.warn("ImmGetDefaultIMEWnd failed", t)
            null
        } ?: return null

        return try {
            val result = user32.SendMessage(
                imeWindow,
                WM_IME_CONTROL,
                WinDef.WPARAM(IMC_GETOPENSTATUS.toLong()),
                WinDef.LPARAM(0),
            )
            result.toInt() != 0
        } catch (t: Throwable) {
            logger.warn("WM_IME_CONTROL/IMC_GETOPENSTATUS failed", t)
            null
        }
    }

    private fun setOpenStatusViaDefaultImeWindow(hwnd: WinDef.HWND, targetOpen: Boolean): Boolean {
        val imeWindow = try {
            imm32?.ImmGetDefaultIMEWnd(hwnd)
        } catch (t: Throwable) {
            logger.warn("ImmGetDefaultIMEWnd failed before set", t)
            null
        } ?: return false

        return try {
            user32.SendMessage(
                imeWindow,
                WM_IME_CONTROL,
                WinDef.WPARAM(IMC_SETOPENSTATUS.toLong()),
                WinDef.LPARAM(if (targetOpen) 1 else 0),
            )
            getOpenStatusViaDefaultImeWindow(hwnd) == targetOpen
        } catch (t: Throwable) {
            logger.warn("WM_IME_CONTROL/IMC_SETOPENSTATUS failed", t)
            false
        }
    }

    private fun getKeyboardLayoutName(): String {
        return try {
            val buffer = CharArray(WinUser.KL_NAMELENGTH)
            if (user32.GetKeyboardLayoutName(buffer)) {
                Native.toString(buffer).orEmpty()
            } else {
                ""
            }
        } catch (t: Throwable) {
            logger.warn("Failed to read Windows keyboard layout name", t)
            ""
        }
    }

    private fun isImmEnabled(): Boolean {
        return try {
            user32.GetSystemMetrics(WinUser.SM_IMMENABLED) != 0
        } catch (t: Throwable) {
            logger.warn("Failed to query SM_IMMENABLED", t)
            false
        }
    }

    private fun loadImmApi(): WindowsImmApi? {
        if (!SystemInfo.isWindows) {
            return null
        }

        return try {
            WindowsImmApi.INSTANCE
        } catch (t: Throwable) {
            logger.warn("Failed to load Windows Imm32 API", t)
            null
        }
    }

    companion object {
        private const val WM_IME_CONTROL = 0x0283
        private const val IMC_GETOPENSTATUS = 0x0005
        private const val IMC_SETOPENSTATUS = 0x0006
    }
}
