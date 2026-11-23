package cc.nanio.tug

import android.content.pm.ActivityInfo
import android.os.Bundle
import com.journeyapps.barcodescanner.CaptureActivity
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.Size

/**
 * Tug 扫码页：固定竖屏，扫描框使用正方形。
 *
 * 竖屏由 Manifest 的 screenOrientation 锁定；这里只调整取景框尺寸，
 * 避免 ZXing 默认的“横向偏长”取景框在手机上显得不协调。
 */
class QrScanActivity : CaptureActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // CaptureManager 可能按当前屏幕方向重新请求横屏；这里强制回到竖屏。
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    override fun initializeContent(): DecoratedBarcodeView {
        val view = super.initializeContent()
        val metrics = resources.displayMetrics
        val side = (minOf(metrics.widthPixels, metrics.heightPixels) * 0.72f).toInt()
        view.barcodeView.setFramingRectSize(Size(side, side))
        return view
    }
}
