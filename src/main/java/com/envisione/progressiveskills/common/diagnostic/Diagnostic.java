package com.envisione.progressiveskills.common.diagnostic;

import com.envisione.progressiveskills.common.id.DefinitionKey;
import com.envisione.progressiveskills.common.source.SourceReference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Pattern;

/** Immutable diagnostic instance with portable source and typed identity context. */
public record Diagnostic(
        DiagnosticDescriptor descriptor,
        DiagnosticSeverity severity,
        String message,
        Optional<SourceReference> source,
        Optional<DefinitionKey> definition,
        List<DefinitionKey> relatedIds,
        Map<String, String> details
) implements Comparable<Diagnostic> {
    public static final int MAX_MESSAGE_CODE_POINTS = 8_192;
    public static final int MAX_RELATED_IDS = 256;
    public static final int MAX_DETAILS = 128;
    public static final int MAX_DETAIL_KEY_CODE_POINTS = 128;
    public static final int MAX_DETAIL_VALUE_CODE_POINTS = 2_048;
    private static final Pattern DETAIL_KEY = Pattern.compile("[a-z][a-z0-9_.-]*");

    public Diagnostic {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(severity, "severity");
        message = requireSafeText(message, MAX_MESSAGE_CODE_POINTS, "message");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(definition, "definition");
        relatedIds = sortedIds(relatedIds);
        details = sortedDetails(details);
        if (descriptor.defaultSeverity() == DiagnosticSeverity.ERROR
                && !descriptor.suppressible()
                && severity != DiagnosticSeverity.ERROR) {
            throw new IllegalArgumentException(
                    "Non-suppressible error " + descriptor.code() + " cannot be downgraded to " + severity
            );
        }
    }

    public static Diagnostic of(DiagnosticDescriptor descriptor, String message) {
        return new Diagnostic(
                descriptor,
                descriptor.defaultSeverity(),
                message,
                Optional.empty(),
                Optional.empty(),
                List.of(),
                Map.of()
        );
    }

    @Override
    public int compareTo(Diagnostic other) {
        int descriptorComparison = compareDescriptors(descriptor, other.descriptor);
        if (descriptorComparison != 0) {
            return descriptorComparison;
        }
        int severityComparison = severity.compareTo(other.severity);
        if (severityComparison != 0) {
            return severityComparison;
        }
        int sourceComparison = compareOptional(source, other.source);
        if (sourceComparison != 0) {
            return sourceComparison;
        }
        int definitionComparison = compareOptional(definition, other.definition);
        if (definitionComparison != 0) {
            return definitionComparison;
        }
        int messageComparison = message.compareTo(other.message);
        if (messageComparison != 0) {
            return messageComparison;
        }
        int relatedComparison = compareLists(relatedIds, other.relatedIds);
        return relatedComparison != 0 ? relatedComparison : compareMaps(details, other.details);
    }

    private static int compareDescriptors(DiagnosticDescriptor left, DiagnosticDescriptor right) {
        int comparison = left.code().compareTo(right.code());
        if (comparison == 0) {
            comparison = left.defaultSeverity().compareTo(right.defaultSeverity());
        }
        if (comparison == 0) {
            comparison = left.title().compareTo(right.title());
        }
        if (comparison == 0) {
            comparison = left.whyItMatters().compareTo(right.whyItMatters());
        }
        if (comparison == 0) {
            comparison = left.suggestedFix().compareTo(right.suggestedFix());
        }
        if (comparison == 0) {
            comparison = left.documentationPath().compareTo(right.documentationPath());
        }
        return comparison != 0 ? comparison : Boolean.compare(left.suppressible(), right.suppressible());
    }

    private static <T extends Comparable<T>> int compareOptional(Optional<T> left, Optional<T> right) {
        if (left.isEmpty()) {
            return right.isEmpty() ? 0 : -1;
        }
        return right.isEmpty() ? 1 : left.orElseThrow().compareTo(right.orElseThrow());
    }

    private static <T extends Comparable<T>> int compareLists(List<T> left, List<T> right) {
        int commonSize = Math.min(left.size(), right.size());
        for (int index = 0; index < commonSize; index++) {
            int comparison = left.get(index).compareTo(right.get(index));
            if (comparison != 0) {
                return comparison;
            }
        }
        return Integer.compare(left.size(), right.size());
    }

    private static int compareMaps(Map<String, String> left, Map<String, String> right) {
        var leftEntries = left.entrySet().iterator();
        var rightEntries = right.entrySet().iterator();
        while (leftEntries.hasNext() && rightEntries.hasNext()) {
            var leftEntry = leftEntries.next();
            var rightEntry = rightEntries.next();
            int comparison = leftEntry.getKey().compareTo(rightEntry.getKey());
            if (comparison == 0) {
                comparison = leftEntry.getValue().compareTo(rightEntry.getValue());
            }
            if (comparison != 0) {
                return comparison;
            }
        }
        return Integer.compare(left.size(), right.size());
    }

    private static List<DefinitionKey> sortedIds(List<DefinitionKey> values) {
        Objects.requireNonNull(values, "relatedIds");
        if (values.size() > MAX_RELATED_IDS) {
            throw new IllegalArgumentException("Diagnostic exceeds " + MAX_RELATED_IDS + " related ids");
        }
        var sorted = new ArrayList<DefinitionKey>(values.size());
        for (var value : values) {
            sorted.add(Objects.requireNonNull(value, "related id"));
        }
        sorted.sort(DefinitionKey::compareTo);
        return Collections.unmodifiableList(sorted);
    }

    private static Map<String, String> sortedDetails(Map<String, String> values) {
        Objects.requireNonNull(values, "details");
        if (values.size() > MAX_DETAILS) {
            throw new IllegalArgumentException("Diagnostic exceeds " + MAX_DETAILS + " detail entries");
        }
        var sorted = new TreeMap<String, String>();
        values.forEach((key, value) -> {
            var checkedKey = requireSafeText(key, MAX_DETAIL_KEY_CODE_POINTS, "detail key");
            if (!DETAIL_KEY.matcher(checkedKey).matches()) {
                throw new IllegalArgumentException("Invalid diagnostic detail key: " + checkedKey);
            }
            var checkedValue = requireSafeText(value, MAX_DETAIL_VALUE_CODE_POINTS, "detail value");
            if (sorted.putIfAbsent(checkedKey, checkedValue) != null) {
                throw new IllegalArgumentException("Duplicate normalized diagnostic detail key: " + checkedKey);
            }
        });
        return Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
    }

    private static String requireSafeText(String value, int maxCodePoints, String name) {
        Objects.requireNonNull(value, name);
        var normalized = value.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        if (normalized.codePointCount(0, normalized.length()) > maxCodePoints) {
            throw new IllegalArgumentException(name + " exceeds " + maxCodePoints + " code points");
        }
        for (int index = 0; index < normalized.length();) {
            char unit = normalized.charAt(index);
            if (Character.isHighSurrogate(unit)
                    && (index + 1 >= normalized.length()
                    || !Character.isLowSurrogate(normalized.charAt(index + 1)))) {
                throw new IllegalArgumentException(name + " contains an unpaired high surrogate");
            }
            if (Character.isLowSurrogate(unit)) {
                throw new IllegalArgumentException(name + " contains an unpaired low surrogate");
            }
            int codePoint = normalized.codePointAt(index);
            if (Character.isISOControl(codePoint) && codePoint != '\n' && codePoint != '\t') {
                throw new IllegalArgumentException(name + " contains a disallowed control character");
            }
            index += Character.charCount(codePoint);
        }
        return normalized;
    }
}
