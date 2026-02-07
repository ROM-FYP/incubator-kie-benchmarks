# PowerShell script to analyze benchmark results
param(
    [string]$BaselineFile,
    [string]$MemoFile,
    [int]$Iterations
)

function Get-Stats {
    param([double[]]$values)
    
    $mean = ($values | Measure-Object -Average).Average
    $min = ($values | Measure-Object -Minimum).Minimum
    $max = ($values | Measure-Object -Maximum).Maximum
    
    # Standard deviation
    $variance = 0
    foreach ($val in $values) {
        $variance += [Math]::Pow($val - $mean, 2)
    }
    $stddev = [Math]::Sqrt($variance / $values.Count)
    
    return @{
        Mean = $mean
        Min = $min
        Max = $max
        StdDev = $stddev
    }
}

Write-Output ""
Write-Output "============================================"
Write-Output "   MULTI-ITERATION BENCHMARK SUMMARY"
Write-Output "============================================"
Write-Output "Iterations: $Iterations"
Write-Output ""

# Parse baseline results
$baselineData = Import-Csv $BaselineFile
$baselineDurations = $baselineData | ForEach-Object { [double]$_.Duration_ms }
$baselineAlerts = $baselineData | ForEach-Object { [int]$_.Alerts }
$baselineRules = $baselineData | ForEach-Object { [int]$_.Rules }
$baselineEPS = $baselineData | ForEach-Object { [double]$_.Events_per_sec }

# Parse memoization results
$memoData = Import-Csv $MemoFile
$memoDurations = $memoData | ForEach-Object { [double]$_.Duration_ms }
$memoAlerts = $memoData | ForEach-Object { [int]$_.Alerts }
$memoRules = $memoData | ForEach-Object { [int]$_.Rules }
$memoEPS = $memoData | ForEach-Object { [double]$_.Events_per_sec }
$memoHits = $memoData | ForEach-Object { [int]$_.Cache_Hits }
$memoMisses = $memoData | ForEach-Object { [int]$_.Cache_Misses }
$memoHitRate = $memoData | ForEach-Object { [double]$_.Hit_Rate }
$memoSkipped = $memoData | ForEach-Object { [int]$_.Evaluations_Skipped }

# Calculate statistics
$baselineDurationStats = Get-Stats $baselineDurations
$memoDurationStats = Get-Stats $memoDurations

Write-Output "--------------------------------------------"
Write-Output "BASELINE MODE (No Partitioning, No Memo)"
Write-Output "--------------------------------------------"
Write-Output ("Duration (ms):    Mean={0:F2}  Min={1:F2}  Max={2:F2}  StdDev={3:F2}" -f $baselineDurationStats.Mean, $baselineDurationStats.Min, $baselineDurationStats.Max, $baselineDurationStats.StdDev)
Write-Output ("Alerts Generated: Mean={0:F1}  Min={1}  Max={2}" -f (($baselineAlerts | Measure-Object -Average).Average), ($baselineAlerts | Measure-Object -Minimum).Minimum, ($baselineAlerts | Measure-Object -Maximum).Maximum)
Write-Output ("Rules Fired:      Mean={0:F0}  Min={1}  Max={2}" -f (($baselineRules | Measure-Object -Average).Average), ($baselineRules | Measure-Object -Minimum).Minimum, ($baselineRules | Measure-Object -Maximum).Maximum)
Write-Output ("Throughput:       Mean={0:F2} events/sec" -f (($baselineEPS | Measure-Object -Average).Average))
Write-Output ""

Write-Output "--------------------------------------------"
Write-Output "MEMOIZATION MODE (Partitioned + Cache)"
Write-Output "--------------------------------------------"
Write-Output ("Duration (ms):    Mean={0:F2}  Min={1:F2}  Max={2:F2}  StdDev={3:F2}" -f $memoDurationStats.Mean, $memoDurationStats.Min, $memoDurationStats.Max, $memoDurationStats.StdDev)
Write-Output ("Alerts Generated: Mean={0:F1}  Min={1}  Max={2}" -f (($memoAlerts | Measure-Object -Average).Average), ($memoAlerts | Measure-Object -Minimum).Minimum, ($memoAlerts | Measure-Object -Maximum).Maximum)
Write-Output ("Rules Fired:      Mean={0:F0}  Min={1}  Max={2}" -f (($memoRules | Measure-Object -Average).Average), ($memoRules | Measure-Object -Minimum).Minimum, ($memoRules | Measure-Object -Maximum).Maximum)
Write-Output ("Throughput:       Mean={0:F2} events/sec" -f (($memoEPS | Measure-Object -Average).Average))
Write-Output ""
Write-Output "Cache Performance:"
Write-Output ("  Hits:           Mean={0:F1}  Min={1}  Max={2}" -f (($memoHits | Measure-Object -Average).Average), ($memoHits | Measure-Object -Minimum).Minimum, ($memoHits | Measure-Object -Maximum).Maximum)
Write-Output ("  Misses:         Mean={0:F1}  Min={1}  Max={2}" -f (($memoMisses | Measure-Object -Average).Average), ($memoMisses | Measure-Object -Minimum).Minimum, ($memoMisses | Measure-Object -Maximum).Maximum)
Write-Output ("  Hit Rate:       Mean={0:F2}%  Min={1:F2}%  Max={2:F2}%" -f (($memoHitRate | Measure-Object -Average).Average), ($memoHitRate | Measure-Object -Minimum).Minimum, ($memoHitRate | Measure-Object -Maximum).Maximum)
Write-Output ("  Evals Skipped:  Mean={0:F1}  Min={1}  Max={2}" -f (($memoSkipped | Measure-Object -Average).Average), ($memoSkipped | Measure-Object -Minimum).Minimum, ($memoSkipped | Measure-Object -Maximum).Maximum)
Write-Output ""

Write-Output "--------------------------------------------"
Write-Output "PERFORMANCE COMPARISON"
Write-Output "--------------------------------------------"
$avgBaselineDuration = $baselineDurationStats.Mean
$avgMemoDuration = $memoDurationStats.Mean
$speedup = $avgBaselineDuration / $avgMemoDuration
$improvement = (($avgBaselineDuration - $avgMemoDuration) / $avgBaselineDuration) * 100

if ($avgMemoDuration -lt $avgBaselineDuration) {
    Write-Output ("Memoization is {0:F2}x FASTER than baseline" -f $speedup)
    Write-Output ("Performance improvement: {0:F2}% reduction in duration" -f $improvement)
} else {
    $slowdown = $avgMemoDuration / $avgBaselineDuration
    $overhead = (($avgMemoDuration - $avgBaselineDuration) / $avgBaselineDuration) * 100
    Write-Output ("Memoization is {0:F2}x SLOWER than baseline" -f $slowdown)
    Write-Output ("Performance overhead: {0:F2}% increase in duration" -f $overhead)
}

Write-Output ""
Write-Output ("Duration delta:   {0:F2} ms ({1:F2}%)" -f ($avgMemoDuration - $avgBaselineDuration), $improvement)

$avgBaselineRules = ($baselineRules | Measure-Object -Average).Average
$avgMemoRules = ($memoRules | Measure-Object -Average).Average
$ruleReduction = (($avgBaselineRules - $avgMemoRules) / $avgBaselineRules) * 100
Write-Output ("Rules reduction:  {0:F0} fewer rules ({1:F2}%)" -f ($avgBaselineRules - $avgMemoRules), $ruleReduction)

Write-Output ""
Write-Output "============================================"
