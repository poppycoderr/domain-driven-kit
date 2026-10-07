package com.example.mall.payment.domain.acl;

import com.example.mall.payment.domain.model.Payment;

/**
 * 支付渠道。领域只关心「扣款有没有成功」和「钱退回去了」，渠道的协议、签名、回调都在实现里。
 * <p>
 * 两个操作都以支付单标识作为商户单号，渠道按它去重：同一张支付单重复请求，只扣一次、只退一次。
 * 应用服务因此可以放心地在失败后重试，不需要自己记「有没有请求过渠道」。
 */
public interface PaymentChannel {

    Charge charge(Payment payment);

    void refund(Payment payment);

    /**
     * 扣款结果。
     *
     * @param approved 是否成功
     * @param tradeNo  渠道流水号，成功时才有
     * @param reason   拒绝原因，失败时才有
     */
    record Charge(
            boolean approved,

            String tradeNo,

            String reason
    ) {

        public static Charge approved(String tradeNo) {
            return new Charge(true, tradeNo, null);
        }

        public static Charge declined(String reason) {
            return new Charge(false, null, reason);
        }
    }
}
