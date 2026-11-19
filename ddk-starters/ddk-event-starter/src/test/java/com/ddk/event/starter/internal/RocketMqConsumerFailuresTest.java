package com.ddk.event.starter.internal;

import com.ddk.event.starter.consumer.IntegrationEventConsumer;
import com.ddk.event.starter.consumer.IntegrationEventDispatcher;
import com.ddk.event.starter.consumer.UndeliverableEvent;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("RocketMQ 消费：按队列统计连续失败")
@ExtendWith(OutputCaptureExtension.class)
class RocketMqConsumerFailuresTest {

    private final IntegrationEventDispatcher dispatcher = mock(IntegrationEventDispatcher.class);

    private final AtomicBoolean failing = new AtomicBoolean(true);

    RocketMqConsumerFailuresTest() {
        IntegrationEventConsumer<?> consumer = mock(IntegrationEventConsumer.class);
        when(consumer.group()).thenReturn("inventory");
        when(consumer.source()).thenReturn("orders:placed");
        when(dispatcher.consumers()).thenReturn(List.of(consumer));
        doAnswer(invocation -> {
            if (failing.get()) {
                throw new IllegalStateException("payload does not match");
            }
            return 0;
        }).when(dispatcher).dispatch(anyString(), anyString(), any(), anyMap(), anyString());
    }

    @Test
    @DisplayName("顺序消费时同一条消息每次失败计数加一，成功后清零，达到阈值记 ERROR")
    void countsConsecutiveFailuresUntilTheQueueMovesOn(CapturedOutput output) {
        RocketMqEventConsumers consumers = new RocketMqEventConsumers(dispatcher, "localhost:9876", true);
        assertThat(consumers.consecutiveFailures("inventory")).isZero();

        for (int attempt = 0; attempt < RocketMqEventConsumers.STUCK_AFTER; attempt++) {
            assertThat(consumers.consume("inventory", List.of(message(1, attempt)))).isFalse();
            assertThat(consumers.consecutiveFailures("inventory")).isEqualTo(attempt + 1);
        }
        assertThat(output).contains("has failed " + RocketMqEventConsumers.STUCK_AFTER + " times in a row");

        failing.set(false);
        assertThat(consumers.consume("inventory", List.of(message(1, RocketMqEventConsumers.STUCK_AFTER)))).isTrue();
        assertThat(consumers.consecutiveFailures("inventory")).isZero();
    }

    @Test
    @DisplayName("一个消费组里取失败最多的队列；别的队列成功不会清掉它")
    void reportsTheLongestFailingQueueOfTheGroup() {
        RocketMqEventConsumers consumers = new RocketMqEventConsumers(dispatcher, "localhost:9876", true);
        consumers.consume("inventory", List.of(message(1, 4)));
        consumers.consume("inventory", List.of(message(2, 0)));

        failing.set(false);
        consumers.consume("inventory", List.of(message(3, 0)));

        assertThat(consumers.consecutiveFailures("inventory")).isEqualTo(5);
    }

    @Test
    @DisplayName("队列转给别的实例后，本实例不再收到它的消息：超过时效的失败记录不再计入")
    void forgetsQueuesThatStoppedFailingHere() {
        MutableClock clock = new MutableClock();
        RocketMqEventConsumers consumers = new RocketMqEventConsumers(dispatcher, "localhost:9876", true, clock);
        consumers.consume("inventory", List.of(message(1, 6)));
        clock.advance(Duration.ofMinutes(1));
        consumers.consume("inventory", List.of(message(2, 2)));

        clock.advance(RocketMqEventConsumers.STALE_AFTER.minusSeconds(30));
        assertThat(consumers.consecutiveFailures("inventory")).as("queue 1 went quiet, queue 2 is still recent").isEqualTo(3);

        clock.advance(Duration.ofMinutes(1));
        assertThat(consumers.consecutiveFailures("inventory")).isZero();

        consumers.consume("inventory", List.of(message(1, 7)));
        assertThat(consumers.consecutiveFailures("inventory")).as("failing here again counts again").isEqualTo(8);
    }

