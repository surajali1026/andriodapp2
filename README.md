# SHOWME Android

SHOWME is a native Android market-watch and chart terminal.

## Market data
- Bybit V5 public **linear USDT perpetual** market data.
- No API key and no trading permissions are used.
- Instrument catalogue is loaded from the public instruments endpoint, including pagination.
- All, Top 100 (24h turnover), Top Gainer and Top Loser watchlist filters.
- REST historical OHLC candles plus a live WebSocket public-trade stream aggregated into the active candle on every trade.
- Live ticker fields: Last Price, Mark Price, Index Price, 24h change and turnover.
- WebSocket subscriptions are paced, heartbeat-enabled and reconnect automatically.

## Chart
- TradingView Lightweight Charts (open-source chart renderer).
- Supported Bybit intervals: 1m, 3m, 5m, 15m, 30m, 1h, 2h, 4h, 6h, 12h, 1D, 1W, 1M.
- Historical candles are loaded first; the active candle is updated from the Bybit Kline stream.

## App scope
Included: Market Watch, symbol search, Chart, Alerts screen and Settings.

Intentionally excluded:
- Buy / Sell interface
- Open Positions
- Trade History
- Order Management
- Depth of Market

Price alerts are currently stored locally; automatic alert triggering and Android notifications are not enabled yet.

## Data-source note
Bybit prices and candles represent **Bybit's own futures market**, not Binance prices or Binance's contract catalogue. Top 100 is ranked by Bybit 24-hour turnover.

## Build
GitHub Actions builds a debug APK on pushes to `main` and manual workflow dispatch. Download the `ShowMe-debug` artifact from the successful workflow run.
