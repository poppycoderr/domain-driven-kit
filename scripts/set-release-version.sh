#!/usr/bin/env bash
#
# Sets the release version in every pom of a checkout of the release tag, the same way in CI and on the maintainer's
# machine, so that both builds are byte for byte the same:
#
# - every module's version, and ddk.version in the BOM, which does not inherit the parent pom;
# - project.build.outputTimestamp, in the parent pom and the BOM, to the commit time of HEAD in UTC. versions:set
#   would set it to the current time, so it is told to leave it alone.
#
#   scripts/set-release-version.sh X.Y.Z
#
# Run it in the repository root of a clean checkout of tag vX.Y.Z. Works with GNU and BSD sed.

set -euo pipefail

VERSION="${1:?usage: set-release-version.sh X.Y.Z}"
TIMESTAMP="$(TZ=UTC git log -1 --format=%cd --date=format-local:%Y-%m-%dT%H:%M:%SZ)"

mvn -B -ntp -q versions:set -DnewVersion="$VERSION" -DprocessAllModules=true -DgenerateBackupPoms=false \
    -DupdateBuildOutputTimestampPolicy=never

# replace <file> <element> <value>: sets the text of every <element> in <file>
replace() {
    sed -i.bak "s|<$2>.*</$2>|<$2>$3</$2>|" "$1"
    rm "$1.bak"
}
replace ddk-dependencies/pom.xml ddk.version "$VERSION"
replace pom.xml project.build.outputTimestamp "$TIMESTAMP"
replace ddk-dependencies/pom.xml project.build.outputTimestamp "$TIMESTAMP"

echo "version $VERSION, output timestamp $TIMESTAMP"
