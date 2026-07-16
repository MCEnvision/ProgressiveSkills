package com.envisione.progressiveskills.common.pack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** Strict SemVer 2.0 value used for pack content and engine compatibility. */
public record SemanticVersion(int major, int minor, int patch, List<String> prerelease, String build)
        implements Comparable<SemanticVersion> {
    private static final Pattern CORE = Pattern.compile("0|[1-9][0-9]*");
    private static final Pattern IDENTIFIER = Pattern.compile("[0-9A-Za-z-]+");
    public static final SemanticVersion ENGINE_CURRENT = parse("1.0.0");

    public SemanticVersion {
        if (major < 0 || minor < 0 || patch < 0) {
            throw new IllegalArgumentException("Semantic-version numbers must be non-negative");
        }
        prerelease = List.copyOf(prerelease);
        for (String identifier : prerelease) {
            requireIdentifier(identifier, true);
        }
        build = Objects.requireNonNull(build, "build");
        if (!build.isEmpty()) {
            for (String identifier : build.split("\\.", -1)) {
                requireIdentifier(identifier, false);
            }
        }
    }

    public static SemanticVersion parse(String value) {
        Objects.requireNonNull(value, "value");
        if (value.isEmpty() || value.length() > 128 || !value.equals(value.strip())) {
            throw new IllegalArgumentException("Invalid semantic version: " + value);
        }
        String withoutBuild = value;
        String build = "";
        int buildSeparator = value.indexOf('+');
        if (buildSeparator >= 0) {
            if (buildSeparator != value.lastIndexOf('+')) {
                throw new IllegalArgumentException("Invalid semantic version: " + value);
            }
            withoutBuild = value.substring(0, buildSeparator);
            build = value.substring(buildSeparator + 1);
        }
        String core = withoutBuild;
        List<String> prerelease = List.of();
        int prereleaseSeparator = withoutBuild.indexOf('-');
        if (prereleaseSeparator >= 0) {
            core = withoutBuild.substring(0, prereleaseSeparator);
            prerelease = List.of(withoutBuild.substring(prereleaseSeparator + 1).split("\\.", -1));
        }
        String[] numbers = core.split("\\.", -1);
        if (numbers.length != 3) {
            throw new IllegalArgumentException("Semantic version must contain major.minor.patch: " + value);
        }
        for (String number : numbers) {
            if (!CORE.matcher(number).matches()) {
                throw new IllegalArgumentException("Invalid semantic-version number: " + value);
            }
        }
        try {
            return new SemanticVersion(
                    Integer.parseInt(numbers[0]),
                    Integer.parseInt(numbers[1]),
                    Integer.parseInt(numbers[2]),
                    prerelease,
                    build
            );
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Semantic-version number exceeds integer bounds: " + value, exception);
        }
    }

    @Override
    public int compareTo(SemanticVersion other) {
        int comparison = Integer.compare(major, other.major);
        if (comparison == 0) {
            comparison = Integer.compare(minor, other.minor);
        }
        if (comparison == 0) {
            comparison = Integer.compare(patch, other.patch);
        }
        if (comparison != 0) {
            return comparison;
        }
        if (prerelease.isEmpty()) {
            return other.prerelease.isEmpty() ? 0 : 1;
        }
        if (other.prerelease.isEmpty()) {
            return -1;
        }
        int common = Math.min(prerelease.size(), other.prerelease.size());
        for (int index = 0; index < common; index++) {
            comparison = comparePrereleaseIdentifier(prerelease.get(index), other.prerelease.get(index));
            if (comparison != 0) {
                return comparison;
            }
        }
        return Integer.compare(prerelease.size(), other.prerelease.size());
    }

    @Override
    public String toString() {
        var value = new StringBuilder().append(major).append('.').append(minor).append('.').append(patch);
        if (!prerelease.isEmpty()) {
            value.append('-').append(String.join(".", prerelease));
        }
        if (!build.isEmpty()) {
            value.append('+').append(build);
        }
        return value.toString();
    }

    private static void requireIdentifier(String identifier, boolean rejectNumericLeadingZero) {
        Objects.requireNonNull(identifier, "identifier");
        if (!IDENTIFIER.matcher(identifier).matches()) {
            throw new IllegalArgumentException("Invalid semantic-version identifier: " + identifier);
        }
        if (rejectNumericLeadingZero && isNumeric(identifier)
                && identifier.length() > 1 && identifier.charAt(0) == '0') {
            throw new IllegalArgumentException("Numeric prerelease identifiers cannot contain leading zeroes: " + identifier);
        }
    }

    private static int comparePrereleaseIdentifier(String left, String right) {
        boolean leftNumeric = isNumeric(left);
        boolean rightNumeric = isNumeric(right);
        if (leftNumeric && rightNumeric) {
            int lengthComparison = Integer.compare(left.length(), right.length());
            return lengthComparison != 0 ? lengthComparison : left.compareTo(right);
        }
        if (leftNumeric != rightNumeric) {
            return leftNumeric ? -1 : 1;
        }
        return left.compareTo(right);
    }

    private static boolean isNumeric(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) < '0' || value.charAt(index) > '9') {
                return false;
            }
        }
        return !value.isEmpty();
    }
}
