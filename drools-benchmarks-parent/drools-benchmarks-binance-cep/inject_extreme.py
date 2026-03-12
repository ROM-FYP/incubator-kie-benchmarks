import json
import gzip
import os
import shutil
from pathlib import Path

# Paths
SOURCE_DIR = Path("src/main/resources/data/run_20260216_0632_10sym")
DEST_DIR = Path("src/main/resources/data/run_20260216_0632_10sym_extreme")

# Ensure destination exists
if DEST_DIR.exists():
    shutil.rmtree(DEST_DIR)
DEST_DIR.mkdir(parents=True)

# Copy metadata
for file_name in ["metadata.json", "reconnects.jsonl", "stats.jsonl"]:
    src_file = SOURCE_DIR / file_name
    if src_file.exists():
        shutil.copy2(src_file, DEST_DIR / file_name)

# Create segments dir
DEST_SEGMENTS = DEST_DIR / "segments"
DEST_SEGMENTS.mkdir()

# Anomalies to inject (flags to ensure we only inject each once or a few times to spread them out)
anomalies_injected = {
    "A01_MissingRequiredFields": False,
    "A02_InvalidNumerics": False,
    "A03_TimestampSkewBound": False,
    "C19_CrossedBook": False,
    "C20_NonPositiveSpread": False,
    "D28_TradeSizeOutlier": False,
    "E36_SpreadBlowout": False,
    "G53_MarkIndexDivergence_New": False,
}

print("Injecting anomalies into BTCUSDT stream...")

# Process each segment
for segment_file in sorted((SOURCE_DIR / "segments").glob("*.jsonl.gz")):
    print(f"Processing {segment_file.name}...")
    
    dest_file = DEST_SEGMENTS / segment_file.name
    
    with gzip.open(segment_file, 'rt', encoding='utf-8') as f_in, \
         gzip.open(dest_file, 'wt', encoding='utf-8') as f_out:
         
        for line in f_in:
            if not line.strip():
                continue
                
            try:
                event = json.loads(line)
                
                # We target BTCUSDT for anomalies so it's easy to track
                if event.get("symbol") == "BTCUSDT":
                    stream = event.get("source_stream")
                    
                    # 1. A01_MissingRequiredFields: empty symbol
                    if stream == "trade" and not anomalies_injected["A01_MissingRequiredFields"]:
                        event["symbol"] = ""
                        anomalies_injected["A01_MissingRequiredFields"] = True
                        
                    # 2. A02_InvalidNumerics: NaN price
                    elif stream == "trade" and not anomalies_injected["A02_InvalidNumerics"]:
                        event["raw"]["p"] = "NaN"
                        anomalies_injected["A02_InvalidNumerics"] = True
                        
                    # 3. A03_TimestampSkewBound: future timestamp
                    elif stream == "trade" and not anomalies_injected["A03_TimestampSkewBound"]:
                        event["exchange_ts"] += 3600000  # +1 hour
                        anomalies_injected["A03_TimestampSkewBound"] = True
                        
                    # 4. C19_CrossedBook: Bid >= Ask
                    elif stream == "book" and not anomalies_injected["C19_CrossedBook"] and "b" in event["raw"] and "a" in event["raw"] and event["raw"]["b"] and event["raw"]["a"]:
                        # Set best bid to current ask + 100
                        current_ask = float(event["raw"]["a"][0][0])
                        event["raw"]["b"][0][0] = str(current_ask + 100.0)
                        # We must ensure this gets written as an event in Java
                        # The Java parser simply reads the first bid/ask. 
                        # Crossed book condition: bid >= ask.
                        anomalies_injected["C19_CrossedBook"] = True
                        
                    # 5. C20_NonPositiveSpread: Bid == Ask (this also triggers C19 actually, but let's test)
                    elif stream == "book" and not anomalies_injected["C20_NonPositiveSpread"] and "b" in event["raw"] and "a" in event["raw"] and event["raw"]["b"] and event["raw"]["a"]:
                        current_ask = event["raw"]["a"][0][0]
                        event["raw"]["b"][0][0] = current_ask
                        anomalies_injected["C20_NonPositiveSpread"] = True
                        
                    # 6. D28_TradeSizeOutlier: quantity > 1e9
                    elif stream == "trade" and not anomalies_injected["D28_TradeSizeOutlier"]:
                        event["raw"]["q"] = "2000000000.0" # 2 billion
                        anomalies_injected["D28_TradeSizeOutlier"] = True
                        
                    # 7. E36_SpreadBlowout: Ask = Bid * 1.5
                    elif stream == "book" and not anomalies_injected["E36_SpreadBlowout"] and "b" in event["raw"] and "a" in event["raw"] and event["raw"]["b"] and event["raw"]["a"]:
                        current_bid = float(event["raw"]["b"][0][0])
                        event["raw"]["a"][0][0] = str(current_bid * 1.5)
                        anomalies_injected["E36_SpreadBlowout"] = True
                        
                    # 8. G53_MarkIndexDivergence_New: Mark != Index significantly
                    elif stream == "mark" and not anomalies_injected["G53_MarkIndexDivergence_New"] and "i" in event["raw"]:
                        index_price = float(event["raw"]["i"])
                        # 10% divergence to guarantee it hits Critical tier
                        event["raw"]["p"] = str(index_price * 1.5) 
                        anomalies_injected["G53_MarkIndexDivergence_New"] = True

                f_out.write(json.dumps(event) + '\n')
                
            except Exception as e:
                # If there's a parse error, just write the original line
                f_out.write(line)

print("Finished injecting anomalies.")
for anomaly, injected in anomalies_injected.items():
    print(f"  {anomaly}: {'Injected' if injected else 'NOT INJECTED'}")

