package com.envisione.progressiveskills.common.schema;

import com.envisione.progressiveskills.common.diagnostic.CoreDiagnostics;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;

class SchemaDefaultValueTest {
    @Test
    void scalarDefaultsRejectUnsafeOrUnboundedValues() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SchemaDefaultValue.StringValue(
                        "x".repeat(SchemaDefaultValue.MAX_STRING_CODE_POINTS + 1)
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new SchemaDefaultValue.StringValue("bad\u0000text")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new SchemaDefaultValue.StringValue("bad\ud800text")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new SchemaDefaultValue.DecimalValue(new BigDecimal("1e1000000000"))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new SchemaDefaultValue.DecimalValue(
                        new BigDecimal("123456789012345678901234567890123456789")
                )
        );
    }

    @Test
    void collectionDefaultsAndRecursiveRenderingAreBounded() {
        var scalar = new SchemaDefaultValue.BooleanValue(false);
        assertThrows(
                IllegalArgumentException.class,
                () -> new SchemaDefaultValue.ListValue(
                        Collections.nCopies(SchemaDefaultValue.MAX_COLLECTION_ENTRIES + 1, scalar)
                )
        );

        SchemaDefaultValue nested = scalar;
        for (int depth = 0; depth < SchemaDefaultValue.MAX_TREE_DEPTH; depth++) {
            nested = new SchemaDefaultValue.ListValue(List.of(nested));
        }
        var tooDeep = nested;
        assertThrows(
                IllegalArgumentException.class,
                () -> fixtureField("nested", SchemaValueType.LIST)
                        .defaultValue(tooDeep)
                        .build()
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> fixtureField("nested_reference", SchemaValueType.LIST)
                        .defaultValue(new SchemaDefaultValue.ListValue(List.of(
                                new SchemaDefaultValue.FieldReference("other")
                        )))
                        .build()
        );
    }

    @Test
    void enumDefaultsMustBelongToTheFrozenVocabulary() {
        assertThrows(
                IllegalArgumentException.class,
                () -> fixtureField("kind", SchemaValueType.ENUM)
                        .allowedValues("first", "second")
                        .defaultString("third")
                        .build()
        );
    }

    private static FieldDescriptor.Builder fixtureField(String path, SchemaValueType type) {
        return FieldDescriptor.builder(path, type)
                .description("Fixture field.")
                .example("fixture")
                .diagnostic(CoreDiagnostics.UNKNOWN_FIELD)
                .editor(new EditorHint(EditorWidget.OBJECT, "fixture", 0, "Fixture help."));
    }
}
