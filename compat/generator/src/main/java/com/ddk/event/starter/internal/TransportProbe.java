package com.ddk.event.starter.internal;

import org.apache.rocketmq.common.message.Message;

/** Reaches the package-private method that builds the RocketMQ message, exactly as 0.10.0 sends it. */
public final class TransportProbe {

    private TransportProbe() {
    }

    public static Message message(RocketMqEventTransport transport, String destination, String key, Object payload) {
        return transport.message(destination, key, payload);
    }
}
