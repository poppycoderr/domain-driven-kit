#!/usr/bin/env bash
#
# Tests the version selection of scripts/ddk.sh without the network and without building DDK.
#
# A throwaway git repository stands in for the DDK repository (two release tags and a main branch with a snapshot),
# and a stub "mvn" stands in for Maven: "install" records the version as installed artifacts, "archetype:generate"
# writes the version it was asked for into the generated project. Everything lives in a temporary directory; the
# real ~/.ddk and ~/.m2 are never touched.
#
#   scripts/test-ddk.sh

set -euo pipefail

SCRIPT="$(cd "$(dirname "$0")" && pwd)/ddk.sh"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

passed=0
check() {
    local description="$1" expected="$2" actual="$3"
    if [[ "$expected" != "$actual" ]]; then
        printf 'FAIL: %s\n  expected: %s\n  actual:   %s\n' "$description" "$expected" "$actual" >&2
        exit 1
    fi
    passed=$((passed + 1))
    printf 'ok   %s\n' "$description"
}

# --- a stand-in for the DDK repository -------------------------------------------------------------------------
REPO="$WORK/ddk-repo"
mkdir -p "$REPO/ddk-dependencies"
git -C "$REPO" init -q -b main
git -C "$REPO" config user.email test@example.com
git -C "$REPO" config user.name test
write_version() {
    printf '<project>\n    <version>%s</version>\n</project>\n' "$1" > "$REPO/pom.xml"
    printf '<project>\n    <properties>\n        <ddk.version>%s</ddk.version>\n    </properties>\n</project>\n' "$1" \
        > "$REPO/ddk-dependencies/pom.xml"
    git -C "$REPO" add -A
    git -C "$REPO" commit -q -m "version $1"
}
write_version 0.1.0-SNAPSHOT && git -C "$REPO" tag v0.1.0
write_version 0.2.0-SNAPSHOT && git -C "$REPO" tag v0.2.0
write_version 0.3.0-SNAPSHOT

# --- a stand-in for Maven --------------------------------------------------------------------------------------
M2="$WORK/m2"
BIN="$WORK/bin"
mkdir -p "$M2" "$BIN"
cat > "$BIN/mvn" <<'MVN'
#!/usr/bin/env bash
set -euo pipefail
pom="" args=("$@")
for ((i = 0; i < ${#args[@]}; i++)); do
    [[ "${args[$i]}" == "-f" ]] && pom="${args[$((i + 1))]}"
done
version() { sed -n 's:.*<version>\(.*\)</version>.*:\1:p' "$1" | head -n 1; }
case " $* " in
    *"-Dexpression=settings.localRepository"*) printf '%s' "$FAKE_M2" ;;
    *"-Dexpression=project.version"*) printf '%s' "$(version "$pom")" ;;
    *" versions:set "*)
        for arg in "$@"; do [[ "$arg" == -DnewVersion=* ]] && new="${arg#-DnewVersion=}"; done
        sed -i.bak "s|<version>.*</version>|<version>$new</version>|" "$pom" && rm -f "$pom.bak" ;;
    *" install "*)
        v="$(version "$pom")"
        for layers in 3 4; do
            mkdir -p "$FAKE_M2/com/ddk/ddk-layer$layers-archetype/$v"
            touch "$FAKE_M2/com/ddk/ddk-layer$layers-archetype/$v/ddk-layer$layers-archetype-$v.jar"
        done
        echo "$v" >> "$FAKE_M2/installs.log" ;;
    *" archetype:generate "*)
        for arg in "$@"; do
            [[ "$arg" == -DarchetypeVersion=* ]] && v="${arg#-DarchetypeVersion=}"
            [[ "$arg" == -DartifactId=* ]] && a="${arg#-DartifactId=}"
        done
        mkdir -p "$a" && echo "$v" > "$a/ddk-version" ;;
    *) echo "unexpected mvn call: $*" >&2; exit 1 ;;
esac
MVN
chmod +x "$BIN/mvn"

export PATH="$BIN:$PATH" FAKE_M2="$M2" DDK_REPO="file://$REPO" DDK_HOME="$WORK/ddk-home"
cd "$WORK"
installs() { [[ -f "$M2/installs.log" ]] && wc -l < "$M2/installs.log" | tr -d ' ' || echo 0; }
new() { rm -rf "$1"; "$SCRIPT" new "$1" >/dev/null 2>"$WORK/stderr"; cat "$1/ddk-version"; }

# --- scenarios -------------------------------------------------------------------------------------------------
check "an empty environment installs the newest release by default" "0.2.0" "$(new p1)"
check "a second project reuses that install" "0.2.0 1" "$(new p2) $(installs)"
check "an explicit older tag is installed and used, not the cached one" "0.1.0 2" "$(DDK_REF=v0.1.0 new p3) $(installs)"
check "switching back to the newest release reinstalls it" "0.2.0 3" "$(new p4) $(installs)"
check "main gives the development snapshot" "0.3.0-SNAPSHOT 4" "$(DDK_REF=main new p5) $(installs)"
check "main unchanged is reused" "0.3.0-SNAPSHOT 4" "$(DDK_REF=main new p6) $(installs)"

write_version 0.3.0-SNAPSHOT >/dev/null 2>&1 || true
echo change >> "$REPO/README" && git -C "$REPO" add -A && git -C "$REPO" commit -q -m "new commit on main"
check "a new commit on main is picked up" "0.3.0-SNAPSHOT 5" "$(DDK_REF=main new p7) $(installs)"

rm -rf "$M2/com/ddk/ddk-layer4-archetype/0.3.0-SNAPSHOT"
check "missing Maven artifacts are installed again" "0.3.0-SNAPSHOT 6" "$(DDK_REF=main new p8) $(installs)"

mv "$REPO" "$REPO.offline"
check "offline, the installed ref is reused" "0.3.0-SNAPSHOT 6" "$(DDK_REF=main new p9) $(installs)"
grep -q "Cannot reach" "$WORK/stderr" && warned=yes || warned=no
check "offline reuse is announced" "yes" "$warned"
if DDK_REF=v0.1.0 "$SCRIPT" new p10 >/dev/null 2>"$WORK/stderr"; then failed=no; else failed=yes; fi
check "offline, a ref that was never installed fails" "yes" "$failed"
grep -q "is not installed" "$WORK/stderr" && explained=yes || explained=no
check "the failure says why" "yes" "$explained"
mv "$REPO.offline" "$REPO"

echo "All $passed checks passed"
