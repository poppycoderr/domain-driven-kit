/**
 * 业务层：事件订阅方。必须和业务一起提交的工作（翻译成集成消息、标记读模型）用 {@code @EventListener}，
 * 可以丢的通知用 {@code @TransactionalEventListener(phase = AFTER_COMMIT)}。
 */
package ${package}.business.handler;
