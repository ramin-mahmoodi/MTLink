package ir.mtlink.client

import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter

fun showProxyQr(context: Context, proxy: ProxyRecord, link: String, ui: UiText) {
    val density = context.resources.displayMetrics.density
    val size = (248 * density).toInt()
    val image = ImageView(context).apply {
        layoutParams = LinearLayout.LayoutParams(size, size)
        setPadding((10 * density).toInt(), (10 * density).toInt(), (10 * density).toInt(), (10 * density).toInt())
    }
    val progress = ProgressBar(context).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { gravity = android.view.Gravity.CENTER }
        isIndeterminate = true
    }
    val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutDirection = if (ui.isRtl) android.view.View.LAYOUT_DIRECTION_RTL else android.view.View.LAYOUT_DIRECTION_LTR
        setPadding((10 * density).toInt(), 0, (10 * density).toInt(), 0)
        gravity = android.view.Gravity.CENTER_HORIZONTAL
        addView(progress)
        addView(image)
        addView(TextView(context).apply {
            text = proxy.displayAddress()
            textSize = 13f
            setTextColor(context.getColor(R.color.mt_text))
            typeface = MTFonts.face(context, true)
            gravity = android.view.Gravity.CENTER
            includeFontPadding = true
            setLineSpacing(2 * density, 1f)
            setPadding(0, (8 * density).toInt(), 0, (4 * density).toInt())
        })
    }
    val dialog = AlertDialog.Builder(context)
        .setTitle(ui.of("QR پراکسی", "Proxy QR"))
        .setView(content)
        .setPositiveButton(ui.of("بستن", "Close"), null)
        .show()
    dialog.window?.decorView?.layoutDirection = if (ui.isRtl) android.view.View.LAYOUT_DIRECTION_RTL else android.view.View.LAYOUT_DIRECTION_LTR
    // fixed: QR encoding and bitmap creation run off the UI thread.
    Thread({
        val bitmap = runCatching {
            val matrix = MultiFormatWriter().encode(link, BarcodeFormat.QR_CODE, size, size)
            val pixels = IntArray(size * size)
            for (y in 0 until size) {
                for (x in 0 until size) pixels[y * size + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
            }
            Bitmap.createBitmap(pixels, size, size, Bitmap.Config.RGB_565)
        }.getOrNull()
        image.post {
            if (!dialog.isShowing) return@post
            progress.visibility = View.GONE
            bitmap?.let(image::setImageBitmap)
        }
    }, "mtlink-qr").start()
}
