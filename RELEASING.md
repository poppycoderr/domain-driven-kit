# Releasing DDK

[简体中文](./RELEASING.zh-CN.md)

A release goes to two places from the same tag: a **GitHub Release**, built by CI with build provenance, and **Maven Central** under `com.domaindrivenkit`, built and signed by the maintainer. Releasing is the maintainer's decision; this page is the procedure. Keys, passphrases and tokens never enter this repository, its history, CI logs or evidence files.

## 1. Prepare

1. `main` is green and contains everything for the release.
2. A pull request dates the `[Unreleased]` section of `CHANGELOG.md` as `[X.Y.Z] - YYYY-MM-DD`, adds a summary paragraph and the compare link. The release notes are taken from this section.
3. The upgrade guide on the documentation site has a section for the release, rehearsed on a project generated from the previous release.

## 2. Tag: GitHub Release

```bash
git switch main && git pull --ff-only
git tag vX.Y.Z
git push origin vX.Y.Z
```

`.github/workflows/release.yml` sets the version from the tag, runs `mvn -B -ntp install -Parchetype-it,release`, attests the build provenance of every file and creates the GitHub Release with the changelog section as its notes. Check the run and the release before going on.

## 3. Maven Central

### One-time setup

All of this lives outside the repository.

- **Namespace.** `com.domaindrivenkit` is verified on the [Central Publisher Portal](https://central.sonatype.com/publishing) through a DNS TXT record on `domaindrivenkit.com`.
- **Signing key.** Keep the armored private key and its passphrase in files only you can read (directory `0700`, files `0600`). The keys used so far:

  | Fingerprint | Created | Expires | Signs |
  |---|---|---|---|
  | `02F5D443996C2388C8039D6CAE07FF4C884256F0` (RSA 4096, [public key](https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x02F5D443996C2388C8039D6CAE07FF4C884256F0)) | 2026-10-22 | 2028-10-21 | 0.10.0 and later |

  The key has a primary key only, no subkeys. Before it expires, either extend it or rotate to a new one. Signatures made while a key was valid stay valid after it expires.

  *Extend the current key.* The fingerprint stays the same, so nothing changes for users:

  ```bash
  gpg --import <private-key.asc>
  gpg --quick-set-expire <fingerprint> 2y
  gpg --armor --export-secret-keys <fingerprint> > <private-key.asc>
  gpg --keyserver hkps://keyserver.ubuntu.com --send-keys <fingerprint>
  ```

  Fetch the key back from the keyserver, check that it shows the new expiry, and update the expiry in the table above.

  *Rotate to a new key.* Users have to trust a new fingerprint:

  ```bash
  gpg --quick-generate-key "Domain Driven Kit Release Signing" rsa4096 sign 2y
  gpg --armor --export-secret-keys <new-fingerprint> > <private-key.asc>
  gpg --keyserver hkps://keyserver.ubuntu.com --send-keys <new-fingerprint>
  ```

  If the keyring already holds a key with this user ID, `gpg` asks before creating another one; with `--batch` it prints `already exists` and creates nothing unless `--yes` is given, so check that a new fingerprint appeared. Fetch the new key back and compare the full fingerprint, replace the signing material outside the repository, add a row to the table above and close the old row's "Signs" range, and update the fingerprint in both READMEs and on the documentation site's quick start. Keep the old public key on the keyserver: it still verifies the releases it signed.
- **Central token.** Generate a user token at <https://central.sonatype.com/usertoken> ([Sonatype's guide](https://central.sonatype.org/publish/generate-portal-token/)). The token is shown only once and expires on the date you choose; the current one is valid until 2027-10-22. Check it before every release. Put it in `~/.m2/settings.xml` (permissions `0600`) under the server id the `central` profile uses:

  ```xml
  <settings>
    <servers>
      <server>
        <id>central</id>
        <username><!-- token username --></username>
        <password><!-- token password --></password>
      </server>
    </servers>
  </settings>
  ```

### Build, sign and upload

Work in a fresh clone of the tag, not in your working copy:

```bash
git clone --depth 1 --branch vX.Y.Z https://github.com/poppycoderr/domain-driven-kit.git ddk-release
cd ddk-release
VERSION=X.Y.Z
mvn -B -ntp versions:set -DnewVersion="$VERSION" -DprocessAllModules=true -DgenerateBackupPoms=false
sed -i.bak "s|<ddk.version>.*</ddk.version>|<ddk.version>$VERSION</ddk.version>|" ddk-dependencies/pom.xml
rm ddk-dependencies/pom.xml.bak
python3 scripts/check-bom.py
mvn -B -ntp install -Parchetype-it,release
```

Then sign and upload. The `central` profile signs with BouncyCastle, so no `gpg` binary is needed, and with `bestPractices` it reads the key and passphrase only from these two environment variables:

```bash
MAVEN_GPG_KEY="$(cat <private-key.asc>)" \
MAVEN_GPG_PASSPHRASE="$(cat <passphrase-file>)" \
mvn -B -ntp -Prelease,central deploy
```

The build signs every file, leaves out `ddk-examples`, `ddk-example-user` and `ddk-mall`, bundles the reactor into `ddk-dependencies/target/central-publishing/central-bundle.zip` and uploads it as the deployment `com.domaindrivenkit:X.Y.Z` (the profile sets `deploymentName`; the plugin's own default is `Deployment`). With `autoPublish=false` and `waitUntil=validated`, Maven returns once the Portal has validated the deployment; nothing is public yet.

### Check, then publish

Open [Deployments](https://central.sonatype.com/publishing/deployments). The deployment `com.domaindrivenkit:X.Y.Z` should be `VALIDATED` with no errors. Before publishing, check:

- the components are the 22 expected ones: the parent `domain-driven-kit`, `ddk-starters`, `ddk-archetypes`, `ddk-dependencies`, `ddk-core`, `ddk-test`, `ddk-mybatis`, the 13 starters and the two archetypes; no example module;
- every coordinate is `com.domaindrivenkit` at version `X.Y.Z`;
- a few files, such as the BOM's POM with its `.asc` and checksums, match the local bundle.

Then press **Publish**. A published release cannot be deleted or changed. Once its files are on [repo.maven.apache.org](https://repo.maven.apache.org/maven2/com/domaindrivenkit/), check one of them as a user would:

```bash
curl -fsSO https://repo.maven.apache.org/maven2/com/domaindrivenkit/ddk-core/X.Y.Z/ddk-core-X.Y.Z.jar
curl -fsSO https://repo.maven.apache.org/maven2/com/domaindrivenkit/ddk-core/X.Y.Z/ddk-core-X.Y.Z.jar.asc
gpg --verify ddk-core-X.Y.Z.jar.asc ddk-core-X.Y.Z.jar
```

### If the upload fails

The upload can fail after the bundle was built, for example with `central.sonatype.com:443 failed to respond`; Maven then exits with an error. Do not run `deploy` again straight away:

1. Look at [Deployments](https://central.sonatype.com/publishing/deployments). If the failed run did create a deployment, continue with that one (check, then publish) or drop it; never leave two deployments of the same version.
2. If there is none, upload the bundle that Maven already built and signed, without rebuilding: record `shasum -a 256 ddk-dependencies/target/central-publishing/central-bundle.zip`, then on the Portal choose **Publish Component**, enter the deployment name `com.domaindrivenkit:X.Y.Z`, upload the file and confirm ([Sonatype's guide](https://central.sonatype.org/publish/publish-portal-upload/)).
3. Wait for `VALIDATED`, then check and publish as above.

This is how 0.10.0 was released; its deployment name was entered by hand in the Portal, before the profile set it.

## 4. After the release

1. **Next snapshot.** A pull request moves every module, the BOM's `ddk.version` and the snapshot examples in the archetype READMEs to the next `-SNAPSHOT`, sets `api.baseline.version` in the root pom to the release, and empties the `api-check` excludes.
2. **Compatibility fixtures.** Run `compat/generator` with `-Dddk.version=X.Y.Z` and add its output under each module's `src/test/resources/compat/X.Y.Z/`, next to the earlier releases (see `compat/README.md`).
3. **Docs.** A pull request points both READMEs (Quick Start and the release list), both archetype READMEs, both roadmaps and the roadmap diagram at the release. The documentation site's quick start, home page and upgrade guide move to it as well.
