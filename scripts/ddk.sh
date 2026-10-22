#!/usr/bin/env bash
#
# Installs Domain Driven Kit into the local Maven repository and generates projects from its archetypes.
# DDK is not published to Maven Central yet, so every project needs the artifacts installed locally first.
#
#   curl -fsSL https://raw.githubusercontent.com/poppycoderr/domain-driven-kit/main/scripts/ddk.sh | bash -s -- new order-service
#
set -euo pipefail

DDK_REPO="${DDK_REPO:-https://github.com/poppycoderr/domain-driven-kit.git}"
DDK_REF="${DDK_REF:-latest}"
DDK_HOME="${DDK_HOME:-$HOME/.ddk}"
SRC_DIR="$DDK_HOME/src"
# 记录最近一次安装的是哪个 ref、哪个提交、装出来的版本号，供下次判断能否直接复用
STATE_FILE="$DDK_HOME/installed"

info() { printf '\033[36m==>\033[0m %s\n' "$*"; }
fail() { printf '\033[31merror:\033[0m %s\n' "$*" >&2; exit 1; }
warn() { printf '\033[33mwarning:\033[0m %s\n' "$*" >&2; }

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
  DDK_REF    What to build: "latest" (default, the newest release tag), a tag such as v0.10.0,
             or a branch such as main for the development snapshot
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

# 0.9 及以前是 com.ddk，0.10 起是 com.domaindrivenkit，所以从装好的源码里读，而不是写死
ddk_group() {
    mvn -f "$SRC_DIR/pom.xml" -q -N help:evaluate -Dexpression=project.groupId -DforceStdout
}

# 把 DDK_REF 解析成一个具体的 ref：latest 是最新的发布 tag。连不上仓库时输出为空
resolve_ref() {
    if [[ "$DDK_REF" != "latest" ]]; then
        echo "$DDK_REF"
        return
    fi
    git ls-remote --tags --refs "$DDK_REPO" 'v*' 2>/dev/null \
        | awk -F/ '{print $3}' | grep -E '^v[0-9]+\.[0-9]+\.[0-9]+$' | sort -V | tail -n 1 || true
}

# 远端上这个 ref 当前指向的提交。连不上仓库时输出为空
remote_commit() {
    local ref="$1"
    git ls-remote "$DDK_REPO" "refs/tags/$ref^{}" "refs/tags/$ref" "refs/heads/$ref" 2>/dev/null \
        | sort -k2 -r | awk 'NR == 1 {print $1}' || true
}

local_repository() {
    mvn -B -q help:evaluate -Dexpression=settings.localRepository -DforceStdout
}

# 两个 archetype 都已经在本地 Maven 仓库里
artifacts_present() {
    local version="$1" repository group
    [[ -f "$SRC_DIR/pom.xml" ]] || return 1
    repository=$(local_repository)
    group=$(ddk_group)
    [[ -f "$repository/${group//.//}/ddk-layer3-archetype/$version/ddk-layer3-archetype-$version.jar" \
        && -f "$repository/${group//.//}/ddk-layer4-archetype/$version/ddk-layer4-archetype-$version.jar" ]]
}

install_ddk() {
    require git
    require mvn
    check_java

    local ref
    ref=$(resolve_ref)
    [[ -n "$ref" ]] || fail "Cannot list the releases of $DDK_REPO. Check the network, or set DDK_REF to a tag or branch"

    if [[ -d "$SRC_DIR/.git" ]]; then
        info "Updating DDK source ($ref)"
        git -C "$SRC_DIR" fetch -q --depth 1 origin "$ref"
        git -C "$SRC_DIR" checkout -q --force --detach FETCH_HEAD
    else
        info "Cloning DDK ($ref)"
        mkdir -p "$DDK_HOME"
        git clone -q --depth 1 --branch "$ref" "$DDK_REPO" "$SRC_DIR"
    fi

    # 仓库里始终是 SNAPSHOT 版本，发布版本号只在发布流程中按 tag 改写；按 tag 构建时同样改写，装出来的才是 X.Y.Z
    if [[ "$ref" =~ ^v([0-9]+\.[0-9]+\.[0-9]+)$ ]]; then
        local release="${BASH_REMATCH[1]}"
        mvn -f "$SRC_DIR/pom.xml" -B -ntp -q versions:set -DnewVersion="$release" -DprocessAllModules=true -DgenerateBackupPoms=false
        sed -i.bak "s|<ddk.version>.*</ddk.version>|<ddk.version>$release</ddk.version>|" "$SRC_DIR/ddk-dependencies/pom.xml"
        rm -f "$SRC_DIR/ddk-dependencies/pom.xml.bak"
    fi

    local version commit
    version=$(ddk_version)
    commit=$(git -C "$SRC_DIR" rev-parse HEAD)
    info "Installing DDK $version ($ref, commit ${commit:0:7}) into the local Maven repository"
    mvn -f "$SRC_DIR/pom.xml" -B -ntp -q install \
        -Dmaven.test.skip=true -Djacoco.skip=true -Dspotless.check.skip=true
    printf '%s %s %s\n' "$ref" "$commit" "$version" > "$STATE_FILE"
    info "Installed DDK $version"
}

# 确保 DDK_REF 对应的版本已经装好，并输出要用的版本号。
# 已装的正是这个 ref、远端没有更新、Maven 产物也都在时直接复用；否则重新安装。连不上仓库时只能复用已装的同一个 ref。
ensure_installed() {
    local ref remote installed_ref="" installed_commit="" installed_version=""
    ref=$(resolve_ref)
    if [[ -f "$STATE_FILE" ]]; then
        read -r installed_ref installed_commit installed_version < "$STATE_FILE" || true
    fi

    if [[ -z "$ref" ]]; then
        [[ -n "$installed_ref" && "$installed_ref" =~ ^v[0-9] ]] \
            || fail "Cannot list the releases of $DDK_REPO, and no release is installed. Check the network, or set DDK_REF"
        warn "Cannot reach $DDK_REPO to look for a newer release; using the installed $installed_ref"
        ref="$installed_ref"
    fi
    remote=$(remote_commit "$ref")

    if [[ "$installed_ref" == "$ref" && -n "$installed_version" ]] && artifacts_present "$installed_version"; then
        if [[ -z "$remote" ]]; then
            warn "Cannot reach $DDK_REPO to check $ref for changes; using what is installed"
            echo "$installed_version"
            return
        fi
        if [[ "$remote" == "$installed_commit" ]]; then
            echo "$installed_version"
            return
        fi
    fi
    [[ -n "$remote" ]] || fail "Cannot reach $DDK_REPO, and DDK $ref is not installed"

    DDK_REF="$ref" install_ddk >&2
    read -r _ _ installed_version < "$STATE_FILE"
    echo "$installed_version"
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

    require git
    require mvn
    local version
    version=$(ensure_installed)
    local commit
    commit=$(awk '{print $2}' "$STATE_FILE")
    local ref
    ref=$(awk '{print $1}' "$STATE_FILE")

    info "Generating $artifact_id ($layers layers, package $package) with DDK $version ($ref, commit ${commit:0:7})"
    mvn -B -ntp -q archetype:generate \
        -DarchetypeGroupId="$(ddk_group)" \
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
