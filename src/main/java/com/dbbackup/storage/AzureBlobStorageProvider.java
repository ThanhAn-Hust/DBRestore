package com.dbbackup.storage;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.models.ListBlobsOptions;
import com.dbbackup.domain.port.StorageProvider;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class AzureBlobStorageProvider implements StorageProvider {

    private BlobServiceClient blobServiceClient;

    public AzureBlobStorageProvider() {
        this(null);
    }

    public AzureBlobStorageProvider(BlobServiceClient blobServiceClient) {
        this.blobServiceClient = blobServiceClient;
    }

    private synchronized BlobServiceClient getBlobServiceClient() {
        if (blobServiceClient == null) {
            blobServiceClient = new BlobServiceClientBuilder().buildClient();
        }
        return blobServiceClient;
    }

    @Override
    public boolean supports(String uriScheme) {
        if (uriScheme == null) return false;
        String scheme = uriScheme.toLowerCase();
        return "azure".equals(scheme) || "az".equals(scheme);
    }

    @Override
    public void store(InputStream input, String destinationPath, long size) throws IOException {
        AzureTarget target = parseUri(destinationPath);
        BlobContainerClient containerClient = getBlobServiceClient().getBlobContainerClient(target.container());
        if (!containerClient.exists()) {
            containerClient.create();
        }
        BlobClient blobClient = containerClient.getBlobClient(target.blob());
        long uploadSize = size > 0 ? size : input.available();
        blobClient.getBlockBlobClient().upload(input, uploadSize, true);
    }

    @Override
    public InputStream retrieve(String sourcePath) throws IOException {
        AzureTarget target = parseUri(sourcePath);
        BlobClient blobClient = getBlobServiceClient().getBlobContainerClient(target.container()).getBlobClient(target.blob());
        return blobClient.openInputStream();
    }

    @Override
    public List<String> list(String prefix) throws IOException {
        AzureTarget target = parseUri(prefix);
        BlobContainerClient containerClient = getBlobServiceClient().getBlobContainerClient(target.container());
        ListBlobsOptions options = new ListBlobsOptions().setPrefix(target.blob());
        return containerClient.listBlobs(options, null).stream()
                .map(BlobItem::getName)
                .map(name -> "azure://" + target.container() + "/" + name)
                .collect(Collectors.toList());
    }

    @Override
    public void delete(String path) throws IOException {
        AzureTarget target = parseUri(path);
        getBlobServiceClient().getBlobContainerClient(target.container()).getBlobClient(target.blob()).deleteIfExists();
    }

    @Override
    public boolean exists(String path) throws IOException {
        AzureTarget target = parseUri(path);
        return getBlobServiceClient().getBlobContainerClient(target.container()).getBlobClient(target.blob()).exists();
    }

    private AzureTarget parseUri(String uriStr) {
        if (uriStr == null || uriStr.isBlank()) {
            throw new IllegalArgumentException("Azure Blob URI cannot be empty");
        }
        String clean = uriStr;
        if (clean.toLowerCase().startsWith("azure://")) {
            clean = clean.substring(8);
        } else if (clean.toLowerCase().startsWith("az://")) {
            clean = clean.substring(5);
        } else if (clean.toLowerCase().startsWith("azure:")) {
            clean = clean.substring(6);
        } else if (clean.toLowerCase().startsWith("az:")) {
            clean = clean.substring(3);
        }
        int slashIdx = clean.indexOf('/');
        if (slashIdx == -1) {
            return new AzureTarget(clean, "");
        }
        String container = clean.substring(0, slashIdx);
        String blob = clean.substring(slashIdx + 1);
        return new AzureTarget(container, blob);
    }

    private record AzureTarget(String container, String blob) {}
}
