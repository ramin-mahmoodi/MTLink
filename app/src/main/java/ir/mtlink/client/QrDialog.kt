package ir.mtlink.client

import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter

fun showProxyQr(context: Context, proxy: ProxyRecord, link: String, ui: UiText) {
    val density = context.resources.displayMetrics.density
    val size = (248 * density).toInt()
    val matrix = MultiFormatWriter().encode(link, BarcodeFormat.QR_CODE, size, size)
    val pixels = IntArray(size * size)
    for (y in 0 until size) {
        for (x in 0 until size) pixels[y * size + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
    }
    val image = ImageView(context).apply {
        setImageBitmap(Bitmap.createBitmap(pixels, size, size, Bitmap.Config.RGB_565))
        setPadding((10 * density).toInt(), (10 * density).toInt(), (10 * density).toInt(), (10 * density).toInt())
    }
    val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutDirection = if (ui.isRtl) android.view.View.LAYOUT_DIRECTION_RTL else android.view.View.LAYOUT_DIRECTION_LTR
        setPadding((10 * density).toInt(), 0, (10 * density).toInt(), 0)
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
}
