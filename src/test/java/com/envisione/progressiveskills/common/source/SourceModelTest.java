package com.envisione.progressiveskills.common.source;

import com.envisione.progressiveskills.common.id.StableId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceModelTest {
    @Test
    void positionsAreOneBasedAndLexicographicallyOrdered() {
        assertThrows(IllegalArgumentException.class, () -> new SourcePosition(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new SourcePosition(1, 0));
        assertTrue(new SourcePosition(1, 99).compareTo(new SourcePosition(2, 1)) < 0);
        assertTrue(new SourcePosition(2, 1).compareTo(new SourcePosition(2, 2)) < 0);
    }

    @Test
    void spansUseHalfOpenCoordinates() {
        SourceSpan span = SourceSpan.between(2, 3, 4, 1);

        assertTrue(span.contains(new SourcePosition(2, 3)));
        assertTrue(span.contains(new SourcePosition(3, 999)));
        assertFalse(span.contains(new SourcePosition(2, 2)));
        assertFalse(span.contains(new SourcePosition(4, 1)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SourceSpan(new SourcePosition(4, 1), new SourcePosition(2, 3))
        );
    }

    @Test
    void zeroWidthSpansAndEnclosingRangesAreWellDefined() {
        SourcePosition point = new SourcePosition(3, 7);
        SourceSpan empty = new SourceSpan(point, point);
        SourceSpan other = SourceSpan.between(2, 5, 4, 2);

        assertTrue(empty.isEmpty());
        assertFalse(empty.contains(point));
        assertEquals(other, empty.enclosing(other));
    }

    @Test
    void provenanceUsesPortableRelativePaths() {
        Provenance provenance = provenance("skills/combat/physique.toml");

        assertEquals("mypack:core", provenance.packId().toString());
        assertEquals("toml", provenance.adapter());
        assertThrows(
                IllegalArgumentException.class,
                () -> new Provenance(StableId.parse("mypack:core"), "/skills/a.toml", "toml")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new Provenance(StableId.parse("mypack:core"), "skills/../a.toml", "toml")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new Provenance(StableId.parse("mypack:core"), "skills\\a.toml", "toml")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new Provenance(StableId.parse("mypack:core"), "C:/skills/a.toml", "toml")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new Provenance(StableId.parse("mypack:core"), "skills/a:legacy.toml", "toml")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new Provenance(StableId.parse("mypack:core"), "skills/bad\ud800.toml", "toml")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new Provenance(StableId.parse("mypack:core"), "skills/a.toml", "TOML")
        );
    }

    @Test
    void sourceMapIsFieldLevelSortedAndDefensivelySnapshotted() {
        SourceReference display = reference("skills/example.toml", 2, 1, 2, 20);
        SourceReference identifier = reference("skills/example.toml", 1, 1, 1, 30);
        SourceMap.Builder builder = SourceMap.builder()
                .put("display", display)
                .put("id", identifier);

        SourceMap firstSnapshot = builder.build();
        builder.put("levels[0].id", reference("skills/example.toml", 4, 1, 4, 16));
        SourceMap secondSnapshot = builder.build();

        assertEquals(List.of("display", "id"), new ArrayList<>(firstSnapshot.fields().keySet()));
        assertEquals(2, firstSnapshot.fields().size());
        assertEquals(3, secondSnapshot.fields().size());
        assertSame(display, firstSnapshot.require("display"));
        assertTrue(firstSnapshot.find("missing").isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> firstSnapshot.fields().put("x", display));
        assertThrows(IllegalArgumentException.class, () -> SourceMap.builder().put("bad field", display));
        assertThrows(IllegalArgumentException.class, () -> SourceMap.builder().put("bad\udc00field", display));
        assertThrows(
                IllegalArgumentException.class,
                () -> SourceMap.builder().put("id", identifier).put("id", display)
        );
    }

    @Test
    void sourceMapEntryCountIsBoundedBeforeSnapshotting() {
        var reference = reference("skills/example.toml", 1, 1, 1, 2);
        var builder = SourceMap.builder();
        for (int index = 0; index < SourceMap.MAX_FIELD_REFERENCES; index++) {
            builder.put("field" + index, reference);
        }

        assertEquals(SourceMap.MAX_FIELD_REFERENCES, builder.build().fields().size());
        assertThrows(
                IllegalArgumentException.class,
                () -> builder.put("one_more", reference)
        );
    }

    private static Provenance provenance(String path) {
        return new Provenance(StableId.parse("mypack:core"), path, "toml");
    }

    private static SourceReference reference(String path, int startLine, int startColumn, int endLine, int endColumn) {
        return new SourceReference(provenance(path), SourceSpan.between(startLine, startColumn, endLine, endColumn));
    }
}
