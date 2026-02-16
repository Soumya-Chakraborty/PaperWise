package com.paperwise.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class NavigationCodecTest {
    @Test
    fun `encodes and decodes file paths safely`() {
        val filePath = "content://com.android.providers.media.documents/document/42?name=My File.pdf"

        val encoded = NavigationCodec.encodeFilePath(filePath)
        val decoded = NavigationCodec.decodeFilePath(encoded)

        assertNotEquals(filePath, encoded)
        assertEquals(filePath, decoded)
    }

    @Test
    fun `returns original value when decoding invalid base64`() {
        val invalid = "not_base64:%%%/"
        assertEquals(invalid, NavigationCodec.decodeFilePath(invalid))
    }

    @Test
    fun `encodes and decodes arbitrary navigation values`() {
        val value = "Annual Report 2026 (Final)#v2.pdf"

        val encoded = NavigationCodec.encodeValue(value)
        val decoded = NavigationCodec.decodeValue(encoded)

        assertNotEquals(value, encoded)
        assertEquals(value, decoded)
    }

    @Test
    fun `preserves empty string when encoding generic values`() {
        assertEquals("", NavigationCodec.decodeValue(NavigationCodec.encodeValue("")))
    }
}
