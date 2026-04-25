package bench.opensky.replay;

import bench.opensky.model.Alert;
import org.kie.api.event.rule.AfterMatchFiredEvent;
import org.kie.api.event.rule.DefaultAgendaEventListener;
import org.kie.api.event.rule.DefaultRuleRuntimeEventListener;
import org.kie.api.event.rule.ObjectInsertedEvent;
import org.kie.api.time.SessionPseudoClock;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class CsvLoggingRuleListener {

    private BufferedWriter alertsWriter;
    private BufferedWriter rulesWriter;
    private SessionPseudoClock clock;
    private long sequenceNumber = 1;
    // Deduplicate: key = "A|B|type|severity|minute-bucket"
    private final Set<String> seenAlerts = new HashSet<>();

    public CsvLoggingRuleListener(String alertsFile, String rulesFile, SessionPseudoClock clock) {
        this.clock = clock;
        try {
            if (alertsFile != null) {
                this.alertsWriter = new BufferedWriter(new FileWriter(alertsFile));
                this.alertsWriter.write("Timestamp,Alert_Type,Severity,Aircraft_A,Aircraft_B,Details\n");
            }
            if (rulesFile != null) {
                this.rulesWriter = new BufferedWriter(new FileWriter(rulesFile));
                this.rulesWriter.write("Sequence_Number,Rule_Name,Timestamp\n");
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize CSV loggers", e);
        }
    }

    public DefaultAgendaEventListener getAgendaEventListener() {
        return new DefaultAgendaEventListener() {
            @Override
            public void afterMatchFired(AfterMatchFiredEvent event) {
                if (rulesWriter != null) {
                    try {
                        long currentSec = clock.getCurrentTime() / 1000;
                        rulesWriter.write(String.format("%d,%s,%d\n", sequenceNumber++, event.getMatch().getRule().getName(), currentSec));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        };
    }

    public DefaultRuleRuntimeEventListener getRuleRuntimeEventListener() {
        return new DefaultRuleRuntimeEventListener() {
            @Override
            public void objectInserted(ObjectInsertedEvent event) {
                if (alertsWriter != null && event.getObject() instanceof Alert) {
                    Alert a = (Alert) event.getObject();
                    try {
                        long currentSec = clock.getCurrentTime() / 1000;
                        // Deduplicate: same pair + type + severity within the same 60-second window
                        String dedupKey = a.getA() + "|" + a.getB() + "|" + a.getType() + "|" + a.getSeverity() + "|" + (currentSec / 60);
                        if (seenAlerts.add(dedupKey)) {
                            alertsWriter.write(String.format("%d,%s,%s,%s,%s,%s\n",
                                    currentSec, a.getType(), a.getSeverity(), a.getA(), a.getB(), a.getReason() != null ? a.getReason().replace(",", ";") : ""));
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        };
    }

    public void close() {
        try {
            if (alertsWriter != null) {
                alertsWriter.flush();
                alertsWriter.close();
            }
            if (rulesWriter != null) {
                rulesWriter.flush();
                rulesWriter.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
