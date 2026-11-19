-- 消费组里一直失败、失败次数达到 ddk.event.rocketmq.consumer.max-attempts 的消息停在这里，由人处理后再决定是否重发
CREATE TABLE t_parked_event
(
    id             BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    consumer_group VARCHAR(100) NOT NULL,
    topic          VARCHAR(100) NOT NULL,
    tag            VARCHAR(100) NULL,
    message_id     VARCHAR(100) NOT NULL,
    event_id       VARCHAR(64)  NULL,
    body           TEXT         NOT NULL,
    attempts       INT          NOT NULL,
    last_error     VARCHAR(500) NOT NULL,
    parked_at      TIMESTAMP    NOT NULL
);

CREATE INDEX idx_parked_event_group ON t_parked_event (consumer_group, parked_at);
