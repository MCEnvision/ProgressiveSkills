package com.envisione.progressiveskills.common.pack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonDefinitionDocumentTest {
    @TempDir
    Path temporary;

    @Test
    void readsExactJsonValuesWithoutLosingIntegerPrecision() throws IOException {
        Path file = write("valid.json", """
                {"schema_version":2,"skill":{"enabled":true,"values":[1,1.25,"text"]}}
                """);

        assertEquals(Map.of(
                "schema_version", 2L,
                "skill", Map.of("enabled", true, "values", List.of(1L, new java.math.BigDecimal("1.25"), "text"))
        ), JsonDefinitionDocument.read(file));
    }

    @Test
    void rejectsDuplicateKeysTrailingDataNullsAndNonobjectRoots() throws IOException {
        assertThrows(IllegalArgumentException.class, () -> JsonDefinitionDocument.read(
                write("duplicate.json", "{\"value\":1,\"value\":2}")));
        assertThrows(IllegalArgumentException.class, () -> JsonDefinitionDocument.read(
                write("trailing.json", "{}{}")));
        assertThrows(IllegalArgumentException.class, () -> JsonDefinitionDocument.read(
                write("null.json", "{\"value\":null}")));
        assertThrows(IllegalArgumentException.class, () -> JsonDefinitionDocument.read(
                write("array.json", "[]")));
        assertThrows(IllegalArgumentException.class, () -> JsonDefinitionDocument.read(
                write("malformed.json", "{\"value\":")));
        assertThrows(IllegalArgumentException.class, () -> JsonDefinitionDocument.read(
                write("number.json", "{\"value\":1e100000}")));
    }

    private Path write(String name, String contents) throws IOException {
        Path file = temporary.resolve(name);
        Files.writeString(file, contents);
        return file;
    }
}
