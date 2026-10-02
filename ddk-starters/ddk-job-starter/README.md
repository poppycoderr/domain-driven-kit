# DDK Job Starter

Scheduled jobs that run on one instance at a time. It turns on Spring's `@Scheduled` and wires [ShedLock](https://github.com/lukas-krecan/ShedLock), with the lock kept in Redis.

```text
instance A  @Scheduled fires ──► lock ddk:job-lock:<app>:<job>  acquired  ──► run the job ──► release (not before at-least-for)
instance B  @Scheduled fires ──► lock ddk:job-lock:<app>:<job>  held by A ──► skip this round
```

## Usage

```xml
<dependency>
    <groupId>com.ddk</groupId>
    <artifactId>ddk-job-starter</artifactId>
</dependency>
```

```java
package com.acme.order.adapter.job;

@Component
public class OrderJobs {

    private final OrderService orders;

    @Scheduled(cron = "0 */5 * * * *")
    @SchedulerLock(name = "closeExpiredOrders")
    public void closeExpiredOrders() {
        orders.closeExpired();
    }
}
```

- The job class lives in `adapter.job` and only calls an application service. `CommonArchRules.SCHEDULED_JOBS_MUST_RESIDE_IN_ADAPTER` checks this in generated projects.
- `@SchedulerLock(name = ...)` is what makes the job exclusive. A `@Scheduled` method without it runs on every instance, which is right for per-instance work such as refreshing a local cache. To make forgetting it a build failure, add the optional rule `CommonArchRules.SCHEDULED_JOBS_MUST_BE_LOCKED` to your architecture test.
- The lock is stored through the application's Spring Data Redis connection (`spring.data.redis.*`). To keep it elsewhere, such as in the database, declare your own ShedLock `LockProvider` bean and it replaces the Redis one.
- No `@EnableScheduling` or `@EnableSchedulerLock` is needed.

## How the lock behaves

| Setting | Meaning |
|---|---|
| `lockAtMostFor` | The lock expires after this time even if the instance died while holding it. Set it longer than the job normally takes; another instance can take over only after it expires |
| `lockAtLeastFor` | The lock is kept at least this long even if the job finishes sooner. It stops a second instance with a slightly different clock from running the same round |

Both have defaults under `ddk.job.lock.*` and can be set per job on `@SchedulerLock`. A job that outlives `lockAtMostFor` can overlap with the next run on another instance, so a job should still be safe to run twice.

## Configuration

| Property | Default | Description |
|---|---|---|
| `ddk.job.enabled` | `true` | Turn scheduling off, for example in a local environment that should only serve the API |
| `ddk.job.lock.at-most-for` | `10m` | Default `lockAtMostFor` |
| `ddk.job.lock.at-least-for` | `0s` | Default `lockAtLeastFor` |
| `ddk.job.lock.key-prefix` | `ddk:job-lock` | Prefix of the Redis key; the full key is `<prefix>:<spring.application.name>:<job name>` |

## Where it does not fit

- It is not a distributed scheduler: no sharding, no retries, no console, no manual trigger. For those, use a scheduling platform.
- XXL-Job has no DDK starter. XXL-Job is licensed under GPL-3.0 and DDK under Apache-2.0, so DDK does not link against it. Configure `XxlJobSpringExecutor` in your application as its documentation describes; `SCHEDULED_JOBS_MUST_RESIDE_IN_ADAPTER` covers `@XxlJob` methods as well.
