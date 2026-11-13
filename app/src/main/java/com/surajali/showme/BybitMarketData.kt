package com.surajali.showme

import android.os.Handler
import android.os.Looper
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class MarketSymbol(
    val symbol: String,
    val lastPrice: String = "--",
    val markPrice: String = "--",
    val indexPrice: String = "--",
    val change24h: Double = 0.0,
    val turnover24h: Double = 0.0
)

data class MarketCandle(
    val time: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double
)

class BybitMarketData(
    private val onMarkets: (List<MarketSymbol>) -> Unit,
    private val onTicker: (MarketSymbol) -> Unit,
    private val onHistory: (String, List<MarketCandle>) -> Unit,
    private val onLiveCandle: (String, MarketCandle) -> Unit,
    private val onStatus: (String) -> Unit
) {
    private val main = Handler(Looper.getMainLooper())
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
    private val marketMap = LinkedHashMap<String, MarketSymbol>()
    private var socket: WebSocket? = null
    private var binanceSocket: WebSocket? = null
    private val binanceSymbols = HashSet<String>()
    @Volatile private var binanceSymbolsLoaded = false
    private var activeSymbol = ""
    private var activeInterval = "1"
    private var activePriceType = "Trade"
    private var activeTradeTopic = ""
    private var activeTickerTopic = ""
    @Volatile private var currentTradeCandle: MarketCandle? = null
    private val heartbeat = object : Runnable {
        override fun run() {
            if (!closed && socketConnected) {
                socket?.send(JSONObject().put("op", "ping").toString())
                main.postDelayed(this, 20_000L)
            }
        }
    }
    private var reconnectAttempt = 0
    @Volatile private var socketConnected = false
    @Volatile private var closed = false

    fun start() {
        closed = false
        // Binance owns the watchlist; Bybit remains the chart/mark/index data source.
        loadBinanceWatchlist()
        loadTickers()
    }


    private fun loadBinanceWatchlist() {
        val request = Request.Builder().url("https://fapi.binance.com/fapi/v1/exchangeInfo").get().build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                status("Binance watchlist failed: " + (e.message ?: "network error"))
            }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    try {
                        val root = JSONObject(it.body?.string() ?: "{}")
                        val list = root.getJSONArray("symbols")
                        binanceSymbols.clear()
                        for (i in 0 until list.length()) {
                            val item = list.getJSONObject(i)
                            if (item.optString("status") == "TRADING" &&
                                item.optString("quoteAsset") == "USDT" &&
                                item.optString("contractType") == "PERPETUAL") {
                                binanceSymbols.add(item.optString("symbol"))
                            }
                        }
                        binanceSymbolsLoaded = true
                        loadBinanceTickers()
                    } catch (e: Exception) {
                        status("Could not parse Binance symbols: " + (e.message ?: ""))
                    }
                }
            }
        })
    }

    private fun loadBinanceTickers() {
        val request = Request.Builder().url("https://fapi.binance.com/fapi/v1/ticker/24hr").get().build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                status("Binance symbols ready; ticker snapshot failed")
                publishBinanceMarkets()
                connectBinanceSocket()
            }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    try {
                        val list = JSONArray(it.body?.string() ?: "[]")
                        for (i in 0 until list.length()) mergeBinanceTicker(list.getJSONObject(i))
                        publishBinanceMarkets()
                        connectBinanceSocket()
                    } catch (e: Exception) {
                        status("Binance ticker parse error: " + (e.message ?: ""))
                    }
                }
            }
        })
    }

    private fun mergeBinanceTicker(json: JSONObject) {
        val symbol = json.optString("symbol")
        if (symbol !in binanceSymbols) return
        val old = marketMap[symbol] ?: MarketSymbol(symbol)
        val updated = MarketSymbol(
            symbol = symbol,
            lastPrice = json.optString("lastPrice", old.lastPrice),
            markPrice = old.markPrice,
            indexPrice = old.indexPrice,
            change24h = (json.optString("priceChangePercent").toDoubleOrNull() ?: 0.0) / 100.0,
            turnover24h = json.optString("quoteVolume").toDoubleOrNull() ?: 0.0
        )
        synchronized(marketMap) { marketMap[symbol] = updated }
        main.post { onTicker(updated) }
    }

    private fun publishBinanceMarkets() {
        val copy = synchronized(marketMap) {
            marketMap.values.filter { it.symbol in binanceSymbols }.sortedByDescending { it.turnover24h }
        }
        main.post {
            onMarkets(copy)
            onStatus("Binance Futures watchlist • Bybit chart feed")
        }
    }

    private fun connectBinanceSocket() {
        if (closed) return
        val request = Request.Builder().url("wss://fstream.binance.com/ws/!ticker@arr").build()
        binanceSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                status("LIVE • Binance watchlist / Bybit candles")
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val array = JSONArray(text)
                    for (i in 0 until array.length()) mergeBinanceTicker(array.getJSONObject(i))
                } catch (_: Exception) { }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!closed) main.postDelayed({ connectBinanceSocket() }, 3000L)
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!closed) main.postDelayed({ connectBinanceSocket() }, 3000L)
            }
        })
    }

    private fun loadInstruments(cursor: String?, collected: MutableList<String>) {
        val suffix = if (cursor.isNullOrBlank()) "" else "&cursor=" + URLEncoder.encode(cursor, "UTF-8")
        val url = "https://api.bybit.com/v5/market/instruments-info?category=linear&limit=1000$suffix"
        client.newCall(Request.Builder().url(url).get().build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                status("Instrument list failed: " + (e.message ?: "network error"))
            }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val body = it.body?.string() ?: return
                    try {
                        val root = JSONObject(body)
                        if (root.optInt("retCode", -1) != 0) {
                            status("Bybit API: " + root.optString("retMsg"))
                            return
                        }
                        val result = root.getJSONObject("result")
                        val list = result.getJSONArray("list")
                        for (i in 0 until list.length()) {
                            val item = list.getJSONObject(i)
                            if (item.optString("status") == "Trading" &&
                                item.optString("quoteCoin") == "USDT" &&
                                item.optString("contractType").contains("Perpetual")) {
                                collected.add(item.optString("symbol"))
                            }
                        }
                        val next = result.optString("nextPageCursor")
                        if (next.isNotBlank()) {
                            loadInstruments(next, collected)
                        } else {
                            collected.distinct().forEach { symbol ->
                                marketMap[symbol] = MarketSymbol(symbol)
                            }
                            loadTickers()
                        }
                    } catch (e: Exception) {
                        status("Could not parse Bybit symbols: " + (e.message ?: ""))
                    }
                }
            }
        })
    }

    private fun loadTickers() {
        val request = Request.Builder()
            .url("https://api.bybit.com/v5/market/tickers?category=linear")
            .get().build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                publishMarkets()
                status("Symbols loaded; ticker REST failed, connecting live feed")
                connectSocket()
            }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    try {
                        val root = JSONObject(it.body?.string() ?: "{}")
                        val list = root.getJSONObject("result").getJSONArray("list")
                        for (i in 0 until list.length()) mergeTicker(list.getJSONObject(i))
                        publishMarkets()
                        connectSocket()
                    } catch (e: Exception) {
                        publishMarkets()
                        status("Symbols loaded; ticker parse issue")
                        connectSocket()
                    }
                }
            }
        })
    }

    private fun mergeTicker(json: JSONObject) {
        val symbol = json.optString("symbol")
        if (symbol.isBlank() || !symbol.endsWith("USDT")) return
        val old = marketMap[symbol] ?: MarketSymbol(symbol)
        marketMap[symbol] = MarketSymbol(
            symbol = symbol,
            lastPrice = old.lastPrice,
            markPrice = json.optString("markPrice", old.markPrice).ifBlank { old.markPrice },
            indexPrice = json.optString("indexPrice", old.indexPrice).ifBlank { old.indexPrice },
            change24h = old.change24h,
            turnover24h = old.turnover24h
        )
        val updated = marketMap[symbol]!!
        processReferencePrice(updated)
        // Bybit contributes mark/index values; Binance remains the watchlist last-price source.
        if (binanceSymbolsLoaded && symbol in binanceSymbols) main.post { onTicker(updated) }
    }

    private fun publishMarkets() {
        val copy = synchronized(marketMap) { marketMap.values.toList() }
        if (!binanceSymbolsLoaded) main.post { onStatus("Connecting Binance watchlist…") }
        else main.post { onMarkets(copy.filter { it.symbol in binanceSymbols }) }
    }

    private fun connectSocket() {
        if (closed) return
        status("Connecting live WebSocket…")
        val request = Request.Builder()
            .url("wss://stream.bybit.com/v5/public/linear")
            .build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                reconnectAttempt = 0
                socketConnected = true
                main.removeCallbacks(heartbeat)
                main.postDelayed(heartbeat, 20_000L)
                status("LIVE • Bybit WebSocket connected")
                subscribeTickers(webSocket)
                if (activeSymbol.isNotBlank()) {
                    subscribeTrades(webSocket, activeSymbol)
                    subscribeSymbolTicker(webSocket, activeSymbol)
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    val topic = json.optString("topic")
                    if (topic.startsWith("tickers.")) {
                        val data = json.opt("data")
                        if (data is JSONObject) mergeTicker(data)
                        else if (data is JSONArray) for (i in 0 until data.length()) mergeTicker(data.getJSONObject(i))
                    } else if (topic.startsWith("publicTrade.")) {
                        val trades = json.optJSONArray("data") ?: return
                        for (i in 0 until trades.length()) processTrade(trades.getJSONObject(i))
                    } else if (topic.startsWith("kline.")) {
                        val data = json.optJSONArray("data") ?: return
                        for (i in 0 until data.length()) {
                            val item = data.getJSONObject(i)
                            val candle = MarketCandle(
                                time = item.optLong("start") / 1000L,
                                open = item.optString("open").toDoubleOrNull() ?: continue,
                                high = item.optString("high").toDoubleOrNull() ?: continue,
                                low = item.optString("low").toDoubleOrNull() ?: continue,
                                close = item.optString("close").toDoubleOrNull() ?: continue
                            )
                            main.post { onLiveCandle(topic.substringAfterLast("."), candle) }
                        }
                    }
                } catch (_: Exception) { }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                socketConnected = false
                activeTradeTopic = ""
                activeTickerTopic = ""
                main.removeCallbacks(heartbeat)
                status("Live feed disconnected; reconnecting…")
                scheduleReconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                socketConnected = false
                activeTradeTopic = ""
                main.removeCallbacks(heartbeat)
                if (!closed) scheduleReconnect()
            }
        })
    }

    private fun subscribeTickers(ws: WebSocket) {
        val symbols = marketMap.values.sortedByDescending { it.turnover24h }.take(100).map { it.symbol }.toMutableList()
        if (activeSymbol.isNotBlank() && activeSymbol !in symbols) symbols.add(activeSymbol)
        symbols.chunked(10).forEachIndexed { index, batch ->
            main.postDelayed({
                if (socketConnected && !closed) {
                    val args = JSONArray()
                    batch.forEach { args.put("tickers.$it") }
                    ws.send(JSONObject().put("op", "subscribe").put("args", args).toString())
                }
            }, index * 120L)
        }
    }

    fun selectChart(symbol: String, interval: String, priceType: String = "Trade") {
        activeSymbol = symbol
        activeInterval = interval
        activePriceType = priceType
        currentTradeCandle = null
        loadHistory(symbol, interval)
        socket?.let {
            subscribeTrades(it, symbol)
            subscribeSymbolTicker(it, symbol)
        }
    }

    private fun subscribeSymbolTicker(ws: WebSocket, symbol: String) {
        val topic = "tickers.$symbol"
        if (activeTickerTopic == topic) return
        if (activeTickerTopic.isNotBlank()) {
            ws.send(JSONObject().put("op", "unsubscribe").put("args", JSONArray().put(activeTickerTopic)).toString())
        }
        activeTickerTopic = topic
        ws.send(JSONObject().put("op", "subscribe").put("args", JSONArray().put(topic)).toString())
    }

    private fun subscribeTrades(ws: WebSocket, symbol: String) {
        val topic = "publicTrade.$symbol"
        if (activeTradeTopic == topic) return
        if (activeTradeTopic.isNotBlank()) {
            ws.send(JSONObject().put("op", "unsubscribe").put("args", JSONArray().put(activeTradeTopic)).toString())
        }
        activeTradeTopic = topic
        ws.send(JSONObject().put("op", "subscribe").put("args", JSONArray().put(topic)).toString())
    }

    @Synchronized
    private fun processReferencePrice(item: MarketSymbol) {
        if (item.symbol != activeSymbol || activePriceType == "Trade") return
        val price = (if (activePriceType == "Mark") item.markPrice else item.indexPrice).toDoubleOrNull() ?: return
        val now = System.currentTimeMillis() / 1000L
        val bucket = candleBucket(now, activeInterval)
        val old = currentTradeCandle
        val updated = when {
            old == null || bucket > old.time -> MarketCandle(bucket, price, price, price, price)
            bucket == old.time -> MarketCandle(bucket, old.open, maxOf(old.high, price), minOf(old.low, price), price)
            else -> return
        }
        currentTradeCandle = updated
        val selected = activeSymbol
        main.post { onLiveCandle(selected, updated) }
    }
    @Synchronized
    private fun processTrade(trade: JSONObject) {
        if (trade.optString("s") != activeSymbol) return
        val tradeTime = trade.optLong("T") / 1000L
        val price = trade.optString("p").toDoubleOrNull() ?: return
        if (tradeTime <= 0L) return
        val bucket = candleBucket(tradeTime, activeInterval)
        val old = currentTradeCandle
        val updated = when {
            old == null || bucket > old.time -> MarketCandle(bucket, price, price, price, price)
            bucket == old.time -> MarketCandle(bucket, old.open, maxOf(old.high, price), minOf(old.low, price), price)
            else -> return
        }
        currentTradeCandle = updated
        val selected = activeSymbol
        main.post { onLiveCandle(selected, updated) }
    }

    private fun candleBucket(epochSeconds: Long, interval: String): Long {
        val minutes = interval.toLongOrNull()
        if (minutes != null) {
            val seconds = minutes * 60L
            return epochSeconds / seconds * seconds
        }
        if (interval == "D") return epochSeconds / 86400L * 86400L
        if (interval == "W") {
            val mondayEpoch = 345600L
            return mondayEpoch + Math.floorDiv(epochSeconds - mondayEpoch, 604800L) * 604800L
        }
        val calendar = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        calendar.timeInMillis = epochSeconds * 1000L
        calendar.set(java.util.Calendar.DAY_OF_MONTH, 1)
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        return calendar.timeInMillis / 1000L
    }

    private fun loadHistory(symbol: String, interval: String) {
        val customMinutes = interval.toIntOrNull()
        val apiInterval = if (customMinutes != null && customMinutes !in listOf(1, 3, 5, 15, 30, 60, 120, 240, 360, 720)) "1" else interval
        val endpoint = when (activePriceType) { "Mark" -> "mark-price-kline"; "Index" -> "index-price-kline"; else -> "kline" }
        val url = "https://api.bybit.com/v5/market/$endpoint?category=linear&symbol=$symbol&interval=$apiInterval&limit=1000"
        client.newCall(Request.Builder().url(url).get().build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                status("Historical candles failed: " + (e.message ?: "network error"))
            }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    try {
                        val root = JSONObject(it.body?.string() ?: "{}")
                        if (root.optInt("retCode", -1) != 0) {
                            status("Kline API: " + root.optString("retMsg"))
                            return
                        }
                        val list = root.getJSONObject("result").getJSONArray("list")
                        val candles = mutableListOf<MarketCandle>()
                        for (i in 0 until list.length()) {
                            val row = list.getJSONArray(i)
                            candles.add(MarketCandle(
                                time = row.getString(0).toLong() / 1000L,
                                open = row.getString(1).toDouble(),
                                high = row.getString(2).toDouble(),
                                low = row.getString(3).toDouble(),
                                close = row.getString(4).toDouble()
                            ))
                        }
                        candles.sortBy { c -> c.time }
                        if (customMinutes != null && customMinutes > 1 && apiInterval == "1") {
                            val seconds = customMinutes * 60L
                            val grouped = candles.groupBy { it.time / seconds * seconds }.toSortedMap()
                            val aggregated = grouped.map { (bucket, values) ->
                                MarketCandle(bucket, values.first().open, values.maxOf { it.high }, values.minOf { it.low }, values.last().close)
                            }
                            candles.clear(); candles.addAll(aggregated)
                        }
                        synchronized(this@BybitMarketData) {
                            val latest = candles.lastOrNull()
                            val live = currentTradeCandle
                            currentTradeCandle = when {
                                latest == null -> live
                                live == null || live.time > latest.time -> live ?: latest
                                live.time == latest.time -> MarketCandle(
                                    latest.time, latest.open, maxOf(latest.high, live.high),
                                    minOf(latest.low, live.low), live.close
                                )
                                else -> latest
                            }
                            val merged = currentTradeCandle
                            if (merged != null && candles.isNotEmpty() && candles.last().time == merged.time) {
                                candles[candles.lastIndex] = merged
                            }
                        }
                        main.post { onHistory(symbol, candles) }
                    } catch (e: Exception) {
                        status("Candle parse error: " + (e.message ?: ""))
                    }
                }
            }
        })
    }

    private fun scheduleReconnect() {
        if (closed) return
        reconnectAttempt = (reconnectAttempt + 1).coerceAtMost(6)
        main.postDelayed({ if (!closed && !socketConnected) connectSocket() }, (1000L shl reconnectAttempt).coerceAtMost(30000L))
    }

    private fun status(message: String) = main.post { onStatus(message) }

    fun stop() {
        closed = true
        main.removeCallbacks(heartbeat)
        socket?.close(1000, "App stopped")
        binanceSocket?.close(1000, "App stopped")
        socket = null
        binanceSocket = null
        client.dispatcher.cancelAll()
    }
}
