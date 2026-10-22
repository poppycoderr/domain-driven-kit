# 发布 DDK

[English](./RELEASING.md)

同一个 tag 发布到两个地方：**GitHub Release** 由 CI 构建，附带构建来源证明；**Maven 中央仓库**以 `com.domaindrivenkit` 发布，由维护者构建并签名。什么时候发布由维护者决定，本页只写流程。私钥、口令和 token 永远不进本仓库、提交历史、CI 日志或证据文件。

## 一、准备

1. `main` 的 CI 是绿的，并且已经包含这次发布的全部内容。
2. 用一个 PR 把 `CHANGELOG.md` 的 `[Unreleased]` 改成 `[X.Y.Z] - YYYY-MM-DD`，补上概述段落和对比链接。发布说明就取自这一节。
3. 文档站的升级指南有这个版本的一节，并且在用上一个版本生成的项目上演练过。

## 二、打 tag：GitHub Release

```bash
git switch main && git pull --ff-only
git tag vX.Y.Z
git push origin vX.Y.Z
```

`.github/workflows/release.yml` 按 tag 设置版本号，执行 `mvn -B -ntp install -Parchetype-it,release`，为每个文件生成构建来源证明，再以 CHANGELOG 的那一节为说明创建 GitHub Release。确认流程运行和 Release 都正常后再继续。

## 三、Maven 中央仓库

### 一次性准备

下面这些都在仓库之外。

- **命名空间。** `com.domaindrivenkit` 已经在 [Central Publisher Portal](https://central.sonatype.com/publishing) 上通过 `domaindrivenkit.com` 的 DNS TXT 记录验证。
- **签名密钥。** 当前密钥为 RSA 4096，指纹 `02F5D443996C2388C8039D6CAE07FF4C884256F0`，2026-10-22 创建，2028-10-21 到期，[已发布到 keyserver.ubuntu.com](https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x02F5D443996C2388C8039D6CAE07FF4C884256F0)。ASCII 格式的私钥和它的口令放在只有自己能读的文件里（目录 `0700`，文件 `0600`）。更换或续期：

  ```bash
  gpg --quick-generate-key "Domain Driven Kit Release Signing" rsa4096 sign 2y
  gpg --armor --export-secret-keys <指纹> > <private-key.asc>
  gpg --keyserver hkps://keyserver.ubuntu.com --send-keys <指纹>
  ```

  使用前先从 keyserver 取回公钥，核对完整指纹；然后更新两份 README 里的指纹。
- **Central token。** 在 <https://central.sonatype.com/usertoken> 生成用户 token（[Sonatype 的说明](https://central.sonatype.org/publish/generate-portal-token/)）。token 只显示一次，到期日由生成时指定，所以每次发布前都要确认它还有效。把它写进 `~/.m2/settings.xml`（权限 `0600`），server id 与 `central` profile 使用的一致：

  ```xml
  <settings>
    <servers>
      <server>
        <id>central</id>
        <username><!-- token 用户名 --></username>
        <password><!-- token 口令 --></password>
      </server>
    </servers>
  </settings>
  ```

### 构建、签名、上传

在这个 tag 的全新克隆里操作，不用自己的工作副本：

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

然后签名并上传。`central` profile 用 BouncyCastle 签名，不需要安装 `gpg`；开启了 `bestPractices`，私钥和口令只从下面两个环境变量读取：

```bash
MAVEN_GPG_KEY="$(cat <private-key.asc>)" \
MAVEN_GPG_PASSPHRASE="$(cat <口令文件>)" \
mvn -B -ntp -Prelease,central deploy
```

构建会给每个文件签名，排除 `ddk-examples`、`ddk-example-user`、`ddk-mall`，把整个 reactor 打成 `ddk-dependencies/target/central-publishing/central-bundle.zip` 并上传。因为 `autoPublish=false`、`waitUntil=validated`，Maven 在 Portal 校验完部署后返回，此时还没有任何东西公开。

### 检查，再发布

打开 [Deployments](https://central.sonatype.com/publishing/deployments)，部署 `com.domaindrivenkit:X.Y.Z` 应该是 `VALIDATED`，没有错误。发布前确认：

- 组件正好是预期的 22 个：父 pom `domain-driven-kit`、`ddk-starters`、`ddk-archetypes`、`ddk-dependencies`、`ddk-core`、`ddk-test`、`ddk-mybatis`、13 个 starter 和两个 archetype，没有示例模块；
- 所有坐标都是 `com.domaindrivenkit`，版本都是 `X.Y.Z`；
- 抽查几个文件，例如 BOM 的 POM 及其 `.asc` 和校验和，与本地的部署包一致。

然后点 **Publish**。发布后不能删除或修改。等文件出现在 [repo.maven.apache.org](https://repo.maven.apache.org/maven2/com/domaindrivenkit/) 上，像使用者一样验证一个：

```bash
curl -fsSO https://repo.maven.apache.org/maven2/com/domaindrivenkit/ddk-core/X.Y.Z/ddk-core-X.Y.Z.jar
curl -fsSO https://repo.maven.apache.org/maven2/com/domaindrivenkit/ddk-core/X.Y.Z/ddk-core-X.Y.Z.jar.asc
gpg --verify ddk-core-X.Y.Z.jar.asc ddk-core-X.Y.Z.jar
```

### 上传失败时

部署包已经生成、上传却失败（例如 `central.sonatype.com:443 failed to respond`）时，Maven 会以错误退出。不要马上重跑 `deploy`：

1. 先看 [Deployments](https://central.sonatype.com/publishing/deployments)。如果失败的那次已经建出了部署，就接着用它（检查，再发布），或者把它丢弃；同一个版本不要留下两个部署。
2. 如果没有，就上传 Maven 已经生成并签好名的部署包，不要重新构建：先记下 `shasum -a 256 ddk-dependencies/target/central-publishing/central-bundle.zip`，然后在 Portal 上点 **Publish Component**，部署名称填 `com.domaindrivenkit:X.Y.Z`，上传这个文件并确认（[Sonatype 的说明](https://central.sonatype.org/publish/publish-portal-upload/)）。
3. 等到 `VALIDATED`，再按上一节检查并发布。

0.10.0 就是这样发布的。

## 四、发布之后

1. **下一个快照。** 用一个 PR 把所有模块、BOM 的 `ddk.version`、两份 archetype README 里的快照示例改成下一个 `-SNAPSHOT`，把根 pom 的 `api.baseline.version` 改成刚发布的版本，并清空 `api-check` 的放行列表。
2. **文档。** 用一个 PR 让两份 README（快速开始和版本列表）、两份 archetype README、两份路线图和路线图配图指向新版本；文档站的快速开始、首页和升级指南也一并更新。
