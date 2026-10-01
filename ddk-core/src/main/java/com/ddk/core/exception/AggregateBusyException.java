package com.ddk.core.exception;

/**
 * 聚合正被另一个操作独占处理，当前操作在等待时间内没有拿到锁。
 * <p>
 * 与 {@code ConcurrentUpdateException} 的区别：那是提交时才发现版本冲突，工作已经做完又被丢弃；这是开始之前就被拒绝，
 * 什么都还没做。Web 层同样映射为 409 Conflict。
 */
public class AggregateBusyException extends BusinessException {

    public AggregateBusyException(String aggregate) {
        super(CommonError.AGGREGATE_BUSY, aggregate);
    }
}
