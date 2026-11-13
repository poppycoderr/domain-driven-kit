#!/usr/bin/env bash
#
# Tests the version selection of scripts/ddk.sh without the network and without building DDK.
#
# A throwaway git repository stands in for the DDK repository (two release tags under the old groupId com.ddk and a
# main branch with a snapshot under com.domaindrivenkit; later a release tag that is on Maven Central),
# and a stub "mvn" stands in for Maven: "install" records the version as installed artifacts, "dependency:get" records
# a download from Maven Central, "archetype:generate" writes the version it was asked for into the generated project.
# A stub "java" reports the JDK version in FAKE_JAVA. Everything lives in a temporary directory; the real ~/.ddk and
# ~/.m2 are never touched.
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
    local group="${2:-com.domaindrivenkit}"
    printf '<project>\n    <groupId>%s</groupId>\n    <version>%s</version>\n</project>\n' "$group" "$1" > "$REPO/pom.xml"
    printf '<project>\n    <properties>\n        <ddk.version>%s</ddk.version>\n    </properties>\n</project>\n' "$1" \
        > "$REPO/ddk-dependencies/pom.xml"
    git -C "$REPO" add -A
    git -C "$REPO" commit -q -m "version $1"
}
write_version 0.1.0-SNAPSHOT com.ddk && git -C "$REPO" tag v0.1.0
write_version 0.2.0-SNAPSHOT com.ddk && git -C "$REPO" tag v0.2.0
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
group() { sed -n 's:.*<groupId>\(.*\)</groupId>.*:\1:p' "$1" | head -n 1; }
case " $* " in
    *"-Dexpression=settings.localRepository"*) printf '%s' "$FAKE_M2" ;;
    *"-Dexpression=project.version"*) printf '%s' "$(version "$pom")" ;;
    *"-Dexpression=project.groupId"*) printf '%s' "$(group "$pom")" ;;
    *" versions:set "*)
        for arg in "$@"; do [[ "$arg" == -DnewVersion=* ]] && new="${arg#-DnewVersion=}"; done
        sed -i.bak "s|<version>.*</version>|<version>$new</version>|" "$pom" && rm -f "$pom.bak" ;;
    *" install "*)
        v="$(version "$pom")" g="$(group "$pom")"
        for layers in 3 4; do
            mkdir -p "$FAKE_M2/${g//.//}/ddk-layer$layers-archetype/$v"
            touch "$FAKE_M2/${g//.//}/ddk-layer$layers-archetype/$v/ddk-layer$layers-archetype-$v.jar"
        done
        echo "$v" >> "$FAKE_M2/installs.log" ;;
    *" dependency:get "*)
        for arg in "$@"; do [[ "$arg" == -Dartifact=* ]] && coordinates="${arg#-Dartifact=}"; done
        IFS=: read -r g a v <<< "$coordinates"
        mkdir -p "$FAKE_M2/${g//.//}/$a/$v" && touch "$FAKE_M2/${g//.//}/$a/$v/$a-$v.jar"
        echo "$a:$v" >> "$FAKE_M2/downloads.log" ;;
    *" archetype:generate "*)
        for arg in "$@"; do
            [[ "$arg" == -DarchetypeVersion=* ]] && v="${arg#-DarchetypeVersion=}"
            [[ "$arg" == -DarchetypeGroupId=* ]] && g="${arg#-DarchetypeGroupId=}"
            [[ "$arg" == -DartifactId=* ]] && a="${arg#-DartifactId=}"
        done
        mkdir -p "$a" && echo "$v" > "$a/ddk-version" && echo "$g" > "$a/ddk-group" ;;
    *) echo "unexpected mvn call: $*" >&2; exit 1 ;;
esac
MVN
chmod +x "$BIN/mvn"
cat > "$BIN/java" <<'JAVA'
#!/usr/bin/env bash
printf 'openjdk version "%s" 2025-10-21 LTS\n' "${FAKE_JAVA:-21.0.9}" >&2
JAVA
chmod +x "$BIN/java"

export PATH="$BIN:$PATH" FAKE_M2="$M2" DDK_REPO="file://$REPO" DDK_HOME="$WORK/ddk-home"
cd "$WORK"
installs() { [[ -f "$M2/installs.log" ]] && wc -l < "$M2/installs.log" | tr -d ' ' || echo 0; }
downloads() { [[ -f "$M2/downloads.log" ]] && wc -l < "$M2/downloads.log" | tr -d ' ' || echo 0; }
new() { rm -rf "$1"; "$SCRIPT" new "$1" >/dev/null 2>"$WORK/stderr"; cat "$1/ddk-version"; }

# --- scenarios -------------------------------------------------------------------------------------------------
check "an empty environment installs the newest release by default" "0.2.0" "$(new p1)"
check "a second project reuses that install" "0.2.0 1" "$(new p2) $(installs)"
check "an explicit older tag is installed and used, not the cached one" "0.1.0 2" "$(DDK_REF=v0.1.0 new p3) $(installs)"
check "switching back to the newest release reinstalls it" "0.2.0 3" "$(new p4) $(installs)"
check "main gives the development snapshot" "0.3.0-SNAPSHOT 4" "$(DDK_REF=main new p5) $(installs)"
check "main unchanged is reused" "0.3.0-SNAPSHOT 4" "$(DDK_REF=main new p6) $(installs)"
check "a release before 0.10 is generated from its own groupId" "com.ddk" "$(cat p3/ddk-group)"
check "main is generated from the new groupId" "com.domaindrivenkit" "$(cat p6/ddk-group)"

write_version 0.3.0-SNAPSHOT >/dev/null 2>&1 || true
echo change >> "$REPO/README" && git -C "$REPO" add -A && git -C "$REPO" commit -q -m "new commit on main"
check "a new commit on main is picked up" "0.3.0-SNAPSHOT 5" "$(DDK_REF=main new p7) $(installs)"

rm -rf "$M2/com/domaindrivenkit/ddk-layer4-archetype/0.3.0-SNAPSHOT"
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

echo change >> "$REPO/README" && git -C "$REPO" add -A && git -C "$REPO" commit -q -m "another commit on main"
if FAKE_JAVA=21.0.5 DDK_REF=main "$SCRIPT" new p11 >/dev/null 2>"$WORK/stderr"; then built=yes; else built=no; fi
check "building from source on JDK 21.0.5 is refused" "no 6" "$built $(installs)"
grep -q "needs JDK 21.0.8 or newer" "$WORK/stderr" && explained=yes || explained=no
check "the refusal names the JDK it needs" "yes" "$explained"
check "a release before 0.10 still builds on JDK 21.0.5" "0.1.0 7" "$(FAKE_JAVA=21.0.5 DDK_REF=v0.1.0 new p12) $(installs)"
check "JDK 25 builds from source" "0.3.0-SNAPSHOT 8" "$(FAKE_JAVA=25.0.1 DDK_REF=main new p13) $(installs)"

write_version 0.10.0-SNAPSHOT && git -C "$REPO" tag v0.10.0
check "the newest release on Maven Central is downloaded, not built" "0.10.0 8 2" "$(FAKE_JAVA=21.0.5 new p14) $(installs) $(downloads)"
check "it is generated from com.domaindrivenkit" "com.domaindrivenkit" "$(cat p14/ddk-group)"
check "a second project reuses the download" "0.10.0 8 2" "$(new p15) $(installs) $(downloads)"
check "an empty home gets a release from Maven Central" "0.10.0 8 4" "$(DDK_HOME="$WORK/fresh-home" new p16) $(installs) $(downloads)"

echo "All $passed checks passed"
