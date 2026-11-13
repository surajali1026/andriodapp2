# ShowMe

ShowMe is a new Android trading-terminal app. Its visual direction follows the compact dark terminal workflow of the user-provided MetaTrader 5 reference APK, implemented with original code and assets.

## Included in this first preview
- App name: **SHOWME**
- Market Watch with symbol search and locally addable symbols
- Dedicated Chart terminal screen
- Last Price, Mark Price, and Index Price shown together in the chart terminal
- Timeframe selector layout (M1, M5, M15, M30, H1, H4, D1)
- Price Alerts screen: create, save, list, and delete above/below alert rules locally
- Settings screen
- Four bottom tabs only: Quotes, Chart, Alerts, Settings

## Intentionally excluded
- Buy / Sell interface
- Open Positions
- Trade History
- Order Management
- Depth of Market

## Not connected yet
- Candle rendering and chart data (will be added only after a separate user instruction)
- Live Last / Mark / Index price feed
- Automatic alert triggering and Android push notifications

Alert rules currently persist on the device. They cannot trigger until a live market-price feed is connected. The three price fields are present in the terminal UI but show placeholders until then.

## Build
GitHub Actions builds a debug APK on pushes to `main` and manual workflow dispatch. Download the `ShowMe-debug` artifact from the workflow run.

## Reference and originality
The supplied MetaTrader 5 APK is used only as a visual/workflow reference. This project does not include MetaQuotes proprietary source code, binary libraries, trademarks, or extracted copyrighted assets.
