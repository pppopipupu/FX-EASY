#!/usr/bin/env bash
set -euo pipefail

usage() {
    cat <<'EOF'
Usage: bash scripts/client-e2e.sh [hmc-test-file.json]

Builds the mod, installs a production NeoForge client with HeadlessMc into
run/e2e and launches it with mc-runtime-test and hmc-specifics.

Without a test file: mc-runtime-test joins a fresh world (a world join smoke
test for the shipped jar) and runs /test runall. On 26.1.x it discovers tests
near the player only, so a fresh world contains none and MCRT_MIN_TESTS has to
stay 0 unless the world has test blocks in it.

With a test file: HeadlessMc drives the client through hmc-specifics commands
(SEND) and asserts on the game output (CONTAINS, ENDS_WITH, REGEX); the exit
code follows SUCCESS/FAIL. MCRT skips the gametests so the test file owns the run.
A test file cannot drive the FX terminal: every harness command needs the client
thread, which mc-runtime-test blocks while any screen is open, and the game is
stopped before the panel is closed. The terminal is therefore opened, walked and
clicked by the client itself (see MCRT_TERMINAL_TICKS and MCRT_CLICKS) and
asserted through the log lines it prints, whether or not a test file is used.

Environment:
  MCRT_MIN_TESTS   gametests mc-runtime-test must find (default 0)
  MCRT_TERMINAL_TICKS
                   client ticks the FX terminal opens itself for (default 200, 0 disables it); when it is
                   not 0 the run fails unless the client log shows the terminal opened, fed and closed
  MCRT_CLICKS      cells the run clicks while the terminal is open, as `tick:target` entries separated by
                   `;` (empty disables the walk). A target names a cell instead of a pixel, so the run does
                   not depend on the window size; the walk asserts the pages, the three overlays and the
                   confirmation cards, and that no element was drawn outside the screen
  MCRT_TYPES       characters the run types while the terminal is open, as `tick:text` entries separated by
                   `;` (empty types nothing). The harness cannot send a key to the window, so a step goes
                   through the handler a real key takes
  MCRT_REGISTER    tick the run registers the account at by itself, or -1 (the default) to leave the
                   registration to the walk, which is what covers the boxes of the account opening
  MCRT_RENDER      set to 1 to render instead of launching with -lwjgl; the panel then has to log its layout
                   and a frame line, which wants a real GL context
  MCRT_JAVA_HOME   JDK 25+ used by HeadlessMc and the game (default JAVA_HOME)
  MCRT_MC_DIR      Minecraft installation directory (default %USERPROFILE%/.minecraft)
  HMC_VERSION      HeadlessMc release (default 3.0.0-RC4)
  MRT_VERSION      mc-runtime-test release (default 4.5.2)
  SPECIFICS_TAG    hmc-specifics release tag (default <minecraft_version>-latest)
EOF
}

if [[ ${1:-} == -h || ${1:-} == --help ]]; then
    usage
    exit 0
fi

ROOT=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)

prop() {
    local key=$1 line
    while IFS= read -r line; do
        if [[ $line == "$key="* ]]; then
            printf '%s' "${line#*=}"
            return 0
        fi
    done <"$ROOT/gradle.properties"
    printf 'missing %s in gradle.properties\n' "$key" >&2
    return 1
}

native_path() {
    if command -v cygpath >/dev/null 2>&1; then
        cygpath -m -- "$1"
    else
        printf '%s' "$1"
    fi
}

MC_VERSION=$(prop minecraft_version)
MOD_ID=$(prop mod_id)
MOD_VERSION=$(prop mod_version)

HMC_VERSION=${HMC_VERSION:-3.0.0-RC4}
MRT_VERSION=${MRT_VERSION:-4.5.2}
SPECIFICS_TAG=${SPECIFICS_TAG:-$MC_VERSION-latest}
MCRT_MIN_TESTS=${MCRT_MIN_TESTS:-0}
MCRT_TERMINAL_TICKS=${MCRT_TERMINAL_TICKS:-240}
MCRT_RENDER=${MCRT_RENDER:-0}
MCRT_MC_DIR=${MCRT_MC_DIR:-${USERPROFILE:-$HOME}/.minecraft}
TEST_FILE=${1:-${MCRT_TEST_FILE:-}}

if [[ -n $TEST_FILE && ! -r $TEST_FILE ]]; then
    printf 'test file %s does not exist\n' "$TEST_FILE" >&2
    exit 1
fi

