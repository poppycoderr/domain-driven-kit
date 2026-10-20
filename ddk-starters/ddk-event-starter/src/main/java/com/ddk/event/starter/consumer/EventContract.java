package com.ddk.event.starter.consumer;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 消费方接受的事件契约：事件类型（消息头 {@code ddk-event-type}）和它能处理的版本（消息头 {@code ddk-event-version}）。
 * <p>
 * 类型不同的消息不交给这个消费方，用于一个主题或标签上有多种事件的情况。类型相同、版本不在列表里的消息，默认跳过并记录警告：
 * 发布方按约定在破坏性变更时并行发布新旧两个版本，只认识旧版本的消费方会同时收到新版本的消息，跳过它们才能继续处理旧版本。
 * 不打算并行发布、希望新版本出现时停下来等消费方升级的，用 {@link #rejectOtherVersions()}。
 * <p>
 * 校验在解析消息体之前进行，消息不会按不认识的版本被解析成消费方的类型。
 *
 * @param type          事件类型，与发布方 {@code @IntegrationEvent(type = ...)} 一致
 * @param versions      能处理的版本
 * @param otherVersions 版本不在列表里（包括消息头缺失或不是整数）时怎么办
 */
public record EventContract(
        String type,

        Set<Integer> versions,

        OtherVersions otherVersions
) {

    /**
     * 版本不在 {@link #versions} 里的消息怎么处理。
     */
    public enum OtherVersions {

        /**
         * 不交给消费方，视为已消费，记录警告日志。
         */
        SKIP,

        /**
         * 抛出异常，消息由消息中间件重新投递，重试耗尽后按中间件的规则进入死信。顺序消费时，同一队列里后面的消息会等待。
         */
        REJECT
    }

    /**
     * @throws IllegalArgumentException 类型为空、版本列表为空或有小于 1 的版本
     */
    public EventContract {
        if (type.isBlank()) {
            throw new IllegalArgumentException("An event contract needs a type");
        }
        if (versions.isEmpty() || versions.stream().anyMatch(version -> version < 1)) {
            throw new IllegalArgumentException("The event contract for " + type + " needs versions of 1 or above, got " + versions);
        }
        versions = Set.copyOf(versions);
    }

    /**
     * 接受这个类型的指定版本，其他版本跳过。
     */
    public static EventContract of(String type, int... versions) {
        return new EventContract(type, Arrays.stream(versions).boxed().collect(Collectors.toSet()), OtherVersions.SKIP);
    }

    /**
     * 同一个契约，但版本不在列表里时拒绝消息。
     */
    public EventContract rejectOtherVersions() {
        return new EventContract(type, versions, OtherVersions.REJECT);
    }
}