    @Test
    @DisplayName("失败到上限时交给 UndeliverableEventHandler，它正常返回后这条消息算消费完成，队列继续往下走")
    void handsOverAtTheLimitAndMovesOn(CapturedOutput output) {
        List<UndeliverableEvent> handed = new ArrayList<>();
        RocketMqEventConsumers consumers = new RocketMqEventConsumers(dispatcher, "localhost:9876", true, 3, handed::add);

        assertThat(consumers.consume("inventory", List.of(message(1, 0)))).isFalse();
        assertThat(consumers.consume("inventory", List.of(message(1, 1)))).isFalse();
        assertThat(handed).as("not before the limit").isEmpty();

        MessageExt third = message(1, 2);
        third.putUserProperty("ddk-event-id", "e-1");
        assertThat(consumers.consume("inventory", List.of(third))).isTrue();

        assertThat(handed).singleElement().satisfies(event -> {
            assertThat(event.group()).isEqualTo("inventory");
            assertThat(event.topic()).isEqualTo("orders");
            assertThat(event.tag()).isEqualTo("placed");
            assertThat(event.headers()).containsEntry("ddk-event-id", "e-1");
            assertThat(event.body()).isEqualTo("{}");
            assertThat(event.attempts()).isEqualTo(3);
            assertThat(event.lastFailure()).hasMessage("payload does not match");
        });
        assertThat(consumers.consecutiveFailures("inventory")).isZero();
        assertThat(output).contains("handed it to the UndeliverableEventHandler and moved on");
    }

    @Test
    @DisplayName("UndeliverableEventHandler 抛出异常时这条消息照常失败，下次失败再交给它")
    void aFailingHandlerKeepsTheMessage(CapturedOutput output) {
        List<Integer> calls = new ArrayList<>();
        RocketMqEventConsumers consumers = new RocketMqEventConsumers(dispatcher, "localhost:9876", true, 2, event -> {
            calls.add(event.attempts());
            throw new IllegalStateException("parking table unavailable");
        });

        assertThat(consumers.consume("inventory", List.of(message(1, 1)))).isFalse();
        assertThat(consumers.consume("inventory", List.of(message(1, 2)))).isFalse();

        assertThat(calls).containsExactly(2, 3);
        assertThat(consumers.consecutiveFailures("inventory")).isEqualTo(3);
        assertThat(output).contains("UndeliverableEventHandler failed");
    }

    @Test
    @DisplayName("设置了上限却没有 UndeliverableEventHandler，或者上限为负，都不能创建")
    void aLimitNeedsAHandler() {
        assertThatThrownBy(() -> new RocketMqEventConsumers(dispatcher, "localhost:9876", true, 3, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no UndeliverableEventHandler bean");
        assertThatThrownBy(() -> new RocketMqEventConsumers(dispatcher, "localhost:9876", true, -1, event -> { }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("max-attempts must be 0");
        assertThat(new RocketMqEventConsumers(dispatcher, "localhost:9876", true, 0, null).consecutiveFailures("inventory")).isZero();
    }

    @Test
    @DisplayName("有 MeterRegistry 时每个消费组注册一个 gauge")
    void registersAGaugePerGroup() {
        RocketMqEventConsumers consumers = new RocketMqEventConsumers(dispatcher, "localhost:9876", true);
        MeterRegistry meters = new SimpleMeterRegistry();
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("meterRegistry", meters);
        beans.addBean("consumers", consumers);
        new RocketMqConsumerMetrics(beans.getBeanProvider(MeterRegistry.class), beans.getBeanProvider(RocketMqEventConsumers.class))
                .afterSingletonsInstantiated();

        consumers.consume("inventory", List.of(message(1, 2)));

        assertThat(meters.get("ddk.event.consume.failures").tag("group", "inventory").gauge().value()).isEqualTo(3.0);
    }

    private static MessageExt message(int queueId, int reconsumeTimes) {
        MessageExt message = new MessageExt();
        message.setTopic("orders");
        message.setTags("placed");
        message.setBrokerName("broker-a");
        message.setQueueId(queueId);
        message.setReconsumeTimes(reconsumeTimes);
        message.setBody("{}".getBytes(StandardCharsets.UTF_8));
        return message;
    }

    private static final class MutableClock extends Clock {

        private Instant now = Instant.parse("2026-11-13T08:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
