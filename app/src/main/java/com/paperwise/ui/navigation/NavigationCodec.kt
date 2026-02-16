package com.paperwise.ui.navigation

import java.nio.charset.StandardCharsets
import java.util.Base64

object NavigationCodec {
    fun encodeValue(value: String): String =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    fun decodeValue(encodedValue: String): String =
        runCatching {
            String(Base64.getUrlDecoder().decode(encodedValue), StandardCharsets.UTF_8)
        }.getOrDefault(encodedValue)

    fun encodeFilePath(filePath: String): String =
        encodeValue(filePath)

    fun decodeFilePath(encodedPath: String): String =
        decodeValue(encodedPath)
}
