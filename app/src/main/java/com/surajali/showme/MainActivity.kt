package com.surajali.showme

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Handler
import android.os.Looper
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
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
        val prices = card()
        val line = LinearLayout(this).apply { orientation = 0 }
        fun priceColumn(label: String, type: String, initial: String): Pair<LinearLayout, TextView> {
            val value = txt(initial, 14f, Color.WHITE, true)
            val column = LinearLayout(this).apply {
                orientation = 1
                setPadding(dp(5), dp(5), dp(3), dp(5))
                setBackgroundColor(if (priceType == type) Color.rgb(42, 65, 88) else panel)
                addView(txt(label, 9f, muted, true))
                addView(value)
                setOnClickListener {
                    priceType = type
                    marketData.selectChart(symbol, interval, priceType)
                    screen("Chart")
                }
            }
            return Pair(column, value)
        }
        val last = priceColumn("LAST PRICE", "Trade", current?.lastPrice ?: "--")
        val mark = priceColumn("MARK PRICE", "Mark", current?.markPrice ?: "--")
        val index = priceColumn("INDEX PRICE", "Index", current?.indexPrice ?: "--")
        lastPriceView = last.second; markPriceView = mark.second; indexPriceView = index.second
        listOf(last.first, mark.first, index.first).forEach { line.addView(it, LinearLayout.LayoutParams(0, -2, 1f)) }
        prices.addView(line); body.addView(prices)
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
        val timeframes = listOf("1m" to "1", "3m" to "3", "5m" to "5", "15m" to "15", "30m" to "30", "1h" to "60", "2h" to "120", "4h" to "240", "6h" to "360", "12h" to "720", "1D" to "D", "1W" to "W", "1M" to "M")
        timeframes.forEach { (label, value) ->
            val button = TextView(this).apply {
                text = label; textSize = 10f; gravity = Gravity.CENTER
                setTextColor(if (interval == value) Color.WHITE else muted)
                setPadding(dp(9), dp(9), dp(9), dp(9))
                setBackgroundColor(if (interval == value) Color.rgb(42, 65, 88) else panel)
                setOnClickListener {
                    interval = value
                    screen("Chart")
                }
            }
            tf.addView(button, LinearLayout.LayoutParams(-2, dp(36)))
        }
        tf.addView(TextView(this).apply {
            text = "＋"; textSize = 18f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            setPadding(dp(10), 0, dp(10), 0)
            setOnClickListener { customTimeframeDialog() }
        }, LinearLayout.LayoutParams(-2, dp(36)))
        body.addView(tfScroll, LinearLayout.LayoutParams(-1, dp(34))); gap(body, 2)
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
        body.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
        gap(body, 3)
        val terminalActions = LinearLayout(this).apply { orientation = 0; gravity = Gravity.CENTER }
        terminalActions.addView(Button(this).apply {
            text = "＋ Alert"; isAllCaps = false
            setOnClickListener { screen("Alerts") }
        }, LinearLayout.LayoutParams(0, dp(40), 1f))
        terminalActions.addView(Button(this).apply {
            text = if (fullScreen) "Exit full screen" else "Landscape"
            isAllCaps = false
            setOnClickListener { toggleFullScreen() }
        }, LinearLayout.LayoutParams(0, dp(40), 1f))
        body.addView(terminalActions)
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
        val symbols = marketSymbols.sortedBy { it.symbol }
        val input = EditText(this).apply {
            hint = "Search Binance futures symbol"
            setTextColor(Color.WHITE)
            setHintTextColor(muted)
            isSingleLine = true
        }
        val list = ListView(this)
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, symbols.map { it.symbol }.toMutableList())
        list.adapter = adapter
        val container = LinearLayout(this).apply {
            orientation = 1
            setPadding(dp(12), dp(6), dp(12), dp(6))
            addView(input)
            addView(list, LinearLayout.LayoutParams(-1, dp(360)))
        }
        val dialog = android.app.AlertDialog.Builder(this).setTitle("Select Binance market").setView(container).create()
        input.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.clear()
                adapter.addAll(symbols.map { it.symbol }.filter { it.contains(s.toString(), true) })
                adapter.notifyDataSetChanged()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        list.setOnItemClickListener { _, _, position, _ ->
            symbol = adapter.getItem(position) ?: symbol
            dialog.dismiss()
            screen("Chart")
        }
        dialog.show()
    }

    @Suppress("DEPRECATION")
    private fun toggleFullScreen() {
        fullScreen = !fullScreen
        if (fullScreen) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            window.decorView.systemUiVisibility = (android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
        } else {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_VISIBLE
        }
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