JAVA_HOME=${MCRT_JAVA_HOME:-${JAVA_HOME:-}}
if [[ -z $JAVA_HOME || ! -x $JAVA_HOME/bin/java ]]; then
    printf 'JAVA_HOME or MCRT_JAVA_HOME must point to a JDK 25 installation\n' >&2
    exit 1
fi
JAVA=$JAVA_HOME/bin/java

JAVA_MAJOR=$("$JAVA" -version 2>&1)
JAVA_MAJOR=${JAVA_MAJOR#*version \"}
JAVA_MAJOR=${JAVA_MAJOR%%.*}
if ((JAVA_MAJOR < 25)); then
    printf 'JDK 25 or newer required, %s is Java %s\n' "$JAVA" "$JAVA_MAJOR" >&2
    exit 1
fi

E2E_DIR=$ROOT/run/e2e
GAME_DIR=$E2E_DIR/game
MODS_DIR=$GAME_DIR/mods
HMC_JAR=$E2E_DIR/headlessmc.jar

fetch() {
    local url=$1 target=$2
    if [[ -s $target ]]; then
        return 0
    fi
    printf 'download %s\n' "$url"
    curl -fL --retry 3 --create-dirs -o "$target" "$url"
}

mkdir -p "$MODS_DIR" "$GAME_DIR"

GITHUB_RELEASES=https://github.com
fetch "$GITHUB_RELEASES/headlesshq/headlessmc/releases/download/$HMC_VERSION/headlessmc.jar" "$HMC_JAR"
fetch "$GITHUB_RELEASES/headlesshq/mc-runtime-test/releases/download/$MRT_VERSION/mc-runtime-test-$MC_VERSION-$MRT_VERSION-neoforge-release.jar" \
    "$MODS_DIR/mc-runtime-test-$MC_VERSION-$MRT_VERSION-neoforge-release.jar"
fetch "$GITHUB_RELEASES/headlesshq/hmc-specifics/releases/download/$SPECIFICS_TAG/hmc-specifics-$MC_VERSION-neoforge-latest.jar" \
    "$MODS_DIR/hmc-specifics-$MC_VERSION-neoforge-latest.jar"

"$ROOT/gradlew" --console=plain --quiet jar
cp -f "$ROOT/build/libs/$MOD_ID-$MOD_VERSION.jar" "$MODS_DIR/"

printf 'onboardAccessibility:false\npauseOnLostFocus:false\n' >"$GAME_DIR/options.txt"

HMC_OPTIONS=(
    "-Dhmc.files.mc=$(native_path "$MCRT_MC_DIR")"
    "-Dhmc.files.game=$(native_path "$GAME_DIR")"
    "-Dhmc.files.game-for-each-version=false"
    "-Dhmc.java.versions=$(native_path "$JAVA_HOME")"
    "-Dhmc.assets.dummy=true"
    "-Dhmc.jline.enabled=false"
    "-Dhmc.xvfb.check=false"
)
# The worlds are survival without cheats, so the harness cannot hand the phone to the player: the client
# opens the terminal itself for MCRT_TERMINAL_TICKS ticks (240 = 12 s, 0 disables it), walks the pages, the
# overlays and the confirmation cards and closes it again. The walk registers the account through the opening
# itself - two clicks to the registration, a name and an age typed into its two boxes and the button that
# files it - because that is the only run that covers those boxes: every click before the registration is
# filed goes to the opening, and the card that hands the welcome capital over is already up behind it, which
# is why it must not take the keys meant for the boxes. The walkthrough that follows is marked as seen by the
# run itself (tick 30), and the welcome capital card is put away before the panel behind it is walked. The
# chart is walked in both styles: the run switches the line on and clicks the plot, which is the only way a run
# with no cursor reaches the readout of the point under the pointer, and the bounds the panel audits are what
# keeps that readout on screen.
# The ticket is walked by handing the order its margin currency - the currency the welcome capital was paid in -
# and dragging the one slider that sets the size: the lots it reaches depend on the free margin, the leverage
# and the live price, so the script asserts that the click landed and that the size moved, not a number. The
# withdrawal is the money the run moves: it is the one of the two operations that needs no items in the
# player's inventory, and the walk cancels it once and then confirms it, so both halves of the card are
# covered. A deposit needs items in the player's inventory, which the harness cannot give a survival player,
# so the platform's deposit rule is covered by the unit tests of TradeEngine.deposit; the panel's own guard
# is not covered by any automated run. The shop is walked to its own limit: the page is opened, the renewal and
# the lounge of a plan the account does not hold are clicked and change nothing, the card of a tier opens its
# checkout, the checkout is cancelled, and it is opened once more and its button pressed - which goes through in
# the currency the checkout showed when the account holds enough of it and is refused when it does not, so the
# walk ends there rather than clicking anything the checkout could be standing in front of. A price of 888 items
# is fourteen stacks, which a survival inventory holds, so that last click may buy the plan rather than be
# refused; the assertions below accept both, and the panel never sends a request the platform would refuse. What
# the checkout charges, what a period buys and what the lounge delivers are covered by the unit
# tests of ShopEngine and FxCheckout.
MCRT_REGISTER=${MCRT_REGISTER--1}
MCRT_TYPES=${MCRT_TYPES-"25:Steve;28:30"}
MCRT_CLICKS=${MCRT_CLICKS-"20:opening.next;22:opening.next;24:opening.name;27:opening.age;30:opening.register;40:bonus.ok;41:bonus.ok;42:bonus.close;45:tab.TRADE;46:chart.LINE;48:chart.plot;50:trade.margin.diamond;55:trade.lots;60:tab.ACCOUNT;65:account.currency.diamond;75:account.withdraw;85:confirm.cancel;95:account.withdraw;105:confirm.ok;110:basis.next;112:timeframe.H1;170:tab.POSITIONS;185:intel;195:intel.view.EVENTS;205:intel.view.ATTRIBUTION;215:intel.close;220:tab.SHOP;222:shop.renew;224:shop.lounge;226:shop.buy.go;230:checkout.cancel;233:shop.buy.go;237:checkout.pay"}
GAME_OPTIONS=(-Djava.awt.headless=true "-Dfxeasy.e2e.openTerminal=$MCRT_TERMINAL_TICKS" "-Dfxeasy.e2e.clicks=$MCRT_CLICKS" "-Dfxeasy.e2e.types=$MCRT_TYPES" "-Dfxeasy.e2e.register=$MCRT_REGISTER")
# The panel lays itself out from the framebuffer, and the stub defaults to 800x600: there the side column is
# two design units too short for the full ticket and shows four of the five currency rows, while the walk below
# aims at the margin currency cells and the diamond row, which need a design height of 350. The stub is the
# client JVM, so its screen size is a game property. A real window (MCRT_RENDER=1) keeps its own size and the
# script only asserts the cells that size can hold.
if [[ $MCRT_RENDER != 1 ]]; then
    GAME_OPTIONS+=("-Dhmc.lwjgl.screenwidth=1280" "-Dhmc.lwjgl.screenheight=720")
fi
LAUNCH_OPTIONS=()
if [[ -n $TEST_FILE ]]; then
    TEST_FILE=$(cd -- "$(dirname -- "$TEST_FILE")" && pwd)/$(basename -- "$TEST_FILE")
    HMC_OPTIONS+=("-Dhmc.test.file=$(native_path "$TEST_FILE")")
    GAME_OPTIONS+=(-DMcRuntimeGameTest=false)
else
    GAME_OPTIONS+=("-DMcRuntimeGameTestMinExpectedGameTests=$MCRT_MIN_TESTS")
fi
if [[ $MCRT_RENDER != 1 ]]; then
    LAUNCH_OPTIONS+=(-lwjgl)
fi

"$JAVA" "${HMC_OPTIONS[@]}" -jar "$HMC_JAR" version install neoforge "$MC_VERSION"

printf 'mods %s\ngame %s\n' "$(native_path "$MODS_DIR")" "$(native_path "$GAME_DIR")"
STATUS=0
"$JAVA" "${HMC_OPTIONS[@]}" -jar "$HMC_JAR" launch "${LAUNCH_OPTIONS[@]}" --offline --jvm "${GAME_OPTIONS[*]}" neoforge "$MC_VERSION" || STATUS=$?

# A test file cannot drive the terminal: every harness command needs the client thread, which the runtime
# test blocks while any screen is open, and the game is gone before the panel is. The client log is
# therefore the place where the panel is asserted, and the panel logs its lifecycle for exactly this.
if (( MCRT_TERMINAL_TICKS > 0 )); then
    GAME_LOG=$GAME_DIR/logs/latest.log
    REQUIRED=("FX terminal mirror reset for a new world" "FX terminal opened" "FX terminal layout:"
        "FX terminal history:" "FX terminal economy:" "FX terminal events:" "FX terminal lounge pool: "
        "panel bounds clean" "FX terminal closed")
    if [[ -n $MCRT_CLICKS ]]; then
        REQUIRED+=("FX terminal account opening, 3 steps ending in the registration" "FX terminal test type Steve"
            "FX terminal test type 30" "FX terminal account registered as Steve (age 30)" "FX account registered by "
            "FX terminal bonus shown" "FX bonus layout:" "FX terminal test click on bonus.ok"
            "FX terminal bonus claimed" "FX welcome capital of 10 × DIA paid to" "FX terminal test click on bonus.ok found no cell"
            "FX terminal test click on bonus.close"
            "FX terminal bonus dismissed"
            "FX terminal test click on tab.ACCOUNT" "FX terminal notice: "
            "FX notice fxeasy.notice.bonus" "FX terminal intel opened" "FX intel layout:"
            "FX terminal test click on chart.LINE at " "FX terminal test click on chart.plot at "
            "FX terminal test click on timeframe.H1 at "
            "FX terminal test click on tab.SHOP at " "FX shop layout:"
            "FX terminal test click on shop.renew at " "FX terminal test click on shop.lounge at "
            "FX terminal test click on shop.buy.go at " "FX checkout layout:"
            "FX terminal test click on checkout.cancel at " "FX terminal checkout closed"
            "FX terminal test click on checkout.pay at ")
    fi
    DESIGN_HEIGHT=$(sed -n 's/.*FX terminal layout: page [A-Z]*, design [0-9]*x\([0-9]*\),.*/\1/p' "$GAME_LOG" | tail -1)
    if (( ${DESIGN_HEIGHT:-0} >= 350 )); then
        REQUIRED+=("FX terminal test click on account.currency.diamond" "FX terminal test click on trade.margin.diamond"
            "FX terminal test click on trade.lots" "FX terminal size "
            "FX terminal test click on basis.next" "FX terminal basis "
            "FX account slider: DIA carries 10, free 0, range 10, amount 10, deposit true, withdraw false"
            "FX terminal confirm: withdraw open" "FX terminal confirm: withdraw cancelled"
            "FX terminal confirm: withdraw confirmed" "FX notice fxeasy.notice.withdraw")
    fi
    if [[ $MCRT_RENDER == 1 ]]; then
        REQUIRED+=("FX terminal frame 60:")
    fi
    for needle in "${REQUIRED[@]}"; do
        if ! grep -qF -- "$needle" "$GAME_LOG"; then
            printf 'missing in %s: %s\n' "$(native_path "$GAME_LOG")" "$needle" >&2
            STATUS=1
        fi
    done
    # The shop cells the walk clicks are the ones the panel refuses to send: an account with no subscription and
    # no balance to buy one must never see the platform answer a request it made. The line is logged with the key
    # of the refusal, so the walk proves the guard and not only that the page drew.
    if [[ -n $MCRT_CLICKS ]] && grep -qF "FX notice fxeasy.reject.shop" "$GAME_LOG"; then
        printf 'the walk asked the shop for something it refuses: %s\n' \
            "$(grep -F 'FX notice fxeasy.reject.shop' "$GAME_LOG" | head -1)" >&2
        STATUS=1
    fi
    # The checkout quotes a payment in one currency and the platform charges its own price in the same one, so a
    # walk whose button went through has to show the receipt and the charge agreeing - and a walk whose account
    # could not cover the total has to show no charge at all, which is what the currency of the quote catches: a
    # receipt that offered nothing can never be followed by a subscription.
    if [[ -n $MCRT_CLICKS ]]; then
        SHOWN=$(sed -n 's/.*FX checkout layout: .*, paying \([^,]*\),.*/\1/p' "$GAME_LOG" | tail -1)
        CHARGED=$(sed -n 's/.*FX subscription .* paid [^ ]* · [^ ]* × \([^ ]*\)$/\1/p' "$GAME_LOG" | tail -1)
        if [[ -n $CHARGED && $CHARGED != "$SHOWN" ]]; then
            printf 'the platform charged %s while the checkout showed %s\n' "$CHARGED" "${SHOWN:-nothing}" >&2
            STATUS=1
        fi
        if [[ -n $CHARGED ]] && ! grep -qF "FX subscription go bought" "$GAME_LOG"; then
            printf 'the walk bought a tier it never opened the checkout of: %s\n' \
                "$(grep -F "FX subscription " "$GAME_LOG" | head -1)" >&2
            STATUS=1
        fi
    fi
fi
exit "$STATUS"
