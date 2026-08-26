package ir.mtlink.client

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.widget.HorizontalScrollView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.core.view.WindowCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class MainActivity : ComponentActivity() {
    private lateinit var store: MTLinkStore
    private lateinit var content: FrameLayout
    private lateinit var nav: LinearLayout
    private lateinit var loadingOverlay: LoadingOverlay
    private val io = Executors.newFixedThreadPool(4)
    private var currentTab = Tab.HOME
    private var proxyFilter: ProxyStatus? = null
    private var favoritesOnly = false
    private var sourceFilter = SourceFilter.ALL
    private lateinit var ui: UiText
    private var proxyListAdapter: ProxyAdapter? = null
    private var sourceListAdapter: SourceAdapter? = null
    private var sourceEmptyState: View? = null
    private var sourceActiveCountText: TextView? = null
    private var sourceIssueCountText: TextView? = null
    private val sourceFilterChips = mutableMapOf<SourceFilter, TextView>()
    // fixed: progress state is safely shared between IO workers and the UI thread.
    @Volatile private var testProgressActive = false
    private val testProgressTotal = AtomicInteger(0)
    private val testProgressCompleted = AtomicInteger(0)
    @Volatile private var fetchProgressActive = false
    private val fetchProgressTotal = AtomicInteger(0)
    private val fetchProgressCompleted = AtomicInteger(0)
    @Volatile private var fetchProgressStatus = ""
    private var homeProgressPanel: LinearLayout? = null
    private var homeProgressHeadline: TextView? = null
    private var homeProgressTrack: FrameLayout? = null
    private var homeProgressFill: View? = null
    private var homeProgressText: TextView? = null
    private val tabInterpolator = DecelerateInterpolator(1.7f)

    private enum class Tab { HOME, PROXIES, SOURCES, SETTINGS }
    private enum class SourceFilter { ALL, ENABLED, ERRORS }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = color(R.color.mt_background)
        window.navigationBarColor = color(R.color.mt_background)
        store = MTLinkStore(this)
        ui = UiText(store.appPreferences().language)
        setContentView(buildRoot())
        // fixed: predictive back uses AndroidX's back dispatcher rather than deprecated onBackPressed().
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    ::loadingOverlay.isInitialized && loadingOverlay.visibility == View.VISIBLE -> Unit
                    currentTab != Tab.HOME -> showTab(Tab.HOME)
                    else -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            }
        })
        showTab(Tab.HOME)
    }

    override fun onDestroy() { io.shutdownNow(); super.onDestroy() }

    private fun buildRoot(): View {
        val rootDirection = if (ui.isRtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
        val frame = FrameLayout(this).apply { layoutDirection = rootDirection }
        val base = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = rootDirection
            setBackgroundColor(color(R.color.mt_background))
            content = FrameLayout(context).apply {
                layoutDirection = rootDirection
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            }
            nav = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL; layoutDirection = rootDirection; gravity = Gravity.CENTER; setPadding(dp(10), dp(8), dp(10), dp(12))
                setBackgroundColor(color(R.color.mt_surface_deep)); elevation = dp(8).toFloat()
            }
            addView(content); addView(nav, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(76)))
        }
        loadingOverlay = LoadingOverlay(this)
        frame.addView(base, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        frame.addView(loadingOverlay, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        ViewCompat.setOnApplyWindowInsetsListener(frame) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            base.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(frame)
        return frame
    }

    private fun showTab(tab: Tab) {
        val previous = currentTab
        currentTab = tab
        applyUiDirection(window.decorView, content, nav)
        val hadContent = content.childCount > 0
        content.removeAllViews(); nav.removeAllViews()
        navItems().forEach { item ->
            nav.addView(navItem(item), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                marginStart = dp(2); marginEnd = dp(2)
            })
        }
        val view = when (tab) {
            Tab.HOME -> homeView()
            Tab.SOURCES -> sourcesView()
            Tab.PROXIES -> proxiesView()
            Tab.SETTINGS -> settingsView()
        }
        applyUiDirection(view)
        if (hadContent) {
            val towardEnd = tab.ordinal > previous.ordinal
            val direction = if (ui.isRtl) if (towardEnd) -1 else 1 else if (towardEnd) 1 else -1
            view.alpha = 0f
            view.translationX = dp(18).toFloat() * direction
            content.addView(view)
            view.animate().alpha(1f).translationX(0f).setDuration(220).setInterpolator(tabInterpolator).start()
        } else {
            content.addView(view)
        }
    }

    private fun navItems() = listOf(Tab.HOME, Tab.PROXIES, Tab.SOURCES, Tab.SETTINGS)
    private fun navItem(tab: Tab) = TextView(this).apply {
        text = when (tab) {
            Tab.HOME -> t("خانه", "Home")
            Tab.SOURCES -> t("منابع", "Sources")
            Tab.PROXIES -> t("پراکسی‌ها", "Proxies")
            Tab.SETTINGS -> t("تنظیمات", "Settings")
        }
        gravity = Gravity.CENTER; textSize = 12f
        typeface = MTFonts.face(this@MainActivity, true); includeFontPadding = true
        setTextColor(if (tab == currentTab) color(R.color.mt_primary_light) else color(R.color.mt_muted))
        setPadding(dp(4), dp(4), dp(4), dp(4))
        background = rounded(
            if (tab == currentTab) color(R.color.mt_primary_soft) else Color.TRANSPARENT,
            if (tab == currentTab) color(R.color.mt_border) else Color.TRANSPARENT,
            15,
        )
        setOnClickListener { showTab(tab) }
    }

    private fun homeView(): View = scroll {
        addView(brandHeader())
        val proxies = store.proxies()
        val reachable = proxies.count { it.status == ProxyStatus.REACHABLE }
        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(22), 0, 0); applyUiDirection(this)
            addView(statCard(R.drawable.ic_stat_globe, t("همهٔ پراکسی‌ها", "All proxies"), proxies.size, "#1A2D4F", "#14223E"), LinearLayout.LayoutParams(0, dp(118), 1f).apply { marginEnd = dp(6) })
            addView(statCard(R.drawable.ic_stat_check, t("در دسترس", "Reachable"), reachable, "#123A34", "#10251F"), LinearLayout.LayoutParams(0, dp(118), 1f).apply { marginStart = dp(6) })
        })
        addView(section(t("اقدام‌های سریع", "Quick actions")))
        addView(quickActionsBar())
        addView(homeTestProgress(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(14) })
        addView(section(t("پراکسی‌های برتر", "Top proxies"), t("مشاهده همه", "View all")) { showTab(Tab.PROXIES) })
        val top = proxies.sortedBy { it.latencyMs ?: Long.MAX_VALUE }.take(3)
        if (top.isEmpty()) addView(emptyCard(t("هنوز پراکسی‌ای دریافت نشده", "No proxies yet"), t("با لمس «دریافت» منابع فعال خوانده می‌شوند.", "Tap Fetch to read active sources.")))
        else addView(topProxyList(top), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(top.size * 100)))
    }

    private fun proxiesView(): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(22), dp(24), 0)
            applyUiDirection(this)
        }
        container.addView(header(t("همهٔ پراکسی‌ها", "All proxies"), "${store.proxies().size} ${t("نتیجهٔ ذخیره‌شده", "saved")}"))
        val filters = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = if (ui.isRtl) Gravity.RIGHT else Gravity.LEFT
            setPadding(0, dp(15), dp(8), dp(5))
            applyUiDirection(this)
        }
        listOf(null to t("همه", "All"), ProxyStatus.REACHABLE to t("معتبر", "Ready"), ProxyStatus.UNTESTED to t("تست‌نشده", "Untested"), ProxyStatus.UNREACHABLE to t("ناموفق", "Failed")).forEach { (status, title) ->
            val chip = action(title) { proxyFilter = status; favoritesOnly = false; showTab(Tab.PROXIES) }
            chip.setTextColor(if (!favoritesOnly && proxyFilter == status) color(R.color.mt_background) else color(R.color.mt_primary))
            chip.background = rounded(if (!favoritesOnly && proxyFilter == status) color(R.color.mt_primary) else color(R.color.mt_surface), color(R.color.mt_border), 12)
            filters.addView(chip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(39)).apply { marginEnd = dp(7) })
        }
        val favoriteChip = action(t("علاقه‌مندی", "Favorites")) { favoritesOnly = true; proxyFilter = null; showTab(Tab.PROXIES) }
        favoriteChip.setTextColor(if (favoritesOnly) color(R.color.mt_background) else color(R.color.mt_primary))
        favoriteChip.background = rounded(if (favoritesOnly) color(R.color.mt_primary) else color(R.color.mt_surface), color(R.color.mt_border), 12)
        filters.addView(favoriteChip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(39)))
        container.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            isFillViewport = true
            applyUiDirection(this)
            addView(filters)
            if (ui.isRtl) post { fullScroll(View.FOCUS_RIGHT) }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(61)))
        val list = RecyclerView(this).apply { layoutManager = LinearLayoutManager(this@MainActivity); setPadding(0, dp(16), 0, dp(12)); clipToPadding = false; applyUiDirection(this) }
        lateinit var adapter: ProxyAdapter
        adapter = ProxyAdapter(ui, { }, { proxy, action -> handleSwipeAction(proxy, action, adapter) })
        val visible = store.proxies().filter { (proxyFilter == null || it.status == proxyFilter) && (!favoritesOnly || it.favorite) }
            .sortedWith(compareByDescending<ProxyRecord> { it.favorite }.thenBy { it.latencyMs ?: Long.MAX_VALUE })
        adapter.submit(visible)
        list.adapter = adapter
        proxyListAdapter = adapter
        if (visible.isEmpty()) {
            val (title, body) = when {
                store.proxies().isEmpty() -> t("هنوز پراکسی‌ای دریافت نشده", "No proxies yet") to t("از صفحهٔ خانه، دریافت پراکسی‌ها را شروع کنید.", "Fetch proxies from Home to get started.")
                favoritesOnly -> t("علاقه‌مندی ندارید", "No favorites yet") to t("برای افزودن، کارت پراکسی را به سمت راست بکشید.", "Swipe a proxy card to the right to save it.")
                else -> t("نتیجه‌ای با این فیلتر نیست", "No match for this filter") to t("فیلتر دیگری را انتخاب کنید یا دوباره دریافت کنید.", "Choose another filter or fetch again.")
            }
            container.addView(emptyCard(title, body), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(16) })
        } else {
            container.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        return container
    }

    private fun sourcesView(): View {
        val allSources = store.sources()
        val activeCount = allSources.count { it.enabled }
        val errorCount = allSources.count { it.lastError != null }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(22), dp(24), 0)
            applyUiDirection(this)
        }
        container.addView(label(t("منابع پراکسی", "Proxy sources"), 26, color(R.color.mt_text), true))
        val stats = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(18), 0, 0)
            applyUiDirection(this)
            addView(statCard(R.drawable.ic_stat_globe, t("فعال", "Active"), activeCount, "#1A2D4F", "#14223E") { sourceActiveCountText = it }, LinearLayout.LayoutParams(0, dp(104), 1f).apply { marginEnd = dp(6) })
            addView(statCard(R.drawable.ic_stat_check, t("خطا", "Issues"), errorCount, "#3F242D", "#291A22") { sourceIssueCountText = it }, LinearLayout.LayoutParams(0, dp(104), 1f).apply { marginStart = dp(6) })
        }
        container.addView(stats)
        container.addView(homeTestProgress(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) })
        container.addView(sourceTools(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) })
        container.addView(sourceFilters(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)).apply { topMargin = dp(8) })
        val empty = emptyCard(t("فهرست خالی است", "Nothing to show"), t("منبعی با این فیلتر وجود ندارد.", "There are no sources matching this filter."))
        sourceEmptyState = empty
        container.addView(empty, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
        val list = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            clipToPadding = false
            setPadding(0, dp(8), 0, dp(12))
            applyUiDirection(this)
        }
        val adapter = SourceAdapter(
            ui = ui,
            onToggle = { source, enabled ->
                store.saveSources(store.sources().map { if (it.id == source.id) it.copy(enabled = enabled) else it })
                refreshSourcesInPlace()
            },
            onLimit = { source -> chooseSourceFetchLimit(source) },
            onEdit = { source -> showSourceActions(source) },
        )
        sourceListAdapter = adapter
        adapter.submit(filteredSources())
        list.adapter = adapter
        container.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        refreshSourcesInPlace()
        return container
    }

    private fun settingsView(): View = scroll {
        var prefs = store.appPreferences()
        addView(buttonCard(t("زبان برنامه", "App language"), if (prefs.language == AppLanguage.FA) "فارسی" else "English") { chooseLanguage() })
        addView(section(t("دریافت و آزمون", "Fetch & testing")))
        val settings = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; background = cardBackground(18); applyUiDirection(this) }
        settings.addView(setting(t("آزمون پس از دریافت", "Test after fetch"), t("فقط پس از دریافت دستی منابع اجرا می‌شود", "Runs only after a manual fetch"), prefs.autoTestAfterFetch) { value ->
            prefs = prefs.copy(autoTestAfterFetch = value)
            store.saveAppPreferences(prefs)
        })
        settings.addView(divider())
        settings.addView(setting(t("آزمون دوره‌ای", "Periodic testing"), t("فقط هنگام فعال‌سازی شما اجرا می‌شود", "Runs only when you enable it"), prefs.periodicTestEnabled) { value ->
            prefs = prefs.copy(periodicTestEnabled = value)
            store.saveAppPreferences(prefs)
            PeriodicTestScheduler.apply(this@MainActivity, prefs)
            showTab(Tab.SETTINGS)
        })
        settings.addView(divider())
        settings.addView(setting(t("بازخورد لمسی", "Haptic feedback"), t("فقط عملیات اصلی", "Only primary actions"), prefs.hapticsEnabled) { value ->
            prefs = prefs.copy(hapticsEnabled = value)
            store.saveAppPreferences(prefs)
        })
        addView(settings)
        if (prefs.periodicTestEnabled) {
            addView(buttonCard(t("بازه آزمون دوره‌ای", "Periodic test interval"), "${prefs.periodicTestMinutes} ${t("دقیقه", "minutes")}") { choosePeriodicInterval() })
        }
        addView(section(t("داده‌ها", "Data")))
        addView(buttonCard(t("پاک‌سازی پراکسی‌ها", "Clear proxies"), t("همهٔ نتایج محلی حذف می‌شوند", "All local results will be removed"), true) { confirmClear() })
    }

    private fun chooseLanguage() {
        val labels = arrayOf("فارسی", "English")
        val selected = if (store.appPreferences().language == AppLanguage.FA) 0 else 1
        AlertDialog.Builder(this)
            .setTitle(t("زبان برنامه", "App language"))
            .setSingleChoiceItems(labels, selected) { dialog, index ->
                val prefs = store.appPreferences().copy(language = if (index == 0) AppLanguage.FA else AppLanguage.EN)
                store.saveAppPreferences(prefs)
                ui = UiText(prefs.language)
                dialog.dismiss()
                showTab(Tab.SETTINGS)
            }
            .setNegativeButton(t("انصراف", "Cancel"), null)
            .show().applyDialogDirection()
    }

    private fun choosePeriodicInterval() {
        val options = intArrayOf(15, 30, 60, 120)
        val prefs = store.appPreferences()
        AlertDialog.Builder(this)
            .setTitle(t("بازه آزمون دوره‌ای", "Periodic test interval"))
            .setSingleChoiceItems(options.map { "$it ${t("دقیقه", "minutes")}" }.toTypedArray(), options.indexOf(prefs.periodicTestMinutes).coerceAtLeast(0)) { dialog, index ->
                val updated = prefs.copy(periodicTestMinutes = options[index])
                store.saveAppPreferences(updated)
                PeriodicTestScheduler.apply(this, updated)
                dialog.dismiss()
                showTab(Tab.SETTINGS)
            }
            .setNegativeButton(t("انصراف", "Cancel"), null)
            .show().applyDialogDirection()
    }

    private fun sourceTools(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        background = rounded(color(R.color.mt_surface_raised), color(R.color.mt_border), 20)
        setPadding(dp(6), dp(10), dp(6), dp(8))
        applyUiDirection(this)
        val actions: List<Triple<Int, String, () -> Unit>> = listOf(
            Triple(R.drawable.ic_quick_add, t("افزودن", "Add"), { editSource(null); Unit }),
            Triple(R.drawable.ic_stat_check, t("همه روشن", "Enable all"), { setAllSourcesEnabled(true); Unit }),
            Triple(R.drawable.ic_action_delete, t("همه خاموش", "Disable all"), { setAllSourcesEnabled(false); Unit }),
            Triple(R.drawable.ic_stat_globe, t("سقف کلی", "Global limit"), { chooseGlobalSourceFetchLimit(); Unit }),
        )
        actions.forEach { (icon, title, click) ->
            addView(quickIconAction(icon, title, click), LinearLayout.LayoutParams(0, dp(74), 1f))
        }
    }

    private fun sourceFilters(): View = HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false
        isFillViewport = true
        applyUiDirection(this)
        val row = LinearLayout(context).apply {
            gravity = if (ui.isRtl) Gravity.RIGHT else Gravity.LEFT
            applyUiDirection(this)
        }
        val filters = listOf(
            SourceFilter.ALL to t("همه", "All"),
            SourceFilter.ENABLED to t("فعال", "Enabled"),
            SourceFilter.ERRORS to t("خطادار", "Issues"),
        )
        sourceFilterChips.clear()
        filters.forEach { (filter, title) ->
            val selected = sourceFilter == filter
            val chip = action(title) { sourceFilter = filter; refreshSourcesInPlace() }
            chip.setTextColor(if (selected) color(R.color.mt_background) else color(R.color.mt_primary))
            chip.background = rounded(if (selected) color(R.color.mt_primary) else color(R.color.mt_surface), color(R.color.mt_border), 12)
            sourceFilterChips[filter] = chip
            row.addView(chip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)).apply { marginEnd = dp(8) })
        }
        addView(row)
        if (ui.isRtl) post { fullScroll(View.FOCUS_RIGHT) }
    }

    private fun setAllSourcesEnabled(enabled: Boolean) {
        store.saveSources(store.sources().map { it.copy(enabled = enabled) })
        toast(if (enabled) t("همهٔ منابع فعال شدند", "All sources enabled") else t("همهٔ منابع غیرفعال شدند", "All sources disabled"))
        refreshSourcesInPlace()
    }

    private fun filteredSources(): List<SourceDefinition> = store.sources().filter { source ->
        when (sourceFilter) {
            SourceFilter.ALL -> true
            SourceFilter.ENABLED -> source.enabled
            SourceFilter.ERRORS -> source.lastError != null
        }
    }

    private fun refreshSourcesInPlace() {
        if (currentTab != Tab.SOURCES) return
        val all = store.sources()
        val visible = filteredSources()
        sourceListAdapter?.submit(visible)
        sourceEmptyState?.visibility = if (visible.isEmpty()) View.VISIBLE else View.GONE
        sourceActiveCountText?.text = all.count { it.enabled }.toString()
        sourceIssueCountText?.text = all.count { it.lastError != null }.toString()
        sourceFilterChips.forEach { (filter, chip) ->
            val selected = filter == sourceFilter
            chip.setTextColor(if (selected) color(R.color.mt_background) else color(R.color.mt_primary))
            chip.background = rounded(if (selected) color(R.color.mt_primary) else color(R.color.mt_surface), color(R.color.mt_border), 12)
        }
    }

    private fun showSourceActions(source: SourceDefinition) {
        val choices = mutableListOf(
            t("دریافت فقط از این منبع", "Fetch this source"),
            t("ویرایش منبع", "Edit source"),
            t("پاک کردن وضعیت و خطا", "Clear status and error"),
        )
        if (!source.builtIn) choices += t("حذف منبع", "Delete source")
        AlertDialog.Builder(this)
            .setTitle(source.title)
            .setItems(choices.toTypedArray()) { _, index ->
                when (choices[index]) {
                    t("دریافت فقط از این منبع", "Fetch this source") -> fetchOneSource(source)
                    t("ویرایش منبع", "Edit source") -> editSource(source)
                    t("پاک کردن وضعیت و خطا", "Clear status and error") -> {
                        store.saveSources(store.sources().map { if (it.id == source.id) it.copy(lastFetchedAt = 0L, lastFetchCount = 0, lastError = null) else it })
                        refreshSourcesInPlace()
                    }
                    t("حذف منبع", "Delete source") -> {
                        store.saveSources(store.sources().filterNot { it.id == source.id })
                        refreshSourcesInPlace()
                    }
                }
            }
            .setNegativeButton(t("بستن", "Close"), null)
            .show().applyDialogDirection()
    }

    private fun chooseSourceFetchLimit(source: SourceDefinition) {
        val options = intArrayOf(5, 10, 15, 20, 25, 50, 100, 250)
        AlertDialog.Builder(this)
            .setTitle(t("سقف دریافت ${source.title}", "Fetch limit for ${source.title}"))
            .setSingleChoiceItems(options.map { "$it" }.toTypedArray(), options.indexOf(source.fetchLimit).coerceAtLeast(0)) { dialog, index ->
                store.saveSources(store.sources().map { if (it.id == source.id) it.copy(fetchLimit = options[index]) else it })
                dialog.dismiss()
                refreshSourcesInPlace()
            }
            .setNegativeButton(t("انصراف", "Cancel"), null)
            .show().applyDialogDirection()
    }

    private fun chooseGlobalSourceFetchLimit() {
        val options = intArrayOf(5, 10, 15, 20, 25, 50, 100, 250)
        val sources = store.sources()
        val sharedLimit = sources.map { it.fetchLimit }.distinct().singleOrNull()
        AlertDialog.Builder(this)
            .setTitle(t("سقف کلی منابع", "Global source limit"))
            .setSingleChoiceItems(options.map { "$it" }.toTypedArray(), sharedLimit?.let { options.indexOf(it).coerceAtLeast(0) } ?: -1) { dialog, index ->
                store.saveSources(sources.map { it.copy(fetchLimit = options[index]) })
                dialog.dismiss()
                toast(t("سقف همهٔ منابع روی ${options[index]} قرار گرفت", "All source limits set to ${options[index]}"))
                refreshSourcesInPlace()
            }
            .setNegativeButton(t("انصراف", "Cancel"), null)
            .show().applyDialogDirection()
    }

    private fun editSource(existing: SourceDefinition?) {
        val form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(6), dp(20), 0); applyUiDirection(this) }
        val name = EditText(this).apply {
            hint = t("نام منبع", "Source name"); setText(existing?.title.orEmpty()); textSize = 15f
            layoutDirection = if (ui.isRtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
            textDirection = if (ui.isRtl) View.TEXT_DIRECTION_FIRST_STRONG_RTL else View.TEXT_DIRECTION_FIRST_STRONG_LTR
            gravity = if (ui.isRtl) Gravity.RIGHT else Gravity.LEFT
        }
        val url = EditText(this).apply {
            hint = "https://…"; setText(existing?.url.orEmpty()); inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI; textSize = 13f
            layoutDirection = View.LAYOUT_DIRECTION_LTR; textDirection = View.TEXT_DIRECTION_LTR; gravity = Gravity.LEFT
        }
        val typeLabels = arrayOf(t("خودکار", "Auto"), "Text", "JSON", "HTML")
        var selected = existing?.type?.ordinal ?: 0
        form.addView(name); form.addView(url)
        AlertDialog.Builder(this)
            .setTitle(if (existing == null) t("منبع جدید", "New source") else t("ویرایش منبع", "Edit source"))
            .setView(form)
            .setSingleChoiceItems(typeLabels, selected) { _, which -> selected = which }
            .setNegativeButton(t("انصراف", "Cancel"), null)
            .setNeutralButton(if (existing?.builtIn == false) t("حذف", "Delete") else "") { _, _ -> if (existing != null) { store.saveSources(store.sources().filterNot { it.id == existing.id }); refreshSourcesInPlace() } }
            .setPositiveButton(t("ذخیره", "Save")) { _, _ ->
                val title = name.text.toString().trim(); val sourceUrl = url.text.toString().trim()
                if (title.isBlank() || !sourceUrl.startsWith("https://")) { toast(t("نام و آدرس HTTPS معتبر لازم است", "A valid name and HTTPS URL are required")); return@setPositiveButton }
                val source = SourceDefinition(
                    id = existing?.id ?: MTLinkStore.newId("source"),
                    title = title,
                    url = sourceUrl,
                    type = SourceType.entries[selected],
                    enabled = existing?.enabled ?: true,
                    builtIn = existing?.builtIn ?: false,
                    fetchLimit = existing?.fetchLimit ?: 25,
                    lastFetchedAt = existing?.lastFetchedAt ?: 0L,
                    lastFetchCount = existing?.lastFetchCount ?: 0,
                    lastError = existing?.lastError,
                )
                store.saveSources(store.sources().map { if (it.id == source.id) source else it }.let { current -> if (existing == null) current + source else current })
                refreshSourcesInPlace()
            }.show().applyDialogDirection()
    }

    private fun fetchOneSource(source: SourceDefinition) {
        if (loadingOverlay.visibility == View.VISIBLE || testProgressActive || fetchProgressActive) return
        startFetchProgress(1)
        io.execute {
            try {
                val found = ProxyEngine.fetch(source, source.fetchLimit.coerceIn(5, 250))
                val current = store.proxies().associateBy { it.stableKey() }.toMutableMap()
                var added = 0
                found.forEach { candidate ->
                    val old = current[candidate.stableKey()]
                    if (old == null) {
                        current[candidate.stableKey()] = candidate
                        added += 1
                    } else {
                        current[candidate.stableKey()] = candidate.copy(
                            id = old.id,
                            status = old.status,
                            latencyMs = old.latencyMs,
                            testedAt = old.testedAt,
                            lastError = old.lastError,
                            favorite = old.favorite,
                            countryCode = old.countryCode,
                        )
                    }
                }
                store.saveProxies(current.values.sortedByDescending { it.fetchedAt }.take(500))
                store.saveSources(store.sources().map {
                    if (it.id == source.id) it.copy(lastFetchedAt = System.currentTimeMillis(), lastFetchCount = found.size, lastError = null) else it
                })
                postUi {
                    updateFetchProgress(1, source.title)
                    finishFetchProgress()
                    toast(if (added > 0) t("$added پراکسی جدید دریافت شد", "$added new proxies fetched") else t("پراکسی تازه‌ای پیدا نشد", "No new proxies found"))
                    refreshSourcesInPlace()
                }
            } catch (error: Exception) {
                store.saveSources(store.sources().map {
                    if (it.id == source.id) it.copy(lastError = error.message?.take(80) ?: t("دریافت ناموفق", "Fetch failed")) else it
                })
                postUi {
                    finishFetchProgress()
                    toast(t("دریافت این منبع ناموفق بود", "This source could not be fetched"))
                    refreshSourcesInPlace()
                }
            }
        }
    }

    private fun fetchSources() {
        if (loadingOverlay.visibility == View.VISIBLE || testProgressActive || fetchProgressActive) return
        val sourceList = store.sources().toMutableList()
        val enabledCount = sourceList.count { it.enabled }
        if (enabledCount == 0) { toast(t("هیچ منبع فعالی انتخاب نشده است", "No active sources selected")); return }
        startFetchProgress(enabledCount)
        io.execute {
            try {
                val prefs = store.appPreferences()
                val incoming = LinkedHashMap<String, ProxyRecord>()
                var errors = 0
                var completed = 0
                sourceList.indices.forEach { index ->
                    val source = sourceList[index]
                    if (!source.enabled) return@forEach
                    postUi { updateFetchProgress(completed, source.title) }
                    try {
                        val found = ProxyEngine.fetch(source, source.fetchLimit.coerceIn(5, 250))
                        found.forEach { proxy -> if (incoming.size < 500) incoming.putIfAbsent(proxy.stableKey(), proxy) }
                        sourceList[index] = source.copy(lastFetchedAt = System.currentTimeMillis(), lastFetchCount = found.size, lastError = null)
                    } catch (error: Exception) {
                        errors += 1; sourceList[index] = source.copy(lastError = error.message?.take(80) ?: "دریافت ناموفق")
                    }
                    completed += 1
                    postUi { updateFetchProgress(completed, source.title) }
                }
                val current = store.proxies().associateBy { it.stableKey() }.toMutableMap()
                var added = 0
                incoming.values.forEach { candidate ->
                    val old = current[candidate.stableKey()]
                    if (old == null) { current[candidate.stableKey()] = candidate; added += 1 } else current[candidate.stableKey()] = candidate.copy(id = old.id, status = old.status, latencyMs = old.latencyMs, testedAt = old.testedAt, lastError = old.lastError, favorite = old.favorite, countryCode = old.countryCode)
                }
                val persisted = current.values.sortedByDescending { it.fetchedAt }.take(500)
                store.saveSources(sourceList)
                store.saveProxies(persisted)
                postUi {
                    finishFetchProgress()
                    toast(if (added > 0) t("$added پراکسی جدید دریافت شد", "$added new proxies fetched") else t("پراکسی تازه‌ای پیدا نشد", "No new proxies found"))
                    if (errors > 0) toast(t("$errors منبع در دسترس نبود", "$errors sources were unavailable"))
                    if (prefs.autoTestAfterFetch && persisted.isNotEmpty()) testAll()
                    else if (currentTab == Tab.SOURCES) refreshSourcesInPlace()
                    else showTab(currentTab)
                }
            } catch (error: Throwable) {
                postUi {
                    finishFetchProgress()
                    toast(t("دریافت کامل نشد", "Fetching did not finish"))
                }
            }
        }
    }

    private fun testAll() {
        val all = store.proxies()
        if (all.isEmpty()) { toast(t("ابتدا پراکسی دریافت کنید", "Fetch proxies first")); return }
        if (loadingOverlay.visibility == View.VISIBLE || testProgressActive || fetchProgressActive) return
        startTestProgress(all.size)
        io.execute {
            val workers = Executors.newFixedThreadPool(8)
            try {
                val done = AtomicInteger(0)
                val checks = all.map { proxy -> workers.submit<ProxyRecord> {
                    val tested = ProxyTestRunner.test(proxy)
                    val completed = done.incrementAndGet()
                    postUi { updateTestProgress(completed) }
                    tested
                } }
                val checked = checks.map { check -> check.get() }
                store.saveProxies(checked)
                postUi {
                    finishTestProgress()
                    toast(t("آزمون اتصال کامل شد", "Connection testing complete"))
                    if (currentTab == Tab.SOURCES) refreshSourcesInPlace() else showTab(currentTab)
                }
            } catch (error: Exception) {
                postUi { finishTestProgress(); toast(t("آزمون کامل نشد: ${error.message ?: "خطای ناشناخته"}", "Testing did not finish: ${error.message ?: "Unknown error"}")) }
            } finally {
                workers.shutdownNow()
            }
        }
    }

    private fun showProxyActions(proxy: ProxyRecord) {
        val actions = mutableListOf(t("تست دوباره", "Test again"), t("کپی لینک", "Copy link"), t("اشتراک‌گذاری", "Share"), t("QR کد", "QR code"), if (proxy.favorite) t("حذف علاقه‌مندی", "Remove favorite") else t("افزودن به علاقه‌مندی", "Add favorite"), t("حذف پراکسی", "Delete proxy"))
        if (proxy.protocol == ProxyProtocol.MTPROTO) actions.add(0, t("بازکردن در Telegram", "Open in Telegram"))
        AlertDialog.Builder(this)
            .setTitle(proxy.displayAddress())
            .setMessage("${if (proxy.protocol == ProxyProtocol.MTPROTO) "MTProto" else "SOCKS5"} · ${proxy.status.name.lowercase()}")
            .setItems(actions.toTypedArray()) { _, index ->
                when (actions[index]) {
                    t("تست دوباره", "Test again") -> testOne(proxy)
                    t("کپی لینک", "Copy link") -> copyProxy(proxy)
                    t("اشتراک‌گذاری", "Share") -> shareProxy(proxy)
                    t("بازکردن در Telegram", "Open in Telegram") -> openTelegram(proxy)
                    t("QR کد", "QR code") -> showQr(proxy)
                    t("حذف علاقه‌مندی", "Remove favorite"), t("افزودن به علاقه‌مندی", "Add favorite") -> toggleFavorite(proxy)
                    t("حذف پراکسی", "Delete proxy") -> deleteProxy(proxy)
                }
            }.setNegativeButton(t("بستن", "Close"), null).show().applyDialogDirection()
    }

    private fun handleSwipeAction(proxy: ProxyRecord, action: SwipeAction, adapter: ProxyAdapter? = proxyListAdapter) {
        when (action) {
            SwipeAction.OPEN_TELEGRAM -> openTelegram(proxy)
            SwipeAction.SHARE -> shareProxy(proxy)
            SwipeAction.COPY -> copyProxy(proxy)
            SwipeAction.DELETE -> deleteProxy(proxy, adapter)
            SwipeAction.QR -> showQr(proxy)
            SwipeAction.FAVORITE -> toggleFavorite(proxy, adapter)
        }
    }

    private fun toggleFavorite(proxy: ProxyRecord, adapter: ProxyAdapter? = proxyListAdapter) {
        val updated = proxy.copy(favorite = !proxy.favorite)
        store.saveProxies(store.proxies().map { if (it.id == proxy.id) updated else it })
        if (store.appPreferences().hapticsEnabled) window.decorView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        if (favoritesOnly && !updated.favorite) adapter?.removeItem(proxy.id) else adapter?.updateItem(updated)
    }

    private fun deleteProxy(proxy: ProxyRecord, adapter: ProxyAdapter? = proxyListAdapter) {
        store.saveProxies(store.proxies().filterNot { it.id == proxy.id })
        adapter?.removeItem(proxy.id)
    }

    private fun showQr(proxy: ProxyRecord) = showProxyQr(this, proxy, proxyLink(proxy), ui)

    private fun testOne(proxy: ProxyRecord) {
        if (loadingOverlay.visibility == View.VISIBLE) return
        loadingOverlay.showLoading(t("در حال بررسی", "Checking"), proxy.displayAddress())
        io.execute {
            try {
                val updated = ProxyTestRunner.test(proxy)
                store.saveProxies(store.proxies().map { if (it.id == proxy.id) updated else it })
                postUi { loadingOverlay.hideLoading(); toast(if (updated.status == ProxyStatus.REACHABLE) t("اتصال برقرار شد", "Connection available") else t("اتصال ناموفق بود", "Connection failed")); showTab(currentTab) }
            } catch (error: Exception) {
                postUi { loadingOverlay.hideLoading(); toast(t("آزمون ناموفق بود", "Test failed")) }
            }
        }
    }

    private fun proxyLink(proxy: ProxyRecord): String = if (proxy.protocol == ProxyProtocol.MTPROTO) {
        "tg://proxy?server=${Uri.encode(proxy.host)}&port=${proxy.port}&secret=${Uri.encode(proxy.secret.orEmpty())}"
    } else "tg://socks?server=${Uri.encode(proxy.host)}&port=${proxy.port}"

    private fun copyProxy(proxy: ProxyRecord) {
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("MTLink proxy", proxyLink(proxy)))
        toast(t("لینک کپی شد", "Link copied"))
    }

    private fun shareProxy(proxy: ProxyRecord) {
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, proxyLink(proxy)) }, t("اشتراک‌گذاری پراکسی", "Share proxy")))
    }

    private fun openTelegram(proxy: ProxyRecord) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(proxyLink(proxy)))) }.onFailure { toast(t("Telegram برای باز کردن لینک در دسترس نیست", "Telegram is unavailable to open this link")) }
    }

    private fun topProxyList(items: List<ProxyRecord>): RecyclerView = RecyclerView(this).apply {
        layoutManager = LinearLayoutManager(this@MainActivity)
        isNestedScrollingEnabled = false
        clipToPadding = false
        applyUiDirection(this)
        lateinit var adapter: ProxyAdapter
        adapter = ProxyAdapter(ui, { }, { proxy, action -> handleSwipeAction(proxy, action, adapter) })
        adapter.submit(items)
        this.adapter = adapter
    }

    private fun confirmClear() = AlertDialog.Builder(this)
        .setTitle(t("پاک‌سازی پراکسی‌ها", "Clear proxies"))
        .setMessage(t("همهٔ نتایج ذخیره‌شده از این دستگاه حذف می‌شوند.", "All stored results will be removed from this device."))
        .setNegativeButton(t("انصراف", "Cancel"), null)
        .setPositiveButton(t("پاک‌سازی", "Clear")) { _, _ -> store.clearProxies(); showTab(currentTab) }
        .show().applyDialogDirection()
    private fun scroll(block: LinearLayout.() -> Unit): ScrollView = ScrollView(this).apply {
        applyUiDirection(this)
        val child = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(22), dp(24), dp(30))
            applyUiDirection(this)
            block()
        }
        addView(child)
    }
    private fun brandHeader(): View = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        applyUiDirection(this)
        val mark = ImageView(context).apply {
            setImageResource(R.drawable.mtlink_mascot)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "MTLink"
        }
        val copy = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; applyUiDirection(this) }
        copy.addView(label("MTLink", 27, color(R.color.mt_text), true).apply { textDirection = View.TEXT_DIRECTION_LTR })
        copy.addView(label(t("مدیریت پراکسی تلگرام", "Telegram proxy manager"), 13, color(R.color.mt_muted), false).apply { maxLines = 1; setPadding(0, dp(2), 0, 0) })
        if (ui.isRtl) {
            addView(mark, LinearLayout.LayoutParams(dp(50), dp(50)))
            addView(copy, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(10) })
        } else {
            addView(mark, LinearLayout.LayoutParams(dp(50), dp(50)))
            addView(copy, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(10) })
        }
    }
    private fun header(title: String, subtitle: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = if (ui.isRtl) Gravity.RIGHT else Gravity.LEFT
        addView(label(title, 26, color(R.color.mt_text), true))
        addView(label(subtitle, 13, color(R.color.mt_muted), false).apply { maxLines = 2; setPadding(0, dp(4), 0, 0) })
    }
    private fun statCard(icon: Int, label: String, number: Int, start: String, end: String, onNumberReady: ((TextView) -> Unit)? = null): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; gravity = if (ui.isRtl) Gravity.RIGHT or Gravity.CENTER_VERTICAL else Gravity.LEFT or Gravity.CENTER_VERTICAL
        background = gradient(start, end, 20); elevation = dp(1).toFloat(); setPadding(dp(16), dp(12), dp(16), dp(12)); applyUiDirection(this)
        addView(ImageView(context).apply { setImageResource(icon); scaleType = ImageView.ScaleType.CENTER_INSIDE }, LinearLayout.LayoutParams(dp(24), dp(24)))
        addView(this@MainActivity.label(label, 12, color(R.color.mt_primary_light), true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
        val numberView = this@MainActivity.label(number.toString(), 34, color(R.color.mt_text), true).apply { textDirection = View.TEXT_DIRECTION_LTR }
        onNumberReady?.invoke(numberView)
        addView(numberView)
    }
    private fun section(title: String, action: String? = null, onAction: (() -> Unit)? = null): View = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(30), 0, dp(10)); applyUiDirection(this)
        addView(label(title, 17, color(R.color.mt_text), true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (action != null) addView(label(action, 12, color(R.color.mt_primary_light), true).apply { setPadding(dp(9), dp(5), dp(9), dp(5)); background = rounded(color(R.color.mt_primary_soft), Color.TRANSPARENT, 11); setOnClickListener { onAction?.invoke() } }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }
    private fun action(text: String, click: () -> Unit): TextView = label(text, 12, color(R.color.mt_primary_light), true).apply { gravity = Gravity.CENTER; setPadding(dp(13), 0, dp(13), 0); background = rounded(color(R.color.mt_surface_soft), color(R.color.mt_border), 14); setOnClickListener { click() } }
    private fun quickAction(text: String, click: () -> Unit): TextView = label(text, 13, color(R.color.mt_primary_light), true).apply { gravity = Gravity.CENTER; setPadding(dp(14), 0, dp(14), 0); background = rounded(color(R.color.mt_surface_soft), color(R.color.mt_border), 14); setOnClickListener { click() } }
    private fun quickActionsBar(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        background = rounded(color(R.color.mt_surface_raised), color(R.color.mt_border), 20)
        setPadding(dp(6), dp(12), dp(6), dp(10))
        applyUiDirection(this)
        val actions: List<Triple<Int, String, () -> Unit>> = listOf(
            Triple(R.drawable.ic_quick_fetch, t("دریافت", "Fetch"), { fetchSources(); Unit }),
            Triple(R.drawable.ic_quick_test, t("تست همه", "Test all"), { testAll(); Unit }),
            Triple(R.drawable.ic_quick_add, t("منابع", "Sources"), { showTab(Tab.SOURCES); Unit }),
            Triple(R.drawable.ic_action_delete, t("پاک‌سازی", "Clear"), { confirmClear(); Unit }),
        )
        actions.forEach { (icon, title, click) ->
            addView(quickIconAction(icon, title, click), LinearLayout.LayoutParams(0, dp(74), 1f))
        }
    }
    private fun quickIconAction(icon: Int, title: String, click: () -> Unit): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setOnClickListener { click() }
        val image = ImageView(context).apply {
            setImageResource(icon)
            scaleType = ImageView.ScaleType.CENTER
            background = rounded(color(R.color.mt_surface_soft), color(R.color.mt_border), 24)
        }
        addView(image, LinearLayout.LayoutParams(dp(46), dp(46)))
        addView(label(title, 11, color(R.color.mt_muted), true).apply { gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER; setPadding(0, dp(4), 0, 0) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }
    private fun homeTestProgress(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = cardBackground(18)
        setPadding(dp(16), dp(14), dp(16), dp(14))
        visibility = if (testProgressActive || fetchProgressActive) View.VISIBLE else View.GONE
        applyUiDirection(this)
        val headline = label(progressHeadline(), 13, color(R.color.mt_text), true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
        val progress = label(progressCopy(), 12, color(R.color.mt_muted), false).apply { gravity = if (ui.isRtl) Gravity.LEFT else Gravity.RIGHT; textDirection = View.TEXT_DIRECTION_LTR }
        val top = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL; applyUiDirection(this); addView(headline, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)); addView(progress, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)) }
        val track = FrameLayout(context).apply { background = rounded(color(R.color.mt_surface_soft), Color.TRANSPARENT, 5) }
        val fill = View(context).apply { background = rounded(color(R.color.mt_primary), Color.TRANSPARENT, 5) }
        track.addView(fill, FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, if (ui.isRtl) Gravity.RIGHT else Gravity.LEFT))
        addView(top)
        addView(track, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(8)).apply { topMargin = dp(11) })
        homeProgressPanel = this
        homeProgressHeadline = headline
        homeProgressTrack = track
        homeProgressFill = fill
        homeProgressText = progress
        post { renderTestProgress() }
    }
    private fun startTestProgress(total: Int) {
        testProgressActive = true
        testProgressTotal.set(total)
        testProgressCompleted.set(0)
        homeProgressPanel?.visibility = View.VISIBLE
        renderTestProgress()
    }
    private fun updateTestProgress(completed: Int) {
        testProgressCompleted.set(completed.coerceIn(0, testProgressTotal.get()))
        renderTestProgress()
    }
    private fun finishTestProgress() {
        testProgressCompleted.set(testProgressTotal.get())
        renderTestProgress()
        testProgressActive = false
        hideHomeProgressIfIdle()
    }
    private fun startFetchProgress(total: Int) {
        fetchProgressActive = true
        fetchProgressTotal.set(total)
        fetchProgressCompleted.set(0)
        fetchProgressStatus = t("در حال آماده‌سازی منابع", "Preparing sources")
        homeProgressPanel?.visibility = View.VISIBLE
        renderTestProgress()
    }
    private fun updateFetchProgress(completed: Int, sourceTitle: String) {
        fetchProgressCompleted.set(completed.coerceIn(0, fetchProgressTotal.get()))
        fetchProgressStatus = sourceTitle
        renderTestProgress()
    }
    private fun finishFetchProgress() {
        fetchProgressCompleted.set(fetchProgressTotal.get())
        renderTestProgress()
        fetchProgressActive = false
        hideHomeProgressIfIdle()
    }
    private fun hideHomeProgressIfIdle() {
        if (!testProgressActive && !fetchProgressActive) homeProgressPanel?.visibility = View.GONE
    }
    private fun renderTestProgress() {
        val total = if (fetchProgressActive) fetchProgressTotal.get().coerceAtLeast(1) else testProgressTotal.get().coerceAtLeast(1)
        val completed = if (fetchProgressActive) fetchProgressCompleted.get() else testProgressCompleted.get()
        val fraction = completed.toFloat() / total.toFloat()
        homeProgressHeadline?.text = progressHeadline()
        homeProgressText?.text = "$completed/$total · ${(fraction * 100).toInt()}%"
        val track = homeProgressTrack ?: return
        if (track.width == 0) { track.post { renderTestProgress() }; return }
        homeProgressFill?.layoutParams = FrameLayout.LayoutParams((track.width * fraction).toInt(), ViewGroup.LayoutParams.MATCH_PARENT, if (ui.isRtl) Gravity.RIGHT else Gravity.LEFT)
    }
    private fun progressHeadline(): String = if (fetchProgressActive) {
        fetchProgressStatus.takeIf { it.isNotBlank() }?.let { source -> t("در حال دریافت: $source", "Fetching: $source") }
            ?: t("در حال دریافت منابع", "Fetching sources")
    } else t("در حال آزمون پراکسی‌ها", "Testing proxies")
    private fun progressCopy(): String {
        val total = if (fetchProgressActive) fetchProgressTotal.get() else testProgressTotal.get()
        val completed = if (fetchProgressActive) fetchProgressCompleted.get() else testProgressCompleted.get()
        return "$completed/$total · ${if (total == 0) 0 else (completed * 100 / total)}%"
    }
    private fun primary(text: String, click: () -> Unit): TextView = label(text, 15, Color.WHITE, true).apply { gravity = Gravity.CENTER; setPadding(dp(18), 0, dp(18), 0); background = gradient("#7697FF", "#536ECA", 16); setOnClickListener { click() } }
    private fun proxyRow(proxy: ProxyRecord, click: () -> Unit): View = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; minimumHeight = dp(76); background = cardBackground(18); setPadding(dp(16), dp(13), dp(16), dp(13)); setOnClickListener { click() }; addView(label(proxy.displayAddress(), 15, color(R.color.mt_text), true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }); addView(label("${if (proxy.protocol == ProxyProtocol.MTPROTO) "MTProto" else "SOCKS5"} · ${proxy.latencyMs?.let { "$it ms" } ?: t("تست‌نشده", "Untested")}", 12, color(R.color.mt_muted), false).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; setPadding(0, dp(4), 0, 0) }); layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(10) } }
    private fun emptyCard(title: String, body: String) = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = if (ui.isRtl) Gravity.RIGHT else Gravity.LEFT; background = cardBackground(20); setPadding(dp(20), dp(22), dp(20), dp(22)); applyUiDirection(this); addView(label(title, 17, color(R.color.mt_text), true)); addView(label(body, 13, color(R.color.mt_muted), false).apply { maxLines = 2; setPadding(0, dp(8), 0, 0) }) }
    private fun setting(title: String, body: String, checked: Boolean, changed: (Boolean) -> Unit) = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL; minimumHeight = dp(82); setPadding(dp(18), dp(15), dp(18), dp(15)); applyUiDirection(this)
        val copy = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, dp(12), 0); applyUiDirection(this) }
        copy.addView(label(title, 15, color(R.color.mt_text), true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
        copy.addView(label(body, 12, color(R.color.mt_muted), false).apply { maxLines = 2; setPadding(0, dp(4), 0, 0) })
        addView(copy, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        // fixed: SwitchCompat replaces the deprecated platform Switch widget.
        addView(SwitchCompat(context).apply { isChecked = checked; setOnCheckedChangeListener { _, value -> changed(value) } })
    }
    private fun buttonCard(title: String, body: String, danger: Boolean = false, click: () -> Unit) = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; minimumHeight = dp(80); background = cardBackground(20); setPadding(dp(18), dp(16), dp(18), dp(16)); applyUiDirection(this); setOnClickListener { click() }; addView(label(title, 15, if (danger) color(R.color.mt_danger) else color(R.color.mt_text), true)); addView(label(body, 12, color(R.color.mt_muted), false).apply { maxLines = 2; setPadding(0, dp(5), 0, 0) }); layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(12) } }
    private fun divider() = View(this).apply { setBackgroundColor(color(R.color.mt_border)); layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply { marginStart = dp(15); marginEnd = dp(15) } }
    private fun label(value: String, size: Int, textColor: Int, bold: Boolean) = TextView(this).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        text = value
        textSize = size.toFloat()
        setTextColor(textColor)
        typeface = MTFonts.face(this@MainActivity, bold)
        layoutDirection = if (ui.isRtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
        textDirection = if (ui.isRtl) View.TEXT_DIRECTION_FIRST_STRONG_RTL else View.TEXT_DIRECTION_FIRST_STRONG_LTR
        textAlignment = if (ui.isRtl) View.TEXT_ALIGNMENT_VIEW_START else View.TEXT_ALIGNMENT_VIEW_START
        gravity = if (ui.isRtl) Gravity.RIGHT else Gravity.LEFT
        includeFontPadding = true
        setLineSpacing(dp(2).toFloat(), 1f)
    }
    private fun cardBackground(radius: Int) = rounded(color(R.color.mt_surface_raised), color(R.color.mt_border), radius)
    private fun rounded(fill: Int, stroke: Int, radius: Int) = GradientDrawable().apply { setColor(fill); if (stroke != Color.TRANSPARENT) setStroke(dp(1), stroke); cornerRadius = dp(radius).toFloat() }
    private fun gradient(start: String, end: String, radius: Int) = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.parseColor(start), Color.parseColor(end))).apply { setStroke(dp(1), Color.parseColor("#3A506C")); cornerRadius = dp(radius).toFloat() }
    private fun applyUiDirection(vararg views: View) {
        val layout = if (ui.isRtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
        val text = if (ui.isRtl) View.TEXT_DIRECTION_FIRST_STRONG_RTL else View.TEXT_DIRECTION_FIRST_STRONG_LTR
        views.forEach { view ->
            view.layoutDirection = layout
            if (view is TextView) {
                view.textDirection = text
                view.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                view.gravity = if (ui.isRtl) Gravity.RIGHT else Gravity.LEFT
            }
        }
    }
    private fun color(id: Int) = getColor(id)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    private fun updateLoading(status: String) = postUi { loadingOverlay.updateStatus(status) }
    private fun t(fa: String, en: String) = ui.of(fa, en)
    private fun postUi(action: () -> Unit) {
        if (!isFinishing && (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1 || !isDestroyed)) runOnUiThread(action)
    }
    private fun AlertDialog.applyDialogDirection(): AlertDialog {
        window?.decorView?.layoutDirection = if (ui.isRtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
        return this
    }
}
