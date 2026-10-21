package com.example.mall.platform;

/**
 * MCP 工具代顾客操作时的授权检查。工具的 {@code customerId} 是模型填的参数，不是可信的身份：
 * {@code secure} profile 下它必须是当前登录的顾客本人，客服可以代任何顾客；开发模式下不检查。
 */
public interface CustomerAccess {

    /**
     * @throws com.ddk.core.exception.BusinessException 当前身份不能代这个顾客操作
     */
    void actFor(long customerId);

    /**
     * @throws com.ddk.core.exception.BusinessException 当前身份不是客服
     */
    void requireSupport();
}
