package com.envisione.progressiveskills.common.pack;

import com.envisione.progressiveskills.common.diagnostic.DiagnosticReport;

import java.util.Objects;
import java.util.Optional;

/** One complete dry-run outcome; invalid results never expose a publishable snapshot. */
public record StagingResult(Optional<PackSnapshot> snapshot, DiagnosticReport diagnostics) {
    public StagingResult {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(diagnostics, "diagnostics");
        if (diagnostics.hasErrors() == snapshot.isPresent()) {
            throw new IllegalArgumentException("Only error-free staging results expose a snapshot");
        }
    }

    public static StagingResult valid(PackSnapshot snapshot, java.util.Collection<PackProblem> warnings) {
        var report = DiagnosticReport.of(warnings.stream().map(PackProblem::toDiagnostic).toList());
        if (report.hasErrors()) {
            throw new IllegalArgumentException("A valid staging result cannot contain errors");
        }
        return new StagingResult(Optional.of(snapshot), report);
    }

    public static StagingResult invalid(java.util.Collection<PackProblem> problems) {
        var report = DiagnosticReport.of(problems.stream().map(PackProblem::toDiagnostic).toList());
        if (!report.hasErrors()) {
            throw new IllegalArgumentException("An invalid staging result requires at least one error");
        }
        return new StagingResult(Optional.empty(), report);
    }

    public boolean valid() {
        return snapshot.isPresent();
    }
}
