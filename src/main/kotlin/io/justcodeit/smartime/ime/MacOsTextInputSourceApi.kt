package io.justcodeit.smartime.ime

import com.sun.jna.Library
import com.sun.jna.Pointer
import com.sun.jna.platform.mac.CoreFoundation

interface MacOsTextInputSourceApi : Library {
    fun TISCopyCurrentKeyboardInputSource(): CoreFoundation.CFTypeRef?

    fun TISCopyCurrentASCIICapableKeyboardInputSource(): CoreFoundation.CFTypeRef?

    fun TISCopyInputSourceForLanguage(language: CoreFoundation.CFStringRef): CoreFoundation.CFTypeRef?

    fun TISCreateInputSourceList(
        properties: CoreFoundation.CFDictionaryRef?,
        includeAllInstalled: Byte,
    ): CoreFoundation.CFArrayRef?

    fun TISGetInputSourceProperty(
        inputSource: CoreFoundation.CFTypeRef,
        propertyKey: CoreFoundation.CFStringRef,
    ): Pointer?

    fun TISSelectInputSource(inputSource: CoreFoundation.CFTypeRef): Int
}
