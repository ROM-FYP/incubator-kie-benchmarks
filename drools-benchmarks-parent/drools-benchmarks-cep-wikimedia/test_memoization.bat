@echo off
REM Test script to verify memoization implementation
REM Runs three configurations and compares results

setlocal

set JAR=target\drools-benchmarks-cep-wikimedia.jar
set MAIN=org.kie.benchmark.cep.wikimedia.ContentModerationRunner
set INPUT=test_events_merged.json

echo ========================================
echo Memoization Verification Tests
echo ========================================
echo.

REM Test 1: Baseline (no partitioning, no memoization)
echo [1/3] Running BASELINE (no partitioning)...
java -cp %JAR% %MAIN% replay %INPUT% false false > memo_test_baseline.log 2>&1
echo Done. Results in memo_test_baseline.log
echo.

REM Test 2: Partitioned WITHOUT memoization
echo [2/3] Running PARTITIONED WITHOUT memoization...
java -cp %JAR% %MAIN% replay %INPUT% true false > memo_test_partitioned_nomemo.log 2>&1
echo Done. Results in memo_test_partitioned_nomemo.log
echo.

REM Test 3: Partitioned WITH memoization
echo [3/3] Running PARTITIONED WITH memoization...
java -cp %JAR% %MAIN% replay %INPUT% true true > memo_test_partitioned_memo.log 2>&1
echo Done. Results in memo_test_partitioned_memo.log
echo.

echo ========================================
echo Extracting Results...
echo ========================================

for /f "tokens=*" %%A in ('findstr /C:"Total Terminal Alerts:" memo_test_baseline.log') do set BASELINE_ALERTS=%%A
for /f "tokens=*" %%B in ('findstr /C:"Total Terminal Alerts:" memo_test_partitioned_nomemo.log') do set PART_NO_MEMO_ALERTS=%%B
for /f "tokens=*" %%C in ('findstr /C:"Total Terminal Alerts:" memo_test_partitioned_memo.log') do set PART_MEMO_ALERTS=%%C

echo Baseline: %BASELINE_ALERTS%
echo Partitioned (No Memo): %PART_NO_MEMO_ALERTS%
echo Partitioned (MEMO): %PART_MEMO_ALERTS%
echo.

for /f "tokens=*" %%D in ('findstr /C:"Cache Hits:" memo_test_partitioned_memo.log') do echo [MEMO] %%D
for /f "tokens=*" %%E in ('findstr /C:"Cache Misses:" memo_test_partitioned_memo.log') do echo [MEMO] %%E
for /f "tokens=*" %%F in ('findstr /C:"Hit Rate:" memo_test_partitioned_memo.log') do echo [MEMO] %%F

echo.
echo ========================================
echo Verification Complete
echo ========================================

endlocal
