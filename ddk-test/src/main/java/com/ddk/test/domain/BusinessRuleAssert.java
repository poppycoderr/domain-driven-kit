package com.ddk.test.domain;

import com.ddk.core.domain.AggregateRoot;
import com.ddk.core.exception.BusinessException;
import com.ddk.core.exception.ErrorCode;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.ThrowableAssert;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

import java.util.Arrays;

/**
 * 对「操作被业务规则拒绝」的断言：抛出的是 {@code BusinessException}，并且错误码、参数符合预期。
 * <p>
 * 按错误码而不是消息文案断言：文案会改，错误码是给调用方的契约。
 */
public final class BusinessRuleAssert extends AbstractAssert<BusinessRuleAssert, BusinessException> {

    private BusinessRuleAssert(BusinessException actual) {
        super(actual, BusinessRuleAssert.class);
    }

    static BusinessRuleAssert of(ThrowingCallable action) {
        Throwable thrown = ThrowableAssert.catchThrowable(action);
        if (thrown == null) {
            throw new AssertionError("Expected the operation to be rejected with a BusinessException, but it succeeded");
        }
        if (!(thrown instanceof BusinessException rejection)) {
            throw new AssertionError("Expected the operation to be rejected with a BusinessException, but it threw " + thrown, thrown);
        }
        return new BusinessRuleAssert(rejection);
    }

    public BusinessRuleAssert withCode(ErrorCode expected) {
        if (!actual.getErrorCode().getCode().equals(expected.getCode())) {
            failWithMessage("Expected rejection with code %s, but the code was %s (%s)", expected.getCode(), actual.getErrorCode().getCode(),
                    actual.getMessage());
        }
        return this;
    }

    /**
     * 错误消息里填入的参数，例如被拒绝的那个值。
     */
    public BusinessRuleAssert withArgs(Object... expected) {
        Object[] args = actual.getArgs() == null ? new Object[0] : actual.getArgs();
        if (!Arrays.equals(args, expected)) {
            failWithMessage("Expected rejection with args %s, but the args were %s", Arrays.toString(expected), Arrays.toString(args));
        }
        return this;
    }

    /**
     * 被拒绝的操作没有在聚合上留下事件。在断言之前先清掉聚合上已有的事件。
     */
    public BusinessRuleAssert withoutRaisingEventsOn(AggregateRoot<?> aggregate) {
        DdkAssertions.assertThat(aggregate).hasRaisedNoEvents();
        return this;
    }
}
