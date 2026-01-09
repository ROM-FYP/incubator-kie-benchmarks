package org.kie.benchmark.cep.reproducible.wikimedia.preprocess;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.kie.benchmark.cep.reproducible.wikimedia.model.WikiEvent;

import java.io.IOException;

public final class WikiEventParser {
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WikiEvent parseEvent(String jsonData) throws IOException {
        JsonNode root = objectMapper.readTree(jsonData);

        String type = root.path("type").asText();
        int namespace = root.path("namespace").asInt(-1);
        if (!"edit".equals(type) || namespace != 0) {
            return null;
        }

        String title = root.path("title").asText();
        String user = root.path("user").asText();
        String comment = root.path("comment").asText("");
        boolean bot = root.path("bot").asBoolean(false);
        long timestamp = root.path("timestamp").asLong(System.currentTimeMillis());

        int lengthNew = root.path("length").path("new").asInt(0);
        int lengthOld = root.path("length").path("old").asInt(0);
        int sizeDelta = lengthNew - lengthOld;

        return new WikiEvent(title, user, comment, bot, timestamp, sizeDelta);
    }
}
