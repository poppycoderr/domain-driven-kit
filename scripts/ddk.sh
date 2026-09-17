#!/usr/bin/env bash
#
# Installs Domain Driven Kit into the local Maven repository and generates projects from its archetypes.
# DDK is not published to Maven Central yet, so every project needs the artifacts installed locally first.
#
#   curl -fsSL https://raw.githubusercontent.com/poppycoderr/domain-driven-kit/main/scripts/ddk.sh | bash -s -- new order-service
#
set -euo pipefail

DDK_REPO="${DDK_REPO:-https://github.com/poppycoderr/domain-driven-kit.git}"
DDK_REF="${DDK_REF:-main}"
DDK_HOME="${DDK_HOME:-$HOME/.ddk}"
SRC_DIR="$DDK_HOME/src"

info() { printf '\033[36m==>\033[0m %s\n' "$*"; }
fail() { printf '\033[31merror:\033[0m %s\n' "$*" >&2; exit 1; }

usage() {
    cat <<'EOF'
Usage: ddk.sh <command> [options]

Commands:
  install                   Build DDK and install it into the local Maven repository
  new <artifactId>          Generate a project (installs DDK first when needed)
  help                      Show this help

Options for "new":
  --group <groupId>         Project groupId (default: com.example)
  --package <package>       Root package (default: <groupId>.<artifactId without dashes>)
  --layers <3|4>            Three- or four-layer architecture (default: 4)

Environment:
  DDK_REF    Branch or tag to build (default: main), e.g. DDK_REF=v0.2.0
  DDK_REPO   Git repository to clone
  DDK_HOME   Working directory for the source checkout (default: ~/.ddk)
EOF
}

require() {
    command -v "$1" >/dev/null 2>&1 || fail "$1 is required but was not found on PATH"
}

check_java() {
    require java
    local version
    version=$(java -version 2>&1 | awk -F'"' '/version/ {print $2}' | cut -d. -f1)
    [[ "$version" =~ ^[0-9]+$ && "$version" -ge 21 ]] || fail "JDK 21 or newer is required (found: ${version:-unknown})"
}

ddk_version() {
    mvn -f "$SRC_DIR/pom.xml" -q -N help:evaluate -Dexpression=project.version -DforceStdout
}

install_ddk() {
    require git
    require mvn
    check_java

    if [[ -d "$SRC_DIR/.git" ]]; then
        info "Updating DDK source ($DDK_REF)"
        git -C "$SRC_DIR" fetch -q --depth 1 origin "$DDK_REF"
        git -C "$SRC_DIR" checkout -q --force --detach FETCH_HEAD
    else
        info "Cloning DDK ($DDK_REF)"
        mkdir -p "$DDK_HOME"
        git clone -q --depth 1 --branch "$DDK_REF" "$DDK_REPO" "$SRC_DIR"
    fi

    # 仓库里始终是 SNAPSHOT 版本，发布版本号只在发布流程中按 tag 改写；按 tag 构建时同样改写，装出来的才是 X.Y.Z
    if [[ "$DDK_REF" =~ ^v([0-9]+\.[0-9]+\.[0-9]+)$ ]]; then
        local release="${BASH_REMATCH[1]}"
        mvn -f "$SRC_DIR/pom.xml" -B -ntp -q versions:set -DnewVersion="$release" -DprocessAllModules=true -DgenerateBackupPoms=false
        sed -i.bak "s|<ddk.version>.*</ddk.version>|<ddk.version>$release</ddk.version>|" "$SRC_DIR/ddk-dependencies/pom.xml"
        rm -f "$SRC_DIR/ddk-dependencies/pom.xml.bak"
    fi

    info "Installing DDK $(ddk_version) into the local Maven repository"
    mvn -f "$SRC_DIR/pom.xml" -B -ntp -q install \
        -Dmaven.test.skip=true -Djacoco.skip=true -Dspotless.check.skip=true
    info "Installed"
}

new_project() {
    local artifact_id="${1:-}"
    [[ -n "$artifact_id" && "$artifact_id" != --* ]] || fail "usage: ddk.sh new <artifactId> [--group g] [--package p] [--layers 3|4]"
    shift

    local group_id="com.example" package="" layers="4"
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --group) group_id="${2:?--group needs a value}"; shift 2 ;;
            --package) package="${2:?--package needs a value}"; shift 2 ;;
            --layers) layers="${2:?--layers needs a value}"; shift 2 ;;
            *) fail "unknown option: $1" ;;
        esac
    done
    [[ "$layers" == "3" || "$layers" == "4" ]] || fail "--layers must be 3 or 4"
    [[ -e "$artifact_id" ]] && fail "./$artifact_id already exists"
    [[ -n "$package" ]] || package="$group_id.${artifact_id//-/}"

    [[ -d "$SRC_DIR/.git" ]] || install_ddk
    local version
    version=$(ddk_version)

    info "Generating $artifact_id ($layers layers, package $package) with DDK $version"
    mvn -B -ntp -q archetype:generate \
        -DarchetypeGroupId=com.ddk \
        -DarchetypeArtifactId="ddk-layer${layers}-archetype" \
        -DarchetypeVersion="$version" \
        -DarchetypeCatalog=local \
        -DgroupId="$group_id" \
        -DartifactId="$artifact_id" \
        -Dpackage="$package" \
        -DinteractiveMode=false

    info "Done. Next steps:"
    printf '    cd %s\n    mvn verify\n    mvn spring-boot:run\n' "$artifact_id"
}

case "${1:-help}" in
    install) install_ddk ;;
    new) shift; new_project "$@" ;;
    help | -h | --help) usage ;;
    *) usage; exit 1 ;;
esac
