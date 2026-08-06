package com.dbbackup.notification;

import com.dbbackup.domain.port.NotificationService;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;

public class TelegramNotificationService implements NotificationService {

    private static final String CHANNEL_TYPE = "telegram";
    private final String botToken;
    private final String chatId;
    private final RestClient restClient;

    public TelegramNotificationService(String botToken, String chatId) {
        this(botToken, chatId, RestClient.create());
    }

    public TelegramNotificationService(String botToken, String chatId, RestClient restClient) {
        this.botToken = botToken;
        this.chatId = chatId;
        this.restClient = restClient;
    }

    @Override
    public String getChannelType() {
        return CHANNEL_TYPE;
    }

    @Override
    public void sendNotification(NotificationPayload payload) {
        String url = "https://api.telegram.org/bot" + botToken + "/sendMessage";

        StringBuilder sb = new StringBuilder();
        String statusText = payload.success() ? "SUCCESS" : "FAILED";
        sb.append("<b>[").append(statusText).append("] ").append(payload.title()).append("</b>\n");
        sb.append("Message: ").append(payload.message()).append("\n");

        if (payload.details() != null && !payload.details().isEmpty()) {
            sb.append("\n<b>Details:</b>\n");
            payload.details().forEach((key, val) -> {
                sb.append("• <b>").append(key).append(":</b> ").append(val).append("\n");
            });
        }

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text", sb.toString());
        body.put("parse_mode", "HTML");

        restClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }
}
