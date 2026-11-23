package cc.nanio.tug.net

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlInspectorTest {
    @Test
    fun recognizesHttpAndHttps() {
        assertTrue(UrlInspector.isHttpUrl("http://example.com/file"))
        assertTrue(UrlInspector.isHttpUrl("HTTPS://example.com/file"))
        assertFalse(UrlInspector.isHttpUrl("magnet:?xt=urn:btih:abc"))
    }

    @Test
    fun recognizesObviousDownloadExtensionsWithoutNetwork() {
        assertTrue(UrlInspector.hasDownloadExtension("https://example.com/file.exe"))
        assertTrue(UrlInspector.hasDownloadExtension("https://example.com/file.ZIP"))
        assertTrue(UrlInspector.hasDownloadExtension("https://example.com/download.torrent"))
        assertFalse(UrlInspector.hasDownloadExtension("https://example.com/index.html"))
        assertFalse(UrlInspector.hasDownloadExtension("https://example.com/no-extension"))
    }

    @Test
    fun identifiesHtmlContentTypes() {
        assertTrue(UrlInspector.isHtmlContentType("text/html"))
        assertTrue(UrlInspector.isHtmlContentType("text/html; charset=utf-8"))
        assertTrue(UrlInspector.isHtmlContentType("application/xhtml+xml"))
        assertFalse(UrlInspector.isHtmlContentType("application/octet-stream"))
        assertFalse(UrlInspector.isHtmlContentType(null))
    }
}
