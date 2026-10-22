# Security

[简体中文](./SECURITY.zh-CN.md)

## Reporting a vulnerability

Please do not open a public issue. Report it privately through GitHub: on the repository's [Security tab](https://github.com/poppycoderr/domain-driven-kit/security), choose **Report a vulnerability**. Only the maintainer sees the report.

Include the DDK version, the affected module, how to reproduce it and what an attacker gains. The maintainer will confirm the report, agree on a fix and its disclosure with you, and credit you in the advisory unless you prefer otherwise. DDK is maintained by one person, so there is no guaranteed response time; see [SUPPORT.md](./SUPPORT.md).

## Supported versions

Security fixes are released as patch releases of the newest minor line (today 0.10.x), on Maven Central and as a GitHub Release, with a [GitHub security advisory](https://github.com/poppycoderr/domain-driven-kit/security/advisories). Older lines get no fixes; upgrade to the newest line.

## Verifying what you download

Artifacts on Maven Central are signed with the DDK release key, and the jars on GitHub Releases carry build provenance. How to check both is in the README under [Verifying artifacts](./README.md#verifying-artifacts).
