package com.envisione.progressiveskills.common.diagnostic;

import java.util.Objects;
import java.util.regex.Pattern;

/** A stable, documented diagnostic identifier such as {@code PS-SCHEMA-001}. */
public record DiagnosticCode(String value) implements Comparable<DiagnosticCode> {
    private static final Pattern FORMAT = Pattern.compile("PS-[A-Z][A-Z0-9]*-[0-9]{3}");

    public DiagnosticCode {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid diagnostic code: " + value);
        }
    }

    @Override
    public int compareTo(DiagnosticCode other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
