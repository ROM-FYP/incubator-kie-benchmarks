@echo off
REM Multi-iteration benchmark script for REFINED baseline and partitioned modes
REM Usage: run_refined_benchmark.bat

set JAR=target\drools-benchmarks-cep-wikimedia.jar
set MAIN_CLASS=org.kie.benchmark.cep.wikimedia.ContentModerationRunner

echo ========================================
echo Refined Multi-Iteration Benchmark Runner
echo Dataset: test_events_merged.json (6215 events)
echo Expected Alerts (Ground Truth): 1097
echo ========================================
echo.

REM Baseline Mode - 5 Iterations
echo Running BASELINE mode (5 iterations)...
echo.

FOR /L %%i IN (1,1,5) DO (
    echo [BASELINE] Iteration %%i/5
    java -cp %JAR% %MAIN_CLASS% replay test_events_merged.json false
    echo.
)

echo.
echo ========================================
echo.

REM Partitioned Mode - 5 Iterations
echo Running PARTITIONED mode (5 iterations)...
echo.

FOR /L %%i IN (1,1,5) DO (
    echo [PARTITIONED] Iteration %%i/5
    java -cp %JAR% %MAIN_CLASS% replay test_events_merged.json true
    echo.
)

echo.
echo ========================================
echo Benchmark Complete!
echo ========================================
