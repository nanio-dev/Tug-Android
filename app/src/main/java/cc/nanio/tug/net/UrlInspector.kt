package cc.nanio.tug.net

import cc.nanio.tug.core.engine.http.HttpDownloadEngine
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 扫码 / 外部链接进入 App 前的轻量预检结果。
 *
 * 这里只判断“应该打开新建下载，还是用 WebView 打开网页”，
 * 真正的下载仍统一交给 TaskManager / HttpDownloadEngine。
 */
sealed interface InspectedTarget {
    data class Download(
        val url: String,
        val suggestedName: String? = null,
    ) : InspectedTarget

    data class WebPage(val url: String) : InspectedTarget

    data class Unsupported(val input: String) : InspectedTarget

    data class Failed(val message: String) : InspectedTarget
}

object UrlInspector {
    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 8_000

    private val downloadExtensions = setOf(
        "apk", "aab", "exe", "msi", "zip", "7z", "rar", "tar", "gz", "bz2", "xz",
        "iso", "dmg", "img", "bin", "jar", "pdf", "doc", "docx", "xls", "xlsx",
        "ppt", "pptx", "epub", "mobi", "torrent", "mp3", "m4a", "flac", "wav",
        "mp4", "mkv", "avi", "mov", "webm", "flv", "png", "jpg", "jpeg", "gif",
        "webp", "svg", "txt", "csv", "json", "xml", "log", "ttf", "otf", "woff",
    )

    private val htmlExtensions = setOf("html", "htm", "xhtml", "php", "asp", "aspx", "jsp")

    suspend fun inspect(raw: String): InspectedTarget {
        val input = raw.trim().trim('"', '\'')
        if (input.isEmpty()) return InspectedTarget.Unsupported(raw)
        if (input.startsWith("magnet:", ignoreCase = true)) {
            return InspectedTarget.Download(input)
        }
        if (!isHttpUrl(input)) return InspectedTarget.Unsupported(input)

        val result = withTimeoutOrNull(12_000) {
            withContext(Dispatchers.IO) {
                runInterruptible { inspectBlocking(input) }
            }
        }
        return result ?: InspectedTarget.WebPage(input)
    }

    private fun inspectBlocking(raw: String): InspectedTarget {
        val input = raw.trim().trim('"', '\'')
        if (input.isEmpty()) return InspectedTarget.Unsupported(raw)
        if (input.startsWith("magnet:", ignoreCase = true)) {
            return InspectedTarget.Download(input)
        }
        if (!isHttpUrl(input)) return InspectedTarget.Unsupported(input)

        val uri = runCatching { URI(input) }.getOrNull()
            ?: return InspectedTarget.Unsupported(input)
        if (uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank()) {
            return InspectedTarget.Unsupported(input)
        }

        // 明显是二进制下载后缀时不发预检请求，避免扫码/外部打开卡顿。
        if (hasDownloadExtension(input)) {
            return InspectedTarget.Download(input, fileNameFromUrl(input))
        }

        val probe = runCatching { probe(input) }.getOrNull()
            ?: return InspectedTarget.WebPage(input)

        val finalUrl = probe.finalUrl.ifBlank { input }
        if (!probe.disposition.isNullOrBlank()) {
            return InspectedTarget.Download(finalUrl, probe.disposition)
        }
        if (hasDownloadExtension(finalUrl)) {
            return InspectedTarget.Download(finalUrl, fileNameFromUrl(finalUrl))
        }
        if (probe.status in 400..599) {
            // 需要登录/权限的页面交给 WebView，用户可在网页里完成登录或跳转。
            return InspectedTarget.WebPage(finalUrl)
        }
        val contentType = probe.contentType?.substringBefore(';')?.trim()?.lowercase()
        if (isHtmlContentType(contentType)) {
            return InspectedTarget.WebPage(finalUrl)
        }
        if (contentType.isNullOrBlank()) {
            return InspectedTarget.WebPage(finalUrl)
        }
        return InspectedTarget.Download(finalUrl, fileNameFromUrl(finalUrl))
    }

    private data class ProbeResult(
        val status: Int,
        val finalUrl: String,
        val contentType: String?,
        val disposition: String?,
    )

    private fun probe(input: String): ProbeResult {
        val head = request(input, method = "HEAD", range = null)
        if (head != null && head.status !in setOf(405, 501)) {
            // URL 没有后缀时，某些 CDN 只在 GET/Range 响应里带 Content-Disposition。
            if (head.disposition.isNullOrBlank() && !hasDownloadExtension(head.finalUrl)) {
                return request(input, method = "GET", range = "bytes=0-0") ?: head
            }
            return head
        }
        return request(input, method = "GET", range = "bytes=0-0") ?: ProbeResult(
            status = 0,
            finalUrl = input,
            contentType = null,
            disposition = null,
        )
    }

    private fun request(input: String, method: String, range: String?): ProbeResult? {
        var connection: HttpURLConnection? = null
        return try {
            connection = URL(input).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.requestMethod = method
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("User-Agent", "Tug-Android/1.0.1")
            connection.setRequestProperty("Accept", "*/*")
            if (range != null) connection.setRequestProperty("Range", range)
            val status = connection.responseCode
            ProbeResult(
                status = status,
                finalUrl = connection.url?.toString() ?: input,
                contentType = connection.contentType,
                disposition = connection.getHeaderField("Content-Disposition")
                    ?.let { HttpDownloadEngine.parseContentDisposition(it) },
            )
        } catch (_: IOException) {
            null
        } finally {
            runCatching { connection?.disconnect() }
        }
    }

    internal fun isHttpUrl(raw: String): Boolean =
        raw.startsWith("http://", ignoreCase = true) ||
            raw.startsWith("https://", ignoreCase = true)

    internal fun hasDownloadExtension(raw: String): Boolean {
        val name = fileNameFromUrl(raw) ?: return false
        val extension = name.substringAfterLast('.', "").lowercase()
        return extension in downloadExtensions && extension !in htmlExtensions
    }

    internal fun isHtmlContentType(contentType: String?): Boolean {
        val type = contentType?.substringBefore(';')?.trim()?.lowercase() ?: return false
        return type == "text/html" || type == "application/xhtml+xml"
    }

    internal fun fileNameFromUrl(raw: String): String? = runCatching {
        val path = URI(raw).path ?: return@runCatching null
        val last = path.substringAfterLast('/')
        if (last.isBlank()) null else java.net.URLDecoder.decode(last, "UTF-8")
    }.getOrNull()
}
