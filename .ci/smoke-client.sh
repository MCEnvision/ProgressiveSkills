#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."

timeout_seconds="${PS_SMOKE_TIMEOUT_SECONDS:-240}"
game_dir="run/client"
game_log="$game_dir/logs/latest.log"
console_log="build/reports/client-smoke-console.log"

if ! command -v xvfb-run >/dev/null 2>&1; then
    printf 'xvfb-run is required for the headless client smoke test.\n' >&2
    exit 1
fi

mkdir -p "$game_dir" "build/reports"
printf 'onboardAccessibility:false\n' > "$game_dir/options.txt"
rm -f "$game_log" "$console_log"

setsid env ALSOFT_DRIVERS=null xvfb-run -a \
    ./gradlew --no-daemon --stacktrace runClient > "$console_log" 2>&1 &
process_id=$!

stop_process_group() {
    if kill -0 "$process_id" 2>/dev/null; then
        kill -- -"$process_id" 2>/dev/null || true
    fi
}
trap stop_process_group EXIT

deadline=$((SECONDS + timeout_seconds))
while (( SECONDS < deadline )); do
    if [[ -f "$game_log" ]] \
        && grep -Fq 'ProgressiveSkills client bootstrap ready' "$game_log" \
        && grep -Fq 'ProgressiveSkills title screen ready' "$game_log"; then
        if grep -Eq 'build/(classes/java|resources)/gameTest' "$console_log"; then
            printf 'Client smoke loaded GameTest output.\n' >&2
            exit 1
        fi
        stop_process_group
        wait "$process_id" 2>/dev/null || true
        trap - EXIT
        printf 'Client title-screen smoke passed.\n'
        exit 0
    fi

    if ! kill -0 "$process_id" 2>/dev/null; then
        wait "$process_id" || true
        printf 'Client exited before reaching the title screen.\n' >&2
        tail -n 120 "$console_log" >&2 || true
        exit 1
    fi

    sleep 2
done

printf 'Client did not reach the title screen within %s seconds.\n' "$timeout_seconds" >&2
tail -n 120 "$console_log" >&2 || true
exit 1
