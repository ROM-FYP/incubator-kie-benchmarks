package bench.opensky.model;

/**
 * Rule engine tuning parameters. Inserted once into KieSession.
 */
public class Params {

    private Double gridDeg;
    private Long stalePosSec;
    private Long staleAnySec;
    private Double nearDistNm;
    private Double warnDistNm;
    private Double alertDistNm;
    private Double vertMinFt;
    private Double warnTtcSec;
    private Double alertTtcSec;
    private Double persistSec;

    public Params() { }

    public Params(Double gridDeg, Long stalePosSec, Long staleAnySec,
                  Double nearDistNm, Double warnDistNm, Double alertDistNm,
                  Double vertMinFt, Double warnTtcSec, Double alertTtcSec, Double persistSec) {
        this.gridDeg = gridDeg;
        this.stalePosSec = stalePosSec;
        this.staleAnySec = staleAnySec;
        this.nearDistNm = nearDistNm;
        this.warnDistNm = warnDistNm;
        this.alertDistNm = alertDistNm;
        this.vertMinFt = vertMinFt;
        this.warnTtcSec = warnTtcSec;
        this.alertTtcSec = alertTtcSec;
        this.persistSec = persistSec;
    }

    public Double getGridDeg() { return gridDeg; }
    public void setGridDeg(Double gridDeg) { this.gridDeg = gridDeg; }
    public Long getStalePosSec() { return stalePosSec; }
    public void setStalePosSec(Long stalePosSec) { this.stalePosSec = stalePosSec; }
    public Long getStaleAnySec() { return staleAnySec; }
    public void setStaleAnySec(Long staleAnySec) { this.staleAnySec = staleAnySec; }
    public Double getNearDistNm() { return nearDistNm; }
    public void setNearDistNm(Double nearDistNm) { this.nearDistNm = nearDistNm; }
    public Double getWarnDistNm() { return warnDistNm; }
    public void setWarnDistNm(Double warnDistNm) { this.warnDistNm = warnDistNm; }
    public Double getAlertDistNm() { return alertDistNm; }
    public void setAlertDistNm(Double alertDistNm) { this.alertDistNm = alertDistNm; }
    public Double getVertMinFt() { return vertMinFt; }
    public void setVertMinFt(Double vertMinFt) { this.vertMinFt = vertMinFt; }
    public Double getWarnTtcSec() { return warnTtcSec; }
    public void setWarnTtcSec(Double warnTtcSec) { this.warnTtcSec = warnTtcSec; }
    public Double getAlertTtcSec() { return alertTtcSec; }
    public void setAlertTtcSec(Double alertTtcSec) { this.alertTtcSec = alertTtcSec; }
    public Double getPersistSec() { return persistSec; }
    public void setPersistSec(Double persistSec) { this.persistSec = persistSec; }

    @Override
    public String toString() {
        return "Params{gridDeg=" + gridDeg + ", nearNm=" + nearDistNm + ", warnNm=" + warnDistNm + "}";
    }
}
