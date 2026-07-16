package com.envisione.progressiveskills.common.diagnostic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Deterministically ordered immutable diagnostics produced by a validation step. */
public final class DiagnosticReport {
    public static final int MAX_DIAGNOSTICS = 10_000;
    private static final DiagnosticReport EMPTY = new DiagnosticReport(List.of());

    private final List<Diagnostic> diagnostics;

    private DiagnosticReport(Collection<Diagnostic> diagnostics) {
        if (diagnostics.size() > MAX_DIAGNOSTICS) {
            throw new IllegalArgumentException("Diagnostic report exceeds " + MAX_DIAGNOSTICS + " entries");
        }
        var sorted = new ArrayList<Diagnostic>(diagnostics.size());
        for (var diagnostic : diagnostics) {
            sorted.add(Objects.requireNonNull(diagnostic, "diagnostic"));
        }
        sorted.sort(Diagnostic::compareTo);
        this.diagnostics = Collections.unmodifiableList(sorted);
    }

    public static DiagnosticReport of(Collection<Diagnostic> diagnostics) {
        Objects.requireNonNull(diagnostics, "diagnostics");
        return diagnostics.isEmpty() ? EMPTY : new DiagnosticReport(diagnostics);
    }

    public static DiagnosticReport empty() {
        return EMPTY;
    }

    public List<Diagnostic> diagnostics() {
        return diagnostics;
    }

    public boolean hasErrors() {
        return diagnostics.stream().anyMatch(diagnostic -> diagnostic.severity() == DiagnosticSeverity.ERROR);
    }
}
