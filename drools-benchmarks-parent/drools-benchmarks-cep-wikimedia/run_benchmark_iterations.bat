@echo off
REM Multi-iteration benchmark script for baseline and partitioned modes
REM Usage: run_benchmark_iterations.bat

echo ========================================
echo Multi-Iteration Benchmark Runner
echo Dataset: test_events_merged.json (6215 events)
echo Expected Alerts (Ground Truth): 4278
echo ========================================
echo.

REM Baseline Mode - 5 Iterations
echo Running BASELINE mode (5 iterations)...
echo.

FOR /L %%i IN (1,1,5) DO (
    echo [BASELINE] Iteration %%i/5
    java -jar target/drools-benchmarks-cep-wikimedia.jar replay test_events_merged.json false
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
    java -jar target/drools-benchmarks-cep-wikimedia.jar replay test_events_merged.json true
    echo.
)

echo.
echo ========================================
echo Benchmark Complete!
echo ========================================
