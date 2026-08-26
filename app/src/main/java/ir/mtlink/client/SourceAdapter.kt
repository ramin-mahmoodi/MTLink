package ir.mtlink.client

import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.core.content.ContextCompat

class SourceAdapter(
    private val ui: UiText,
    private val onToggle: (SourceDefinition, Boolean) -> Unit,
    private val onLimit: (SourceDefinition) -> Unit,
    private val onEdit: (SourceDefinition) -> Unit,
) : RecyclerView.Adapter<SourceAdapter.SourceHolder>() {
    private var items: List<SourceDefinition> = emptyList()
    fun submit(value: List<SourceDefinition>) { items = value; notifyDataSetChanged() }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SourceHolder {
        val context = parent.context
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; layoutDirection = if (ui.isRtl) android.view.View.LAYOUT_DIRECTION_RTL else android.view.View.LAYOUT_DIRECTION_LTR
            minimumHeight = dp(94); setPadding(dp(16), dp(13), dp(13), dp(13))
            background = ProxyAdapter.rounded(ContextCompat.getColor(context, R.color.mt_surface_raised), ContextCompat.getColor(context, R.color.mt_border), 18)
            layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(10) }
        }
        val copy = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = if (ui.isRtl) android.view.View.LAYOUT_DIRECTION_RTL else android.view.View.LAYOUT_DIRECTION_LTR
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val title = text(context, 15, ContextCompat.getColor(context, R.color.mt_text), true)
        val detail = text(context, 12, ContextCompat.getColor(context, R.color.mt_muted), false).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            textDirection = android.view.View.TEXT_DIRECTION_LTR
            textAlignment = android.view.View.TEXT_ALIGNMENT_VIEW_START
            gravity = Gravity.LEFT
        }
        val status = text(context, 11, ContextCompat.getColor(context, R.color.mt_primary_light), true).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, dp(2), dp(8), 0)
        }
        copy.addView(title); copy.addView(detail); copy.addView(status)
        val limit = text(context, 11, ContextCompat.getColor(context, R.color.mt_primary_light), true).apply {
            gravity = Gravity.CENTER
            textAlignment = android.view.View.TEXT_ALIGNMENT_CENTER
            setPadding(dp(8), 0, dp(8), 0)
            background = ProxyAdapter.rounded(ContextCompat.getColor(context, R.color.mt_surface_soft), ContextCompat.getColor(context, R.color.mt_primary), 12)
        }
        val switch = Switch(context)
        row.addView(copy)
        row.addView(limit, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)).apply { marginEnd = dp(6) })
        row.addView(switch)
        return SourceHolder(row, title, detail, status, limit, switch)
    }

    override fun onBindViewHolder(holder: SourceHolder, position: Int) {
        val source = items[position]
        holder.title.text = source.title
        holder.detail.text = "${source.type.name}  •  ${android.net.Uri.parse(source.url).host.orEmpty()}"
        when {
            source.lastError != null -> {
                holder.status.text = if (ui.isRtl) "خطا: ${source.lastError}" else "Error: ${source.lastError}"
                holder.status.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.mt_danger))
            }
            source.lastFetchedAt > 0L -> {
                holder.status.text = if (ui.isRtl) "آخرین خروجی: ${source.lastFetchCount} پراکسی" else "Last output: ${source.lastFetchCount} proxies"
                holder.status.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.mt_success))
            }
            else -> {
                holder.status.text = if (ui.isRtl) "هنوز دریافت نشده" else "Not fetched yet"
                holder.status.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.mt_primary_light))
            }
        }
        holder.toggle.setOnCheckedChangeListener(null)
        holder.toggle.isChecked = source.enabled
        holder.toggle.setOnCheckedChangeListener { _, enabled -> onToggle(source, enabled) }
        holder.limit.text = if (ui.isRtl) "حد ${source.fetchLimit}" else "Limit ${source.fetchLimit}"
        holder.limit.setOnClickListener { onLimit(source) }
        holder.itemView.setOnClickListener { onEdit(source) }
    }

    override fun getItemCount() = items.size
    class SourceHolder(view: LinearLayout, val title: TextView, val detail: TextView, val status: TextView, val limit: TextView, val toggle: Switch) : RecyclerView.ViewHolder(view)

    private fun text(context: android.content.Context, size: Int, color: Int, bold: Boolean) = TextView(context).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        textSize = size.toFloat(); setTextColor(color); typeface = MTFonts.face(context, bold)
        includeFontPadding = true; setLineSpacing(dp(1).toFloat(), 1f)
        textDirection = if (ui.isRtl) android.view.View.TEXT_DIRECTION_FIRST_STRONG_RTL else android.view.View.TEXT_DIRECTION_FIRST_STRONG_LTR
        textAlignment = android.view.View.TEXT_ALIGNMENT_VIEW_START
        gravity = if (ui.isRtl) Gravity.RIGHT else Gravity.LEFT
        setPadding(0, 0, dp(8), dp(3))
    }
    private fun dp(value: Int) = (value * android.content.res.Resources.getSystem().displayMetrics.density).toInt()
}
