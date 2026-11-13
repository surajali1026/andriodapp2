# ShowMe

ShowMe is an original Android trading-terminal project inspired by the navigation and workflow patterns visible in the user-provided MetaTrader 5 APK.

## Current scope

This first stage establishes the app identity and terminal structure only. The candlestick renderer/data pipeline is intentionally deferred until the user gives a separate candle-creation command.

## App identity

- Display name: **ShowMe**
- Android application id (planned): `com.surajali.showme`
- Platform: Android
- Initial visual direction: dark, compact, professional trading-terminal UI

## Reference APK observations

The supplied APK is MetaTrader 5. Its visible product structure and embedded identifiers indicate these major areas:

- Quotes / Market Watch and symbol selection
- Chart workspace and chart toolbar/settings
- Trade / open positions and pending orders
- History / deals, orders, and positions
- Settings
- Market depth (Depth of Market)
- Symbol search and symbol details
- Order actions: new, modify, close, close-by, and reverse
- One-click trading controls

These are functional references for an independently implemented app. This repository does not contain MetaTrader's proprietary source code, binary libraries, trademarks, or extracted copyrighted UI assets.

## Stage 1 — shell only

- Create a native Android app shell named ShowMe.
- Add bottom navigation for Quotes, Chart, Trade, History, and Settings.
- Add a Quotes/Market Watch list with symbol rows and bid/ask placeholders.
- Add a Chart screen container and toolbar placeholder. **Do not implement candles yet.**
- Add Trade, History, and Settings screen shells.
- Keep chart data providers and candle rendering isolated so they can be added later without rewriting navigation.

## Deferred until a separate instruction

- Candlestick creation/rendering and market-data source
- Live prices and streaming
- Timeframes and chart interactions
- Drawing tools and indicators
- Trading execution or broker connectivity

## Implementation note

Use original code and assets. Recreate general interaction patterns where practical, but do not decompile, copy, or redistribute MetaQuotes implementation code or proprietary assets.
