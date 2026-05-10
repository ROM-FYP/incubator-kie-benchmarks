# Data Format Documentation

## Overview
This document describes the format of the recorded Binance WebSocket data used in the benchmark.

---

## Data Source
- **Source**: Binance WebSocket API
- **Streams**: (To be documented)
- **Time Period**: (To be documented)
- **Symbols**: (To be documented)
- **Total Events**: (To be documented)

---

## Event Types

### 1. Trade Events
(To be documented - paste your trade event format here)

### 2. Order Book Events
(To be documented - paste your order book event format here)

### 3. Kline/Candlestick Events
(To be documented - paste your kline event format here)

### 4. Aggregated Trade Events
(To be documented - paste your aggregated trade event format here)

---

## File Format

### Directory Structure
```
src/main/resources/data/
├── binance_stream_BTCUSDT_trade.json
├── binance_stream_ETHUSDT_trade.json
├── binance_stream_BTCUSDT_depth.json
└── ...
```

### File Naming Convention
`binance_stream_{SYMBOL}_{STREAM_TYPE}.{FORMAT}`

Examples:
- `binance_stream_BTCUSDT_trade.json`
- `binance_stream_ETHUSDT_depth.json`
- `binance_stream_BNBUSDT_kline_1m.json`

---

## Data Schema

### JSON Format Example
```json
{
  "stream": "btcusdt@trade",
  "data": {
    "e": "trade",
    "E": 1234567890,
    "s": "BTCUSDT",
    "t": 12345,
    "p": "50000.00",
    "q": "0.001",
    "T": 1234567890,
    "m": true
  }
}
```

### Field Descriptions
(To be documented - describe each field)

---

## Data Statistics

### Volume
- **Total Events**: (To be filled)
- **Events per Symbol**: (To be filled)
- **Time Range**: (To be filled)
- **File Size**: (To be filled)

### Event Distribution
- **Trade Events**: (To be filled)
- **Order Book Events**: (To be filled)
- **Kline Events**: (To be filled)

---

## Data Quality

### Completeness
- Missing events: (To be documented)
- Gaps in timeline: (To be documented)

### Accuracy
- Timestamp precision: (To be documented)
- Out-of-order events: (To be documented)

---

## Loading Instructions

### How to Load Data
```java
BinanceDataLoader loader = new BinanceDataLoader();
SortedSet<BinanceEvent> events = loader.loadFromFile(
    "binance_stream_BTCUSDT_trade.json"
);
```

### Filtering Options
- By symbol
- By time range
- By event type
- By custom criteria

---

## Notes
- Add any specific notes about the data here
- Known issues or quirks
- Special handling requirements
