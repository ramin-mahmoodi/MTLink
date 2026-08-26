package ir.mtlink.client

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

class LoadingOverlay(context: Context) : FrameLayout(context) {
    private val spinner: TextView
    private val title: TextView
    private val detail: TextView
    private val rotate: ObjectAnimator
    private val pulse: ObjectAnimator

    init {
        setBackgroundColor(Color.parseColor("#B80B0F14"))
        isClickable = true
        visibility = View.GONE
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(26), dp(24), dp(26), dp(22))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#FF182230"))
                setStroke(dp(1), Color.parseColor("#FF344359"))
                cornerRadius = dp(24).toFloat()
            }
        }
        spinner = TextView(context).apply {
            text = "◌"
            textSize = 46f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#FF8AA4FF"))
            typeface = MTFonts.face(context, true)
        }
        title = text(16, Color.parseColor("#FFF2F6FC"), true)
        detail = text(12, Color.parseColor("#FF93A3B8"), false)
        card.addView(spinner, LinearLayout.LayoutParams(dp(66), dp(66)))
        card.addView(title.apply { gravity = Gravity.CENTER; setPadding(0, dp(8), 0, 0) })
        card.addView(detail.apply { gravity = Gravity.CENTER; setPadding(0, dp(6), 0, 0) })
        addView(card, LayoutParams(dp(250), LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        rotate = ObjectAnimator.ofFloat(spinner, View.ROTATION, 0f, 360f).apply { duration = 900; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.RESTART }
        pulse = ObjectAnimator.ofFloat(spinner, View.SCALE_X, 0.82f, 1.08f, 0.82f).apply { duration = 1200; repeatCount = ValueAnimator.INFINITE }
    }

    fun showLoading(headline: String, status: String) {
        title.text = headline
        detail.text = status
        visibility = View.VISIBLE
        if (!rotate.isStarted) rotate.start()
        if (!pulse.isStarted) pulse.start()
    }

    fun updateStatus(status: String) { detail.text = status }

    fun hideLoading() {
        rotate.cancel(); pulse.cancel(); spinner.rotation = 0f; spinner.scaleX = 1f
        visibility = View.GONE
    }

    private fun text(size: Int, color: Int, bold: Boolean) = TextView(context).apply {
        textSize = size.toFloat(); setTextColor(color); typeface = MTFonts.face(context, bold)
        includeFontPadding = true; setLineSpacing(dp(2).toFloat(), 1f)
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
