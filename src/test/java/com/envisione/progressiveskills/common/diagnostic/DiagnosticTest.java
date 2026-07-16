package com.envisione.progressiveskills.common.diagnostic;

import com.envisione.progressiveskills.common.id.DefinitionKey;
import com.envisione.progressiveskills.common.id.DefinitionKinds;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiagnosticTest {
    @Test
    void diagnosticAndReportDefensivelyCopyAndOrderContext() {
        var descriptor = CoreDiagnostics.catalog().require(CoreDiagnostics.INVALID_ALIAS);
        var first = DefinitionKey.parse(DefinitionKinds.SKILL, "mypack:first");
        var second = DefinitionKey.parse(DefinitionKinds.SKILL, "mypack:second");
        var related = new ArrayList<>(List.of(second, first));
        var details = new HashMap<>(Map.of("zeta", "last", "alpha", "first"));
        var diagnostic = new Diagnostic(
                descriptor,
                DiagnosticSeverity.ERROR,
                "Alias fixture is invalid.",
                Optional.empty(),
                Optional.of(first),
                related,
                details
        );
        related.clear();
        details.clear();

        assertEquals(List.of(first, second), diagnostic.relatedIds());
        assertEquals(List.of("alpha", "zeta"), diagnostic.details().keySet().stream().toList());
        assertThrows(UnsupportedOperationException.class, () -> diagnostic.relatedIds().add(first));
        assertTrue(DiagnosticReport.of(List.of(diagnostic)).hasErrors());
    }

    @Test
    void hardErrorsCannotBeDowngradedAndContextProvidesATotalOrder() {
        var descriptor = CoreDiagnostics.catalog().require(CoreDiagnostics.UNSAFE_PRESENTATION);
        assertThrows(IllegalArgumentException.class, () -> new Diagnostic(
                descriptor,
                DiagnosticSeverity.INFO,
                "Unsafe component.",
                Optional.empty(),
                Optional.empty(),
                List.of(),
                Map.of()
        ));

        var alpha = Diagnostic.of(descriptor, "Unsafe component.");
        var zeta = new Diagnostic(
                descriptor,
                DiagnosticSeverity.ERROR,
                "Unsafe component.",
                Optional.empty(),
                Optional.empty(),
                List.of(),
                Map.of("source", "zeta")
        );
        var report = DiagnosticReport.of(Set.of(zeta, alpha));

        assertTrue(alpha.compareTo(zeta) < 0);
        assertEquals(List.of(alpha, zeta), report.diagnostics());
    }

    @Test
    void diagnosticPayloadAndReportSizesAreBounded() {
        var descriptor = CoreDiagnostics.catalog().require(CoreDiagnostics.INVALID_COMPONENT);
        var key = DefinitionKey.parse(DefinitionKinds.SKILL, "mypack:physique");

        assertThrows(
                IllegalArgumentException.class,
                () -> Diagnostic.of(descriptor, "x".repeat(Diagnostic.MAX_MESSAGE_CODE_POINTS + 1))
        );
        assertThrows(IllegalArgumentException.class, () -> Diagnostic.of(descriptor, "bad\ud800message"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new Diagnostic(
                        descriptor,
                        DiagnosticSeverity.ERROR,
                        "Too many related ids.",
                        Optional.empty(),
                        Optional.empty(),
                        Collections.nCopies(Diagnostic.MAX_RELATED_IDS + 1, key),
                        Map.of()
                )
        );
        var normalizedCollision = new HashMap<String, String>();
        normalizedCollision.put("alpha", "first");
        normalizedCollision.put(" alpha ", "second");
        assertThrows(
                IllegalArgumentException.class,
                () -> new Diagnostic(
                        descriptor,
                        DiagnosticSeverity.ERROR,
                        "Duplicate normalized detail key.",
                        Optional.empty(),
                        Optional.empty(),
                        List.of(),
                        normalizedCollision
                )
        );

        var details = new HashMap<String, String>();
        for (int index = 0; index <= Diagnostic.MAX_DETAILS; index++) {
            details.put("detail." + index, "value");
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> new Diagnostic(
                        descriptor,
                        DiagnosticSeverity.ERROR,
                        "Too many details.",
                        Optional.empty(),
                        Optional.empty(),
                        List.of(),
                        details
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new Diagnostic(
                        descriptor,
                        DiagnosticSeverity.ERROR,
                        "Invalid detail key.",
                        Optional.empty(),
                        Optional.empty(),
                        List.of(),
                        Map.of("Bad Key", "value")
                )
        );

        var diagnostic = Diagnostic.of(descriptor, "Bounded fixture.");
        assertThrows(
                IllegalArgumentException.class,
                () -> DiagnosticReport.of(Collections.nCopies(
                        DiagnosticReport.MAX_DIAGNOSTICS + 1,
                        diagnostic
                ))
        );
    }
}
