package com.example.mall.platform.messaging;

/**
 * 上下文之间传递的消息。每个上下文对外发出的消息是它的契约，类型放在各自的 {@code application.integration} 包里，
 * 与领域事件分开：领域模型可以随意重构，契约要对下游保持稳定。
 */
public interface IntegrationMessage {

    /**
     * 消息标识。在发出时生成并成为消息的一部分，重投时保持不变，消费方据此去重。
     */
    String eventId();
}
