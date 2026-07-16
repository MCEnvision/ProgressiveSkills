package com.envisione.progressiveskills.common.pack;

import com.envisione.progressiveskills.common.diagnostic.CoreDiagnostics;
import com.envisione.progressiveskills.common.diagnostic.Diagnostic;
import com.envisione.progressiveskills.common.diagnostic.DiagnosticCode;
import com.envisione.progressiveskills.common.diagnostic.DiagnosticSeverity;
import com.envisione.progressiveskills.common.id.DefinitionKey;
import com.envisione.progressiveskills.common.source.Provenance;
import com.envisione.progressiveskills.common.source.SourcePosition;
import com.envisione.progressiveskills.common.source.SourceReference;
import com.envisione.progressiveskills.common.source.SourceSpan;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Internal validation problem converted into the public stable diagnostic model at the loader boundary. */
public record PackProblem(
        DiagnosticCode code,
        DiagnosticSeverity severity,
        String message,
        Optional<Provenance> provenance,
        Optional<DefinitionKey> definition
) implements Comparable<PackProblem> {
    public PackProblem {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(provenance, "provenance");
        Objects.requireNonNull(definition, "definition");
    }

    public static PackProblem error(DiagnosticCode code, String message, Provenance provenance) {
        return new PackProblem(code, DiagnosticSeverity.ERROR, message, Optional.of(provenance), Optional.empty());
    }

    public static PackProblem error(
            DiagnosticCode code,
            String message,
            Provenance provenance,
            DefinitionKey definition
    ) {
        return new PackProblem(code, DiagnosticSeverity.ERROR, message, Optional.of(provenance), Optional.of(definition));
    }

    public static PackProblem warning(
            DiagnosticCode code,
            String message,
            Provenance provenance,
            DefinitionKey definition
    ) {
        return new PackProblem(code, DiagnosticSeverity.WARNING, message,
                Optional.of(provenance), Optional.of(definition));
    }

    public Diagnostic toDiagnostic() {
        var descriptor = CoreDiagnostics.catalog().require(code);
        Optional<SourceReference> source = provenance.map(value -> new SourceReference(
                value,
                new SourceSpan(new SourcePosition(1, 1), new SourcePosition(1, 1))
        ));
        return new Diagnostic(descriptor, severity, message, source, definition, List.of(), Map.of());
    }

    @Override
    public int compareTo(PackProblem other) {
        int comparison = code.compareTo(other.code);
        if (comparison == 0) {
            comparison = severity.compareTo(other.severity);
        }
        if (comparison == 0) {
            comparison = compareOptional(provenance, other.provenance);
        }
        if (comparison == 0) {
            comparison = compareOptional(definition, other.definition);
        }
        return comparison != 0 ? comparison : message.compareTo(other.message);
    }

    private static <T extends Comparable<T>> int compareOptional(Optional<T> left, Optional<T> right) {
        if (left.isEmpty()) {
            return right.isEmpty() ? 0 : -1;
        }
        return right.isEmpty() ? 1 : left.orElseThrow().compareTo(right.orElseThrow());
    }
}
