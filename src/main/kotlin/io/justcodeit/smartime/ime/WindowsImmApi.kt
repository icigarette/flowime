package io.justcodeit.smartime.ime

import com.sun.jna.Native
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.win32.StdCallLibrary

interface WindowsImmApi : StdCallLibrary {
    fun ImmGetContext(hwnd: WinDef.HWND): HIMC?

    fun ImmReleaseContext(hwnd: WinDef.HWND, himc: HIMC): Boolean

    fun ImmGetOpenStatus(himc: HIMC): Boolean

    fun ImmSetOpenStatus(himc: HIMC, open: Boolean): Boolean

    fun ImmGetDefaultIMEWnd(hwnd: WinDef.HWND): WinDef.HWND?

    class HIMC : WinNT.HANDLE()

    companion object {
        val INSTANCE: WindowsImmApi by lazy {
            Native.load("Imm32", WindowsImmApi::class.java) as WindowsImmApi
        }
    }
}

