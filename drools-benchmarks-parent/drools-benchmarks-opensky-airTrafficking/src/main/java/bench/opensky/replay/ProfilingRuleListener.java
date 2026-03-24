package bench.opensky.replay;

import org.kie.api.event.rule.AfterMatchFiredEvent;
import org.kie.api.event.rule.BeforeMatchFiredEvent;
import org.kie.api.event.rule.DefaultAgendaEventListener;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Listener that measures the CPU cost of the RHS (action) of rules.
 */
public class ProfilingRuleListener extends DefaultAgendaEventListener {

    private final ThreadMXBean threadMXBean;
    private final Map<String, RuleStats> statsMap = new HashMap<>();

    // Temporary storage for the start time of the current rule firing
    private long startTime;

    public ProfilingRuleListener() {
        this.threadMXBean = ManagementFactory.getThreadMXBean();
        if (!threadMXBean.isThreadCpuTimeSupported()) {
            throw new UnsupportedOperationException("Thread CPU time measurement is not supported on this JVM.");
        }
        if (!threadMXBean.isThreadCpuTimeEnabled()) {
            threadMXBean.setThreadCpuTimeEnabled(true);
        }
    }

    @Override
    public void beforeMatchFired(BeforeMatchFiredEvent event) {
        startTime = threadMXBean.getCurrentThreadCpuTime();
    }

    @Override
    public void afterMatchFired(AfterMatchFiredEvent event) {
        long endTime = threadMXBean.getCurrentThreadCpuTime();
        String ruleName = event.getMatch().getRule().getName();
        long duration = endTime - startTime;

        statsMap.computeIfAbsent(ruleName, k -> new RuleStats()).record(duration);
    }

    public void printReport() {
        System.out.println("\n" + "=".repeat(85));
        System.out.println(String.format("%-45s | %-10s | %-12s | %-10s", "Rule Name", "Count", "Total CPU(ms)", "Avg CPU(us)"));
        System.out.println("-".repeat(85));

        statsMap.entrySet().stream()
                .sorted((e1, e2) -> Long.compare(e2.getValue().totalCpuTime, e1.getValue().totalCpuTime))
                .forEach(entry -> {
                    RuleStats s = entry.getValue();
                    System.out.println(String.format("%-45s | %-10d | %-12.3f | %-10.2f",
                            entry.getKey(),
                            s.count,
                            s.totalCpuTime / 1_000_000.0,
                            (s.totalCpuTime / 1_000.0) / s.count));
                });
        System.out.println("=".repeat(85) + "\n");
    }

    private static class RuleStats {
        long count = 0;
        long totalCpuTime = 0;

        void record(long duration) {
            count++;
            totalCpuTime += duration;
        }
    }
}
