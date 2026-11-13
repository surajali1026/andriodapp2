package com.surajali.showme

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Handler
import android.os.Looper
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.widget.FrameLayout
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : Activity() {
    companion object { init { System.loadLibrary("showme_native") } }
    private external fun nativeCrossedAbove(previous: Double, current: Double, target: Double): Boolean
    private external fun nativeCrossedBelow(previous: Double, current: Double, target: Double): Boolean

    private val bg = Color.rgb(13, 18, 27)
    private val panel = Color.rgb(24, 32, 45)
    private val muted = Color.rgb(145, 158, 176)
    private lateinit var body: LinearLayout
    private var statusView: TextView? = null
    private lateinit var marketData: BybitMarketData
    private var symbol = "BTCUSDT"
    private var interval = "1"
    private var marketSymbols = mutableListOf<MarketSymbol>()
    private var marketStatus = "Loading Bybit USDT perpetual markets…"
    private var quoteRows = LinkedHashMap<String, TextView>()
    private var lastPriceView: TextView? = null
    private var markPriceView: TextView? = null
    private var indexPriceView: TextView? = null
    private var chartWeb: WebView? = null
    private var chartReady = false
    private var countdownView: TextView? = null
    private var fullScreen = false
    private var priceType = "Trade"
    private val uiHandler = Handler(Looper.getMainLooper())
    private val countdownTask = object : Runnable {
        override fun run() {
            countdownView?.text = "Candle closes in  " + candleCountdown()
            uiHandler.postDelayed(this, 1000L)
        }
    }
    private var pendingCandles: List<MarketCandle> = emptyList()
    private var activeTab = "All"
    private val prefs by lazy { getSharedPreferences("alerts", MODE_PRIVATE) }

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun txt(s: String, size: Float = 14f, color: Int = Color.WHITE, bold: Boolean = false) =
        TextView(this).apply { text = s; textSize = size; setTextColor(color); if (bold) setTypeface(typeface, 1) }
    private fun card() = LinearLayout(this).apply {
        orientation = 1; setPadding(dp(12), dp(12), dp(12), dp(12)); setBackgroundColor(panel)
    }
    private fun gap(parent: LinearLayout, h: Int = 8) {
        parent.addView(android.view.View(this), LinearLayout.LayoutParams(1, dp(h)))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        marketData = BybitMarketData(
            onMarkets = { list ->
                marketSymbols = list.toMutableList()
                if (marketSymbols.none { it.symbol == symbol } && marketSymbols.isNotEmpty()) symbol = marketSymbols.first().symbol
                if (body.childCount > 0) screen("Quotes")
            },
            onTicker = { item ->
                val index = marketSymbols.indexOfFirst { it.symbol == item.symbol }
                if (index >= 0) marketSymbols[index] = item else marketSymbols.add(item)
                quoteRows[item.symbol]?.text = quoteText(item)
                if (item.symbol == symbol) updatePriceLabels(item)
            },
            onHistory = { selected, candles ->
                if (selected == symbol) {
                    pendingCandles = candles
                    renderHistory(candles)
                }
            },
            onLiveCandle = { selected, candle ->
                if (selected == symbol) renderLiveCandle(candle)
            },
            onStatus = { status ->
                marketStatus = status
                statusView?.text = status
            }
        )
        screen("Quotes")
        marketData.start()
        uiHandler.post(countdownTask)
    }

    override fun onDestroy() {
        uiHandler.removeCallbacks(countdownTask)
        if (::marketData.isInitialized) marketData.stop()
        chartWeb?.destroy()
        super.onDestroy()
    }

    private fun screen(tab: String) {
        chartReady = false
        chartWeb = null
        quoteRows.clear()
        val root = LinearLayout(this).apply {
            orientation = 1
            setPadding(if (fullScreen && tab == "Chart") 0 else dp(8),
                if (fullScreen && tab == "Chart") 0 else dp(8),
                if (fullScreen && tab == "Chart") 0 else dp(8),
                if (fullScreen && tab == "Chart") 0 else dp(3))
            setBackgroundColor(bg)
        }
        if (!(fullScreen && tab == "Chart")) {
            val head = LinearLayout(this).apply { orientation = 0; gravity = Gravity.CENTER_VERTICAL }
            head.addView(txt("SHOWME", 20f, Color.WHITE, true), LinearLayout.LayoutParams(0, dp(36), 1f))
            head.addView(txt("● LIVE DATA", 10f, Color.rgb(40, 190, 130), true))
            root.addView(head)
            statusView = txt(marketStatus, 10f, muted)
            root.addView(statusView)
            gap(root, 6)
        } else statusView = null
        body = LinearLayout(this).apply { orientation = 1 }
        root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        when (tab) {
            "Quotes" -> quotes()
            "Chart" -> chart()
            "Alerts" -> alerts()
            else -> settings()
        }
        if (!(fullScreen && tab == "Chart")) {
            val nav = LinearLayout(this).apply { orientation = 0; gravity = Gravity.CENTER }
            listOf("Quotes", "Chart", "Alerts", "Settings").forEach { name ->
                val b = TextView(this).apply {
                    text = name; gravity = Gravity.CENTER; textSize = 11f
                    setTextColor(if (tab == name) Color.WHITE else muted)
                    setPadding(0, dp(14), 0, dp(14))
                    setOnClickListener { screen(name) }
                }
                nav.addView(b, LinearLayout.LayoutParams(0, dp(50), 1f))
            }
            root.addView(nav)
        }
        setContentView(root)
    }

    private fun quoteText(item: MarketSymbol): String {
        val pct = item.change24h * 100.0
        return item.lastPrice + "   " + String.format(java.util.Locale.US, "%+.2f%%", pct)
    }

    private fun quotes() {
        body.addView(txt("Market Watch", 23f, Color.WHITE, true))
        body.addView(txt("Binance USDT Perpetual • Live watchlist", 11f, muted))
        gap(body, 10)
        val search = EditText(this).apply {
            hint = "Search futures symbol"; setHintTextColor(muted); setTextColor(Color.WHITE)
            isSingleLine = true; setPadding(dp(12), 0, dp(12), 0); setBackgroundColor(panel)
        }
        body.addView(search, LinearLayout.LayoutParams(-1, dp(46)))
        gap(body, 8)
        val rows = LinearLayout(this).apply { orientation = 1 }
        val filters = LinearLayout(this).apply { orientation = 0 }
        listOf("All", "Top 100", "Top Gainer", "Top Loser").forEach { label ->
            val button = TextView(this).apply {
                text = label; textSize = 10f; gravity = Gravity.CENTER
                setTextColor(if (activeTab == label) Color.WHITE else muted)
                setPadding(dp(7), dp(10), dp(7), dp(10))
                setBackgroundColor(if (activeTab == label) Color.rgb(42, 65, 88) else panel)
                setOnClickListener { activeTab = label; renderQuoteRows(rows, search.text.toString()) }
            }
            filters.addView(button, LinearLayout.LayoutParams(0, dp(38), 1f))
        }
        body.addView(filters)
        gap(body, 8)
        val scroll = ScrollView(this)
        scroll.addView(rows)
        body.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        renderQuoteRows(rows, "")
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                renderQuoteRows(rows, s?.toString() ?: "")
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        body.addView(txt("Symbols: " + marketSymbols.size + "   •   " + marketStatus, 10f, muted))
    }

    private fun renderQuoteRows(rows: LinearLayout, query: String) {
        rows.removeAllViews()
        quoteRows.clear()
        val filtered = when (activeTab) {
            "Top 100" -> marketSymbols.sortedByDescending { it.turnover24h }.take(100)
            "Top Gainer" -> marketSymbols.sortedByDescending { it.change24h }
            "Top Loser" -> marketSymbols.sortedBy { it.change24h }
            else -> marketSymbols.sortedBy { it.symbol }
        }.filter { it.symbol.contains(query.trim(), true) }
        filtered.forEach { item ->
            val row = LinearLayout(this).apply {
                orientation = 0; gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(8), dp(10), dp(8)); setBackgroundColor(panel)
                setOnClickListener { symbol = item.symbol; screen("Chart") }
            }
            row.addView(txt(item.symbol, 13f, Color.WHITE, true), LinearLayout.LayoutParams(0, dp(38), 1f))
            val price = txt(quoteText(item), 11f, if (item.change24h >= 0) Color.rgb(40, 190, 130) else Color.rgb(239, 83, 80))
            row.addView(price)
            quoteRows[item.symbol] = price
            rows.addView(row)
            gap(rows, 2)
        }
        if (filtered.isEmpty()) rows.addView(txt(if (marketSymbols.isEmpty()) "Loading symbols…" else "No matching symbols", 12f, muted))
    }

    private fun chart() {
        val current = marketSymbols.firstOrNull { it.symbol == symbol }
        val chartHead = LinearLayout(this).apply { orientation = 0; gravity = Gravity.CENTER_VERTICAL }
        val symbolTitle = txt(symbol + "  ▾", 17f, Color.WHITE, true).apply {
            setPadding(dp(5), dp(5), dp(12), dp(5))
            setOnClickListener { showSymbolPicker() }
        }
        chartHead.addView(symbolTitle, LinearLayout.LayoutParams(0, dp(38), 1f))
        chartHead.addView(Button(this).apply {
            text = if (fullScreen) "Exit" else "⛶"
            isAllCaps = false
            minWidth = dp(44)
            setOnClickListener { toggleFullScreen() }
        })
        body.addView(chartHead)
        body.addView(txt("USDT Perpetual • Bybit live feed", 10f, muted))
        gap(body, 4)
        // Compact Last / Mark / Index segmented selector.
        val prices = LinearLayout(this).apply {
            orientation = 0
            setPadding(dp(4), dp(4), dp(4), dp(4))
            setBackgroundColor(panel)
        }
        fun priceOption(label: String, type: String, initial: String): Pair<LinearLayout, TextView> {
            val value = txt(initial, 13f, Color.WHITE, true)
            val option = LinearLayout(this).apply {
                orientation = 1
                gravity = Gravity.CENTER
                setPadding(dp(5), dp(7), dp(5), dp(7))
                setOnClickListener {
                    priceType = type
                    marketData.selectChart(symbol, interval, priceType)
                    screen("Chart")
                }
                addView(txt(label, 9f, muted, true))
                addView(value)
            }
            return Pair(option, value)
        }
        val last = priceOption("LAST", "Trade", current?.lastPrice ?: "--")
        val mark = priceOption("MARK", "Mark", current?.markPrice ?: "--")
        val index = priceOption("INDEX", "Index", current?.indexPrice ?: "--")
        lastPriceView = last.second; markPriceView = mark.second; indexPriceView = index.second
        listOf(last, mark, index).forEach { (option, _) ->
            prices.addView(option, LinearLayout.LayoutParams(0, -2, 1f))
        }
        fun highlightPriceType() {
            listOf("Trade", "Mark", "Index").forEachIndexed { i, type ->
                prices.getChildAt(i).setBackgroundColor(
                    if (priceType == type) Color.rgb(42, 65, 88) else Color.TRANSPARENT)
            }
        }
        highlightPriceType()
        body.addView(prices)
        countdownView = txt("00:00", 10f, Color.rgb(255, 209, 102), true).apply {
            gravity = Gravity.END
            setPadding(0, dp(3), dp(6), dp(3))
        }
        body.addView(countdownView!!)
        gap(body, 3)
        val tf = LinearLayout(this).apply { orientation = 0 }
        val tfScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(tf)
        }
        val timeframes = listOf("1m" to "1", "5m" to "5", "15m" to "15", "1h" to "60", "4h" to "240", "1D" to "D")
        timeframes.forEach { (label, value) ->
            val button = TextView(this).apply {
                text = label; textSize = 11f; gravity = Gravity.CENTER
                setTextColor(if (interval == value) Color.WHITE else muted)
                setPadding(dp(13), dp(9), dp(13), dp(9))
                setBackgroundColor(if (interval == value) Color.rgb(42, 65, 88) else panel)
                setOnClickListener { interval = value; screen("Chart") }
            }
            tf.addView(button, LinearLayout.LayoutParams(-2, dp(38)))
        }
        tf.addView(TextView(this).apply {
            text = "＋"; textSize = 19f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            setPadding(dp(13), 0, dp(13), 0); setBackgroundColor(panel)
            setOnClickListener { customTimeframeDialog() }
        }, LinearLayout.LayoutParams(-2, dp(38)))
        body.addView(tfScroll, LinearLayout.LayoutParams(-1, dp(38))); gap(body, 2)
        val web = WebView(this).apply {
            setBackgroundColor(Color.rgb(9, 14, 22))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    chartReady = true
                    renderHistory(pendingCandles)
                }
            }
            loadUrl("file:///android_asset/chart.html")
        }
        chartWeb = web
        val chartFrame = FrameLayout(this)
        chartFrame.addView(web, FrameLayout.LayoutParams(-1, -1))
        val alertPlus = TextView(this).apply {
            text = "+"
            textSize = 27f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(25, 177, 139))
            elevation = dp(8).toFloat()
            setPadding(0, 0, 0, dp(2))
        }
        val plusSize = dp(48)
        val plusParams = FrameLayout.LayoutParams(plusSize, plusSize, Gravity.END or Gravity.TOP).apply {
            rightMargin = dp(14); topMargin = dp(18)
        }
        chartFrame.addView(alertPlus, plusParams)
        var downX = 0f; var downY = 0f; var moved = false
        alertPlus.setOnTouchListener { view, event ->
            val lp = view.layoutParams as FrameLayout.LayoutParams
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX; downY = event.rawY; moved = false; true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()
                    if (kotlin.math.abs(dx) > dp(4) || kotlin.math.abs(dy) > dp(4)) moved = true
                    if (moved) {
                        lp.gravity = Gravity.TOP or Gravity.START
                        lp.leftMargin = (view.x + dx).toInt().coerceIn(0, (chartFrame.width - view.width).toInt().coerceAtLeast(0))
                        lp.topMargin = (view.y + dy).toInt().coerceIn(0, (chartFrame.height - view.height).toInt().coerceAtLeast(0))
                        view.layoutParams = lp
                        downX = event.rawX; downY = event.rawY
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        val yInChart = (view.y + view.height / 2f).coerceIn(0f, web.height.toFloat())
                        web.evaluateJavascript("window.priceAtY($yInChart)") { result ->
                            val suggested = result?.trim('"')?.toDoubleOrNull()?.let {
                                java.math.BigDecimal(it).stripTrailingZeros().toPlainString()
                            } ?: current?.let { if (priceType == "Mark") it.markPrice else if (priceType == "Index") it.indexPrice else it.lastPrice } ?: ""
                            showChartAlertDialog(suggested)
                        }
                    }
                    true
                }
                else -> true
            }
        }
        body.addView(chartFrame, LinearLayout.LayoutParams(-1, 0, 1f))
        current?.let { updatePriceLabels(it) }
        marketData.selectChart(symbol, interval, priceType)
    }


    private fun candleCountdown(): String {
        val now = System.currentTimeMillis() / 1000L
        val seconds = when (interval) {
            "D" -> 86400L
            "W" -> 604800L
            "M" -> {
                val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
                cal.timeInMillis = now * 1000L
                cal.set(java.util.Calendar.DAY_OF_MONTH, 1); cal.add(java.util.Calendar.MONTH, 1)
                cal.set(java.util.Calendar.HOUR_OF_DAY, 0); cal.set(java.util.Calendar.MINUTE, 0)
                cal.set(java.util.Calendar.SECOND, 0)
                (cal.timeInMillis / 1000L) - now
            }
            else -> (interval.toLongOrNull() ?: 1L) * 60L
        }
        val remain = if (interval == "M") seconds else seconds - (now % seconds)
        return String.format(java.util.Locale.US, "%02d:%02d", remain / 60L, remain % 60L)
    }

    private fun customTimeframeDialog() {
        val input = EditText(this).apply {
            hint = "Minutes (e.g. 2, 10, 45)"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(interval.takeIf { it.toIntOrNull() != null } ?: "")
        }
        android.app.AlertDialog.Builder(this).setTitle("Custom timeframe").setView(input)
            .setPositiveButton("Apply") { _, _ ->
                val minutes = input.text.toString().toIntOrNull()
                if (minutes == null || minutes !in 1..10080) {
                    Toast.makeText(this, "Enter 1–10080 minutes", Toast.LENGTH_SHORT).show()
                } else { interval = minutes.toString(); screen("Chart") }
            }.setNegativeButton("Cancel", null).show()
    }


    private fun showSymbolPicker() {
        val symbols = marketSymbols.toList()
        var filter = "All"
        val input = EditText(this).apply {
            hint = "Search Binance futures symbol"
            setTextColor(Color.WHITE)
            setHintTextColor(muted)
            isSingleLine = true
        }
        val list = ListView(this)
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf<String>())
        list.adapter = adapter
        fun refresh() {
            val ranked = when (filter) {
                "Gainers" -> symbols.sortedByDescending { it.change24h }.take(30)
                "Losers" -> symbols.sortedBy { it.change24h }.take(30)
                else -> symbols.sortedBy { it.symbol }
            }
            val query = input.text.toString().trim()
            adapter.clear()
            adapter.addAll(ranked.filter { it.symbol.contains(query, true) }.map {
                it.symbol + "     " + String.format(java.util.Locale.US, "%+.2f%%", it.change24h * 100.0)
            })
            adapter.notifyDataSetChanged()
        }
        val filters = LinearLayout(this).apply { orientation = 0 }
        listOf("All", "Gainers", "Losers").forEach { name ->
            filters.addView(TextView(this).apply {
                text = if (name == "Gainers") "Top Gainers" else if (name == "Losers") "Top Losers" else name
                textSize = 11f; gravity = Gravity.CENTER
                setTextColor(Color.WHITE); setPadding(dp(8), dp(10), dp(8), dp(10))
                setBackgroundColor(if (filter == name) Color.rgb(42, 65, 88) else panel)
                setOnClickListener { filter = name; refresh()
                    for (i in 0 until filters.childCount) filters.getChildAt(i).setBackgroundColor(
                        if ((listOf("All", "Gainers", "Losers")[i]) == filter) Color.rgb(42,65,88) else panel)
                }
            }, LinearLayout.LayoutParams(0, dp(40), 1f))
        }
        val container = LinearLayout(this).apply {
            orientation = 1
            setPadding(dp(12), dp(6), dp(12), dp(6))
            addView(input)
            addView(filters)
            addView(list, LinearLayout.LayoutParams(-1, dp(360)))
        }
        val dialog = android.app.AlertDialog.Builder(this).setTitle("Select Binance market").setView(container).create()
        input.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { refresh() }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        list.setOnItemClickListener { _, _, position, _ ->
            symbol = adapter.getItem(position)?.substringBefore(" ") ?: symbol
            dialog.dismiss()
            screen("Chart")
        }
        refresh()
        dialog.show()
    }

    @Suppress("DEPRECATION")
    private fun toggleFullScreen() {
        fullScreen = !fullScreen
        // Keep the device's current orientation: fullscreen must work in portrait AND landscape.
        requestedOrientation = if (fullScreen) ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        window.decorView.systemUiVisibility = if (fullScreen) {
            (android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
        } else android.view.View.SYSTEM_UI_FLAG_VISIBLE
        screen("Chart")
    }

    private fun updatePriceLabels(item: MarketSymbol) {
        lastPriceView?.text = item.lastPrice
        markPriceView?.text = item.markPrice
        indexPriceView?.text = item.indexPrice
    }

    private fun renderHistory(candles: List<MarketCandle>) {
        if (!chartReady || candles.isEmpty()) return
        val array = JSONArray()
        candles.forEach { c ->
            array.put(JSONObject().put("time", c.time).put("open", c.open).put("high", c.high)
                .put("low", c.low).put("close", c.close))
        }
        chartWeb?.evaluateJavascript("window.setCandles($array)", null)
    }

    private fun renderLiveCandle(candle: MarketCandle) {
        if (!chartReady) return
        val item = JSONObject().put("time", candle.time).put("open", candle.open)
            .put("high", candle.high).put("low", candle.low).put("close", candle.close)
        chartWeb?.evaluateJavascript("window.updateCandle($item)", null)
    }

    private fun showChartAlertDialog(suggestedPrice: String) {
        val input = EditText(this).apply {
            hint = "Target price"
            setText(suggestedPrice)
            setTextColor(Color.WHITE)
            setHintTextColor(muted)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            isSingleLine = true
        }
        val condition = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("Crosses above", "Crosses below"))
        }
        val content = LinearLayout(this).apply {
            orientation = 1
            setPadding(dp(18), dp(8), dp(18), dp(4))
            addView(txt("Symbol: $symbol", 12f, muted))
            addView(txt("Alert price", 11f, muted, true))
            addView(input, LinearLayout.LayoutParams(-1, dp(48)))
            addView(condition, LinearLayout.LayoutParams(-1, dp(48)))
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("Create chart alert")
            .setView(content)
            .setPositiveButton("Save") { _, _ ->
                val target = input.text.toString().trim()
                if (target.toDoubleOrNull() == null) {
                    Toast.makeText(this, "Enter a valid price", Toast.LENGTH_SHORT).show()
                } else {
                    prefs.edit().putString("alert_" + System.currentTimeMillis(),
                        symbol + "|" + target + "|" + condition.selectedItem.toString()).apply()
                    Toast.makeText(this, "Alert saved on device", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun alerts() {
        body.addView(txt("Price Alerts", 23f, Color.WHITE, true))
        body.addView(txt("Create and manage alert rules", 12f, muted)); gap(body, 10)
        val box = card()
        box.addView(txt("SYMBOL", 10f, muted, true))
        val sym = EditText(this).apply { setText(symbol); setTextColor(Color.WHITE); isSingleLine = true }
        box.addView(sym)
        box.addView(txt("TRIGGER PRICE", 10f, muted, true))
        val price = EditText(this).apply {
            hint = "Enter target price"; setHintTextColor(muted); setTextColor(Color.WHITE)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        box.addView(price)
        val cond = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("Crosses above", "Crosses below"))
        }
        box.addView(cond)
        box.addView(Button(this).apply {
            text = "Save alert"; isAllCaps = false
            setOnClickListener {
                val s = sym.text.toString().trim().uppercase()
                val p = price.text.toString().trim()
                if (s.isEmpty() || p.toDoubleOrNull() == null) {
                    Toast.makeText(this@MainActivity, "Enter symbol and valid price", Toast.LENGTH_SHORT).show()
                } else {
                    prefs.edit().putString("alert_" + System.currentTimeMillis(), s + "|" + p + "|" + cond.selectedItem.toString()).apply()
                    screen("Alerts")
                    Toast.makeText(this@MainActivity, "Alert rule saved on device", Toast.LENGTH_SHORT).show()
                }
            }
        }, LinearLayout.LayoutParams(-1, dp(44)))
        body.addView(box); gap(body, 12)
        body.addView(txt("SAVED ALERTS", 11f, muted, true)); gap(body, 6)
        val list = LinearLayout(this).apply { orientation = 1 }
        val scroll = ScrollView(this); scroll.addView(list)
        prefs.all.filterKeys { it.startsWith("alert_") }.forEach { (k, v) ->
            val parts = (v as? String ?: "").split("|")
            if (parts.size == 3) {
                val row = LinearLayout(this).apply {
                    orientation = 0; gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(10), dp(8), dp(8), dp(8)); setBackgroundColor(panel)
                }
                row.addView(txt(parts[0] + "  " + parts[1] + "\n" + parts[2], 13f, Color.WHITE), LinearLayout.LayoutParams(0, -2, 1f))
                row.addView(txt("✕", 18f, Color.RED).apply {
                    setPadding(dp(12), dp(8), dp(12), dp(8))
                    setOnClickListener { prefs.edit().remove(k).apply(); screen("Alerts") }
                })
                list.addView(row); gap(list, 4)
            }
        }
        if (list.childCount == 0) list.addView(txt("No saved alerts.", 12f, muted))
        body.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f)); gap(body, 5)
        body.addView(txt("Alert rules are saved locally. Live alert triggering is not enabled yet.", 10f, muted))
    }

    private fun settings() {
        body.addView(txt("Settings", 23f, Color.WHITE, true)); gap(body, 12)
        listOf(
            "Watchlist     Binance USDT Perpetual",
            "Candles     Bybit Public Market Data",
            "Connection     $marketStatus",
            "Chart engine     TradingView Lightweight Charts",
            "API credentials     Not required",
            "Trading features     Disabled"
        ).forEach {
            body.addView(card().apply { addView(txt(it, 13f)) }); gap(body, 6)
        }
    }
}
