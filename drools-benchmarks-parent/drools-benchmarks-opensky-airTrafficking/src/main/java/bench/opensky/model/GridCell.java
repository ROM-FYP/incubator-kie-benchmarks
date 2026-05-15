package bench.opensky.model;

/**
 * Spatial grid cell assignment for join-reduction.
 */
// Event role + expiration declared in airTraffick_rules.drl
public class GridCell {

    private String cellId;
    private String icao24;

    public GridCell() { }

    public GridCell(String cellId, String icao24) {
        this.cellId = cellId;
        this.icao24 = icao24;
    }

    public String getCellId() { return cellId; }
    public void setCellId(String cellId) { this.cellId = cellId; }
    public String getIcao24() { return icao24; }
    public void setIcao24(String icao24) { this.icao24 = icao24; }

    @Override
    public String toString() {
        return "GridCell{cellId=" + cellId + ", icao24=" + icao24 + "}";
    }
}
