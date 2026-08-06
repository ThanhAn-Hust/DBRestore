package com.dbbackup.notification;

import com.dbbackup.domain.port.NotificationService;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DiscordNotificationService implements NotificationService {

    private static final String CHANNEL_TYPE = "discord";
    private final String webhookUrl;
    private final RestClient restClient;

    public DiscordNotificationService(String webhookUrl) {
        this(webhookUrl, RestClient.create());
    }

    public DiscordNotificationService(String webhookUrl, RestClient restClient) {
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
        String embedTitle = "[" + statusText + "] " + payload.title();
        int color = payload.success() ? 0x2ECC71 : 0xE74C3C;

        Map<String, Object> embed = new HashMap<>();
        embed.put("title", embedTitle);
        embed.put("description", payload.message());
        embed.put("color", color);

        if (payload.details() != null && !payload.details().isEmpty()) {
            List<Map<String, Object>> fields = new ArrayList<>();
            payload.details().forEach((key, val) -> {
                Map<String, Object> field = new HashMap<>();
                field.put("name", key);
                field.put("value", String.valueOf(val));
                field.put("inline", true);
                fields.add(field);
            });
            embed.put("fields", fields);
        }

        Map<String, Object> body = Map.of("embeds", List.of(embed));

        restClient.post()
                .uri(webhookUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }
}
