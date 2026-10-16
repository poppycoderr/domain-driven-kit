package com.ddk.event.starter.consumer;

import com.ddk.event.starter.inbox.IdempotentConsumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 去重表以「消费组:消费方标识」记录处理过的事件。两个消费方的标识相同，一个处理过的事件另一个就会跳过；
 * 这里用真实的去重表（H2）验证标识的唯一性和稳定性。
 */
@DisplayName("消费方标识")
class ConsumerIdentityTest {

    private static final Map<String, String> EVENT = Map.of(IntegrationEventDispatcher.EVENT_ID_HEADER, "evt-1");

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private IdempotentConsumer inbox;

    record OrderPaid(
            Long orderId
    ) {
    }

    /**
     * 两个简单类名都叫 {@code Handler} 的消费方：包装在不同的外部类里，就像放在不同的包里一样。
     */
    static class Billing {

        static class Handler extends Counting {
        }
    }

    static class Shipping {

        static class Handler extends Counting {
        }
    }

    static class Counting implements IntegrationEventConsumer<OrderPaid> {

        final AtomicInteger handled = new AtomicInteger();

        private final String id;

        Counting() {
            this(null);
        }

        Counting(String id) {
            this.id = id;
        }

        @Override
        public String group() {
            return "orders";
        }

        @Override
        public String source() {
            return "order-events:paid";
        }

        @Override
        public Class<OrderPaid> payloadType() {
            return OrderPaid.class;
        }

        @Override
        public void handle(ReceivedEvent<OrderPaid> event) {
            handled.incrementAndGet();
        }

        @Override
        public boolean idempotent() {
            return true;
        }

        @Override
        public String consumerId() {
            return id == null ? IntegrationEventConsumer.super.consumerId() : id;
        }
    }

    @BeforeEach
    void inbox() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource("jdbc:h2:mem:consumer-identity;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        inbox = new IdempotentConsumer(jdbc, new DataSourceTransactionManager(dataSource), "processed_message", Clock.systemUTC());
        jdbc.execute("DROP TABLE IF EXISTS processed_message");
        jdbc.execute(inbox.schema());
    }

    @Test
    @DisplayName("同一个组里两个同名的去重消费方：启动失败，而不是让其中一个悄悄跳过全部事件")
    void sameNamedConsumersInOneGroupFailAtStartup() {
        assertThatThrownBy(() -> dispatcher(new Billing.Handler(), new Shipping.Handler()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("share the identity orders:Handler")
                .hasMessageContaining("Override consumerId()");
    }

    @Test
    @DisplayName("补上各自的标识后，同一个事件两个消费方各处理一次；重复投递时各自都只处理一次")
    void distinctIdentitiesEachHandleAnEventOnce() {
        Counting billing = new Counting("billing-ledger");
        Counting shipping = new Counting("shipping-label");
        IntegrationEventDispatcher dispatcher = dispatcher(billing, shipping);

        assertThat(dispatcher.dispatch("orders", "order-events", "paid", EVENT, "{\"orderId\":1}")).isEqualTo(2);
        dispatcher.dispatch("orders", "order-events", "paid", EVENT, "{\"orderId\":1}");

        assertThat(billing.handled).hasValue(1);
        assertThat(shipping.handled).hasValue(1);
    }

    @Test
    @DisplayName("同一个类注册了两个 Bean 也算冲突；匿名类没有类名，必须给出标识")
    void twoBeansOfOneClassAndAnonymousClassesNeedAnId() {
        assertThatThrownBy(() -> dispatcher(new Counting(), new Counting())).hasMessageContaining("share the identity orders:Counting");
        IntegrationEventConsumer<OrderPaid> anonymous = new Counting() {
        };
        assertThatThrownBy(() -> dispatcher(anonymous)).hasMessageContaining("has no consumer id");
    }

    @Test
    @DisplayName("默认标识取自用户类，Spring 生成的代理不改变它，去重记录因此在加不加代理时都一样")
    void aProxyKeepsTheIdentity() {
        Counting plain = new Billing.Handler();
        ProxyFactory factory = new ProxyFactory(new Billing.Handler());
        factory.setProxyTargetClass(true);
        @SuppressWarnings("unchecked")
        IntegrationEventConsumer<OrderPaid> proxied = (IntegrationEventConsumer<OrderPaid>) factory.getProxy();

        assertThat(proxied.getClass().getName()).contains("SpringCGLIB");
        assertThat(proxied.consumerId()).isEqualTo(plain.consumerId()).isEqualTo("Handler");
        assertThat(IntegrationEventDispatcher.inboxName(proxied)).isEqualTo("orders:Handler");
    }

    @Test
    @DisplayName("不去重的消费方不受标识约束：同名也可以共存，各自都会执行")
    void nonIdempotentConsumersAreNotConstrained() {
        Counting first = new Counting() {

            @Override
            public boolean idempotent() {
                return false;
            }
        };
        Counting second = new Counting() {

            @Override
            public boolean idempotent() {
                return false;
            }
        };
        assertThat(dispatcher(first, second).dispatch("orders", "order-events", "paid", EVENT, "{\"orderId\":1}")).isEqualTo(2);
    }

    @SafeVarargs
    private IntegrationEventDispatcher dispatcher(IntegrationEventConsumer<OrderPaid>... consumers) {
        return new IntegrationEventDispatcher(List.of(consumers), jsonMapper, inbox);
    }
}
