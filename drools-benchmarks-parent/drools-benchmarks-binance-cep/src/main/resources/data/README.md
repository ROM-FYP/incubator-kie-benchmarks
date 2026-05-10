# Placeholder for Binance WebSocket recorded data

## Instructions
Place your recorded Binance WebSocket data files here.

## Expected Format
- JSON files with Binance WebSocket stream data
- One file per symbol/stream combination

## Example Files
- `binance_stream_BTCUSDT_trade.json`
- `binance_stream_ETHUSDT_trade.json`
- `binance_stream_BTCUSDT_depth.json`
- `binance_stream_BTCUSDT_kline_1m.json`

## File Naming Convention
`binance_stream_{SYMBOL}_{STREAM_TYPE}.json`

Where:
- `{SYMBOL}`: Trading pair (e.g., BTCUSDT, ETHUSDT)
- `{STREAM_TYPE}`: Stream type (e.g., trade, depth, kline_1m, aggTrade)
