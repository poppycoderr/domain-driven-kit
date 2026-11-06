#!/usr/bin/env bash
# Copies the fixtures that compat/generator wrote for a release into the test resources of the modules that read them.
# Usage: compat/install-fixtures.sh X.Y.Z   (after: cd compat/generator && mvn -Dddk.version=X.Y.Z compile exec:java)
set -euo pipefail
VERSION=${1:?usage: compat/install-fixtures.sh X.Y.Z}
ROOT=$(cd "$(dirname "$0")/.." && pwd)
FROM="$ROOT/compat/generator/target/compat/$VERSION"
[ -d "$FROM" ] || { echo "No generated fixtures in $FROM; run the generator with -Dddk.version=$VERSION first" >&2; exit 1; }

install() { # module file...
    local module=$1; shift
    local to="$ROOT/ddk-starters/$module/src/test/resources/compat/$VERSION"
    # Fixtures of a release are written once and never regenerated
    [ ! -e "$to" ] || { echo "$to already exists; fixtures of a release are not regenerated" >&2; exit 1; }
    mkdir -p "$to"
    for file in "$@"; do
        cp "$FROM/$file" "$to/$file"
        echo "  $module/src/test/resources/compat/$VERSION/$file"
    done
}

echo "Installing the fixtures of $VERSION:"
install ddk-event-starter rocketmq-message.json outbox-publication.json processed-message-table.json
install ddk-projection-starter projection-pending-table.sql
install ddk-redis-starter redis.json
install ddk-cache-starter redis.json
install ddk-concurrency-starter lock-key.txt concurrency-keys.json
