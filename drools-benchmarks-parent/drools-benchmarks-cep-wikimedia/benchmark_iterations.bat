@echo off
REM Multi-iteration benchmark comparing baseline vs memoization
REM Usage: benchmark_iterations.bat <iterations> <event_file>

setlocal enabledelayedexpansion

set ITERATIONS=%1
set EVENT_FILE=%2

if "%ITERATIONS%"=="" set ITERATIONS=5
if "%EVENT_FILE%"=="" set EVENT_FILE=test_events_merged.json

echo ========================================
echo Multi-Iteration Benchmark
echo ========================================
echo Iterations: %ITERATIONS%
echo Event File: %EVENT_FILE%
echo.

REM Initialize result files
set BASELINE_RESULTS=baseline_results.txt
set MEMO_RESULTS=memo_results.txt
set SUMMARY_FILE=benchmark_summary.txt

echo Iteration,Events,Alerts,Rules,Duration_ms,Events_per_sec > %BASELINE_RESULTS%
echo Iteration,Events,Alerts,Rules,Duration_ms,Events_per_sec,Cache_Hits,Cache_Misses,Hit_Rate,Evaluations_Skipped > %MEMO_RESULTS%

echo Running %ITERATIONS% iterations...
echo.

REM Run baseline iterations
echo ========================================
echo BASELINE MODE (No Partitioning)
echo ========================================
for /L %%i in (1,1,%ITERATIONS%) do (
    echo [%%i/%ITERATIONS%] Running baseline iteration %%i...
    java -cp target/drools-benchmarks-cep-wikimedia.jar org.kie.benchmark.cep.wikimedia.ContentModerationRunner replay %EVENT_FILE% false false > baseline_iter_%%i.log 2>&1
    
    REM Extract metrics using PowerShell
    powershell -Command "$log = Get-Content baseline_iter_%%i.log -Raw; $events = if ($log -match 'Total Events Ingested: (\d+)') {$matches[1]} else {'0'}; $alerts = if ($log -match 'Total Terminal Alerts: (\d+)') {$matches[1]} else {'0'}; $rules = if ($log -match 'Total Rules Fired: (\d+)') {$matches[1]} else {'0'}; $duration = if ($log -match 'Duration: ([\d.]+) seconds') {[int]([double]$matches[1] * 1000)} else {'0'}; $eps = if ($log -match 'Events/sec: ([\d.]+)') {$matches[1]} else {'0'}; Write-Output \"%%i,$events,$alerts,$rules,$duration,$eps\"" >> %BASELINE_RESULTS%
    
    echo   Events: 
    powershell -Command "(Get-Content baseline_iter_%%i.log | Select-String 'Total Events Ingested').Line"
    echo   Alerts: 
    powershell -Command "(Get-Content baseline_iter_%%i.log | Select-String 'Total Terminal Alerts').Line"
    echo   Duration: 
    powershell -Command "(Get-Content baseline_iter_%%i.log | Select-String 'Duration:').Line"
    echo.
)

REM Run memoization iterations
echo ========================================
echo MEMOIZATION MODE (Partitioned + Cache)
echo ========================================
for /L %%i in (1,1,%ITERATIONS%) do (
    echo [%%i/%ITERATIONS%] Running memoization iteration %%i...
    java -cp target/drools-benchmarks-cep-wikimedia.jar org.kie.benchmark.cep.wikimedia.ContentModerationRunner replay %EVENT_FILE% true true > memo_iter_%%i.log 2>&1
    
    REM Extract metrics including cache stats
    powershell -Command "$log = Get-Content memo_iter_%%i.log -Raw; $events = if ($log -match 'Total Events Ingested: (\d+)') {$matches[1]} else {'0'}; $alerts = if ($log -match 'Total Terminal Alerts: (\d+)') {$matches[1]} else {'0'}; $rules = if ($log -match 'Total Rules Fired: (\d+)') {$matches[1]} else {'0'}; $duration = if ($log -match 'Duration: ([\d.]+) seconds') {[int]([double]$matches[1] * 1000)} else {'0'}; $eps = if ($log -match 'Events/sec: ([\d.]+)') {$matches[1]} else {'0'}; $hits = if ($log -match 'Cache Hits: (\d+)') {$matches[1]} else {'0'}; $misses = if ($log -match 'Cache Misses: (\d+)') {$matches[1]} else {'0'}; $hitrate = if ($log -match 'Hit Rate: ([\d.]+)%%') {$matches[1]} else {'0'}; $skipped = if ($log -match 'Cluster Evaluations Skipped: (\d+)') {$matches[1]} else {'0'}; Write-Output \"%%i,$events,$alerts,$rules,$duration,$eps,$hits,$misses,$hitrate,$skipped\"" >> %MEMO_RESULTS%
    
    echo   Events: 
    powershell -Command "(Get-Content memo_iter_%%i.log | Select-String 'Total Events Ingested').Line"
    echo   Alerts: 
    powershell -Command "(Get-Content memo_iter_%%i.log | Select-String 'Total Terminal Alerts').Line"
    echo   Duration: 
    powershell -Command "(Get-Content memo_iter_%%i.log | Select-String 'Duration:').Line"
    echo   Cache Hits: 
    powershell -Command "(Get-Content memo_iter_%%i.log | Select-String 'Cache Hits:').Line"
    echo.
)

REM Generate summary statistics
echo ========================================
echo GENERATING SUMMARY STATISTICS
echo ========================================

powershell -File analyze_results.ps1 %BASELINE_RESULTS% %MEMO_RESULTS% %ITERATIONS% > %SUMMARY_FILE%

REM Display summary
type %SUMMARY_FILE%

echo.
echo ========================================
echo Benchmark Complete!
echo ========================================
echo Detailed results saved to:
echo   - %BASELINE_RESULTS%
echo   - %MEMO_RESULTS%
echo   - %SUMMARY_FILE%
echo Individual logs: baseline_iter_*.log, memo_iter_*.log
echo.

endlocal
