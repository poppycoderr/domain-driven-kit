package com.ddk.event.starter.internal;

/**
 * 让一轮维护在多个实例之间互斥。
 */
public interface MaintenanceLock {

    /**
     * 不加锁：每个实例都执行。
     */
    MaintenanceLock NONE = new MaintenanceLock() {

        @Override
        public void run(Runnable task) {
            task.run();
        }

        @Override
        public boolean distributed() {
            return false;
        }
    };

    /**
     * 拿到锁时执行 {@code task}；别的实例正在执行时跳过这一轮。
     */
    void run(Runnable task);

    /**
     * 是否真的在实例之间互斥。
     */
    boolean distributed();
}
