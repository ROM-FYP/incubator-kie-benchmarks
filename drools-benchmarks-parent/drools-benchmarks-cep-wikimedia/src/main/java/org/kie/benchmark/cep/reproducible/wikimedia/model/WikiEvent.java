package org.kie.benchmark.cep.reproducible.wikimedia.model;

import org.kie.api.definition.type.Expires;
import org.kie.api.definition.type.Role;
import org.kie.api.definition.type.Timestamp;

import static org.kie.api.definition.type.Role.Type.EVENT;

@Role(EVENT)
@Timestamp("timestamp")
@Expires("5m")
public final class WikiEvent {
    private final String title;
    private final String user;
    private final String comment;
    private final boolean bot;
    private final long timestamp;
    private final int sizeDelta;

    public WikiEvent(String title, String user, String comment, boolean bot, long timestamp, int sizeDelta) {
        this.title = title;
        this.user = user;
        this.comment = comment;
        this.bot = bot;
        this.timestamp = timestamp;
        this.sizeDelta = sizeDelta;
    }

    public String getTitle() { return title; }
    public String getUser() { return user; }
    public String getComment() { return comment; }
    public boolean isBot() { return bot; }
    public long getTimestamp() { return timestamp; }
    public int getSizeDelta() { return sizeDelta; }
}
