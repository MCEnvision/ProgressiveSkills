package com.envisione.progressiveskills.common.diagnostic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DiagnosticCatalogTest {
    @Test
    void codesUseTheStableNamespacedFormat() {
        assertEquals("PS-SCHEMA-001", new DiagnosticCode("PS-SCHEMA-001").value());
        assertThrows(IllegalArgumentException.class, () -> new DiagnosticCode("schema-1"));
    }

    @Test
    void catalogFreezesAndSecurityErrorsCannotBeSuppressible() {
        var builder = DiagnosticCatalog.builder();
        var code = new DiagnosticCode("PS-SEC-999");
        builder.register(new DiagnosticDescriptor(
                code,
                DiagnosticSeverity.ERROR,
                "Unsafe fixture",
                "The fixture crosses a trust boundary.",
                "Remove the unsafe fixture.",
                "docs/reference/SCHEMA-V2.md#ps-sec-999",
                true
        ));

        var catalog = builder.build();

        assertFalse(catalog.require(code).suppressible());
        assertThrows(IllegalStateException.class, builder::build);
    }

    @Test
    void descriptorTextAndDocumentationPathsArePortableAndBounded() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new DiagnosticDescriptor(
                        new DiagnosticCode("PS-TEST-001"),
                        DiagnosticSeverity.ERROR,
                        "Bad documentation path",
                        "The path escapes the repository docs root.",
                        "Use a portable path.",
                        "docs/../secret.md",
                        false
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new DiagnosticDescriptor(
                        new DiagnosticCode("PS-TEST-002"),
                        DiagnosticSeverity.WARNING,
                        "x".repeat(DiagnosticDescriptor.MAX_TITLE_CODE_POINTS + 1),
                        "Why.",
                        "Fix.",
                        "docs/reference/fixture.md",
                        true
                )
        );
    }

    @Test
    void treeDiagnosticsHaveStableCodesAndIntentionalSeverities() {
        var catalog = CoreDiagnostics.catalog();

        assertEquals("PS-TREE-001", CoreDiagnostics.INVALID_TREE.value());
        assertEquals("PS-TREE-002", CoreDiagnostics.TREE_PURCHASE_DENIED.value());
        assertEquals("PS-TREE-003", CoreDiagnostics.TREE_REFUND_DENIED.value());
        assertEquals("PS-TREE-004", CoreDiagnostics.TREE_ORPHANED_PURCHASE.value());
        assertEquals("PS-TREE-005", CoreDiagnostics.PAID_COST_LEDGER_INVALID.value());
        assertEquals(DiagnosticSeverity.ERROR, catalog.require(CoreDiagnostics.INVALID_TREE).defaultSeverity());
        assertEquals(DiagnosticSeverity.WARNING, catalog.require(CoreDiagnostics.TREE_PURCHASE_DENIED).defaultSeverity());
        assertEquals(DiagnosticSeverity.WARNING, catalog.require(CoreDiagnostics.TREE_REFUND_DENIED).defaultSeverity());
        assertEquals(DiagnosticSeverity.WARNING, catalog.require(CoreDiagnostics.TREE_ORPHANED_PURCHASE).defaultSeverity());
        assertEquals(DiagnosticSeverity.ERROR, catalog.require(CoreDiagnostics.PAID_COST_LEDGER_INVALID).defaultSeverity());
    }
}
