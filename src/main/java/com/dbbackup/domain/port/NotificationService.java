package com.dbbackup.domain.port;

import java.util.Map;

public interface NotificationService {
    String getChannelType();
    void sendNotification(NotificationPayload payload);

    record NotificationPayload(
        String title,
        String message,
        boolean success,
        Map<String, Object> details
    ) {
        public NotificationPayload(String title, String message, boolean success) {
            this(title, message, success, Map.of());
        }
    }
}
