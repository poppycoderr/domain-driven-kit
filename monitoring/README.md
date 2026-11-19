# Monitoring

[中文](./README.zh-CN.md)

A Grafana dashboard and a set of Prometheus alert rules for the metrics that the projection, event and cache starters register. They need Micrometer's Prometheus registry in the application (`micrometer-registry-prometheus` and Spring Boot Actuator, with `prometheus` among the exposed endpoints). `ddk-example-inventory` is set up that way.

| File | What it is |
|---|---|
| `grafana/ddk-overview.json` | The dashboard "Domain Driven Kit": a row each for projections, events and the cache. Import it in Grafana (Dashboards → New → Import) or provision it from a file. It asks for a Prometheus data source and filters by `job` |
| `prometheus/ddk-alerts.yml` | Alert rules; add the file to `rule_files` in Prometheus |
| `prometheus/ddk-alerts.test.yml` | Unit tests of the rules: `promtool test rules ddk-alerts.test.yml`. CI runs them |

## Alerts

| Alert | Fires when | Severity |
|---|---|---|
| `DdkProjectionRefreshFailing` | A projection has marks that failed and are being retried, for 15 minutes | warning |
| `DdkProjectionLagging` | The oldest due mark of a projection has waited more than 5 minutes, for 10 minutes | warning |
| `DdkEventPublicationsFailed` | Publications stay `FAILED`, for 15 minutes | warning |
| `DdkEventPublicationMetricsStale` | The publication counts of an instance were last sampled more than 10 minutes ago | warning |
| `DdkEventConsumerStuck` | A RocketMQ consumer group has failed on one message 10 times in a row, for 5 minutes. In orderly consumption its queue waits meanwhile | critical |
| `DdkEventPublishErrors` | Sending events to a destination fails, for 10 minutes | warning |
| `DdkCacheRedisErrors` | Redis operations of a cache fail, for 10 minutes | warning |

The thresholds follow the starters' defaults, such as `ddk.projection.max-retry-delay` (5m) and `ddk.event.metrics.publications.interval` (1m). Change them in the rules when you change those settings. Each alert's description says where to look next; the starter READMEs have the details.

The gauges for projections and publications count a table that all instances share, so every instance reports the same value. The rules and the dashboard take the maximum per `job`, not the sum.

## Compatibility

The metric names and tags are the ones the starters register. `MonitoringMetricsTest` in `ddk-example-inventory` checks that every metric used here appears in its scrape, apart from those that need RocketMQ or a Redis failure. The dashboard and the rules are examples to copy and adapt, not a contract: they may change in any release.
