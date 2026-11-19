# 监控

[English](./README.md)

一个 Grafana 看板和一份 Prometheus 告警规则，用的是 projection、event 和 cache 三个 starter 注册的指标。应用里要有 Micrometer 的 Prometheus 注册表（`micrometer-registry-prometheus` 和 Spring Boot Actuator，暴露的端点里包括 `prometheus`）。`ddk-example-inventory` 就是这样配置的。

| 文件 | 内容 |
|---|---|
| `grafana/ddk-overview.json` | 看板「Domain Driven Kit」：投影、事件、缓存各一行。在 Grafana 里导入（Dashboards → New → Import），或者用文件预置。导入时选一个 Prometheus 数据源，可以按 `job` 过滤 |
| `prometheus/ddk-alerts.yml` | 告警规则，加到 Prometheus 的 `rule_files` 里 |
| `prometheus/ddk-alerts.test.yml` | 规则的单元测试：`promtool test rules ddk-alerts.test.yml`，CI 会跑 |

## 告警

| 告警 | 触发条件 | 级别 |
|---|---|---|
| `DdkProjectionRefreshFailing` | 某个读模型有刷新失败、正在重试的标记，持续 15 分钟 | warning |
| `DdkProjectionLagging` | 某个读模型最早到期的标记等了超过 5 分钟，持续 10 分钟 | warning |
| `DdkEventPublicationsFailed` | 有发布一直是 `FAILED`，持续 15 分钟 | warning |
| `DdkEventPublicationMetricsStale` | 某个实例的发布数量超过 10 分钟没有采样 | warning |
| `DdkEventConsumerStuck` | 某个 RocketMQ 消费组在一条消息上连续失败 10 次，持续 5 分钟；顺序消费时这期间整个队列都在等 | critical |
| `DdkEventPublishErrors` | 向某个目的地发送事件失败，持续 10 分钟 | warning |
| `DdkCacheRedisErrors` | 某个缓存的 Redis 操作失败，持续 10 分钟 | warning |

阈值按 starter 的默认配置定，例如 `ddk.projection.max-retry-delay`（5m）和 `ddk.event.metrics.publications.interval`（1m）；改了这些配置，规则里的阈值也要跟着改。每条告警的描述写了下一步看哪里，细节在各 starter 的 README 里。

投影和发布的 gauge 统计的是所有实例共用的表，每个实例报的值相同，所以规则和看板按 `job` 取最大值，不求和。

## 兼容性

指标名和标签就是 starter 注册的那些。`ddk-example-inventory` 里的 `MonitoringMetricsTest` 检查这里用到的每个指标都出现在它的抓取结果里，需要 RocketMQ 或 Redis 故障才出现的几个除外。看板和规则是拿去复制、按需修改的示例，不是兼容性契约，任何版本都可能改。
