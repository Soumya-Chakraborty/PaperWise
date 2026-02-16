package com.paperwise.ui.navigation

import java.nio.charset.StandardCharsets
import java.util.Base64

object NavigationCodec {
    fun encodeFilePath(filePath: String): String =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(filePath.toByteArray(StandardCharsets.UTF_8))

    fun decodeFilePath(encodedPath: String): String =
        runCatching {
            String(Base64.getUrlDecoder().decode(encodedPath), StandardCharsets.UTF_8)
        }.getOrDefault(encodedPath)
}
