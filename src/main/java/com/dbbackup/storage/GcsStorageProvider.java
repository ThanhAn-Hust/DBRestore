package com.dbbackup.storage;

import com.dbbackup.domain.port.StorageProvider;
import com.google.api.gax.paging.Page;
import com.google.cloud.ReadChannel;
import com.google.cloud.WriteChannel;
import com.google.cloud.storage.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.List;

@Component
public class GcsStorageProvider implements StorageProvider {

    private Storage storage;

    public GcsStorageProvider() {
        this(null);
    }

    public GcsStorageProvider(Storage storage) {
        this.storage = storage;
    }

    private synchronized Storage getStorage() {
        if (storage == null) {
            storage = StorageOptions.getDefaultInstance().getService();
        }
        return storage;
    }

    @Override
    public boolean supports(String uriScheme) {
        if (uriScheme == null) return false;
        String scheme = uriScheme.toLowerCase();
        return "gcs".equals(scheme) || "gs".equals(scheme);
    }

    @Override
    public void store(InputStream input, String destinationPath, long size) throws IOException {
        GcsTarget target = parseUri(destinationPath);
        BlobId blobId = BlobId.of(target.bucket(), target.objectName());
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId).build();

        try (WriteChannel writer = getStorage().writer(blobInfo);
             ReadableByteChannel src = Channels.newChannel(input)) {
            ByteBuffer buffer = ByteBuffer.allocate(8192);
            while (src.read(buffer) != -1) {
                buffer.flip();
                writer.write(buffer);
                buffer.clear();
            }
        }
    }

    @Override
    public InputStream retrieve(String sourcePath) throws IOException {
        GcsTarget target = parseUri(sourcePath);
        BlobId blobId = BlobId.of(target.bucket(), target.objectName());
        ReadChannel reader = getStorage().reader(blobId);
        return Channels.newInputStream(reader);
    }

    @Override
    public List<String> list(String prefix) throws IOException {
        GcsTarget target = parseUri(prefix);
        Page<Blob> blobs = getStorage().list(target.bucket(), Storage.BlobListOption.prefix(target.objectName()));
        List<String> results = new ArrayList<>();
        for (Blob blob : blobs.iterateAll()) {
            results.add("gs://" + target.bucket() + "/" + blob.getName());
        }
        return results;
    }

    @Override
    public void delete(String path) throws IOException {
        GcsTarget target = parseUri(path);
        getStorage().delete(BlobId.of(target.bucket(), target.objectName()));
    }

    @Override
    public boolean exists(String path) throws IOException {
        GcsTarget target = parseUri(path);
        Blob blob = getStorage().get(BlobId.of(target.bucket(), target.objectName()));
        return blob != null && blob.exists();
    }

    private GcsTarget parseUri(String uriStr) {
        if (uriStr == null || uriStr.isBlank()) {
            throw new IllegalArgumentException("GCS URI cannot be empty");
        }
        String clean = uriStr;
        if (clean.toLowerCase().startsWith("gcs://")) {
            clean = clean.substring(6);
        } else if (clean.toLowerCase().startsWith("gs://")) {
            clean = clean.substring(5);
        } else if (clean.toLowerCase().startsWith("gcs:")) {
            clean = clean.substring(4);
        } else if (clean.toLowerCase().startsWith("gs:")) {
            clean = clean.substring(3);
        }
        int slashIdx = clean.indexOf('/');
        if (slashIdx == -1) {
            return new GcsTarget(clean, "");
        }
        String bucket = clean.substring(0, slashIdx);
        String objectName = clean.substring(slashIdx + 1);
        return new GcsTarget(bucket, objectName);
    }

    private record GcsTarget(String bucket, String objectName) {}
}
