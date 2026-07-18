package com.envisione.progressiveskills.common.studio;

import java.util.List;
import java.util.Objects;

public record StudioDiff(String digest, long bytes, List<Entry> entries) {
    public StudioDiff {
        digest = Objects.requireNonNull(digest, "digest");
        if (bytes < 0 || entries.size() > 8192) {
            throw new IllegalArgumentException("Studio diff exceeds capacity");
        }
        entries = List.copyOf(entries);
    }

    public record Entry(String path, String digest, long bytes) {
        public Entry {
            path = Objects.requireNonNull(path, "path");
            digest = Objects.requireNonNull(digest, "digest");
            if (bytes < 0) {
                throw new IllegalArgumentException("Studio diff entry size is invalid");
            }
        }
    }
}
