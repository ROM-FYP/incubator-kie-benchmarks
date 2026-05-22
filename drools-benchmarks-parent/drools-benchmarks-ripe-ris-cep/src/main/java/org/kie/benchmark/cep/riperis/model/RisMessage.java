package org.kie.benchmark.cep.riperis.model;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import org.kie.api.definition.type.Expires;
import org.kie.api.definition.type.Role;
import org.kie.api.definition.type.Timestamp;

@Role(Role.Type.EVENT)
@Timestamp("timestamp")
@Expires("30m")
public class RisMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    private String envelopeType;
    private String id;
    private double timestamp;
    private String peer;
    private String peerAsn;
    private String host;
    private String bgpType;
    private List<?> path;
    private List<?> community;
    private String origin;
    private Integer med;
    private Object aggregator;
    private List<?> announcements;
    private List<?> withdrawals;

    public RisMessage() {
    }

    public RisMessage(String envelopeType, String id, double timestamp, String peer, String peerAsn, String host,
            String bgpType, List<?> path, List<?> community, String origin, Integer med, Object aggregator,
            List<?> announcements, List<?> withdrawals) {
        this.envelopeType = envelopeType;
        this.id = id;
        this.timestamp = timestamp;
        this.peer = peer;
        this.peerAsn = peerAsn;
        this.host = host;
        this.bgpType = bgpType;
        this.path = path;
        this.community = community;
        this.origin = origin;
        this.med = med;
        this.aggregator = aggregator;
        this.announcements = announcements;
        this.withdrawals = withdrawals;
    }

    public String getEnvelopeType() { return envelopeType; }
    public void setEnvelopeType(String envelopeType) { this.envelopeType = envelopeType; }
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public double getTimestamp() { return timestamp; }
    public void setTimestamp(double timestamp) { this.timestamp = timestamp; }
    public long getTimestampMs() { return (long) (timestamp * 1000); }
    public String getPeer() { return peer; }
    public void setPeer(String peer) { this.peer = peer; }
    public String getPeerAsn() { return peerAsn; }
    public void setPeerAsn(String peerAsn) { this.peerAsn = peerAsn; }
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public String getBgpType() { return bgpType; }
    public void setBgpType(String bgpType) { this.bgpType = bgpType; }
    public List<?> getPath() { return path; }
    public void setPath(List<?> path) { this.path = path; }
    public List<?> getCommunity() { return community; }
    public void setCommunity(List<?> community) { this.community = community; }
    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }
    public Integer getMed() { return med; }
    public void setMed(Integer med) { this.med = med; }
    public Object getAggregator() { return aggregator; }
    public void setAggregator(Object aggregator) { this.aggregator = aggregator; }
    public List<?> getAnnouncements() { return announcements; }
    public void setAnnouncements(List<?> announcements) { this.announcements = announcements; }
    public List<?> getWithdrawals() { return withdrawals; }
    public void setWithdrawals(List<?> withdrawals) { this.withdrawals = withdrawals; }

    public static RisMessage fromRisLiveEnvelope(Map<?, ?> envelope) {
        if (envelope == null) {
            return null;
        }
        Object dataObj = envelope.get("data");
        Map<?, ?> data = dataObj instanceof Map ? (Map<?, ?>) dataObj : envelope;
        RisMessage m = new RisMessage();
        m.setEnvelopeType(dataObj instanceof Map ? stringValue(envelope.get("type"), "ris_message") : "ris_message");
        m.setId(stringValue(data.get("id"), null));
        Object ts = data.get("timestamp");
        if (ts instanceof Number) {
            m.setTimestamp(((Number) ts).doubleValue());
        }
        m.setPeer(stringValue(data.get("peer"), null));
        m.setPeerAsn(stringValue(data.get("peer_asn"), null));
        m.setHost(stringValue(data.get("host"), null));
        m.setBgpType(stringValue(data.get("type"), null));
        m.setPath(asList(data.get("path")));
        m.setCommunity(asList(data.get("community")));
        m.setOrigin(stringValue(data.get("origin"), null));
        Object medObj = data.get("med");
        if (medObj instanceof Number) {
            m.setMed(((Number) medObj).intValue());
        }
        m.setAggregator(data.get("aggregator"));
        m.setAnnouncements(asList(data.get("announcements")));
        m.setWithdrawals(asList(data.get("withdrawals")));
        return m;
    }

    @SuppressWarnings("unchecked")
    private static List<?> asList(Object value) {
        return value instanceof List ? (List<?>) value : null;
    }

    private static String stringValue(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }
}
