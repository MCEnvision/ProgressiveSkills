package com.envisione.progressiveskills.common.pack;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.toml.TomlParser;
import com.envisione.progressiveskills.common.source.Provenance;
import com.envisione.progressiveskills.common.source.SourceMap;
import com.envisione.progressiveskills.common.source.SourcePosition;
import com.envisione.progressiveskills.common.source.SourceReference;
import com.envisione.progressiveskills.common.source.SourceSpan;

import java.io.StringReader;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Bounded UTF-8 TOML document with neutral immutable values and best-effort field spans. */
public final class TomlDocument {
    public static final int MAX_DEPTH = 64;
    public static final int MAX_NODES = 100_000;
    public static final long MAX_TEXT_CODE_POINTS = 1_000_000;
    private final String text;
    private final Map<String, Object> values;
    private final byte[] sourceBytes;

    private TomlDocument(String text, Map<String, Object> values, byte[] sourceBytes) {
        this.text = text;
        this.values = values;
        this.sourceBytes = sourceBytes;
    }

    public static TomlDocument read(Path file) throws java.io.IOException {
        Objects.requireNonNull(file, "file");
        if (Files.isSymbolicLink(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("TOML source must be a regular non-symbolic file: " + file);
        }
        long size = Files.size(file);
        if (size > SourceBundle.MAX_FILE_BYTES) {
            throw new IllegalArgumentException("TOML source exceeds " + SourceBundle.MAX_FILE_BYTES + " bytes: " + file);
        }
        return parse(Files.readAllBytes(file));
    }

    public static TomlDocument parse(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length > SourceBundle.MAX_FILE_BYTES) {
            throw new IllegalArgumentException("TOML source exceeds " + SourceBundle.MAX_FILE_BYTES + " bytes");
        }
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("TOML source is not valid UTF-8", exception);
        }
        preflight(text);
        Config parsed = Config.inMemory();
        new TomlParser().parse(new StringReader(text), parsed, com.electronwill.nightconfig.core.io.ParsingMode.REPLACE);
        Map<String, Object> values = immutableObject(parsed);
        validateTree(values);
        return new TomlDocument(text, values, bytes.clone());
    }

    public Map<String, Object> values() {
        return values;
    }

    public byte[] sourceBytes() {
        return sourceBytes.clone();
    }

    public SourceMap sourceMap(Provenance provenance) {
        Objects.requireNonNull(provenance, "provenance");
        var builder = SourceMap.builder();
        var table = new ArrayList<String>();
        var arrayIndexes = new java.util.HashMap<String, Integer>();
        String[] lines = text.split("\\R", -1);
        for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
            String line = stripTomlComment(lines[lineIndex]).strip();
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("[[") && line.endsWith("]]")) {
                String path = line.substring(2, line.length() - 2).strip();
                int index = arrayIndexes.merge(path, 1, Integer::sum) - 1;
                table = new ArrayList<>(List.of(path.split("\\.")));
                if (!table.isEmpty()) {
                    int last = table.size() - 1;
                    table.set(last, table.get(last) + "[" + index + "]");
                }
                continue;
            }
            if (line.startsWith("[") && line.endsWith("]")) {
                String path = line.substring(1, line.length() - 1).strip();
                table = new ArrayList<>(List.of(path.split("\\.")));
                continue;
            }
            int equals = findAssignment(line);
            if (equals <= 0) {
                continue;
            }
            String key = unquoteKey(line.substring(0, equals).strip());
            var path = new ArrayList<>(table);
            path.add(key);
            String fieldPath = String.join(".", path);
            var start = new SourcePosition(lineIndex + 1, 1);
            var end = new SourcePosition(lineIndex + 2, 1);
            try {
                builder.put(fieldPath, new SourceReference(provenance, new SourceSpan(start, end)));
            } catch (IllegalArgumentException ignored) {
                // Duplicate/complex TOML paths retain the first deterministic span.
            }
        }
        return builder.build();
    }

    private static Map<String, Object> immutableObject(com.electronwill.nightconfig.core.UnmodifiableConfig source) {
        var sorted = new TreeMap<String, Object>();
        for (var entry : source.entrySet()) {
            sorted.put(entry.getKey(), immutableValue(entry.getRawValue()));
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
    }

    private static Object immutableValue(Object value) {
        Objects.requireNonNull(value, "TOML value");
        if (value instanceof Config config) {
            return immutableObject(config);
        }
        if (value instanceof List<?> list) {
            return list.stream().map(TomlDocument::immutableValue).toList();
        }
        if (value instanceof String || value instanceof Boolean || value instanceof Number) {
            return value;
        }
        throw new IllegalArgumentException("Unsupported TOML value type: " + value.getClass().getName());
    }

    private static void validateTree(Map<String, Object> root) {
        record AtDepth(Object value, int depth) {}
        var pending = new ArrayDeque<AtDepth>();
        pending.add(new AtDepth(root, 1));
        int nodes = 0;
        long text = 0;
        while (!pending.isEmpty()) {
            AtDepth current = pending.removeFirst();
            if (++nodes > MAX_NODES) {
                throw new IllegalArgumentException("TOML document exceeds " + MAX_NODES + " values");
            }
            if (current.depth > MAX_DEPTH) {
                throw new IllegalArgumentException("TOML document exceeds depth " + MAX_DEPTH);
            }
            if (current.value instanceof Map<?, ?> map) {
                for (var entry : map.entrySet()) {
                    text += entry.getKey().toString().codePointCount(0, entry.getKey().toString().length());
                    pending.addLast(new AtDepth(entry.getValue(), current.depth + 1));
                }
            } else if (current.value instanceof List<?> list) {
                list.forEach(value -> pending.addLast(new AtDepth(value, current.depth + 1)));
            } else if (current.value instanceof String string) {
                text += string.codePointCount(0, string.length());
            }
            if (text > MAX_TEXT_CODE_POINTS) {
                throw new IllegalArgumentException("TOML document exceeds aggregate text budget " + MAX_TEXT_CODE_POINTS);
            }
        }
    }

    private static void preflight(String text) {
        int lines = 1;
        int structuralDepth = 0;
        boolean quoted = false;
        char quote = 0;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == '\n' && ++lines > 200_000) {
                throw new IllegalArgumentException("TOML source exceeds 200000 lines");
            }
            if ((character == '\'' || character == '"') && (index == 0 || text.charAt(index - 1) != '\\')) {
                if (!quoted) {
                    quoted = true;
                    quote = character;
                } else if (quote == character) {
                    quoted = false;
                }
                continue;
            }
            if (quoted) {
                continue;
            }
            if (character == '[' || character == '{') {
                if (++structuralDepth > MAX_DEPTH) {
                    throw new IllegalArgumentException("TOML syntax exceeds structural depth " + MAX_DEPTH);
                }
            } else if ((character == ']' || character == '}') && structuralDepth > 0) {
                structuralDepth--;
            }
        }
        for (String line : text.split("\\R", -1)) {
            String stripped = stripTomlComment(line).strip();
            if (stripped.startsWith("[") && stripped.endsWith("]")) {
                String table = stripped.replaceFirst("^\\[+", "").replaceFirst("\\]+$", "");
                if (table.split("\\.", -1).length > MAX_DEPTH) {
                    throw new IllegalArgumentException("TOML table path exceeds depth " + MAX_DEPTH);
                }
            }
        }
    }

    private static int findAssignment(String line) {
        boolean quoted = false;
        char quote = 0;
        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            if ((character == '\'' || character == '"') && (index == 0 || line.charAt(index - 1) != '\\')) {
                if (!quoted) {
                    quoted = true;
                    quote = character;
                } else if (quote == character) {
                    quoted = false;
                }
            } else if (character == '=' && !quoted) {
                return index;
            }
        }
        return -1;
    }

    private static String stripTomlComment(String line) {
        boolean quoted = false;
        char quote = 0;
        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            if ((character == '\'' || character == '"') && (index == 0 || line.charAt(index - 1) != '\\')) {
                if (!quoted) {
                    quoted = true;
                    quote = character;
                } else if (quote == character) {
                    quoted = false;
                }
            } else if (character == '#' && !quoted) {
                return line.substring(0, index);
            }
        }
        return line;
    }

    private static String unquoteKey(String key) {
        if (key.length() >= 2 && (key.charAt(0) == '"' && key.charAt(key.length() - 1) == '"'
                || key.charAt(0) == '\'' && key.charAt(key.length() - 1) == '\'')) {
            return key.substring(1, key.length() - 1);
        }
        return key;
    }
}
