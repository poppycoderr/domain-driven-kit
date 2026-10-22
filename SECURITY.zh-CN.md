# 安全

[English](./SECURITY.md)

## 报告漏洞

请不要提公开的 issue，通过 GitHub 私下报告：在仓库的 [Security 页](https://github.com/poppycoderr/domain-driven-kit/security)选择 **Report a vulnerability**。报告只有维护者能看到。

请写明 DDK 版本、受影响的模块、复现步骤，以及攻击者能因此得到什么。维护者会确认报告，和你商定修复与公开的时间，并在安全公告里致谢（除非你不希望署名）。DDK 由一个人维护，不保证响应时间，见 [SUPPORT.zh-CN.md](./SUPPORT.zh-CN.md)。

## 支持的版本

安全修复以最新次版本线（目前是 0.10.x）的补丁版本发布，同时发布到 Maven 中央仓库和 GitHub Release，并附 [GitHub 安全公告](https://github.com/poppycoderr/domain-driven-kit/security/advisories)。更早的版本线不再修复，请升级到最新版本线。

## 校验下载的构件

Maven 中央仓库上的构件用 DDK 的发布密钥签名，GitHub Release 上的 jar 附有构建来源证明。两者的校验方法见 README 的[校验构件](./README.zh-CN.md#校验构件)。
