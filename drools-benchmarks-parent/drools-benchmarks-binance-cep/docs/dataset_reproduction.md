# Reproducing the Binance Dataset

Due to Binance's terms of service regarding the redistribution of their proprietary market data feeds, the raw dataset (`run_20260311_1340_10sym`) used in this benchmark cannot be included directly in the repository.

However, an equivalent dataset can be generated at any time using the standalone collection utility.

## The Collector Tool

The data collector is a standalone Go application available at:
**[https://github.com/ROM-FYP/binance-cep-collector](https://github.com/ROM-FYP/binance-cep-collector)**

## Instructions

1. **Clone the collector:**
   ```bash
   git clone https://github.com/ROM-FYP/binance-cep-collector
   cd binance-cep-collector
   ```

2. **Run the collection script:**
   To capture an equivalent 30-minute window for the same 10 perpetual futures symbols:
   ```bash
   go run main.go \
       --symbols BTCUSDT,ETHUSDT,SOLUSDT,BNBUSDT,XRPUSDT,DOGEUSDT,ADAUSDT,AVAXUSDT,LINKUSDT,ARBUSDT \
       --streams trade,depth,markPrice,forceOrder \
       --duration 30m \
       --output ./dataset/
   ```

3. **Output Format:**
   The collector produces gzipped NDJSON files (`.jsonl.gz`), rotating every minute.
   These files adhere exactly to the schema expected by `BinanceEventProvider.java`:
   ```json
   { "symbol": "BTCUSDT", "eventType": "TRADE", "p1": 65000.50, "p2": 0.0, "qty": 1.5, "tsMs": 1710164400000 }
   ```

4. **Integration:**
   Place the resulting `segments/` directory into:
   `src/main/resources/data/YOUR_DATASET_NAME/`
   and update the `BinanceEventProvider` to point to your new dataset directory.
