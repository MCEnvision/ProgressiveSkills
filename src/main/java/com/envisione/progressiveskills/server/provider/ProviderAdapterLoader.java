package com.envisione.progressiveskills.server.provider;

import com.envisione.progressiveskills.common.provider.IntegrationProvider;

import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;

public final class ProviderAdapterLoader {
    public static final long MAX_ADAPTER_BYTES = 16L * 1024L * 1024L;

    private ProviderAdapterLoader() {
    }

    public static LoadedAdapter load(Path artifact, String expectedSha256) throws IOException {
        Path canonical = Objects.requireNonNull(artifact, "artifact").toRealPath();
        if (!Files.isRegularFile(canonical) || Files.size(canonical) > MAX_ADAPTER_BYTES) {
            throw new IllegalArgumentException("Provider adapter artifact is invalid");
        }
        String expected = Objects.requireNonNull(expectedSha256, "expectedSha256").toLowerCase(java.util.Locale.ROOT);
        String actual = digest(Files.readAllBytes(canonical));
        if (!actual.equals(expected)) {
            throw new IllegalArgumentException("Provider adapter checksum does not match its pin");
        }
        URLClassLoader loader = new URLClassLoader(
                new java.net.URL[]{canonical.toUri().toURL()},
                IntegrationProvider.class.getClassLoader()
        );
        try {
            List<IntegrationProvider> providers = ServiceLoader.load(IntegrationProvider.class, loader)
                    .stream().limit(17).map(ServiceLoader.Provider::get).toList();
            if (providers.isEmpty() || providers.size() > 16) {
                throw new IllegalArgumentException("Provider adapter service count is invalid");
            }
            return new LoadedAdapter(loader, providers, actual);
        } catch (RuntimeException exception) {
            loader.close();
            throw exception;
        }
    }

    private static String digest(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record LoadedAdapter(
            URLClassLoader classLoader,
            List<IntegrationProvider> providers,
            String sha256
    ) implements AutoCloseable {
        public LoadedAdapter {
            Objects.requireNonNull(classLoader, "classLoader");
            providers = List.copyOf(providers);
            Objects.requireNonNull(sha256, "sha256");
        }

        @Override
        public void close() throws IOException {
            classLoader.close();
        }
    }
}
