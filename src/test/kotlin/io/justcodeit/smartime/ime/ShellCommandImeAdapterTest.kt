package io.justcodeit.smartime.ime

import com.intellij.openapi.util.SystemInfo
import io.justcodeit.smartime.model.InputMode
import io.justcodeit.smartime.model.SwitchStatus
import io.justcodeit.smartime.settings.SmartImeSettingsState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShellCommandImeAdapterTest {
    @Test
    fun `is supported when both switch commands are configured`() {
        val adapter = ShellCommandImeAdapter {
            SmartImeSettingsState.State(
                englishSwitchCommand = successCommand(),
                chineseSwitchCommand = successCommand(),
            )
        }

        assertTrue(adapter.isSupported())
    }

    @Test
    fun `parses current mode command output`() {
        val adapter = ShellCommandImeAdapter {
            SmartImeSettingsState.State(
                englishSwitchCommand = successCommand(),
                chineseSwitchCommand = successCommand(),
                currentModeCommand = printCommand("english"),
            )
        }

        assertEquals(InputMode.ENGLISH, adapter.getCurrentMode())
    }

    @Test
    fun `returns failed when switch command exits non-zero`() {
        val adapter = ShellCommandImeAdapter {
            SmartImeSettingsState.State(
                englishSwitchCommand = failingCommand(),
                chineseSwitchCommand = successCommand(),
            )
        }

        val result = adapter.switchTo(InputMode.ENGLISH)
        assertEquals(SwitchStatus.FAILED, result.status)
    }

    @Test
    fun `is unsupported when commands are missing`() {
        val adapter = ShellCommandImeAdapter {
            SmartImeSettingsState.State(
                englishSwitchCommand = "",
                chineseSwitchCommand = successCommand(),
            )
        }

        assertFalse(adapter.isSupported())
    }

    private fun successCommand(): String {
        return if (SystemInfo.isWindows) {
            "exit /b 0"
        } else {
            "true"
        }
    }

    private fun failingCommand(): String {
        return if (SystemInfo.isWindows) {
            "exit /b 7"
        } else {
            "false"
        }
    }

    private fun printCommand(text: String): String {
        return if (SystemInfo.isWindows) {
            "echo $text"
        } else {
            "printf '%s' '$text'"
        }
    }
}
