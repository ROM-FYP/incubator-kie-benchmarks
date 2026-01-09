package org.kie.benchmark.cep.reproducible.wikimedia.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;

public final class ResourceFiles {

    private ResourceFiles() {}

    /**
     * Copies a classpath resource to a temp file and returns its Path.
     * Works both from IDE and from packaged JAR.
     *
     * @param resourcePath classpath absolute path, e.g. "/reproducible/wikimedia/data/wikimedia_60min.ndjson"
     */
    public static Path copyToTempFile(String resourcePath, String prefix, String suffix) throws IOException {
        if (!resourcePath.startsWith("/")) {
            throw new IllegalArgumentException("resourcePath must start with '/': " + resourcePath);
        }

        try (InputStream in = ResourceFiles.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalArgumentException("Resource not found on classpath: " + resourcePath);
            }

            Path tmp = Files.createTempFile(prefix, suffix);
            tmp.toFile().deleteOnExit();
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            return tmp;
        }
    }

    /**
     * Copies a classpath resource to target/ and returns its Path (nice for repeat runs).
     */
    public static Path copyToTarget(String resourcePath, String targetRelativePath) throws IOException {
        if (!resourcePath.startsWith("/")) {
            throw new IllegalArgumentException("resourcePath must start with '/': " + resourcePath);
        }

        Path out = Paths.get("target").resolve(targetRelativePath);
        Files.createDirectories(out.getParent());

        try (InputStream in = ResourceFiles.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalArgumentException("Resource not found on classpath: " + resourcePath);
            }
            Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
            return out.toAbsolutePath().normalize();
        }
    }
}
