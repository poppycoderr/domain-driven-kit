package com.example.mall.order.domain.model;

import com.ddk.core.exception.BusinessException;
import com.example.mall.order.domain.error.OrderError;

import java.math.BigDecimal;

/**
 * 金额，单位元，最多两位小数，不能为负。
 *
 * @param amount 金额
 */
public record Money(
        BigDecimal amount
) {

    public static final Money ZERO = new Money(BigDecimal.ZERO);

    public Money {
        if (amount == null || amount.signum() < 0 || amount.stripTrailingZeros().scale() > 2) {
            throw new BusinessException(OrderError.INVALID_AMOUNT, amount);
        }
        amount = amount.setScale(2);
    }

    public static Money of(String amount) {
        return new Money(new BigDecimal(amount));
    }

    public Money plus(Money other) {
        return new Money(amount.add(other.amount));
    }

    public Money times(int quantity) {
        return new Money(amount.multiply(BigDecimal.valueOf(quantity)));
    }
}
