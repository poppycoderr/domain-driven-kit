/**
 * 并发控制：按聚合加锁、防重复提交。{@link com.ddk.concurrency.starter.AggregateLock} 与
 * {@link com.ddk.concurrency.starter.Idempotent} 标在应用服务方法上，{@link com.ddk.concurrency.starter.AggregateLocks} 供编程式使用。
 */
@NullMarked
package com.ddk.concurrency.starter;

import org.jspecify.annotations.NullMarked;
