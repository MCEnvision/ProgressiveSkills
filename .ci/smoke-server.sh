#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."

timeout_seconds="${PS_SMOKE_TIMEOUT_SECONDS:-180}"
server_port="${PS_SMOKE_SERVER_PORT:-0}"
game_dir="run/server"
mods_dir="$game_dir/mods"
game_log="$game_dir/logs/latest.log"
console_log="build/reports/server-smoke-console.log"

mkdir -p "$game_dir" "$mods_dir" "build/reports"
if find "$mods_dir" -mindepth 1 -maxdepth 1 -print -quit | grep -q .; then
    printf 'Dedicated-server smoke requires an empty %s directory.\n' "$mods_dir" >&2
    exit 1
fi
printf 'eula=true\n' > "$game_dir/eula.txt"
printf 'server-port=%s\nonline-mode=false\nmotd=ProgressiveSkills smoke\n' \
    "$server_port" > "$game_dir/server.properties"
rm -f "$game_log" "$console_log"

setsid ./gradlew --no-daemon --stacktrace runServer > "$console_log" 2>&1 &
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
        && grep -Fq 'ProgressiveSkills common bootstrap ready' "$game_log" \
        && grep -Fq 'Done (' "$game_log"; then
        if grep -Eiq '/(ERROR|FATAL)\]|exception|crash report' "$game_log"; then
            printf 'Dedicated-server log contains an error, exception, or crash marker.\n' >&2
            tail -n 120 "$game_log" >&2 || true
            exit 1
        fi
        if grep -Eq 'build/(classes/java|resources)/gameTest' "$console_log"; then
            printf 'Dedicated-server smoke loaded GameTest output.\n' >&2
            exit 1
        fi
        stop_process_group
        wait "$process_id" 2>/dev/null || true
        trap - EXIT
        printf 'Dedicated-server smoke passed.\n'
        exit 0
    fi

    if ! kill -0 "$process_id" 2>/dev/null; then
        wait "$process_id" || true
        printf 'Dedicated server exited before reaching ready state.\n' >&2
        tail -n 120 "$console_log" >&2 || true
        exit 1
    fi

    sleep 2
done

printf 'Dedicated server did not reach ready state within %s seconds.\n' "$timeout_seconds" >&2
tail -n 120 "$console_log" >&2 || true
exit 1
