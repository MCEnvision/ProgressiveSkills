#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."

jar_file="${1:-}"
if [[ -z "$jar_file" ]]; then
    jar_file="$(find build/libs -maxdepth 1 -type f \
        -name 'progressiveskills-*.jar' \
        ! -name '*-sources.jar' \
        ! -name '*-javadoc.jar' \
        -print -quit)"
fi

if [[ -z "$jar_file" || ! -f "$jar_file" ]]; then
    printf 'ProgressiveSkills release JAR was not found.\n' >&2
    exit 1
fi

jar_entries="$(mktemp)"
test_entries="$(mktemp)"
leaked_entries="$(mktemp)"
archive_contents="$(mktemp)"
metadata="$(mktemp)"
trap 'rm -f "$jar_entries" "$test_entries" "$leaked_entries" "$archive_contents" "$metadata"' EXIT

jar tf "$jar_file" | sort -u > "$jar_entries"
: > "$test_entries"

for output_root in \
    build/classes/java/test \
    build/classes/java/gameTest \
    build/resources/test \
    build/resources/gameTest
do
    if [[ -d "$output_root" ]]; then
        find "$output_root" -type f -printf '%P\n' >> "$test_entries"
    fi
done

sort -u -o "$test_entries" "$test_entries"
comm -12 "$jar_entries" "$test_entries" > "$leaked_entries"
if [[ -s "$leaked_entries" ]]; then
    printf 'Release JAR contains compiled test output:\n' >&2
    sed 's/^/  /' "$leaked_entries" >&2
    exit 1
fi

if grep -Eq '^(org/junit|org/junitplatform|net/jqwik|com/tngtech/archunit)/' "$jar_entries"; then
    printf 'Release JAR contains a test-library package.\n' >&2
    exit 1
fi

unzip -p "$jar_file" > "$archive_contents"
if grep -aEiq 'unrealskills|com[./]enviouse' "$archive_contents"; then
    printf 'Release JAR contains a retired project identity.\n' >&2
    exit 1
fi

unzip -p "$jar_file" META-INF/neoforge.mods.toml > "$metadata"
grep -Fq 'modId = "progressiveskills"' "$metadata"
grep -Fq 'displayName = "ProgressiveSkills"' "$metadata"
grep -Fq 'versionRange = "[21.1.236,21.2)"' "$metadata"
grep -Fq 'versionRange = "[1.21.1]"' "$metadata"

sha256sum "$jar_file"
printf 'Release JAR verification passed.\n'
