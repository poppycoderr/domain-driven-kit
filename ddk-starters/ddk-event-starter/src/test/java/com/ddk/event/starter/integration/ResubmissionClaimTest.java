package com.ddk.event.starter.integration;

import com.ddk.core.domain.DomainEventPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.core.EventPublicationRepository;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * 重投在实例之间是否认领，取决于 Spring Modulith 的 JDBC 仓储：默认的 V2 结构用条件更新把记录改为 RESUBMITTED，只有改到一行
 * 才算认领成功，Modulith 认领失败就跳过这条；旧结构不认领。事件维护的锁和文档都以此为前提，升级 Modulith 时由这里发现变化。
 */
@DisplayName("Modulith 重投的认领")
class ResubmissionClaimTest {

    @Nested
    @SpringBootTest(classes = IntegrationEventExternalizationTest.TestApplication.class, properties = {
            "spring.datasource.url=jdbc:h2:mem:resubmission-v2;DB_CLOSE_DELAY=-1",
            "spring.modulith.events.jdbc.schema-initialization.enabled=true"
    })
    @DisplayName("默认结构")
    class DefaultStructure {

        @Autowired
        EventPublicationRepository repository;

        @Autowired
        JdbcTemplate jdbc;

        @Autowired
        TransactionTemplate transaction;

        @Autowired
        DomainEventPublisher publisher;

        @Test
        @DisplayName("V2 仓储：同一条失败的发布只能被认领重投一次")
        void claimsAFailedPublicationOnce() {
            assertThat(AopUtils.getTargetClass(repository).getSimpleName()).isEqualTo("JdbcEventPublicationRepositoryV2");
            UUID id = failedPublication(jdbc, transaction, publisher, repository);

            assertThat(repository.markResubmitted(id, Instant.now())).as("the first instance claims it").isTrue();
            assertThat(repository.markResubmitted(id, Instant.now())).as("a second instance does not").isFalse();
        }
    }

    @Nested
    @SpringBootTest(classes = IntegrationEventExternalizationTest.TestApplication.class, properties = {
            "spring.datasource.url=jdbc:h2:mem:resubmission-legacy;DB_CLOSE_DELAY=-1",
            "spring.modulith.events.jdbc.schema-initialization.enabled=true",
            "spring.modulith.events.jdbc.use-legacy-structure=true"
    })
    @DisplayName("旧结构")
    class LegacyStructure {

        @Autowired
        EventPublicationRepository repository;

        @Autowired
        JdbcTemplate jdbc;

        @Autowired
        TransactionTemplate transaction;

        @Autowired
        DomainEventPublisher publisher;

        @Test
        @DisplayName("旧仓储不认领：每次都返回 true，两个实例会重投同一条")
        void doesNotClaim() {
            assertThat(AopUtils.getTargetClass(repository).getSimpleName()).isEqualTo("JdbcEventPublicationRepository");
            UUID id = failedPublication(jdbc, transaction, publisher, repository);

            assertThat(repository.markResubmitted(id, Instant.now())).isTrue();
            assertThat(repository.markResubmitted(id, Instant.now())).isTrue();
        }
    }

    private static UUID failedPublication(JdbcTemplate jdbc, TransactionTemplate transaction, DomainEventPublisher publisher,
                                          EventPublicationRepository repository) {
        transaction.executeWithoutResult(status -> publisher.publishEventsOf(
                IntegrationEventExternalizationTest.Order.pay(IntegrationEventExternalizationTest.OrderId.of(42L))));
        await().atMost(Duration.ofSeconds(5)).until(() -> jdbc.queryForObject("SELECT COUNT(*) FROM event_publication", Long.class) == 1);
        UUID id = jdbc.queryForObject("SELECT id FROM event_publication", UUID.class);
        repository.markFailed(id);
        return id;
    }
}
