package com.dbbackup.storage;

import com.dbbackup.domain.port.StorageProvider;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class AwsS3StorageProvider implements StorageProvider {

    private S3Client s3Client;

    public AwsS3StorageProvider() {
        this(null);
    }

    public AwsS3StorageProvider(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    private synchronized S3Client getS3Client() {
        if (s3Client == null) {
            s3Client = S3Client.builder().build();
        }
        return s3Client;
    }

    @Override
    public boolean supports(String uriScheme) {
        return uriScheme != null && "s3".equalsIgnoreCase(uriScheme);
    }

    @Override
    public void store(InputStream input, String destinationPath, long size) throws IOException {
        S3Target target = parseUri(destinationPath);
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(target.bucket())
                .key(target.key())
                .build();

        RequestBody body;
        if (size > 0) {
            body = RequestBody.fromInputStream(input, size);
        } else {
            byte[] bytes = input.readAllBytes();
            body = RequestBody.fromBytes(bytes);
        }

        getS3Client().putObject(request, body);
    }

    @Override
    public InputStream retrieve(String sourcePath) throws IOException {
        S3Target target = parseUri(sourcePath);
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(target.bucket())
                .key(target.key())
                .build();
        return getS3Client().getObject(request);
    }

    @Override
    public List<String> list(String prefix) throws IOException {
        S3Target target = parseUri(prefix);
        ListObjectsV2Request request = ListObjectsV2Request.builder()
                .bucket(target.bucket())
                .prefix(target.key())
                .build();

        ListObjectsV2Response response = getS3Client().listObjectsV2(request);
        return response.contents().stream()
                .map(S3Object::key)
                .map(k -> "s3://" + target.bucket() + "/" + k)
                .collect(Collectors.toList());
    }

    @Override
    public void delete(String path) throws IOException {
        S3Target target = parseUri(path);
        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(target.bucket())
                .key(target.key())
                .build();
        getS3Client().deleteObject(request);
    }

    @Override
    public boolean exists(String path) throws IOException {
        S3Target target = parseUri(path);
        try {
            HeadObjectRequest request = HeadObjectRequest.builder()
                    .bucket(target.bucket())
                    .key(target.key())
                    .build();
            getS3Client().headObject(request);
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            throw e;
        }
    }

    private S3Target parseUri(String uriStr) {
        if (uriStr == null || uriStr.isBlank()) {
            throw new IllegalArgumentException("S3 URI cannot be empty");
        }
        String clean = uriStr;
        if (clean.toLowerCase().startsWith("s3://")) {
            clean = clean.substring(5);
        } else if (clean.toLowerCase().startsWith("s3:")) {
            clean = clean.substring(3);
        }
        int slashIdx = clean.indexOf('/');
        if (slashIdx == -1) {
            return new S3Target(clean, "");
        }
        String bucket = clean.substring(0, slashIdx);
        String key = clean.substring(slashIdx + 1);
        return new S3Target(bucket, key);
    }

    private record S3Target(String bucket, String key) {}
}
