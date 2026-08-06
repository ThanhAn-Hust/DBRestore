package com.dbbackup.notification;

import com.dbbackup.domain.port.NotificationService;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SlackNotificationService implements NotificationService {

    private static final String CHANNEL_TYPE = "slack";
    private final String webhookUrl;
    private final RestClient restClient;

    public SlackNotificationService(String webhookUrl) {
        this(webhookUrl, RestClient.create());
    }

    public SlackNotificationService(String webhookUrl, RestClient restClient) {
        this.webhookUrl = webhookUrl;
        this.restClient = restClient;
    }

    @Override
    public String getChannelType() {
        return CHANNEL_TYPE;
    }

    @Override
    public void sendNotification(NotificationPayload payload) {
        String statusText = payload.success() ? "SUCCESS" : "FAILED";
        String headerText = "[" + statusText + "] " + payload.title();

        StringBuilder sectionSb = new StringBuilder();
        sectionSb.append("*Message:* ").append(payload.message()).append("\n");

        if (payload.details() != null && !payload.details().isEmpty()) {
            sectionSb.append("\n*Details:*\n");
            payload.details().forEach((key, val) -> {
                sectionSb.append("• *").append(key).append(":* ").append(val).append("\n");
            });
        }

        List<Map<String, Object>> blocks = new ArrayList<>();

        Map<String, Object> headerBlock = new HashMap<>();
        headerBlock.put("type", "header");
        headerBlock.put("text", Map.of(
                "type", "plain_text",
                "text", headerText
        ));
        blocks.add(headerBlock);

        Map<String, Object> sectionBlock = new HashMap<>();
        sectionBlock.put("type", "section");
        sectionBlock.put("text", Map.of(
                "type", "mrkdwn",
                "text", sectionSb.toString()
        ));
        blocks.add(sectionBlock);

        Map<String, Object> body = new HashMap<>();
        body.put("text", headerText + ": " + payload.message());
        body.put("blocks", blocks);

        restClient.post()
                .uri(webhookUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }
}
