@echo off
REM Benchmark for Selective Routing with Join-Heavy Rules
REM Branch: wikimedia-routing-experiment-with-join-heavy-rules-and-shared-states-are-managed
REM Memory caps: 128MB, 256MB
REM Mode: Partitioned (selective routing enabled)
REM Iterations: 10 warmup + 5 measurement

setlocal enabledelayedexpansion

set EVENT_FILE=test_events_merged.json
set JAR=target\drools-benchmarks-cep-wikimedia.jar
set MAIN=org.kie.benchmark.cep.wikimedia.ContentModerationRunner

set MEMORY_CAPS=128 256
set WARMUP_ITERATIONS=10
set MEASURE_ITERATIONS=5
set CPU_AFFINITY=0x0F

echo ========================================
echo Selective Routing Benchmark (Join-Heavy Rules)
echo ========================================
echo Event File: %EVENT_FILE%
echo Memory Caps: %MEMORY_CAPS%
echo Warmup: %WARMUP_ITERATIONS%, Measurement: %MEASURE_ITERATIONS%
echo Mode: Partitioned (Selective Routing Enabled)
echo.

if not exist selective_routing_results mkdir selective_routing_results

for %%M in (%MEMORY_CAPS%) do (
    echo ========================================
    echo Testing Selective Routing with -Xmx%%MM
    echo ========================================
    
    echo [SELECTIVE ROUTING %%MM] Warmup...
    for /L %%W in (1,1,%WARMUP_ITERATIONS%) do (
        echo   Warmup %%W/%WARMUP_ITERATIONS%...
        start /affinity %CPU_AFFINITY% /B /WAIT java -Xms%%MM -Xmx%%MM ^
             -Xlog:gc*:file=selective_routing_results/gc_selective_%%MM_w%%W.log:time ^
             -cp %JAR% %MAIN% replay %EVENT_FILE% true ^
             > selective_routing_results/selective_%%MM_w%%W.log 2>&1
    )
    
    echo [SELECTIVE ROUTING %%MM] Measurement...
    for /L %%I in (1,1,%MEASURE_ITERATIONS%) do (
        echo   Run %%I/%MEASURE_ITERATIONS%
        start /affinity %CPU_AFFINITY% /B /WAIT java -Xms%%MM -Xmx%%MM ^
             -Xlog:gc*:file=selective_routing_results/gc_selective_%%MM_r%%I.log:time ^
             -cp %JAR% %MAIN% replay %EVENT_FILE% true ^
             > selective_routing_results/selective_%%MM_r%%I.log 2>&1
    )
    echo.
)

echo ========================================
echo Extracting Results
echo ========================================

powershell -Command "$caps = @('128', '256'); Write-Output 'Cap,Run,Events,Alerts,Duration_ms,Peak_MB,Max_MB,GC_Count,GC_Time_ms,Util_Pct' | Out-File selective_routing_results/results.csv -Encoding ASCII; foreach ($cap in $caps) { for ($i=1; $i -le 5; $i++) { $log = Get-Content \"selective_routing_results/selective_${cap}M_r${i}.log\" -Raw; $events = if ($log -match 'Total Events Ingested: (\d+)') {$matches[1]} else {'0'}; $alerts = if ($log -match 'Total Terminal Alerts: (\d+)') {$matches[1]} else {'0'}; $duration = if ($log -match 'Duration: ([\d.]+) seconds') {[int]([double]$matches[1] * 1000)} else {'0'}; $peak = if ($log -match 'Peak Heap Used: (\d+) MB') {$matches[1]} else {'0'}; $max = if ($log -match 'Max Heap: (\d+) MB') {$matches[1]} else {'0'}; $gcCnt = if ($log -match 'GC Collections: (\d+)') {$matches[1]} else {'0'}; $gcTime = if ($log -match 'GC Time: (\d+) ms') {$matches[1]} else {'0'}; $util = if ($max -ne '0' -and $peak -ne '0') { [math]::Round(([double]$peak / [double]$max) * 100, 1) } else { 0 }; Write-Output \"$cap,$i,$events,$alerts,$duration,$peak,$max,$gcCnt,$gcTime,$util\" | Out-File selective_routing_results/results.csv -Append -Encoding ASCII } }; Import-Csv selective_routing_results/results.csv | Format-Table -AutoSize"

echo.
echo Complete! Results in selective_routing_results/
endlocal
