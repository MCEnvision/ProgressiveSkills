package com.envisione.progressiveskills.common.pack;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SemanticVersionTest {
    @Test
    void strictSemverOrderingAndConstraintsAreDeterministic() {
        assertTrue(SemanticVersion.parse("1.0.0-alpha.1").compareTo(SemanticVersion.parse("1.0.0")) < 0);
        assertTrue(SemanticVersion.parse("2.0.0").compareTo(SemanticVersion.parse("1.99.99")) > 0);
        assertThrows(IllegalArgumentException.class, () -> SemanticVersion.parse("1.0"));
        assertThrows(IllegalArgumentException.class, () -> SemanticVersion.parse("1.0.0-01"));

        VersionConstraint core = VersionConstraint.parse(">=1.0.0 <2.0.0");
        assertTrue(core.hasLowerBound());
        assertTrue(core.hasUpperBound());
        assertTrue(core.contains(SemanticVersion.parse("1.8.4")));
        assertFalse(core.contains(SemanticVersion.parse("2.0.0")));
    }

    @Test
    void versionConstraintComplexityAndMalformedTokensFailClosed() {
        assertThrows(IllegalArgumentException.class, () -> VersionConstraint.parse(""));
        assertThrows(IllegalArgumentException.class, () -> VersionConstraint.parse(">=1.0.0 nonsense"));
        assertThrows(IllegalArgumentException.class, () -> VersionConstraint.parse(
                ">=1.0.0 >1.0.0 >1.0.0 >1.0.0 >1.0.0 >1.0.0 >1.0.0 >1.0.0 >1.0.0"
        ));
    }
}
