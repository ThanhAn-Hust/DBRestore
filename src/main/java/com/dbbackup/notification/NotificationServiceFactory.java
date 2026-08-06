package com.dbbackup.notification;

import com.dbbackup.domain.port.NotificationService;
import com.dbbackup.domain.port.NotificationService.NotificationPayload;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class NotificationServiceFactory {

    private final Map<String, NotificationService> serviceMap = new ConcurrentHashMap<>();

    public NotificationServiceFactory() {
    }

    public NotificationServiceFactory(Collection<NotificationService> services) {
        if (services != null) {
            services.forEach(this::registerService);
        }
    }

    public void registerService(NotificationService service) {
        if (service != null && service.getChannelType() != null) {
            serviceMap.put(service.getChannelType().toLowerCase(), service);
        }
    }

    public Optional<NotificationService> getService(String channelType) {
        if (channelType == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(serviceMap.get(channelType.toLowerCase()));
    }

    public void sendNotification(List<String> channelTypes, NotificationPayload payload) {
        if (channelTypes == null || channelTypes.isEmpty()) {
            return;
        }
        for (String channelType : channelTypes) {
            getService(channelType).ifPresent(service -> service.sendNotification(payload));
        }
    }
}
