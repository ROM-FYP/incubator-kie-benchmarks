package bench.opensky.model;

/**
 * Hysteresis / persistence tracking per aircraft pair.
 */
// Event role + expiration declared in airTraffick_rules.drl
public class PairRiskState {

    private String key;         // "a|b"
    private Double lastAtSec;
    private int warnStreak;
    private int alertStreak;
    private int safeStreak;

    public PairRiskState() { }

    public PairRiskState(String key, Double lastAtSec, int warnStreak, int alertStreak, int safeStreak) {
        this.key = key;
        this.lastAtSec = lastAtSec;
        this.warnStreak = warnStreak;
        this.alertStreak = alertStreak;
        this.safeStreak = safeStreak;
    }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public Double getLastAtSec() { return lastAtSec; }
    public void setLastAtSec(Double lastAtSec) { this.lastAtSec = lastAtSec; }
    public int getWarnStreak() { return warnStreak; }
    public void setWarnStreak(int warnStreak) { this.warnStreak = warnStreak; }
    public int getAlertStreak() { return alertStreak; }
    public void setAlertStreak(int alertStreak) { this.alertStreak = alertStreak; }
    public int getSafeStreak() { return safeStreak; }
    public void setSafeStreak(int safeStreak) { this.safeStreak = safeStreak; }

    @Override
    public String toString() {
        return "PairRiskState{key=" + key + ", warn=" + warnStreak + ", alert=" + alertStreak + "}";
    }
}
