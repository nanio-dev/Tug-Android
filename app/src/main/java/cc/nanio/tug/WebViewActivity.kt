package cc.nanio.tug

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient as Client
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import cc.nanio.tug.R

/**
 * 扫码/外部 HTTP 链接不是直链时，用 WebView 打开网页。
 *
 * 当网页触发下载（或用户点击磁力链接）时，把真实下载地址通过
 * RESULT_OK 返回给调用方，由调用方复用新建下载弹窗。
 */
class WebViewActivity : AppCompatActivity() {
    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialUrl = intent.getStringExtra(EXTRA_URL)?.trim()
        if (initialUrl.isNullOrEmpty() || !initialUrl.startsWith("http", ignoreCase = true)) {
            Toast.makeText(this, R.string.webview_invalid_url, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        title = getString(R.string.webview_title)
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.loadsImagesAutomatically = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.setSupportMultipleWindows(false)
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = object : Client() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?,
                ): Boolean {
                    val target = request?.url?.toString() ?: return false
                    if (target.startsWith("magnet:", ignoreCase = true)) {
                        deliverDownload(target)
                        return true
                    }
                    return false
                }
            }
            webChromeClient = WebChromeClient()
            setDownloadListener { url, _, _, mimeType, _ ->
                if (url.startsWith("http://", ignoreCase = true) ||
                    url.startsWith("https://", ignoreCase = true)
                ) {
                    deliverDownload(url)
                } else {
                    Toast.makeText(
                        this@WebViewActivity,
                        getString(R.string.webview_download_unsupported, mimeType ?: url),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
        setContentView(webView)
        webView.loadUrl(initialUrl)

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (::webView.isInitialized && webView.canGoBack()) {
                        webView.goBack()
                    } else {
                        finish()
                    }
                }
            },
        )
    }

    private fun deliverDownload(url: String) {
        setResult(
            RESULT_OK,
            Intent().putExtra(EXTRA_DOWNLOAD_URL, url),
        )
        finish()
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.destroy()
        }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "cc.nanio.tug.extra.WEB_URL"
        const val EXTRA_DOWNLOAD_URL = "cc.nanio.tug.extra.DOWNLOAD_URL"

        fun intent(context: Context, url: String): Intent =
            Intent(context, WebViewActivity::class.java).putExtra(EXTRA_URL, url)
    }
}
