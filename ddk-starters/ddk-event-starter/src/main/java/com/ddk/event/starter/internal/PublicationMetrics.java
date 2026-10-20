package com.ddk.event.starter.internal;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.core.EventPublicationRepository;

import java.util.List;
import java.util.Locale;

/**
 * 发布记录按状态的数量：{@code ddk.event.publications{status=published|processing|failed|resubmitted}}。
 * 已完成的不统计，它们只会越来越多。
 * <p>
 * 每次采集时按状态各查一次发布记录表。{@code MeterRegistry} 和 Modulith 的仓储都在所有单例创建之后才去取。
 */
public class PublicationMetrics implements SmartInitializingSingleton {

    private static final List<EventPublication.Status> WATCHED = List.of(EventPublication.Status.PUBLISHED, EventPublication.Status.PROCESSING,
            EventPublication.Status.FAILED, EventPublication.Status.RESUBMITTED);

    private final ObjectProvider<MeterRegistry> registry;

    private final ObjectProvider<EventPublicationRepository> repository;

    public PublicationMetrics(ObjectProvider<MeterRegistry> registry, ObjectProvider<EventPublicationRepository> repository) {
        this.registry = registry;
        this.repository = repository;
    }

    @Override
    public void afterSingletonsInstantiated() {
        MeterRegistry meters = registry.getIfAvailable();
        EventPublicationRepository publications = repository.getIfAvailable();
        if (meters == null || publications == null) {
            return;
        }
        for (EventPublication.Status status : WATCHED) {
            Gauge.builder("ddk.event.publications", publications, repo -> repo.countByStatus(status))
                    .tag("status", status.name().toLowerCase(Locale.ROOT))
                    .description("Event publications in this status")
                    .register(meters);
        }
    }
}
