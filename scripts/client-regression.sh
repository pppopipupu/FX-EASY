#!/usr/bin/env bash
set -eu

usage() {
    cat <<'EOF'
Usage: bash scripts/client-regression.sh

Runs the full chain regression of the FX terminal inside a development client
(`gradlew runClientRegression`) and reports the result.

The run is one client launch: it resizes the window, hands the player a phone
through the server, opens the panel through the item, walks every page, every
overlay and every path that moves money - the account opening with its two
boxes, the first run walkthrough, the welcome capital, the market list, the
chart settings, the ticket, two opens, a single close, a close all, a deposit
and a withdrawal - and asserts the client mirror and the cells of the panel
after each step. The client prints one line per step and the summary line
`FX regression: <n> steps, <m> failed, <t> ticks`, then stops itself.

The opening, the walkthrough and the welcome capital happen once per account,
so every run uses a fresh player name in the same world and a run that finds
the opening already gone fails instead of quietly testing less.

Environment:
  REGRESSION_WORLD   world the run joins (default probe, which has to exist in run/saves)
  REGRESSION_USER    player name the run uses (default Regress<HHMMSS>); it has to be one the world has
                     never seen, so a fixed name only works once
  REGRESSION_GRADLE  gradle wrapper command (default ./gradlew)

The client is headed: it needs a window and a real GL context, so run it on a
machine with a display.
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
    usage
    exit 0
fi

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

WORLD="${REGRESSION_WORLD:-probe}"
USER_NAME="${REGRESSION_USER:-Regress$(date +%H%M%S)}"
GRADLE="${REGRESSION_GRADLE:-./gradlew}"

if [[ -z "${JAVA_HOME:-}" ]]; then
    for candidate in "/c/Program Files/Java"/jdk-25*; do
        if [[ -x "$candidate/bin/java" ]]; then
            JAVA_HOME="$candidate"
            break
        fi
    done
fi
if [[ -z "${JAVA_HOME:-}" ]]; then
    echo "JAVA_HOME is not set and no JDK 25 was found; set JAVA_HOME to a JDK 25" >&2
    exit 1
fi
export JAVA_HOME

if [[ ! -d "run/saves/$WORLD" ]]; then
    echo "no world run/saves/$WORLD; create one with the probe client first" >&2
    exit 1
fi

LOG="run/logs/latest.log"

# A client that is still closing keeps the world's session lock, and a second client that starts on top of it
# fails to join and sits on the title screen. Windows refuses to rename a locked file, so waiting until the
# lock can be renamed is waiting until the previous client has let go of the world.
LOCK="run/saves/$WORLD/session.lock"
for _ in $(seq 1 60); do
    if [[ ! -f "$LOCK" ]] || mv "$LOCK" "$LOCK.free" 2>/dev/null; then
        [[ -f "$LOCK.free" ]] && mv "$LOCK.free" "$LOCK"
        break
    fi
    sleep 1
done
if [[ -f "$LOCK.free" ]]; then
    echo "the world $WORLD is still locked by another client; close it and run again" >&2
    exit 1
fi

if [[ -f "$LOG" ]]; then
    mv "$LOG" "$LOG.previous"
fi

echo "running the FX terminal regression as $USER_NAME in $WORLD"
set +e
"$GRADLE" runClientRegression -PregressionWorld="$WORLD" -PregressionUser="$USER_NAME"
STATUS=$?
set -e

if [[ ! -f "$LOG" ]]; then
    echo "the client wrote no log at $LOG (exit $STATUS)" >&2
    exit 1
fi

SUMMARY="$(grep -c 'FX regression:' "$LOG" || true)"
if [[ "$SUMMARY" == "0" ]]; then
    echo "the run never finished: no summary line in $LOG (exit $STATUS)" >&2
    grep -E 'FX regression|FX terminal' "$LOG" | tail -20 >&2 || true
    exit 1
fi

grep -E 'FX regression (FAIL|ok|resized|ran)' "$LOG" || true
grep -E 'FX regression:' "$LOG" | tail -1

FAILED="$(grep -c 'FX regression FAIL' "$LOG" || true)"
if [[ "$FAILED" != "0" ]]; then
    exit 1
fi
if [[ "$STATUS" != "0" ]]; then
    echo "the client exited with $STATUS" >&2
    exit 1
fi
