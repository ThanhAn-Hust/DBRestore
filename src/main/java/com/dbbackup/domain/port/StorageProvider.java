package com.dbbackup.domain.port;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public interface StorageProvider {
    boolean supports(String uriScheme);
    void store(InputStream input, String destinationPath, long size) throws IOException;
    InputStream retrieve(String sourcePath) throws IOException;
    List<String> list(String prefix) throws IOException;
    void delete(String path) throws IOException;
    boolean exists(String path) throws IOException;
}
