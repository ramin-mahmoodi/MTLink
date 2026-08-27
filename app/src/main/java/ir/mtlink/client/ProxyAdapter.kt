package ir.mtlink.client

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView

enum class SwipeAction { OPEN_TELEGRAM, SHARE, COPY, DELETE, QR, FAVORITE }

class ProxyAdapter(
    private val ui: UiText,
    private val onClick: (ProxyRecord) -> Unit,
    private val onAction: (ProxyRecord, SwipeAction) -> Unit,
    private val elevatedCards: Boolean = true,
) : RecyclerView.Adapter<ProxyAdapter.ProxyHolder>() {
    private var items: List<ProxyRecord> = emptyList()
    private val motion = DecelerateInterpolator(1.65f)
    private var openHolder: ProxyHolder? = null

    private val compactReveal = dp(96).toFloat()
    private val expandedReveal = dp(160).toFloat()

    fun submit(value: List<ProxyRecord>) {
        val previous = items
        val next = value.toList()
        // fixed: DiffUtil preserves unchanged proxy cards and their RecyclerView animations.
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = previous.size
            override fun getNewListSize() = next.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) = previous[oldItemPosition].id == next[newItemPosition].id
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) = previous[oldItemPosition] == next[newItemPosition]
        })
        items = next
        diff.dispatchUpdatesTo(this)
    }
    fun updateItem(updated: ProxyRecord) {
        val index = items.indexOfFirst { it.id == updated.id }
        if (index < 0) return
        items = items.toMutableList().also { it[index] = updated }
        notifyItemChanged(index)
    }
    fun removeItem(id: String) {
        val index = items.indexOfFirst { it.id == id }
        if (index < 0) return
        items = items.toMutableList().also { it.removeAt(index) }
        notifyItemRemoved(index)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProxyHolder {
        val context = parent.context
        val root = FrameLayout(context).apply {
            layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(90)).apply { bottomMargin = dp(10) }
            layoutDirection = if (ui.isRtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
        }
        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            visibility = View.INVISIBLE
            alpha = 0f
            setPadding(dp(3), 0, dp(3), 0)
        }
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = if (ui.isRtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
            setPadding(dp(14), dp(13), dp(13), dp(13))
            background = cardSurface(context, 20)
            elevation = if (elevatedCards) dp(2).toFloat() else 0f
        }
        val dot = TextView(context).apply {
            gravity = Gravity.CENTER
            text = "MT"
            textSize = 14f
            setTextColor(ContextCompat.getColor(context, R.color.mt_primary_light))
            typeface = MTFonts.face(context, true)
            background = rounded(ContextCompat.getColor(context, R.color.mt_primary_soft), Color.TRANSPARENT, 14)
        }
        val favorite = TextView(context).apply {
            text = "★"
            gravity = Gravity.CENTER
            textSize = 17f
            setTextColor(Color.rgb(250, 198, 79))
            visibility = View.GONE
        }
        val meta = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = (if (ui.isRtl) Gravity.RIGHT else Gravity.LEFT) or Gravity.CENTER_VERTICAL
            layoutDirection = if (ui.isRtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
            setPadding(dp(11), 0, dp(9), 0)
        }
        val address = label(context, 15, ContextCompat.getColor(context, R.color.mt_text), true).apply {
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
            textDirection = View.TEXT_DIRECTION_LTR; textAlignment = View.TEXT_ALIGNMENT_VIEW_START; gravity = Gravity.LEFT
        }
        val detail = label(context, 12, ContextCompat.getColor(context, R.color.mt_muted), false).apply {
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END; setPadding(0, dp(4), 0, 0)
            textDirection = if (ui.isRtl) View.TEXT_DIRECTION_FIRST_STRONG_RTL else View.TEXT_DIRECTION_FIRST_STRONG_LTR
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            gravity = if (ui.isRtl) Gravity.RIGHT else Gravity.LEFT
        }
        val status = label(context, 11, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(dp(7), dp(6), dp(7), dp(6))
        }
        meta.addView(address)
        meta.addView(detail)
        card.addView(dot, LinearLayout.LayoutParams(dp(42), dp(42)))
        card.addView(favorite, LinearLayout.LayoutParams(dp(24), dp(42)))
        card.addView(meta, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(status, LinearLayout.LayoutParams(dp(80), dp(38)))
        root.addView(actions, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(card, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        return ProxyHolder(root, card, actions, dot, favorite, address, detail, status)
    }

    override fun onBindViewHolder(holder: ProxyHolder, position: Int) {
        val proxy = items[position]
        if (openHolder === holder) openHolder = null
        holder.card.translationX = 0f
        holder.card.alpha = 1f
        holder.card.scaleX = 1f
        holder.card.scaleY = 1f
        holder.actions.visibility = View.INVISIBLE
        holder.actions.alpha = 0f
        holder.dot.text = flag(proxy.countryCode)
        holder.favorite.visibility = if (proxy.favorite) View.VISIBLE else View.GONE
        holder.address.text = proxy.displayAddress()
        holder.detail.text = "${if (proxy.protocol == ProxyProtocol.MTPROTO) "MTProto" else "SOCKS5"}  ·  ${statusCopy(proxy)}"
        val context = holder.itemView.context
        val (label, color) = when (proxy.status) {
            ProxyStatus.REACHABLE -> (proxy.latencyMs?.let { "$it ms" } ?: ui.of("در دسترس", "Ready")) to ContextCompat.getColor(context, R.color.mt_success)
            ProxyStatus.UNREACHABLE -> ui.of("ناموفق", "Failed") to ContextCompat.getColor(context, R.color.mt_danger)
            ProxyStatus.CHECKING -> ui.of("در حال تست", "Testing") to ContextCompat.getColor(context, R.color.mt_accent_amber)
            ProxyStatus.UNTESTED -> ui.of("تست‌نشده", "Untested") to ContextCompat.getColor(context, R.color.mt_accent_violet)
        }
        holder.status.text = label
        holder.status.background = rounded(color, color, 10)
        bindTouch(holder, proxy)
    }

    private fun statusCopy(proxy: ProxyRecord): String = when {
        proxy.favorite -> ui.of("علاقه‌مندی", "Favorite")
        proxy.testedAt > 0 && proxy.status == ProxyStatus.REACHABLE -> ui.of("بررسی‌شده", "Checked")
        else -> ui.of("دریافت‌شده", "Fetched")
    }

    private fun bindTouch(holder: ProxyHolder, proxy: ProxyRecord) {
        val slop = ViewConfiguration.get(holder.card.context).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var dragging = false
        var startingTranslation = 0f
        var closingExistingRail = false
        holder.card.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    dragging = false
                    startingTranslation = holder.card.translationX
                    closingExistingRail = false
                    // A gesture started on a proxy card belongs to the card. This prevents ViewPager2
                    // from stealing its horizontal swipe before the action rail can open.
                    holder.card.parent?.requestDisallowInterceptTouchEvent(true)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!dragging && kotlin.math.abs(dy) > slop && kotlin.math.abs(dy) >= kotlin.math.abs(dx)) {
                        // Let the RecyclerView intercept immediately for a vertical list scroll.
                        holder.card.parent?.requestDisallowInterceptTouchEvent(false)
                        return@setOnTouchListener false
                    }
                    if (!dragging && kotlin.math.abs(dx) > slop && kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
                        dragging = true
                        holder.card.parent?.requestDisallowInterceptTouchEvent(true)
                    }
                    if (dragging) {
                        if (startingTranslation != 0f) {
                            val isOpposite = dx * startingTranslation < 0f
                            if (isOpposite) {
                                closingExistingRail = true
                                holder.card.translationX = (startingTranslation + dx).coerceIn(-expandedReveal, compactReveal)
                            }
                        } else {
                            val direction = if (dx >= 0f) 1 else -1
                            if (openHolder !== holder) {
                                closeOpenCard()
                                openHolder = holder
                            }
                            configureActions(holder, proxy, direction)
                            val reveal = revealFor(direction)
                            holder.card.translationX = dx.coerceIn(-expandedReveal, compactReveal).coerceIn(-reveal, reveal)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragging) {
                        val dx = event.rawX - downX
                        if (closingExistingRail) {
                            closeCard(holder)
                        } else if (startingTranslation != 0f) {
                            holder.card.animate().translationX(startingTranslation).setDuration(150).setInterpolator(motion).start()
                        } else {
                            val direction = if (dx >= 0f) 1 else -1
                            val reveal = revealFor(direction)
                            if (kotlin.math.abs(dx) > reveal * 0.28f) {
                                holder.actions.animate().alpha(1f).setDuration(130).setInterpolator(motion).start()
                                holder.card.animate().translationX(if (dx > 0) reveal else -reveal).setDuration(190).setInterpolator(motion).start()
                            } else {
                                closeCard(holder)
                            }
                        }
                    } else {
                        holder.card.animate().scaleX(.985f).scaleY(.985f).setDuration(75).setInterpolator(motion).withEndAction {
                            holder.card.animate().scaleX(1f).scaleY(1f).setDuration(120).setInterpolator(motion).withEndAction { onClick(proxy) }.start()
                        }.start()
                    }
                    holder.card.parent?.requestDisallowInterceptTouchEvent(false)
                    true
                }
                else -> true
            }
        }
    }

    private fun revealFor(direction: Int) = if (direction > 0) compactReveal else expandedReveal

    private fun closeOpenCard() {
        openHolder?.let { closeCard(it) }
        openHolder = null
    }

    private fun closeCard(holder: ProxyHolder) {
        holder.card.animate().translationX(0f).setDuration(170).setInterpolator(motion).withEndAction {
            holder.actions.visibility = View.INVISIBLE
            holder.actions.alpha = 0f
        }.start()
        if (openHolder === holder) openHolder = null
    }

    private fun configureActions(holder: ProxyHolder, proxy: ProxyRecord, direction: Int) {
        val container = holder.actions
        if (container.tag == "$direction:${proxy.id}" && container.visibility == View.VISIBLE) return
        container.tag = "$direction:${proxy.id}"
        container.removeAllViews()
        container.visibility = View.VISIBLE
        container.alpha = 0f
        val actions: List<SwipeAction> = if (direction > 0) {
            listOf(SwipeAction.QR, SwipeAction.FAVORITE)
        } else {
            listOf(SwipeAction.OPEN_TELEGRAM, SwipeAction.SHARE, SwipeAction.COPY, SwipeAction.DELETE)
        }
        val actionsWidth = if (direction > 0) compactReveal.toInt() else expandedReveal.toInt()
        container.layoutParams = FrameLayout.LayoutParams(actionsWidth, ViewGroup.LayoutParams.MATCH_PARENT, if (direction > 0) Gravity.LEFT else Gravity.RIGHT)
        container.gravity = Gravity.CENTER
        actions.forEach { action ->
            val color = when (action) {
                SwipeAction.DELETE -> Color.rgb(143, 43, 59)
                SwipeAction.OPEN_TELEGRAM -> Color.rgb(38, 112, 190)
                SwipeAction.FAVORITE -> Color.rgb(131, 91, 211)
                else -> Color.rgb(35, 51, 71)
            }
            val icon = when (action) {
                SwipeAction.OPEN_TELEGRAM -> R.drawable.ic_action_telegram
                SwipeAction.SHARE -> R.drawable.ic_action_share
                SwipeAction.COPY -> R.drawable.ic_action_copy
                SwipeAction.DELETE -> R.drawable.ic_action_delete
                SwipeAction.QR -> R.drawable.ic_action_qr
                SwipeAction.FAVORITE -> R.drawable.ic_action_favorite
            }
            val button = ImageView(container.context).apply {
                setImageResource(icon)
                scaleType = ImageView.ScaleType.CENTER
                contentDescription = action.name
                background = rounded(color, Color.TRANSPARENT, 12)
                setOnClickListener { closeCard(holder); onAction(proxy, action) }
            }
            container.addView(button, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply { marginStart = dp(2); marginEnd = dp(2) })
        }
        container.animate().alpha(.9f).setDuration(130).setInterpolator(motion).start()
    }

    override fun getItemCount() = items.size

    class ProxyHolder(
        view: FrameLayout,
        val card: LinearLayout,
        val actions: LinearLayout,
        val dot: TextView,
        val favorite: TextView,
        val address: TextView,
        val detail: TextView,
        val status: TextView,
    ) : RecyclerView.ViewHolder(view)

    companion object {
        fun flag(code: String?): String {
            val value = code?.uppercase()?.takeIf { it.length == 2 } ?: return "🌐"
            return String(Character.toChars(0x1F1E6 + (value[0] - 'A'))) + String(Character.toChars(0x1F1E6 + (value[1] - 'A')))
        }
        fun dp(value: Int) = (value * android.content.res.Resources.getSystem().displayMetrics.density).toInt()
        fun label(context: android.content.Context, size: Int, color: Int, bold: Boolean) = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            textSize = size.toFloat()
            setTextColor(color)
            typeface = MTFonts.face(context, bold)
            includeFontPadding = true
            setLineSpacing(dp(1).toFloat(), 1f)
        }
        fun rounded(fill: Int, stroke: Int, radius: Int) = GradientDrawable().apply { setColor(fill); if (stroke != Color.TRANSPARENT) setStroke(dp(1), stroke); cornerRadius = dp(radius).toFloat() }
        fun cardSurface(context: android.content.Context, radius: Int) = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(ContextCompat.getColor(context, R.color.mt_surface_raised), ContextCompat.getColor(context, R.color.mt_surface))).apply { setStroke(dp(1), ContextCompat.getColor(context, R.color.mt_border)); cornerRadius = dp(radius).toFloat() }
    }
}
