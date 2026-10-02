package com.ddk.test.domain;

import com.ddk.core.domain.AggregateRoot;
import com.ddk.core.domain.DomainEvent;
import org.assertj.core.api.AbstractAssert;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * 对聚合根的断言，关注它登记了哪些领域事件。
 * <p>
 * 断言读取的是聚合上尚未发布的事件，不会清空它们。要只看某一步操作产生的事件，先调用聚合的 {@code clearEvents()}。
 */
public final class AggregateAssert<A extends AggregateRoot<?>> extends AbstractAssert<AggregateAssert<A>, A> {

    AggregateAssert(A actual) {
        super(actual, AggregateAssert.class);
    }

    /**
     * 登记过至少一个该类型的事件。
     */
    public AggregateAssert<A> hasRaised(Class<? extends DomainEvent> eventType) {
        isNotNull();
        if (eventsOf(eventType).isEmpty()) {
            failWithMessage("Expected %s to have raised %s, but its events were %s", name(), eventType.getSimpleName(), raised());
        }
        return this;
    }

    /**
     * 恰好登记过一个该类型的事件，并且它满足给定的断言。
     */
    public <E extends DomainEvent> AggregateAssert<A> hasRaised(Class<E> eventType, Consumer<? super E> requirements) {
        isNotNull();
        List<E> events = eventsOf(eventType);
        if (events.size() != 1) {
            failWithMessage("Expected %s to have raised exactly one %s, but its events were %s", name(), eventType.getSimpleName(), raised());
        }
        requirements.accept(events.getFirst());
        return this;
    }

    /**
     * 登记的事件类型与顺序和给定的完全一致。
     */
    @SafeVarargs
    public final AggregateAssert<A> hasRaisedExactly(Class<? extends DomainEvent>... eventTypes) {
        isNotNull();
        List<Class<?>> expected = List.of(eventTypes);
        List<Class<?>> raised = actual.domainEvents().stream().<Class<?>>map(Object::getClass).toList();
        if (!raised.equals(expected)) {
            failWithMessage("Expected %s to have raised exactly %s, but its events were %s", name(),
                    Arrays.stream(eventTypes).map(Class::getSimpleName).toList(), raised());
        }
        return this;
    }

    public AggregateAssert<A> hasNotRaised(Class<? extends DomainEvent> eventType) {
        isNotNull();
        if (!eventsOf(eventType).isEmpty()) {
            failWithMessage("Expected %s not to have raised %s, but its events were %s", name(), eventType.getSimpleName(), raised());
        }
        return this;
    }

    public AggregateAssert<A> hasRaisedNoEvents() {
        isNotNull();
        if (actual.hasDomainEvents()) {
            failWithMessage("Expected %s to have raised no events, but its events were %s", name(), raised());
        }
        return this;
    }

    private <E extends DomainEvent> List<E> eventsOf(Class<E> eventType) {
        return actual.domainEvents().stream().filter(eventType::isInstance).map(eventType::cast).toList();
    }

    private String name() {
        return actual.getClass().getSimpleName();
    }

    private List<String> raised() {
        return actual.domainEvents().stream().map(event -> event.getClass().getSimpleName()).toList();
    }
}
