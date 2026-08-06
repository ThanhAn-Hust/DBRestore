package com.dbbackup.storage;

import com.dbbackup.domain.port.StorageProvider;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class LocalStorageProvider implements StorageProvider {

    @Override
    public boolean supports(String uriScheme) {
        if (uriScheme == null) return false;
        String scheme = uriScheme.toLowerCase();
        return "file".equals(scheme) || "local".equals(scheme);
    }

    @Override
    public void store(InputStream input, String destinationPath, long size) throws IOException {
        Path path = resolvePath(destinationPath);
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        Files.copy(input, path, StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public InputStream retrieve(String sourcePath) throws IOException {
        Path path = resolvePath(sourcePath);
        return Files.newInputStream(path);
    }

    @Override
    public List<String> list(String prefix) throws IOException {
        Path path = resolvePath(prefix);
        if (Files.isDirectory(path)) {
            try (Stream<Path> walk = Files.walk(path)) {
                return walk.filter(Files::isRegularFile)
                           .map(Path::toString)
                           .collect(Collectors.toList());
            }
        }

        Path parent = path.getParent();
        if (parent == null || !Files.exists(parent)) {
            return Collections.emptyList();
        }

        String targetPrefix = path.toString();
        try (Stream<Path> walk = Files.walk(parent)) {
            return walk.filter(Files::isRegularFile)
                       .filter(p -> p.toString().startsWith(targetPrefix))
                       .map(Path::toString)
                       .collect(Collectors.toList());
        }
    }

    @Override
    public void delete(String path) throws IOException {
        Files.deleteIfExists(resolvePath(path));
    }

    @Override
    public boolean exists(String path) throws IOException {
        return Files.exists(resolvePath(path));
    }

    private Path resolvePath(String pathStr) {
        if (pathStr == null || pathStr.isBlank()) {
            throw new IllegalArgumentException("Path cannot be empty");
        }
        String clean = pathStr;
        if (clean.startsWith("file://") || clean.startsWith("local://")) {
            clean = clean.substring(clean.indexOf("://") + 3);
        } else if (clean.startsWith("file:") || clean.startsWith("local:")) {
            clean = clean.substring(clean.indexOf(":") + 1);
        }
        if (clean.matches("^/[A-Za-z]:.*")) {
            clean = clean.substring(1);
        }
        return Path.of(clean);
    }
}
