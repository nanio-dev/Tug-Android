package cc.nanio.tug.core.engine.http

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpHelpersTest {
    @Test
    fun mergeRanges_mergesOverlappingAndAdjacent() {
        val merged = HttpDownloadEngine.mergeRanges(
            listOf(0L..10L, 5L..15L, 16L..20L, 30L..40L),
        )
        assertEquals(listOf(0L..20L, 30L..40L), merged)
    }

    @Test
    fun computePending_splitsAroundCompleted() {
        val pending = HttpDownloadEngine.computePending(
            total = 100,
            segmentSize = 30,
            completed = listOf(30L..59L),
        )
        assertEquals(listOf(0L..29L, 60L..89L, 90L..99L), pending)
    }

    @Test
    fun parseContentRangeTotal_readsTotal() {
        assertEquals(12345L, HttpDownloadEngine.parseContentRangeTotal("bytes 0-0/12345"))
        assertNull(HttpDownloadEngine.parseContentRangeTotal("bytes 0-0/*"))
    }

    @Test
    fun sanitizeFileName_stripsIllegalChars() {
        val name = HttpDownloadEngine.sanitizeFileName("a/b:c*?.iso")
        assertEquals("a_b_c__.iso", name)
    }

    @Test
    fun parseContentDisposition_supportsUtf8Name() {
        val name = HttpDownloadEngine.parseContentDisposition(
            "attachment; filename*=UTF-8''%E4%B8%AD%E6%96%87.iso",
        )
        assertEquals("中文.iso", name)
    }

    @Test
    fun parseContentDisposition_supportsNameWithoutExtension() {
        assertEquals(
            "download-report",
            HttpDownloadEngine.parseContentDisposition(
                "attachment; filename=\"download-report\"",
            ),
        )
        assertEquals(
            "无后缀文件",
            HttpDownloadEngine.parseContentDisposition(
                "attachment; filename*=UTF-8''%E6%97%A0%E5%90%8E%E7%BC%80%E6%96%87%E4%BB%B6",
            ),
        )
    }

    @Test
    fun parseContentDisposition_prefersExtendedFilename() {
        assertEquals(
            "完整名称.pdf",
            HttpDownloadEngine.parseContentDisposition(
                "attachment; filename=\"fallback.bin\"; filename*=UTF-8''%E5%AE%8C%E6%95%B4%E5%90%8D%E7%A7%B0.pdf",
            ),
        )
    }

    @Test
    fun guessNameFromUrl_usesLastPathSegment() {
        assertEquals(
            "file.bin",
            HttpDownloadEngine.guessNameFromUrl("https://example.com/path/file.bin?x=1"),
        )
    }

    @Test
    fun mergeRanges_emptyAndSingle() {
        assertTrue(HttpDownloadEngine.mergeRanges(emptyList()).isEmpty())
        assertEquals(listOf(1L..2L), HttpDownloadEngine.mergeRanges(listOf(1L..2L)))
    }

    @Test
    fun effectiveSegmentSize_adaptsToFileSize() {
        val mib = 1024L * 1024
        assertEquals(256L * 1024, HttpDownloadEngine.effectiveSegmentSize(mib, 4, 8 * mib))
        assertEquals(8 * mib, HttpDownloadEngine.effectiveSegmentSize(100 * mib, 8, 8 * mib))
        assertEquals(64L * 1024, HttpDownloadEngine.effectiveSegmentSize(1024, 4, 8 * mib))
        // 未知大小：使用期望值
        assertEquals(8 * mib, HttpDownloadEngine.effectiveSegmentSize(0, 4, 8 * mib))
    }
}
