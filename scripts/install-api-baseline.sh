#!/usr/bin/env bash
#
# Install the release that the API compatibility check compares against into the local Maven repository.
#
# DDK is published as GitHub Releases, not to Maven Central, so the baseline jars cannot be resolved the usual way.
# This script downloads them from the release and places them where Maven looks. Jars that are already there are kept.
#
#   scripts/install-api-baseline.sh            # the version in <api.baseline.version> of the root pom
#   scripts/install-api-baseline.sh 0.8.0      # a specific version
#
# Then: mvn verify -Papi-check
#
# Requires the GitHub CLI (gh), authenticated or with GH_TOKEN set.

set -euo pipefail

cd "$(dirname "$0")/.."

VERSION="${1:-$(sed -n 's:.*<api.baseline.version>\(.*\)</api.baseline.version>.*:\1:p' pom.xml)}"
# The groupId the baseline was published under; it changed from com.ddk to com.domaindrivenkit in 0.10
GROUP="${DDK_BASELINE_GROUP:-$(sed -n 's:.*<api.baseline.groupId>\(.*\)</api.baseline.groupId>.*:\1:p' pom.xml)}"
REPO="${DDK_GITHUB_REPO:-poppycoderr/domain-driven-kit}"
LOCAL_REPO="${MAVEN_REPO_LOCAL:-$HOME/.m2/repository}"

if [ -z "$VERSION" ] || [ -z "$GROUP" ]; then
  echo "No baseline version or groupId given and none found in pom.xml" >&2
  exit 1
fi

DOWNLOADS="$(mktemp -d)"
trap 'rm -rf "$DOWNLOADS"' EXIT

echo "==> Downloading DDK $VERSION from $REPO"
# The pattern leaves out -sources.jar and -javadoc.jar, which end differently
gh release download "v$VERSION" --repo "$REPO" --pattern "*-$VERSION.jar" --dir "$DOWNLOADS"

installed=0
for jar in "$DOWNLOADS"/*-"$VERSION".jar; do
  artifact="$(basename "$jar" "-$VERSION.jar")"
  target="$LOCAL_REPO/${GROUP//.//}/$artifact/$VERSION"
  if [ -f "$target/$artifact-$VERSION.jar" ]; then
    continue
  fi
  mkdir -p "$target"
  cp "$jar" "$target/"
  # A minimal pom: the check compares the classes of this jar only and ignores classes it cannot find
  cat > "$target/$artifact-$VERSION.pom" <<POM
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <groupId>$GROUP</groupId>
    <artifactId>$artifact</artifactId>
    <version>$VERSION</version>
</project>
POM
  installed=$((installed + 1))
done

echo "==> Installed $installed artifact(s) of DDK $VERSION into $LOCAL_REPO"
