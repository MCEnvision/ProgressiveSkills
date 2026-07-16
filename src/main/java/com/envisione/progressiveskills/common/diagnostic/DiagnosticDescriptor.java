package com.envisione.progressiveskills.common.diagnostic;

import java.util.Objects;

/** Schema-owned help text and policy for a diagnostic code. */
public record DiagnosticDescriptor(
        DiagnosticCode code,
        DiagnosticSeverity defaultSeverity,
        String title,
        String whyItMatters,
        String suggestedFix,
        String documentationPath,
        boolean suppressible
) {
    public static final int MAX_TITLE_CODE_POINTS = 256;
    public static final int MAX_HELP_CODE_POINTS = 2_048;
    public static final int MAX_DOCUMENTATION_PATH_CODE_POINTS = 512;

    public DiagnosticDescriptor {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(defaultSeverity, "defaultSeverity");
        title = requireText(title, MAX_TITLE_CODE_POINTS, "title");
        whyItMatters = requireText(whyItMatters, MAX_HELP_CODE_POINTS, "whyItMatters");
        suggestedFix = requireText(suggestedFix, MAX_HELP_CODE_POINTS, "suggestedFix");
        documentationPath = requireDocumentationPath(documentationPath);
        if (defaultSeverity == DiagnosticSeverity.ERROR
                && (code.value().startsWith("PS-SEC-") || code.value().startsWith("PS-SCHEMA-"))) {
            suppressible = false;
        }
    }

    private static String requireText(String value, int maxCodePoints, String name) {
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

    private static String requireDocumentationPath(String value) {
        var normalized = requireText(value, MAX_DOCUMENTATION_PATH_CODE_POINTS, "documentationPath");
        if (!normalized.startsWith("docs/")
                || normalized.indexOf('\\') >= 0
                || normalized.indexOf(':') >= 0
                || java.util.Arrays.asList(normalized.split("/", -1)).contains("..")) {
            throw new IllegalArgumentException("documentationPath must be repository-relative under docs/");
        }
        return normalized;
    }
}
