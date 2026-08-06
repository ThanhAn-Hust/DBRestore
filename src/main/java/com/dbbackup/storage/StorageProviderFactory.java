package com.dbbackup.storage;

import com.dbbackup.domain.port.StorageProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class StorageProviderFactory {

    private final List<StorageProvider> providers;

    public StorageProviderFactory() {
        this(List.of(
            new LocalStorageProvider(),
            new AwsS3StorageProvider(),
            new AzureBlobStorageProvider(),
            new GcsStorageProvider()
        ));
    }

    public StorageProviderFactory(List<StorageProvider> providers) {
        this.providers = providers != null ? new ArrayList<>(providers) : new ArrayList<>();
    }

    public StorageProvider getProvider(String uriOrScheme) {
        return findProvider(uriOrScheme)
                .orElseThrow(() -> new IllegalArgumentException("No StorageProvider available for URI/scheme: " + uriOrScheme));
    }

    public Optional<StorageProvider> findProvider(String uriOrScheme) {
        String scheme = extractScheme(uriOrScheme);
        for (StorageProvider provider : providers) {
            if (provider.supports(scheme)) {
                return Optional.of(provider);
            }
        }
        return Optional.empty();
    }

    public static String extractScheme(String uriOrScheme) {
        if (uriOrScheme == null || uriOrScheme.isBlank()) {
            return "file";
        }
        int colonIdx = uriOrScheme.indexOf(":");
        if (colonIdx != -1) {
            return uriOrScheme.substring(0, colonIdx).trim().toLowerCase();
        }
        return "file";
    }
}
